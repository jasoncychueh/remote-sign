
package com.fihtdc.gradle.remotesign;

import com.android.annotations.NonNull;

import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.internal.reflect.Instantiator;
import org.slf4j.Logger;

/**
 * Created by JasonCYChueh on 2016/11/15.
 */
public class RemoteSigningConfigFactory implements NamedDomainObjectFactory<RemoteSigningConfig> {
    private final Instantiator mInstantiator;
    private final Logger mLogger;

    public RemoteSigningConfigFactory(Instantiator instantiator, Logger logger) {
        mInstantiator = instantiator;
        mLogger = logger;
    }

    @Override
    @NonNull
    public RemoteSigningConfig create(@NonNull String name) {
        RemoteSigningConfig config = mInstantiator.newInstance(RemoteSigningConfig.class, name);
        mLogger.info("Created {}", config);
        return config;
    }
}
