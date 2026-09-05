package com.example.slowclock.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

/**
 * 앱·라이브러리 공통 SDK·JVM 설정. 숫자는 여기 한 곳에만 둔다.
 * Kotlin 은 AGP 9 내장 지원을 쓰므로 kotlin-android 플러그인을 따로 적용하지 않고,
 * jvmTarget 도 compileOptions.targetCompatibility 를 따라가서 별도 설정이 없다
 * (developer.android.com/build/migrate-to-built-in-kotlin).
 */
// androidx.core 1.19.0 이 compileSdk 37 이상을 요구한다(AAR 메타데이터 minCompileSdk=37). API 37 = Android 17 정식(코드네임 없음).
internal const val COMPILE_SDK = 37
internal const val MIN_SDK = 32
internal const val TARGET_SDK = 36

internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    // AGP 9 의 CommonExtension 은 제네릭이 없고 블록 함수 대신 프로퍼티로 접근한다
    // (developer.android.com/build/releases/agp-9-0-0-release-notes "CommonExtension Parameterization Removed").
    commonExtension.apply {
        compileSdk = COMPILE_SDK
        defaultConfig.apply {
            minSdk = MIN_SDK
        }
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
}
