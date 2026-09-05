plugins {
    alias(libs.plugins.slowclock.android.library.compose)
}

android {
    namespace = "com.example.slowclock.core.ui"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))

    // Compose 를 api 로 노출 → :feature 모듈은 :core:ui 만 의존해도 Compose 사용 가능
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.ui.tooling.preview)
    api(libs.androidx.material3)
    api(libs.androidx.material.icons.extended)
    // MviViewModel 이 androidx.lifecycle.ViewModel 을 상속해 노출하므로 feature 컴파일
    // 클래스패스에도 올라가야 한다. implementation 이면 상속체가 supertype 을 못 본다.
    api(libs.androidx.lifecycle.viewmodel.ktx)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
