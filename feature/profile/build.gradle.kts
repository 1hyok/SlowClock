plugins {
    alias(libs.plugins.slowclock.android.feature)
    alias(libs.plugins.compose.screenshot)
}

android {
    namespace = "com.example.slowclock.feature.profile"
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    // 계정을 지우면 이 기기에 걸린 알람도 근거를 잃는다. 함께 지운다(#127).
    implementation(project(":core:alarm"))
    screenshotTestImplementation(libs.androidx.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)
}
