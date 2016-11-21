package com.fihtdc.gradle.remotesign

import com.android.annotations.NonNull
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.internal.BadPluginException
import com.android.build.gradle.internal.TaskContainerAdaptor
import com.android.build.gradle.internal.TaskFactory
import com.android.build.gradle.internal.api.ReadOnlyBuildType
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.scope.AndroidTask
import com.android.build.gradle.internal.scope.AndroidTaskRegistry
import com.android.build.gradle.internal.scope.DefaultGradlePackagingScope
import com.android.build.gradle.internal.scope.VariantOutputScope
import com.android.build.gradle.internal.variant.ApkVariantOutputData
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject

/**
 * Created by Jason on 11/12/2016.
 */
class RemoteSignPlugin implements Plugin<Project> {
    private Instantiator mInstantiator;

    private mProperties = Collections.synchronizedMap([:])

    def getRemoteSigningConfig(delegate) {
        def buildType = delegate instanceof ReadOnlyBuildType ? delegate.buildType : delegate
        return mProperties[System.identityHashCode(buildType) + "remoteSigningConfig"]
    }

    def remoteSigningConfig(delegate, config) {
        def buildType = delegate instanceof ReadOnlyBuildType ? delegate.buildType : delegate
        mProperties[System.identityHashCode(buildType) + "remoteSigningConfig"] = config
    }

    @Inject
    RemoteSignPlugin(@NonNull Instantiator instantiator) {
        mInstantiator = instantiator;
    }

    void apply(Project project) {
        if (project.plugins.hasPlugin("com.android.application")) {
            throw new BadPluginException(
                    "The 'android' plugin has been applied. Remote sign plugin needs to be applied before 'android' plugin.")
        }

        NamedDomainObjectContainer<RemoteSigningConfig> remoteSigningConfigs = project
                .container(RemoteSigningConfig, new RemoteSigningConfigFactory(mInstantiator, project.logger));

        BaseExtension.metaClass.remoteSigningConfigs = { Closure closure ->
            delegate.checkWritability();
            remoteSigningConfigs.configure(closure)
        }

        BaseExtension.metaClass.getRemoteSigningConfigs = { remoteSigningConfigs }

        BuildType.metaClass.getRemoteSigningConfig << { getRemoteSigningConfig(delegate) }

        BuildType.metaClass.remoteSigningConfig << { RemoteSigningConfig config ->
            remoteSigningConfig(delegate, config)
        }

        project.getPluginManager().withPlugin('com.android.application', { plugin ->
            project.afterEvaluate {
                project.logger.debug("Creating task remoteSign for each build type.")

                def appPlugin = project.plugins.findPlugin(plugin.id)
                def TaskFactory tasks = new TaskContainerAdaptor(project.getTasks());
                AndroidTaskRegistry androidTasks = appPlugin.taskManager.androidTasks

                project.android.applicationVariants.each { ApplicationVariant variant ->
                    variant.buildType.metaClass.getRemoteSigningConfig << {
                        getRemoteSigningConfig(delegate)
                    }

                    variant.buildType.metaClass.remoteSigningConfig << { RemoteSigningConfig config ->
                        remoteSigningConfig(delegate, config)
                    }

                    // println variant.buildType.remoteSigningConfig
                    if (variant.buildType.remoteSigningConfig != null) {
                        List<ApkVariantOutputData> outputDataList = variant.apkVariantData.outputs

                        def files = []
                        variant.outputs.each { output ->
                            files << output.packageApplication.outputFile
                        }

                        outputDataList.each { outputData ->
                            VariantOutputScope outputScope = outputData.scope

                            DefaultGradlePackagingScope packagingScope = new DefaultGradlePackagingScope(outputScope);
                            def configAction = new RemoteSigningTask.ConfigAction(packagingScope, variant)

                            AndroidTask<RemoteSigningTask> signingTask = androidTasks.create(tasks, configAction)
                            AndroidTask<?> packageTask = androidTasks.get(packagingScope.getTaskName('package'));
                            AndroidTask<?> assembleTask = androidTasks.get(packagingScope.getTaskName('assemble'))

                            /*println "Origin packageTask: ${packageTask.downstreamTasks}"
                            println "Origin assembleTask: ${assembleTask.downstreamTasks}"*/

                            signingTask.dependsOn(tasks, packageTask)
                            assembleTask.dependsOn(tasks, signingTask)

                            /*println "New packageTask: ${packageTask.downstreamTasks}"
                            println "New assembleTask: ${assembleTask.downstreamTasks}"*/
                        }
                    }
                }
            }
        })
    }
}