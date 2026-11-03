package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import com.example.termux.TermuxService
import com.example.ui.MainViewModel
import com.example.ui.screens.ActionLogScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FileBrowserScreen
import com.example.ui.screens.IntegrationsScreen
import com.example.ui.screens.MemoryNotesScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ShenTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start Foreground Service for persistence
        val serviceIntent = Intent(this, TermuxService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            val isDark by viewModel.isDarkTheme.collectAsState()
            val lang by viewModel.currentLanguage.collectAsState()
            val isFa = lang == "fa"

            val layoutDir = if (isFa) LayoutDirection.Rtl else LayoutDirection.Ltr

            ShenTheme(darkTheme = isDark) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                    ShenMainApp(viewModel = viewModel, isFa = isFa)
                }
            }
        }
    }
}

@Composable
fun ShenMainApp(viewModel: MainViewModel, isFa: Boolean) {
    var selectedTab by remember { mutableStateOf("chat") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == "chat",
                    onClick = { selectedTab = "chat" },
                    icon = { Icon(imageVector = Icons.Default.Chat, contentDescription = "Chat") },
                    label = { Text(if (isFa) "چت" else "Chat") },
                    modifier = Modifier.testTag("nav_chat_tab")
                )

                NavigationBarItem(
                    selected = selectedTab == "bootstrap",
                    onClick = { selectedTab = "bootstrap" },
                    icon = { Icon(imageVector = Icons.Default.RocketLaunch, contentDescription = "Bootstrap") },
                    label = { Text(if (isFa) "راه اندازی" else "Setup") },
                    modifier = Modifier.testTag("nav_bootstrap_tab")
                )

                NavigationBarItem(
                    selected = selectedTab == "logs",
                    onClick = { selectedTab = "logs" },
                    icon = { Icon(imageVector = Icons.Default.History, contentDescription = "Logs") },
                    label = { Text(if (isFa) "لاگ‌ها" else "Logs") }
                )

                NavigationBarItem(
                    selected = selectedTab == "files",
                    onClick = { selectedTab = "files" },
                    icon = { Icon(imageVector = Icons.Default.Folder, contentDescription = "Files") },
                    label = { Text(if (isFa) "فایل‌ها" else "Files") }
                )

                NavigationBarItem(
                    selected = selectedTab == "dashboard",
                    onClick = { selectedTab = "dashboard" },
                    icon = { Icon(imageVector = Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text(if (isFa) "داشبورد" else "Board") }
                )

                NavigationBarItem(
                    selected = selectedTab == "vault",
                    onClick = { selectedTab = "vault" },
                    icon = { Icon(imageVector = Icons.Default.Key, contentDescription = "Vault") },
                    label = { Text(if (isFa) "گاوصندوق" else "Vault") }
                )

                NavigationBarItem(
                    selected = selectedTab == "memory",
                    onClick = { selectedTab = "memory" },
                    icon = { Icon(imageVector = Icons.Default.Memory, contentDescription = "Memory") },
                    label = { Text(if (isFa) "حافظه" else "Memory") }
                )

                NavigationBarItem(
                    selected = selectedTab == "settings",
                    onClick = { selectedTab = "settings" },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text(if (isFa) "تنظیمات" else "Settings") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                "chat" -> ChatScreen(viewModel = viewModel)
                "bootstrap" -> OnboardingScreen(viewModel = viewModel)
                "logs" -> ActionLogScreen(viewModel = viewModel)
                "files" -> FileBrowserScreen(viewModel = viewModel)
                "dashboard" -> DashboardScreen(viewModel = viewModel)
                "vault" -> IntegrationsScreen(viewModel = viewModel)
                "memory" -> MemoryNotesScreen(viewModel = viewModel)
                "settings" -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
