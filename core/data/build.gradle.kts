plugins {
    alias(libs.plugins.slowclock.android.library)
    alias(libs.plugins.slowclock.android.hilt)
    // 걸어 둔 알람 장부를 JSON 으로 남긴다(#127).
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.slowclock.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))

    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
