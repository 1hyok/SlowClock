plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.slowclock.core.model"
    compileSdk = 35

    defaultConfig {
        minSdk = 32
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // 모델 public API 가 Firebase Timestamp/annotations 를 노출하므로 api 로 전파
    api(platform(libs.firebase.bom))
    api(libs.firebase.firestore)
    implementation(libs.kotlinx.serialization.core)
}
