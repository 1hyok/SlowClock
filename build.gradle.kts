buildscript {
    dependencies {
        // Hilt Gradle 플러그인(hiltAggregateDepsDebug)이 호출하는 JavaPoet ClassName.canonicalName() 보장 — 버전 충돌(NoSuchMethodError) 방지
        classpath("com.squareup:javapoet:1.13.0")
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}