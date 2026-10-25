plugins { id("com.android.application") }

android {
    namespace = "org.pockettts.android.settingsprobe"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.pockettts.android.settingsprobe"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
