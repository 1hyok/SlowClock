package com.example.slowclock.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SharedNotificationDeliveryTest {
    private var uid: String? = "uid"
    private var code: String? = "CODE01"
    private val visible = mutableSetOf<String>()
    private val delivery = SharedNotificationDelivery({ uid to code }, { visible.clear() })
    private val message = SharedScheduleMessage("uid", "CODE01", "schedule", "일정", "본문")

    @Test
    fun `현재 계정과 공유 코드가 모두 일치해야 표시한다`() {
        assertTrue(delivery.showIfCurrent(message) { visible.add("schedule") })
        for ((nextUid, nextCode) in listOf(null to "CODE01", "other" to "CODE01", "uid" to null, "uid" to "CODE02")) {
            delivery.changeSession {
                uid = nextUid
                code = nextCode
            }
            assertFalse(delivery.showIfCurrent(message) { error("잘못된 세션에 표시") })
        }
        assertTrue(visible.isEmpty())
    }

    @Test
    fun `표시가 먼저 시작되어도 로그아웃 반환 뒤 알림이 남지 않는다`() {
        assertNotifyThenChange {
            uid = null
            code = null
        }
    }

    @Test
    fun `공유 코드 교체도 검사와 표시 사이에 끼어들지 않는다`() {
        assertNotifyThenChange { code = "CODE02" }
        assertFalse(delivery.showIfCurrent(message) { error("이전 코드 표시") })
    }

    private fun assertNotifyThenChange(change: () -> Unit) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val changeStarted = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val receive =
                executor.submit<Boolean> {
                    delivery.showIfCurrent(message) {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        visible.add("schedule")
                    }
                }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val mutate =
                executor.submit {
                    changeStarted.countDown()
                    delivery.changeSession(change)
                }
            assertTrue(changeStarted.await(5, TimeUnit.SECONDS))
            assertFalse(mutate.isDone)
            release.countDown()
            assertTrue(receive.get(5, TimeUnit.SECONDS))
            mutate.get(5, TimeUnit.SECONDS)
            assertTrue(visible.isEmpty())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `로그아웃이 먼저 완료되면 뒤 수신은 표시하지 않는다`() {
        delivery.changeSession {
            uid = null
            code = null
        }
        assertFalse(delivery.showIfCurrent(message) { error("로그아웃 뒤 표시") })
    }

    @Test
    fun `같은 계정으로 다시 로그인해도 앞 세션의 저장 완료를 반영하지 않는다`() {
        val oldSession = delivery.snapshot()
        delivery.changeSession {
            uid = null
            code = null
        }
        delivery.changeSession {
            uid = "uid"
            code = "CODE01"
        }
        assertFalse(delivery.changeIfCurrent(oldSession) { code = "OLD_SAVE" })
        assertEquals("CODE01", code)
    }

    @Test
    fun `세션 변경 중 예외가 나도 기존 공유 알림은 지운다`() {
        visible.add("schedule")
        try {
            delivery.changeSession {
                code = null
                error("auth failure")
            }
        } catch (_: IllegalStateException) {
            // Auth 실패를 호출자에 전달하더라도 이미 뜬 알림은 남기지 않는다.
        }
        assertTrue(visible.isEmpty())
    }

    @Test
    fun `토큰 갱신은 현재 로그인한 세션만 넘긴다`() {
        val submitted = mutableListOf<SharedNotificationSession>()
        delivery.withCurrentSession { submitted.add(it) }
        delivery.changeSession {
            uid = null
            code = null
        }
        delivery.withCurrentSession { error("로그아웃 뒤 등록") }
        assertEquals(listOf(SharedNotificationSession("uid", "CODE01", 0)), submitted)
    }

    @Test
    fun `Auth 삭제 완료 뒤에도 같은 세션의 공유 알림을 지운다`() {
        val session = delivery.snapshot()
        visible.add("schedule")
        uid = null
        assertTrue(delivery.changeAfterAccountDeletion(session) { code = null })
        assertTrue(visible.isEmpty())
    }

    @Test
    fun `늦은 계정 삭제 응답이 새 로그인 알림과 코드를 지우지 않는다`() {
        val session = delivery.snapshot()
        delivery.changeSession {
            uid = "new-uid"
            code = "NEW002"
        }
        visible.add("new schedule")
        assertFalse(delivery.changeAfterAccountDeletion(session) { error("새 설정 삭제") })
        assertEquals(setOf("new schedule"), visible)
        assertEquals("NEW002", code)
    }
}
