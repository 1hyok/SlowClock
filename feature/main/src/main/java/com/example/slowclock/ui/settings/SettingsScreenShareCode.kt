package com.example.slowclock.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.slowclock.ui.main.MainViewModel

@Composable
fun SettingsScreenShareCode(
    onReturn: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var shareCode by remember { mutableStateOf(prefs.getString("share_code", "") ?: "") }
    var inputValue by remember { mutableStateOf(TextFieldValue(shareCode)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Enter Share Code to View Shared Reminders", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = inputValue,
            onValueChange = { inputValue = it },
            label = { Text("Share Code") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            prefs.edit().putString("share_code", inputValue.text).apply()
            shareCode = inputValue.text
            // Register watcher after saving share code
            viewModel.addShareCodeWatcher(context, inputValue.text)
            onReturn()
        }) {
            Text("Save and Return")
        }
    }
}
