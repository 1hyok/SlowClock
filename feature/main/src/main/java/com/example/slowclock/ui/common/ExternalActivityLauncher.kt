package com.example.slowclock.ui.common

import android.content.ActivityNotFoundException
import android.util.Log

/** 시스템 설정이나 브라우저가 없거나 정책으로 막힌 기기에서도 현재 화면을 유지한다. */
internal fun launchExternalActivity(
    open: () -> Unit,
    fallback: (() -> Unit)? = null,
): Boolean {
    try {
        open()
        return true
    } catch (e: ActivityNotFoundException) {
        Log.w("ExternalActivity", "외부 화면을 열지 못했다", e)
    } catch (e: SecurityException) {
        Log.w("ExternalActivity", "외부 화면이 허용되지 않았다", e)
    }
    return fallback?.let { launchExternalActivity(it) } ?: false
}
