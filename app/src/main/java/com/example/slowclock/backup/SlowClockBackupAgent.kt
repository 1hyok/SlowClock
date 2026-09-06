package com.example.slowclock.backup

import android.app.backup.BackupAgentHelper
import java.io.IOException

/**
 * 이전 버전 백업에 남은 기기 설정도 복원 직후 비운다. 다음 실행은 새 설치의 로그아웃·초기화를 거친다.
 * 일반 앱 업데이트에서는 이 콜백이 실행되지 않으므로 현재 기기의 설정은 유지된다.
 *
 * 복원 중에는 Hilt Application과 Firebase provider가 초기화되지 않으므로 사용하지 않는다.
 * https://developer.android.com/identity/data/autobackup#ImplementingBackupAgent
 */
class SlowClockBackupAgent : BackupAgentHelper() {
    override fun onRestoreFinished() {
        super.onRestoreFinished()
        for (name in DEVICE_PREFERENCES) {
            // 복원 프로세스가 바로 끝나도 다음 앱 실행 전에 정리가 디스크에 반영되어야 한다.
            if (!getSharedPreferences(name, MODE_PRIVATE).edit().clear().commit()) {
                throw IOException("Restored device preferences could not be cleared: $name")
            }
        }
    }

    private companion object {
        val DEVICE_PREFERENCES = listOf("app_state", "settings", "scheduled_alarms", "snoozed_alarms")
    }
}
