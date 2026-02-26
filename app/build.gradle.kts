plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.villazon.cybercam3"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.villazon.cybercam3"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
    // ESTO ES CLAVE PARA LA COMPATIBILIDAD CON COMPOSE
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    // ... Tus dependencias quedan igual (CameraX, AdMob, Material3, etc.)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Google AdMob
    implementation("com.google.android.gms:play-services-ads:23.0.0")

    // CameraX
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")

    // Iconos
    implementation("androidx.compose.material:material-icons-extended:1.6.3")

    // Solución para el error de ListenableFuture en CameraX
    implementation("com.google.guava:guava:32.1.3-android")

    // Solución para el error de LocalLifecycleOwner en Compose
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
}