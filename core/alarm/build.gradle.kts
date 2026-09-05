plugins {
    alias(libs.plugins.slowclock.android.library)
    alias(libs.plugins.slowclock.android.hilt)
}

android {
    namespace = "com.example.slowclock.core.alarm"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:data")) // Notifier 인터페이스 구현 (역결합 제거)
    implementation(libs.androidx.core.ktx)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.auth)
}
