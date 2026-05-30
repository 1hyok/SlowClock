// ui/common/components/BottomNavigationBar.kt
package com.example.slowclock.ui.common.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class NavItem(val route: String, val icon: ImageVector, val label: String)

@Composable
fun BottomNavigationBar(currentRoute: String, onNavigate: (String) -> Unit) {
    val items = listOf(
        NavItem("main", Icons.Default.Home, "메인"),
        NavItem("done", Icons.Default.Check, "완료"),
        NavItem("timeline", Icons.Default.DateRange, "타임라인"),
        NavItem("settings", Icons.AutoMirrored.Filled.Article, "정보")
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                },
                label = {
                    Text(
                        item.label,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            )
        }
    }
}
