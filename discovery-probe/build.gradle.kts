plugins { id("com.android.application") }

android {
    namespace = "org.pockettts.android.discoveryprobe"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.pockettts.android.discoveryprobe"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
