package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

@Composable
fun ActionLogScreen(viewModel: MainViewModel) {
    val logs by viewModel.actionLogs.collectAsState()
    val snapshots by viewModel.snapshots.collectAsState()
    val isFa = viewModel.currentLanguage.collectAsState().value == "fa"

    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = logs.filter {
        it.command.contains(searchQuery, ignoreCase = true) ||
                it.actionName.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
    ) {
        Text(
            text = if (isFa) "📋 تاریخچه لایوها و اکشن‌های عامل" else "📋 Action History & Snapshots",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_action_logs_input"),
            placeholder = { Text(if (isFa) "جستجوی دستورات یا اکشن‌ها..." else "Search command logs...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredLogs.isEmpty()) {
            Text(
                text = if (isFa) "هیچ لاگی یافت نشد." else "No action logs recorded yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredLogs) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = log.actionName,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (log.status == "SUCCESS") "EXIT 0" else "EXIT ${log.exitCode}",
                                    color = if (log.status == "SUCCESS") Color(0xFF00E676) else Color(0xFFFF5252),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = log.command,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (log.stdout.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "STDOUT: ${log.stdout.take(300)}${if (log.stdout.length > 300) "..." else ""}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val matchingSnapshot = snapshots.find { it.id == log.snapshotId }
                            if (matchingSnapshot != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.restoreSnapshot(matchingSnapshot) },
                                    modifier = Modifier.testTag("restore_snapshot_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Restore, contentDescription = "Restore")
                                    Spacer(modifier = Modifier.padding(4.dp))
                                    Text(if (isFa) "بازگردانی فایل از اسنپ‌شات" else "Restore Snapshot")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
