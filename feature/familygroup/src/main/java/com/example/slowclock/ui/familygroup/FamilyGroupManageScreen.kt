package com.example.slowclock.ui.familygroup

import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState

@Composable
fun FamilyGroupManageScreen(viewModel: FamilyGroupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var groupId by remember { mutableStateOf("") }
    var isGuardian by remember { mutableStateOf(false) }
    val statusMessage by viewModel.statusMessage.collectAsState(initial = "")

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isGuardian, onCheckedChange = { isGuardian = it })
            Text(text = if (isGuardian) "보호자" else "사용자")
        }

        if (isGuardian) {
            OutlinedTextField(
                value = groupId,
                onValueChange = { groupId = it },
                label = { Text("그룹 ID 입력") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                viewModel.joinGroup(groupId)
            }, modifier = Modifier.fillMaxWidth()) {
                Text("그룹 참가")
            }
        } else {
            Button(onClick = {
                viewModel.createGroup()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("새 그룹 생성")
            }
        }

        Text(text = statusMessage, modifier = Modifier.padding(top = 8.dp))
    }
}

