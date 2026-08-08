plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = providers.environmentVariable("POCKETTTS_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("POCKETTTS_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("POCKETTTS_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("POCKETTTS_KEY_PASSWORD").orNull
val requiredNdkVersion = "27.2.12479018"
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "org.pockettts.android.engine"
    compileSdk = 35
    // A CI/WSL build may provide a complete NDK outside the Android SDK.
    // Android Studio falls back to the version managed by the SDK.
    ndkVersion = requiredNdkVersion
    providers.environmentVariable("ANDROID_NDK_HOME").orNull?.let { candidate ->
        val sourceProperties = file("$candidate/source.properties")
        val matchesRequiredVersion = sourceProperties.isFile && sourceProperties
            .readLines()
            .any { it.trim() == "Pkg.Revision = $requiredNdkVersion" }
        if (matchesRequiredVersion) ndkPath = candidate
    }

    defaultConfig {
        applicationId = "org.pockettts.android.engine"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "0.5.1"
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake { cppFlags += listOf("-std=c++17", "-O3") }
        }
    }

    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    buildFeatures { buildConfig = true }
    packaging { jniLibs.useLegacyPackaging = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation("junit:junit:4.13.2")
}
