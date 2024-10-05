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

    //firebase
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation ("com.google.firebase:firebase-analytics-ktx")
    implementation ("com.google.firebase:firebase-config:21.0.2")
    implementation ("com.google.firebase:firebase-firestore-ktx:24.5.0")
    //appcompat
    implementation ("androidx.appcompat:appcompat:1.6.1")
    //recyclerview
    implementation ("androidx.recyclerview:recyclerview:1.3.1")
    //material
    implementation ("com.google.android.material:material:1.9.0")
    //glide
    implementation ("com.github.bumptech.glide:glide:4.15.1")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.14.2")
    implementation ("jp.wasabeef:glide-transformations:4.3.0")
    //media3
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation ("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation ("androidx.media3:media3-datasource-okhttp:1.4.1")
    implementation ("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation ("androidx.media3:media3-datasource:1.4.1")
    //core
    implementation ("androidx.core:core-ktx:1.8.0")
    //coroutines
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")
    //fuel
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation ("com.github.kittinunf.fuel:fuel-gson:2.3.1")
    implementation ("com.squareup.okhttp3:okhttp:4.9.3")
    //viewmodel
    implementation ("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    //work
    implementation ("androidx.work:work-runtime-ktx:2.8.1")
}