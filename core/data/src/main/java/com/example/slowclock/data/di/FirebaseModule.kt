package com.example.slowclock.data.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firebase 진입점을 Hilt 로 제공한다. Repository 는 `getInstance()` 를 직접 부르지 않고 생성자로
 * 받는다. 테스트에서는 이 모듈을 `@TestInstallIn` 으로 바꾸거나 생성자에 fake 를 넣는다.
 *
 * Firestore 오프라인 캐시 같은 전역 설정은 `SlowClockApplication` 이 앱 시작 시 한 번 적용한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}
