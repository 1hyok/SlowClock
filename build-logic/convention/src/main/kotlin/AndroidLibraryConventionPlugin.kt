import com.android.build.api.dsl.LibraryExtension
import com.example.slowclock.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `:core:*`·`:feature:*` 공통. JVM 단위 테스트에서 android.util.Log 같은 스텁이 예외를 던지지 않게 한다. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                testOptions.unitTests.isReturnDefaultValues = true
            }
        }
    }
}
