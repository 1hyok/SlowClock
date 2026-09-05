buildscript {
    // AGP 8.13.x(9.1 도 동일)가 빌드 클래스패스에 올리는 Bouncy Castle 1.79 는 GHSA-574f-3g2m-x479(critical) 에 걸린다.
    // 앱 런타임과 무관한 서명 도구 의존이지만 dependency-review 가 빌드 클래스패스도 검사하므로 패치본으로 고정한다.
    // https://github.com/advisories/GHSA-574f-3g2m-x479 (patched 1.80.2), https://repo1.maven.org/maven2/org/bouncycastle/
    configurations.classpath {
        resolutionStrategy.force(
            "org.bouncycastle:bcprov-jdk18on:1.85",
            "org.bouncycastle:bcpkix-jdk18on:1.85",
            "org.bouncycastle:bcutil-jdk18on:1.85",
        )
    }
    dependencies {
        // AGP 9 내장 Kotlin 은 KGP 2.2.10 을 런타임 의존으로 갖는다. 더 높은 KGP 를 쓰려면 classpath 에 직접 올린다
        // (developer.android.com/build/releases/agp-9-0-0-release-notes 의 "Upgrade KGP/KSP").
        classpath(libs.kotlin.gradlePlugin)
        // Hilt Gradle 플러그인(hiltAggregateDepsDebug)이 호출하는 JavaPoet ClassName.canonicalName() 보장 — 버전 충돌(NoSuchMethodError) 방지
        classpath("com.squareup:javapoet:1.13.0")
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint.gradle) apply false
    alias(libs.plugins.google.services) apply false
}

// ktlint 를 모든 모듈에 적용한다. 종전엔 :app 에만 붙어 있어 core/feature 모듈은 Gradle 로 검사되지 않았다.
// 버전은 .github/workflows 의 ktlint 와 통일(1.8.0). compose 규칙셋(io.nlopez.compose.rules)도 전 모듈 공통.
// AGP 가 만드는 Unified Test Platform 내부 설정(_internal-unified-test-platform-*)도 Bouncy Castle 1.79 를
// 끌어온다. buildscript classpath 만 고정하면 dependency-review 가 그 경로의 1.79 를 새 취약 의존으로 잡는다.
// 앱 산출물에는 들어가지 않는 도구 의존이므로 모든 프로젝트 설정에서 패치본으로 맞춘다.
// https://github.com/advisories/GHSA-574f-3g2m-x479
allprojects {
    configurations.configureEach {
        resolutionStrategy.force(
            "org.bouncycastle:bcprov-jdk18on:1.85",
            "org.bouncycastle:bcpkix-jdk18on:1.85",
            "org.bouncycastle:bcutil-jdk18on:1.85",
        )
    }
}

val composeRules = libs.compose.rules
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        ignoreFailures.set(false)
    }
    dependencies {
        add("ktlintRuleset", composeRules)
    }
}
