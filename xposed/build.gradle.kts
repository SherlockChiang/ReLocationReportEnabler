plugins {
    alias(libs.plugins.agp.app)
}

val verName: String by rootProject.extra
val verCode: Int by rootProject.extra
val releaseStoreFile = providers.environmentVariable("RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }
val requireReleaseSigning = providers.environmentVariable("REQUIRE_RELEASE_SIGNING")
    .map(String::toBoolean)
    .orElse(false)
    .get()

if (requireReleaseSigning && !hasReleaseSigning) {
    error("Release signing is required but one or more RELEASE_* environment variables are missing")
}

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

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(libs.xposed.api)
    testImplementation(libs.junit)
}
