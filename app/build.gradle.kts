import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.util.Properties

plugins {
    alias(libs.plugins.slowclock.android.application)
    alias(libs.plugins.slowclock.android.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.screenshot)
    alias(libs.plugins.firebase.app.distribution)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
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

// 사용자에게 보이는 판 이름. 테스터 배포(App Distribution)는 여기에 배포 번호와 커밋을 붙여
// 어느 빌드를 받았는지 앱 정보에서 바로 읽게 한다. 값이 없으면 Play 와 로컬은 "1.0" 그대로다.
// 붙이지 않으면 모든 배포본이 「1.0 (1)」 이라 Crashlytics 에서 빌드를 가릴 수 없다(#139).
val slowClockVersionNameSuffixEnv = "SLOWCLOCK_VERSION_NAME_SUFFIX"

fun resolveSlowClockVersionName(suffix: String?): String {
    val base = "1.0"
    val trimmed = suffix?.trim().orEmpty()
    require(trimmed.length <= 40) { "$slowClockVersionNameSuffixEnv 는 40자 이하여야 한다: '$suffix'" }
    return if (trimmed.isEmpty()) base else "$base-$trimmed"
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
        versionName = resolveSlowClockVersionName(System.getenv(slowClockVersionNameSuffixEnv))

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
        debug {
            // 개발 중에 낸 크래시가 대시보드를 채우면 테스터가 낸 크래시를 못 찾는다(#98).
            manifestPlaceholders["crashlyticsCollectionEnabled"] = false
        }
        release {
            manifestPlaceholders["crashlyticsCollectionEnabled"] = true
            // R8 로 쓰지 않는 코드와 리소스를 걷어낸다. Firestore 가 이름으로 읽는 모델과
            // kotlinx.serialization 이 만드는 serializer 는 proguard-rules.pro 가 남긴다(#113).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
            // 난독화한 스택 트레이스를 되돌리려면 매핑 파일이 있어야 한다(#113).
            configure<CrashlyticsExtension> { mappingFileUploadEnabled = true }
            firebaseAppDistribution {
                // 릴리스 노트는 배포 워크플로가 머지된 PR 본문에서 만들어 --releaseNotesFile 로 넘긴다.
                // 여기서 releaseNotes 를 지정하면 그 파일을 덮어써 모든 배포가 같은 문구로 나간다(#79).
                // WIF canary는 인증·업로드만 확인하며 테스터에게 전달하지 않는다.
                groups = if (System.getenv("SLOWCLOCK_DISTRIBUTION_UPLOAD_ONLY") == "true") "" else "slowclock"
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
    // Navigation 3: 백스택은 앱이 소유하고 화면은 네비게이션 콜백만 받는다
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)

    // Firebase 초기화(Application)·Analytics·Firebase UI 로그인(AuthManager)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
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
