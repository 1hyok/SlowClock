package com.example.slowclock.ui.timeline

import android.app.DatePickerDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

/** 화면을 떠나거나 Activity가 바뀌면 달력 창도 함께 닫는다. */
@Composable
fun TimelineDatePickerDialog(
    selectedDate: Calendar,
    onSelectDate: (Int, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnSelectDate by rememberUpdatedState(onSelectDate)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(context) {
        val dialog =
            DatePickerDialog(
                context,
                { _, year, month, day -> currentOnSelectDate(year, month, day) },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH),
            )
        dialog.setOnDismissListener { currentOnDismiss() }
        dialog.show()
        onDispose {
            // 폐기된 화면의 상태를 뒤늦게 바꾸지 않는다.
            dialog.setOnDismissListener(null)
            dialog.dismiss()
        }
    }
}
