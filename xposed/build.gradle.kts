plugins {
    alias(libs.plugins.agp.app)
}

val verName: String by rootProject.extra
val verCode: Int by rootProject.extra

android {
    namespace = "io.github.timeline_unlocker.xposed"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.timeline_unlocker.xposed"
        minSdk = 26
        targetSdk = 36
        versionCode = verCode
        versionName = verName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(libs.xposed.api)
}
