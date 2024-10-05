plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)

    id("com.google.gms.google-services")
}

android {
    namespace = "com.creativem.fulltv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.creativem.fulltv"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables {
            useSupportLibrary = true
        }

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        viewBinding = true
    }

}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation ("com.google.firebase:firebase-config:21.0.2")

    implementation ("androidx.appcompat:appcompat:1.6.1")
    implementation ("androidx.recyclerview:recyclerview:1.3.1")
    implementation ("com.google.android.material:material:1.9.0")
    implementation ("com.github.bumptech.glide:glide:4.15.1")

    implementation ("com.google.firebase:firebase-firestore-ktx:24.5.0")

    implementation(platform("com.google.firebase:firebase-bom:32.1.1"))
    implementation ("com.google.firebase:firebase-firestore-ktx")

    implementation ("com.google.firebase:firebase-analytics-ktx")

    implementation("androidx.media3:media3-exoplayer:1.4.1") // Dependencia principal
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1") // Soporte para DASH
    implementation("androidx.media3:media3-ui:1.4.1") // Interfaz de usuario
    implementation ("androidx.media3:media3-exoplayer-hls:1.4.1") // Para soporte HLS
    implementation ("androidx.media3:media3-datasource-okhttp:1.4.1")
    implementation ("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation ("androidx.media3:media3-datasource:1.4.1")
//    implementation("androidx.media3:media3-exoplayer-ffmpeg:1.4.1")

    implementation ("com.google.android.material:material:1.7.0")
    implementation ("androidx.core:core-ktx:1.8.0")
    implementation ("com.squareup.okhttp3:okhttp:4.9.3")

    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")
    implementation ("com.github.kittinunf.fuel:fuel-gson:2.3.1")

    implementation ("com.github.bumptech.glide:glide:4.14.2")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.14.2")
    implementation ("jp.wasabeef:glide-transformations:4.3.0")




}

