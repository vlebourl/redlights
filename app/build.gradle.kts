import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
}

// Load keystore properties from keystore.properties file
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.tiarkaerell.redlights"
    compileSdk = 36

    signingConfigs {
        create("release") {
            // Secure password loading: prioritizes environment variables over file
            // 1st priority: Environment variable
            // 2nd priority: keystore.properties (if not blank)
            // 3rd priority: Default/empty

            val propKeyAlias = keystoreProperties.getProperty("keyAlias")?.takeIf { it.isNotBlank() }
            val propKeyPassword = keystoreProperties.getProperty("keyPassword")?.takeIf { it.isNotBlank() }
            val propStoreFile = keystoreProperties.getProperty("storeFile")?.takeIf { it.isNotBlank() }
            val propStorePassword = keystoreProperties.getProperty("storePassword")?.takeIf { it.isNotBlank() }

            keyAlias = System.getenv("REDLIGHTS_KEYSTORE_ALIAS")
                ?: propKeyAlias
                ?: "redlights-release"

            keyPassword = System.getenv("REDLIGHTS_KEYSTORE_PASSWORD")
                ?: propKeyPassword
                ?: ""

            storeFile = rootProject.file(System.getenv("REDLIGHTS_KEYSTORE_FILE")
                ?: propStoreFile
                ?: "app/redlights-production.jks")

            storePassword = System.getenv("REDLIGHTS_KEYSTORE_PASSWORD")
                ?: propStorePassword
                ?: ""
        }
    }

    defaultConfig {
        applicationId = "com.tiarkaerell.redlights"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // MapLibre
    implementation(libs.maplibre.android)

    // Location Services
    implementation(libs.play.services.location)

    // Debug Tools
    debugImplementation(libs.leakcanary.android)

    // Testing
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
