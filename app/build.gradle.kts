import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import java.util.Properties

plugins {
    alias(libs.plugins.slowclock.android.application)
    alias(libs.plugins.slowclock.android.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.screenshot)
    alias(libs.plugins.firebase.app.distribution)
    alias(libs.plugins.google.services)
}

// local.properties 에서 release 서명 키 로드 (CI 는 시크릿으로 local.properties 합성)
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

// Play 내부 트랙 배포 워크플로(release-play-internal.yml)가 단조 증가 versionCode 를 이 환경변수로 넘긴다.
// 로컬·PR 검증 빌드는 값이 없으므로 1 을 쓴다. 잘못된 값은 조용히 1 로 떨어뜨리지 않고 설정 단계에서 실패시킨다.
val slowClockVersionCodeEnv = "SLOWCLOCK_VERSION_CODE"

fun resolveSlowClockVersionCode(raw: String?): Int {
    if (raw.isNullOrBlank()) return 1
    val code = raw.trim().toIntOrNull()
    require(code != null && code >= 1) { "$slowClockVersionCodeEnv 는 1 이상의 정수여야 한다: '$raw'" }
    return code
}

android {
    // namespace(R 클래스·소스 패키지)는 그대로 두고 applicationId 만 Play 등록용으로 바꾼다.
    // Play 는 com.example.* 패키지를 거부하며, Firebase Android 앱은 이 applicationId 로 등록돼 있다.
    namespace = "com.example.slowclock"

    // Compose Preview Screenshot Testing (alpha) 활성화
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        applicationId = "com.ilhyok.slowclock"
        versionCode = resolveSlowClockVersionCode(System.getenv(slowClockVersionCodeEnv))
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/*.kotlin_module"
        }
    }

    signingConfigs {
        create("release") {
            val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE")
            if (releaseStoreFile != null) {
                storeFile = file(releaseStoreFile)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // v1 은 R8 을 켜지 않는다 — Firestore 가 리플렉션으로 매핑하는 data/model 클래스에 keep 규칙이 없고,
            // 난독화 산출물을 기기에서 검증한 적이 없다. 켜는 작업은 별도 이슈로 다룬다.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
            firebaseAppDistribution {
                // 릴리스 노트는 배포 워크플로가 머지된 PR 본문에서 만들어 --releaseNotesFile 로 넘긴다.
                // 여기서 releaseNotes 를 지정하면 그 파일을 덮어써 모든 배포가 같은 문구로 나간다(#79).
                groups = "slowclock"
            }
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // 화면은 feature 모듈이, Compose·테마는 core:ui 가, 저장소는 core:data 가 노출한다.
    implementation(project(":core:ui"))
    implementation(project(":core:alarm"))
    implementation(project(":core:data"))
    implementation(project(":feature:main"))
    implementation(project(":feature:addschedule"))
    implementation(project(":feature:recommendation"))
    implementation(project(":feature:profile"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Firebase 초기화(Application)·Analytics·Firebase UI 로그인(AuthManager)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.ui.auth)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Compose Preview Screenshot Testing
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.ui.tooling)
}
