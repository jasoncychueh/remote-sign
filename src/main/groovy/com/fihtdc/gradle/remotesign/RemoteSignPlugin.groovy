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
import com.android.build.gradle.internal.tasks.InstallVariantTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject

/**
 * Created by Jason on 11/12/2016.
 */
class RemoteSignPlugin implements Plugin<Project> {
    private Instantiator mInstantiator;

    private mProperties = Collections.synchronizedMap([:])

    def getRemoteSigningConfig(delegate) {
        return mProperties[System.identityHashCode(delegate) + "remoteSigningConfig"]
    }

    def remoteSigningConfig(delegate, config) {
        mProperties[System.identityHashCode(delegate) + "remoteSigningConfig"] = config
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

        BuildType.metaClass.remoteSigningConfig << { config ->
            remoteSigningConfig(delegate, config)
        }

        ReadOnlyBuildType.metaClass.getRemoteSigningConfig << {
            getRemoteSigningConfig(delegate.buildType)
        }

        project.getPluginManager().withPlugin('com.android.application', { plugin ->
            project.afterEvaluate {
                project.logger.debug("Creating task remoteSign for each build type.")

                def appPlugin = project.plugins.findPlugin(plugin.id)
                def TaskFactory tasks = new TaskContainerAdaptor(project.getTasks());
                AndroidTaskRegistry androidTasks = appPlugin.taskManager.androidTasks

                project.android.applicationVariants.each { ApplicationVariant variant ->
                    if (variant.buildType.remoteSigningConfig != null) {
                        variant.apkVariantData.outputs.each { outputData ->
                            DefaultGradlePackagingScope packagingScope = new DefaultGradlePackagingScope(outputData.scope);
                            def configAction = new RemoteSigningTask.ConfigAction(packagingScope, variant)

                            AndroidTask<RemoteSigningTask> signingTask = androidTasks.create(tasks, configAction)
                            AndroidTask<? extends Task> packageTask = androidTasks.get(packagingScope.getTaskName('package'))
                            AndroidTask<? extends Task> assembleTask = androidTasks.get(packagingScope.getTaskName('assemble'))
                            AndroidTask<? extends Task> installTask = androidTasks.get(packagingScope.getTaskName('install'))

                            /*println "Origin packageTask: ${packageTask.downstreamTasks}"
                            println "Origin assembleTask: ${assembleTask.downstreamTasks}"*/

                            signingTask.dependsOn(tasks, packageTask)
                            assembleTask.dependsOn(tasks, signingTask)

                            /*println "New packageTask: ${packageTask.downstreamTasks}"
                            println "New assembleTask: ${assembleTask.downstreamTasks}"*/

                            def task = project.tasks.getByName(packageTask.name)
                            task.outputFile = new File("${task.outputFile}".replaceAll("-unsigned", ""))

                            if (installTask == null) {
                                configAction = new InstallVariantTask.ConfigAction(outputData.scope.variantScope)
                                installTask = androidTasks.create(tasks, configAction)
                                installTask.dependsOn(tasks, assembleTask)
                            }
                        }
                    }
                }
            }
        })
    }
}