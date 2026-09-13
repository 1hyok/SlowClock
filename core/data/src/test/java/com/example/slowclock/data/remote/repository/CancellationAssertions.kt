package com.example.slowclock.data.remote.repository

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertFalse

/** Job 의 isCancelled 만 보면 취소를 삼킨 함수도 통과한다. 호출 뒤 동기 코드가 실행됐는지 본다. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal suspend fun TestScope.assertCancellationStops(block: suspend () -> Any?) {
    var returned = false
    val job =
        launch {
            block()
            returned = true
        }
    runCurrent()
    assertFalse("실제 Task 응답을 기다려야 한다", job.isCompleted)
    job.cancelAndJoin()
    assertFalse("취소를 일반 반환으로 바꾸면 호출 뒤 코드가 실행된다", returned)
}
