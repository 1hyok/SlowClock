package com.example.slowclock.notification

import com.example.slowclock.data.notification.Notifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * [Notifier] 추상화에 :core:alarm 의 [GuardianNotifier] 구현을 바인딩한다.
 *
 * :core:data 의 Repository 들은 이 모듈을 통해 [Notifier] 를 주입받는다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotifierModule {
    @Binds
    abstract fun bindNotifier(impl: GuardianNotifier): Notifier
}
