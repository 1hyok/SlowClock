package com.example.slowclock.data.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
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
 * 오프라인 캐시 설정은 이 모듈이 인스턴스를 만들 때 한 번 적용한다. 다른 Firestore 사용처가
 * 모두 이 인스턴스를 주입받으므로 설정보다 먼저 쓰이는 경로가 없다.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore =
        FirebaseFirestore.getInstance().apply {
            // 디스크 캐시가 기본값이지만 명시해 둔다. 옛 setPersistenceEnabled 는 deprecated 다.
            // https://firebase.google.com/docs/firestore/manage-data/enable-offline
            firestoreSettings =
                FirebaseFirestoreSettings
                    .Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
        }

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}
