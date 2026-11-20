plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tua.claroscuro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tua.claroscuro"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // Llave fija: cada APK nuevo se instala encima del anterior
    signingConfigs {
        create("claro") {
            storeFile = file("claroscuro.keystore")
            storePassword = "claro123"
            keyAlias = "claroscuro"
            keyPassword = "claro123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("claro")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("claro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
}
