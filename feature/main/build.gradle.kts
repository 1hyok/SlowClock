plugins {
    alias(libs.plugins.slowclock.android.feature)
    // 화면 미리보기를 PNG 로 굳혀 시각 회귀를 잡는다. 화면이 이 모듈 안에서 internal 이라
    // :app 의 screenshotTest 에서는 부를 수 없어 모듈마다 붙인다(#109).
    alias(libs.plugins.compose.screenshot)
}

android {
    namespace = "com.example.slowclock.feature.main"
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    implementation(project(":core:alarm"))
    screenshotTestImplementation(libs.androidx.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)
}
