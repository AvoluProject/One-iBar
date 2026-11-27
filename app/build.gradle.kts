import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.coerceAtLeast(1) ?: 20
val signingStore = System.getenv("SIGNING_KEYSTORE_PATH")
val signingStorePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
val signingAlias = System.getenv("SIGNING_KEY_ALIAS")
val signingKeyPassword = System.getenv("SIGNING_KEY_PASSWORD")
val hasReleaseSigning = !signingStore.isNullOrBlank() &&
    !signingStorePassword.isNullOrBlank() && !signingAlias.isNullOrBlank() &&
    !signingKeyPassword.isNullOrBlank()

android {
    namespace = "com.avolu.oneibar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.avolu.oneibar"
        minSdk = 31
        targetSdk = 35
        versionCode = 2000 + runNumber
        versionName = "0.2.0-alpha03"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    signingConfigs {
        if (hasReleaseSigning) {
            create("stableRelease") {
                storeFile = file(signingStore!!)
                storePassword = signingStorePassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("stableRelease")
        }
    }

    buildFeatures { buildConfig = true }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
}
