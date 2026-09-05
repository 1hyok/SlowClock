import com.example.slowclock.buildlogic.library
import com.example.slowclock.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * `:feature:*`. Compose 라이브러리 + Hilt 에 화면 모듈이 늘 쓰는 의존을 더한다.
 * `:core:ui` 가 Compose·테마·MVI 베이스를, `:core:data` 가 Repository 를 노출한다.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("slowclock.android.library.compose")
            pluginManager.apply("slowclock.android.hilt")

            dependencies {
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:data"))
                add("implementation", libs.library("androidx-hilt-navigation-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("androidx-lifecycle-runtime-compose"))

                add("testImplementation", libs.library("junit"))
                add("testImplementation", libs.library("mockk"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
            }
        }
    }
}
