import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.example.slowclock.buildlogic"

// 컨벤션 플러그인은 Gradle 데몬 JVM(21)에서 돌지만 산출물은 17 로 맞춘다. AGP 9.1 의 최소 JDK 가 17 이다.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    // included build 는 루트 프로젝트의 constraints 를 상속하지 않는다.
    constraints {
        compileOnly(libs.security.jdom)
        compileOnly(libs.security.jose)
        compileOnly(libs.security.commons.lang)
        compileOnly(libs.security.httpclient)
        compileOnly(libs.security.bouncycastle.provider)
        compileOnly(libs.security.bouncycastle.pkix)
        compileOnly(libs.security.bouncycastle.util)
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "slowclock.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "slowclock.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "slowclock.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "slowclock.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidFeature") {
            id = "slowclock.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
    }
}
