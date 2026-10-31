plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Número de construcción: lo pone GitHub Actions solo (1, 2, 3...)
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.luisell.bestkeyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.luisell.bestkeyboard"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNumber
        versionName = "7.5 (build $buildNumber)"
        // Solo los procesadores de teléfonos reales: la app pesa menos
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Firma fija: así cada APK nuevo se instala ENCIMA del anterior sin borrar nada
    signingConfigs {
        create("fija") {
            storeFile = rootProject.file("app-keystore.jks")
            storePassword = "bestkeyboard"
            keyAlias = "bestkeyboard"
            keyPassword = "bestkeyboard"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("fija")
        }
        debug {
            signingConfig = signingConfigs.getByName("fija")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Traducción dentro del teléfono (Google ML Kit, gratis)
    implementation("com.google.mlkit:translate:17.0.3")
    // Leer texto de fotos dentro del teléfono (Google ML Kit, gratis, sin internet)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Cámara dentro del teclado (CameraX de Google)
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.lifecycle:lifecycle-runtime:2.6.2")
    testImplementation("junit:junit:4.13.2")
}
