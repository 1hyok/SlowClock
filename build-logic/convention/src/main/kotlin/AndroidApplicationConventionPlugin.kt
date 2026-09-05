import com.android.build.api.dsl.ApplicationExtension
import com.example.slowclock.buildlogic.TARGET_SDK
import com.example.slowclock.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `:app`. 공통 SDK·JVM 설정에 targetSdk 를 더한다. 서명·버전·배포 설정은 앱 빌드 파일에 남긴다. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = TARGET_SDK
            }
        }
    }
}
