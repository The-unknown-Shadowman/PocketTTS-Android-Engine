buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP supplies built-in Kotlin; keep its compiler at the reviewed version.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
}
