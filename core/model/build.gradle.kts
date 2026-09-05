plugins {
    alias(libs.plugins.slowclock.android.library)
}

android {
    namespace = "com.example.slowclock.core.model"
}

dependencies {
    // 모델 public API 가 Firebase Timestamp/annotations 를 노출하므로 api 로 전파
    api(platform(libs.firebase.bom))
    api(libs.firebase.firestore)
    implementation(libs.kotlinx.serialization.core)
}
