plugins {
    alias(libs.plugins.slowclock.android.library)
    alias(libs.plugins.slowclock.android.hilt)
}

android {
    namespace = "com.example.slowclock.core.alarm"
}

dependencies {
    api(project(":core:model"))
    // 반복 규칙을 알람 경로가 함께 본다(#130).
    implementation(project(":core:common"))
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    implementation(project(":core:data")) // Notifier 인터페이스 구현 (역결합 제거)
    implementation(libs.androidx.core.ktx)
    // FCMService의 공개 superclass/메시지 인자와 Hilt service 계층을 앱 lint에서도 해석한다.
    api(libs.firebase.messaging)
    implementation(libs.firebase.auth)
}
