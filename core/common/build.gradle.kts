plugins {
    alias(libs.plugins.slowclock.android.library)
}

android {
    namespace = "com.example.slowclock.core.common"
}

dependencies {
    // ScheduleUtils 가 Schedule(model) 을 다루고, ErrorType 이 Firestore 예외를 매핑하므로 model api 전파
    api(project(":core:model"))
}
