plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.slowclock.core.data"
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
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:alarm")) // GuardianNotifier (FCM 전송) 호출

    implementation(libs.firebase.auth)

    // Hilt: @Inject 생성자 Repository 의 Factory 가 본 모듈에서 생성되도록
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
