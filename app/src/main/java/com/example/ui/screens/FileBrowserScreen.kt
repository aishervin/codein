package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

data class FileItem(val name: String, val isDir: Boolean, val size: String)

@Composable
fun FileBrowserScreen(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val isFa = viewModel.currentLanguage.collectAsState().value == "fa"

    var currentPath by remember { mutableStateOf("/data/data/com.termux/files/home") }
    val fileList = remember { mutableStateListOf<FileItem>() }
    var selectedFileContent by remember { mutableStateOf<String?>(null) }
    var selectedFilePath by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }

    fun refreshDirectory() {
        scope.launch {
            val res = viewModel.termuxManager.runShellCommand("ls -la \"$currentPath\"")
            fileList.clear()
            if (res.exitCode == 0) {
                val lines = res.stdout.lines()
                for (line in lines.drop(1)) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 9) {
                        val isDir = line.startsWith("d")
                        val name = parts.subList(8, parts.size).joinToString(" ")
                        if (name != "." && name != "..") {
                            fileList.add(FileItem(name = name, isDir = isDir, size = parts[4]))
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(currentPath) {
        refreshDirectory()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isFa) "📁 مدیر فایل‌های ترموکس" else "📁 Termux File Manager",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            IconButton(onClick = { refreshDirectory() }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "PATH: $currentPath",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedFileContent != null) {
            // File Editor View
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Editing: ${selectedFilePath.takeLast(30)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val b64 = android.util.Base64.encodeToString(
                                            editContent.toByteArray(Charsets.UTF_8),
                                            android.util.Base64.NO_WRAP
                                        )
                                        viewModel.termuxManager.runShellCommand("echo \"$b64\" | base64 -d > \"$selectedFilePath\"")
                                        selectedFileContent = null
                                        refreshDirectory()
                                    }
                                },
                                modifier = Modifier.testTag("save_file_button")
                            ) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = "Save")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isFa) "ذخیره" else "Save")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(onClick = { selectedFileContent = null }) {
                                Text(if (isFa) "بستن" else "Close")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("file_editor_text_field"),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        } else {
            // File List View
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (currentPath != "/data/data/com.termux/files/home") {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val parent = currentPath.substringBeforeLast("/")
                                        currentPath = if (parent.isEmpty()) "/data/data/com.termux/files/home" else parent
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(modifier = Modifier.padding(12.dp)) {
                                    Icon(imageVector = Icons.Default.Folder, contentDescription = "Up")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(".. (بازگشت به پوشه قبل)")
                                }
                            }
                        }
                    }

                    items(fileList) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (item.isDir) {
                                        currentPath = "$currentPath/${item.name}"
                                    } else {
                                        selectedFilePath = "$currentPath/${item.name}"
                                        scope.launch {
                                            val res = viewModel.termuxManager.runShellCommand("cat \"$selectedFilePath\"")
                                            selectedFileContent = res.stdout
                                            editContent = res.stdout
                                        }
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (item.isDir) Icons.Default.Folder else Icons.Default.Description,
                                        contentDescription = "Type",
                                        tint = if (item.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.name,
                                        fontWeight = if (item.isDir) FontWeight.Bold else FontWeight.Normal
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.size,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                viewModel.termuxManager.runShellCommand("rm -rf \"$currentPath/${item.name}\"")
                                                refreshDirectory()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
