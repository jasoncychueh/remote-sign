[![GitHub license](https://img.shields.io/github/license/dcendents/android-maven-gradle-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![](https://jitpack.io/v/jasoncychueh/remote-sign.svg)](https://jitpack.io/#jasoncychueh/remote-sign/1.0.1)

# Remote Sign Gradle Plugin
> A gradle plugin which only can be used internally in Foxconn for remote signing APK

### Apply remote sign plugin

Add build script dependency to your module's `build.gradle`
```gradle
buildscript {
    repositories {
        jcenter()
        maven { url "https://jitpack.io" }
    }
    dependencies {
        classpath 'com.github.jasoncychueh:remote-sign:1.0.1'
    }
}
```

Then, apply the plugin before android application plugin:

```gradle
// must before android application plugin
apply plugin: 'com.fihtdc.gradle.remote-sign'
apply plugin: 'com.android.application'
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
