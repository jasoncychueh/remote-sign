[![GitHub license](https://img.shields.io/github/license/dcendents/android-maven-gradle-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![](https://jitpack.io/v/jasoncychueh/remote-sign.svg)](https://jitpack.io/#jasoncychueh/remote-sign/1.2.0)

# Remote Sign Gradle Plugin
> A gradle plugin can **only** be used internally in **Foxconn** for remote signing APK

### Apply remote sign plugin

Add build script dependency to your module's `build.gradle`
```gradle
buildscript {
    repositories {
        jcenter()
        maven { url "https://jitpack.io" }
    }
    dependencies {
        classpath 'com.github.jasoncychueh:remote-sign:1.2.0'
    }
}
```

Then, apply the plugin after android application plugin:

```gradle
apply plugin: 'com.android.application'
apply plugin: 'com.fihtdc.gradle.remote-sign'
```

### Configuration

Under android scope, define keysets in remoteSigningConfigs closure.
```gradle
android {
    compileSdkVersion 24
    buildToolsVersion "25"

    /* Skip some configurations... */

    remoteSigningConfigs {
        keyset1 {
            username 'YOUR_COMPANY_USERNAME'
            password 'YOUR_COMPANY_PASSWORD'
            keySet 'KEYSET'
            apkCert 'APK_CERT'
        }
        keyset2 {
            username 'YOUR_COMPANY_USERNAME'
            password 'YOUR_COMPANY_PASSWORD'
            keySet 'KEYSET'
            apkCert 'APK_CERT'
        }
    }
}
```

And then specify the remoteSigningConfig within your build types.
```gradle
android {

    /* Skip some configurations... */

    buildTypes {
        debug {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
            remoteSigningConfig remoteSigningConfigs.keyset1
        }
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
            remoteSigningConfig remoteSigningConfigs.keyset2
        }
        someOtherRelease1 {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
            remoteSigningConfig remoteSigningConfigs.keyset1
        }
        someOtherRelease2 {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
            remoteSigningConfig remoteSigningConfigs.keyset2
        }
    }
}
```

You can also set the remoteSigningConfig in product flavors or default config. If the product flavor and build type both contains remote signing config, the one in the build type will be taken.
```gradle
android {

    /* Skip some configurations... */
    defaultConfig {
        applicationId "your.application.id"
        minSdkVersion 25
        targetSdkVersion 25
        versionCode 1
        versionName "1.0"
        testInstrumentationRunner "android.support.test.runner.AndroidJUnitRunner"
        remoteSigningConfig remoteSigningConfigs.keyset1
    }

    productFlavors {
            remoteSigningConfig remoteSigningConfigs.keyset1
        }
        flavor2 {
            remoteSigningConfig remoteSigningConfigs.keyset2
        }
    }
}
```
