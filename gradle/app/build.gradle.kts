plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)

    // вместо kapt
    id("com.google.devtools.ksp")
}

android {
    namespace = "production.d1tan.d1tnotes"
    compileSdk = 35

    defaultConfig {
        applicationId = "production.d1tan.d1tnotes"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // Важно: включаем Compose
    buildFeatures {
        compose = true
    }
    // При Kotlin 2.x блок composeOptions можно НЕ писать — компилятор берётся из плагина
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.compose.ui.ui.graphics)
    implementation(libs.androidx.compose.ui.ui.tooling.preview)
    implementation(libs.androidx.compose.material3.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.activity.compose.v193)
    implementation(libs.androidx.lifecycle.runtime.ktx.v287)

    // --- ROOM (ОСНОВНОЕ) ---
    val roomVersion = "2.8.4"

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")

    ksp("androidx.room:room-compiler:$roomVersion")
}