plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("kotlin-kapt")
}

android {
    namespace = "com.creativem.fulltv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.creativem.fulltv"
        minSdk = 24
        targetSdk = 34
        versionCode = 10
        versionName = "1.0.51"
        ndk {
            // Solo incluye las arquitecturas más comunes para bajar el peso
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.13"
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs.add("-P")
            freeCompilerArgs.add("plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Firebase (BOM)
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Google Auth y QR
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.zxing:core:3.5.3")

    // UI y Material Tradicional
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Glide y Coil
    implementation("com.github.bumptech.glide:glide:4.15.1")
    kapt("com.github.bumptech.glide:compiler:4.15.1")
    implementation("jp.wasabeef:glide-transformations:4.3.0")

    // 🖼️ Coil para Jetpack Compose (CORREGIDO)
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-compose:2.7.0") // 👈 AÑADIDO: Requerido para AsyncImage
    implementation("io.coil-kt:coil-svg:2.7.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-gson:2.3.1")
    implementation("com.android.volley:volley:1.2.1")

    // 🎧 Media3 ExoPlayer (UNIFICADO A 1.4.1)
    val media3Version = "1.9.1" // Versión estable más reciente
    implementation("androidx.media3:media3-exoplayer:${media3Version}")
    implementation("androidx.media3:media3-exoplayer-dash:${media3Version}")
    implementation("androidx.media3:media3-exoplayer-hls:${media3Version}")
    implementation("androidx.media3:media3-exoplayer-rtsp:${media3Version}")
    implementation("androidx.media3:media3-ui:${media3Version}")
    implementation("androidx.media3:media3-common:${media3Version}")
    implementation("androidx.media3:media3-session:${media3Version}")
    implementation("androidx.media3:media3-extractor:${media3Version}")
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-process:2.8.1")

    // 🎨 JETPACK COMPOSE (CORREGIDO Y COMPLETADO)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3") // 👈 AÑADIDO: Requerido para la interfaz Material3
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
}