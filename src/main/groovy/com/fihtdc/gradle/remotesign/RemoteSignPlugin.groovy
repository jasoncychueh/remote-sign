package com.fihtdc.gradle.remotesign

import com.android.annotations.NonNull
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.internal.BadPluginException
import com.android.build.gradle.internal.TaskContainerAdaptor
import com.android.build.gradle.internal.api.ReadOnlyBuildType
import com.android.build.gradle.internal.api.ReadOnlyProductFlavor
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.scope.AndroidTaskRegistry
import com.android.build.gradle.internal.scope.DefaultGradlePackagingScope
import com.android.build.gradle.internal.tasks.InstallVariantTask
import com.android.builder.model.ProductFlavor
import org.apache.commons.lang3.reflect.FieldUtils
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject
import java.lang.reflect.Field

/**
 * Created by Jason on 11/12/2016.
 */
class RemoteSignPlugin implements Plugin<Project> {
    private Instantiator mInstantiator;

    private mProperties = Collections.synchronizedMap([:])

    def getRemoteSigningConfig(delegate) {
        delegate = getRealDelegate(delegate)
        return mProperties[System.identityHashCode(delegate) + "_RemoteSigningConfig"]
    }

    def setRemoteSigningConfig(delegate, config) {
        delegate = getRealDelegate(delegate)
        mProperties[System.identityHashCode(delegate) + "_RemoteSigningConfig"] = config
    }

    def getRealDelegate(delegate) {
        if (delegate.class == ReadOnlyBuildType.class) {
            Field field = delegate.class.getDeclaredField("buildType");
            field.setAccessible(true);
            return field.get(delegate);
        } else if (delegate.class == ReadOnlyProductFlavor.class) {
            Field field = delegate.class.getDeclaredField("productFlavor");
            field.setAccessible(true);
            return field.get(delegate);
        }
        return delegate;
    }

    def getTaskRegistry(def appPlugin) {
        return FieldUtils.getField(appPlugin.class, "taskManager", true).get(appPlugin).androidTasks
    }

    private void setSigningConfig(def variant, def signingConfig) {
        def variantConfig = variant.variantData.variantConfiguration
        Field field = FieldUtils.getField(variantConfig.class, "mSigningConfigOverride", true)
        field.set(variantConfig, signingConfig)
    }

    @Inject
    RemoteSignPlugin(@NonNull Instantiator instantiator) {
        mInstantiator = instantiator;
    }

    void apply(Project project) {
        if (!project.plugins.hasPlugin("com.android.application")) {
            throw new BadPluginException("Remote sign plugin needs to work with 'android' plugin."
                    + " Did you apply remote sign plugin before 'android' plugin?")
        }

        def configFactory = new RemoteSigningConfigFactory(mInstantiator, project.logger)
        NamedDomainObjectContainer<RemoteSigningConfig> remoteSigningConfigs = project
                .container(RemoteSigningConfig, configFactory);

        BaseExtension.metaClass.remoteSigningConfigs = { Closure closure ->
            delegate.checkWritability();
            remoteSigningConfigs.configure(closure)
        }

        BaseExtension.metaClass.getRemoteSigningConfigs = { remoteSigningConfigs }

        BuildType.metaClass.getRemoteSigningConfig << { getRemoteSigningConfig(delegate) }

        BuildType.metaClass.remoteSigningConfig << { config ->
            setRemoteSigningConfig(delegate, config)
        }

        ReadOnlyBuildType.metaClass.getRemoteSigningConfig << {
            delegate.buildType.remoteSigningConfig
        }

        ProductFlavor.metaClass.getRemoteSigningConfig << { getRemoteSigningConfig(delegate) }

        ProductFlavor.metaClass.remoteSigningConfig << { config ->
            setRemoteSigningConfig(delegate, config)
        }

        ReadOnlyProductFlavor.metaClass.getRemoteSigningConfig << {
            delegate.productFlavor.remoteSigningConfig
        }

        project.getPluginManager().withPlugin('com.android.application', { plugin ->
            project.afterEvaluate {
                project.logger.debug("Creating task remoteSign for each build type.")

                def appPlugin = project.plugins.findPlugin(plugin.id)
                def tasks = new TaskContainerAdaptor(project.getTasks());
                AndroidTaskRegistry androidTasks = getTaskRegistry(appPlugin)

                project.android.applicationVariants.each { ApplicationVariant variant ->
                    variant.buildType.metaClass.getRemoteSigningConfig << {
                        getRemoteSigningConfig(delegate)
                    }

                    variant.metaClass.getRemoteSigningConfig << {
                        getRemoteSigningConfig(delegate)
                    }

                    def realBuildType = getRealDelegate(variant.buildType);
                    realBuildType.metaClass.getRemoteSigningConfig << {
                        getRemoteSigningConfig(delegate)
                    }
                    realBuildType.metaClass.setRemoteSigningConfig << { config ->
                        setRemoteSigningConfig(delegate, config)
                    }

                    if (realBuildType.remoteSigningConfig == null) {
                        def flavors = variant.variantData.variantConfiguration.productFlavors
                        flavors.reverseEach { flavor ->
                            if (flavor.remoteSigningConfig != null) {
                                setRemoteSigningConfig(variant, flavor.remoteSigningConfig)
                            }
                        }
                    } else {
                        setRemoteSigningConfig(variant, realBuildType.remoteSigningConfig)
                    }

                    if (variant.signingConfig == null) {
                        setSigningConfig(variant, project.android.signingConfigs.debug)
                    }

                    if (variant.remoteSigningConfig != null) {
                        variant.apkVariantData.outputs.each { outputData ->
                            variant.metaClass.getRemoteSigningConfig << {
                                getRemoteSigningConfig(delegate)
                            }

                            def scope = new DefaultGradlePackagingScope(outputData.scope);
                            def configAction = new RemoteSigningTask.ConfigAction(scope, variant)

                            def signingTask = androidTasks.create(tasks, configAction)
                            def packageTask = androidTasks.get(scope.getTaskName('package'))
                            def assembleTask = androidTasks.get(scope.getTaskName('assemble'))
                            def installTask = androidTasks.get(scope.getTaskName('install'))

                            /*println "Origin packageTask: ${packageTask.downstreamTasks}"
                            println "Origin assembleTask: ${assembleTask.downstreamTasks}"*/

                            signingTask.dependsOn(tasks, packageTask)
                            assembleTask.dependsOn(tasks, signingTask)

                            /*println "New packageTask: ${packageTask.downstreamTasks}"
                            println "New assembleTask: ${assembleTask.downstreamTasks}"*/

                            if (installTask == null) {
                                def variantScope = outputData.scope.variantScope
                                configAction = new InstallVariantTask.ConfigAction(variantScope)
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