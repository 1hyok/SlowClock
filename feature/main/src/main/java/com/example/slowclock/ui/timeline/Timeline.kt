package com.example.slowclock.ui.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.slowclock.data.model.Schedule
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun Timeline(
    height: Dp,
    items: List<Schedule>
) {
    val sortedItems = items.sortedBy { it.startTime.seconds }
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        // 전체 세로선: Box 전체 높이를 따라 고정
        Divider(
            color = Color(0xFF3A5CCC),
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .align(Alignment.Center)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(60.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            itemsIndexed(sortedItems) { index, item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    // 점
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.Center)
                            .background(Color(0xFF3A5CCC), shape = CircleShape)
                    )

                    val alignStart = index % 2 == 0
                    Card(
                        modifier = Modifier
                            .align(if (alignStart) Alignment.CenterStart else Alignment.CenterEnd)
                            .padding(horizontal = 40.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (item.completed) Color.Transparent else Color(0xFF1A73E8)),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.completed) Color(0xFFE6F4EA) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = item.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = timeFormat.format(item.startTime.toDate()),
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }

}