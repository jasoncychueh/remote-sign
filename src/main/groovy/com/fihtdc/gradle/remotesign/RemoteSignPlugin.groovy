package com.fihtdc.gradle.remotesign

import com.android.annotations.NonNull
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.BadPluginException
import com.android.build.gradle.internal.dsl.BuildType
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

        BuildType.metaClass.remoteSigningConfig << { RemoteSigningConfig config ->
            remoteSigningConfig(delegate, config)
        }

        project.getPluginManager().withPlugin('com.android.application', {
            project.afterEvaluate {
                project.logger.debug("Creating task remoteSign for each build type.")
                project.android.buildTypes.findAll {
                    it.remoteSigningConfig != null
                }.each { buildType ->
                    project.logger.debug("Creating task remoteSign${buildType.name.capitalize()}...")
                    def signTask = project.task("remoteSign${buildType.name.capitalize()}") << {
                        project.logger.info("Start remote signing apk...")

                        def variant = project.android.applicationVariants.find {
                            it.name == buildType.name
                        }

                        variant.outputs.each { output ->
                            File file = output.packageApplication.outputFile
                            new RemoteSigner(project.logger, buildType.remoteSigningConfig).signApk(file)
                        }
                    }
                    def assembleTask = project.tasks["assemble${buildType.name.capitalize()}"]

                    def packageTask = project.tasks["package${buildType.name.capitalize()}"]

                    signTask.dependsOn packageTask
                    assembleTask.dependsOn signTask
                }
            }
        })
    }
}