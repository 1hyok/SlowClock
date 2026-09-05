plugins {
    alias(libs.plugins.slowclock.android.library.compose)
}

android {
    namespace = "com.example.slowclock.feature.recommendation"
}

dependencies {
    implementation(project(":core:ui"))
}
