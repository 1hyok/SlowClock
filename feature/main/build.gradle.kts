plugins {
    alias(libs.plugins.slowclock.android.feature)
}

android {
    namespace = "com.example.slowclock.feature.main"
}

dependencies {
    implementation(project(":core:alarm"))
}
