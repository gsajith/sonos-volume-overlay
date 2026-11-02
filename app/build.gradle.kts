plugins {
    id("com.android.application")
}

android {
    namespace = "com.gsajith.sonosvolume"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gsajith.sonosvolume"
        minSdk = 28 // Shield runs Android 11 (API 30)
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
