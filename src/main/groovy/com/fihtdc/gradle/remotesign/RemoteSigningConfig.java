
package com.fihtdc.gradle.remotesign;

import com.android.annotations.NonNull;

/**
 * Created by Jason on 11/12/2016.
 */
public class RemoteSigningConfig {
    private String mName;

    private String mServerHost = "10.57.63.201";
    private int mServerPort = 8080;
    private String mUsername;
    private String mPassword;

    private String mKeySet = "FIH";
    private String mApkCert = "platform";

    private String mDbUser = "jenkins";
    private String mDbPassword = "fihtdc";

    /**
     * Creates a RemoteSigningConfig.
     */
    public RemoteSigningConfig(@NonNull String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public void setServerHost(@NonNull String host) {
        mServerHost = host;
    }

    public String getServerHost() {
        return mServerHost;
    }

    public void setServerPort(@NonNull Object mServerPort) {
        if (mServerPort instanceof String) {
            mServerPort = Integer.parseInt((String) mServerPort);
        } else if (mServerPort instanceof Integer) {
            mServerPort = (int) mServerPort;
        }
    }

    public int getServerPort() {
        return mServerPort;
    }

    public void setUsername(@NonNull String username) {
        mUsername = username;
    }

    public String getUsername() {
        return mUsername;
    }

    public void setPassword(@NonNull String password) {
        mPassword = password;
    }

    public String getPassword() {
        return mPassword;
    }

    public void setKeySet(@NonNull String keySet) {
        mKeySet = keySet;
    }

    public String getKeySet() {
        return mKeySet;
    }

    public void setApkCert(@NonNull String apkCert) {
        mApkCert = apkCert;
    }

    public String getApkCert() {
        return mApkCert;
    }

    public void setDbUser(@NonNull String user) {
        mDbUser = user;
    }

    public String getDbUser() {
        return mDbUser;
    }

    public void setDbPassword(@NonNull String password) {
        mDbPassword = password;
    }

    public String getDbPassword() {
        return mDbPassword;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "{name=" + mName + ", " +
                "serviceHost=" + mServerHost + ", " +
                "servicePort=" + mServerPort + ", " +
                "username=" + mUsername + ", " +
                "password=" + mPassword + ", " +
                "mKeySet=" + mKeySet + ", " +
                "mApkCert=" + mApkCert + ", " +
                "mDbUser=" + mDbUser + ", " +
                "mDbPassword=" + mDbPassword + "}";
    }
}
