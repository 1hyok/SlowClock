plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.slowclock.feature.profile"
    compileSdk = 35
    defaultConfig { minSdk = 32 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data")) // ProfileScreen 이 FirestoreDB 직접 사용 — #29 에서 ViewModel 화 예정
    implementation(libs.firebase.auth)
}
