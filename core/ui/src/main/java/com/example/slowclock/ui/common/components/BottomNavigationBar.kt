// ui/common/components/BottomNavigationBar.kt
package com.example.slowclock.ui.common.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

data class NavItem(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items =
        listOf(
            NavItem("main", Icons.Default.Home, "메인"),
            NavItem("done", Icons.Default.Check, "완료"),
            NavItem("timeline", Icons.Default.DateRange, "시간표"),
            NavItem("settings", Icons.AutoMirrored.Filled.Article, "정보"),
        )

    // 색은 테마가 정한다. 항목마다 tint 를 손으로 주면 다크에서 선택 표시와 글자가 따로 논다.
    // 선택 표시는 기본색(남색)이다. 주황은 「지금 할 일」 한 자리에만 쓴다(#109).
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                icon = { Icon(item.icon, contentDescription = null) },
                // 글꼴을 키우면 두 줄로 흘러 옆 항목과 겹친다. 한 줄로 고정한다.
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}
