plugins {
    alias(libs.plugins.slowclock.android.library)
    alias(libs.plugins.slowclock.android.hilt)
}

android {
    namespace = "com.example.slowclock.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))

    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
