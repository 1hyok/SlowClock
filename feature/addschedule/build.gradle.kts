plugins {
    alias(libs.plugins.slowclock.android.feature)
}

android {
    namespace = "com.example.slowclock.feature.addschedule"
}

dependencies {
    implementation(project(":core:alarm"))
}
