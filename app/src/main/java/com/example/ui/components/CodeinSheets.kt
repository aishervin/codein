package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.termux.TermuxBridgeClient
import com.example.ui.theme.NeonOrange
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeinSettingsSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onClearChat: () -> Unit,
    onClearSession: () -> Unit,
    isConnected: Boolean,
    onOpenIntegrations: () -> Unit
) {
    val context = LocalContext.current
    val settingsDirection = if (
        context.resources.configuration.locales[0].language in setOf("fa", "ar", "he", "ur")
    ) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides settingsDirection) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                SheetTitle(Icons.Default.Settings, "Settings", "Session and conversation controls")
                Spacer(Modifier.height(14.dp))
                SettingRow(
                    icon = Icons.Default.Link,
                    title = "Integrations & Services",
                    subtitle = "Connect GitHub, Slack, Gmail and more via Composio",
                    onClick = onOpenIntegrations
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                SettingRow(
                    icon = Icons.Default.DeleteSweep,
                    title = "New conversation",
                    subtitle = "Start with a clean conversation",
                    onClick = {
                        onClearChat()
                        Toast.makeText(context, "Conversation cleared", Toast.LENGTH_SHORT).show()
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                SettingRow(
                    icon = Icons.Default.DeleteSweep,
                    title = "Clear browser session",
                    subtitle = "Remove cookies and local session data",
                    onClick = {
                        onClearSession()
                        Toast.makeText(context, "Browser session cleared", Toast.LENGTH_SHORT).show()
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                SettingRow(
                    icon = Icons.Default.Info,
                    title = "Service status",
                    subtitle = if (isConnected) "Connected and ready" else "Connecting to service",
                    onClick = {}
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .78f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, NeonOrange.copy(alpha = .16f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "DEVELOPER",
                            color = NeonOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("SHΞЯVIN™", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(6.dp))
                        Text("Designed and crafted for focused conversations and code.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("A private interface by SHΞЯVIN™.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("CODΞiN™  •  SHΞN™", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    SocialLink(R.drawable.ic_social_telegram, "Telegram", "https://t.me/shervini", context)
                    SocialLink(R.drawable.ic_social_x, "X", "https://x.com/shervinonx", context)
                    SocialLink(R.drawable.ic_social_github, "GitHub", "https://github.com/aishervin", context)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeinIntegrationsSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    termuxClient: TermuxBridgeClient
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("codein_integrations", Context.MODE_PRIVATE) }
    var connectingToolkit by remember { mutableStateOf<String?>(null) }

    val services = listOf(
        Triple("github", "گیت‌هاب (GitHub)", "اتصال به مخازن، ریپازیتوری‌ها و کدهای من"),
        Triple("slack", "اسلک (Slack)", "ارسال پیام و تعامل با کانال‌های تیمی"),
        Triple("gmail", "جیمیل (Gmail)", "مدیریت ایمیل‌ها و پیام‌ها"),
        Triple("google_calendar", "تقویم گوگل (Google Calendar)", "مدیریت رویدادها و تقویم"),
        Triple("jira", "جیرا (Jira)", "مدیریت وظایف و پروژه‌ها"),
        Triple("notion", "نوشن (Notion)", "مدیریت یادداشت‌ها و پایگاه‌های دانش"),
        Triple("discord", "دیسکورد (Discord)", "مدیریت سرورها و پیام‌رسانی"),
        Triple("trello", "ترلو (Trello)", "مدیریت بوردها و تسک‌های تیمی")
    )

    val settingsDirection = if (
        context.resources.configuration.locales[0].language in setOf("fa", "ar", "he", "ur")
    ) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides settingsDirection) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                SheetTitle(Icons.Default.Link, "Integrations & Services", "اتصال به سرویس‌های ابری از طریق کامپوزیو")
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(services) { (toolkit, name, desc) ->
                        val isConnected = prefs.getBoolean("connected_$toolkit", false)
                        val isBusy = connectingToolkit == toolkit
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (isConnected) Color(0xFF4DDB8A).copy(alpha = 0.5f) else NeonOrange.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        if (isConnected) {
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                color = Color(0xFF4DDB8A).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    "متصل",
                                                    color = Color(0xFF4DDB8A),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Button(
                                    onClick = {
                                        connectingToolkit = toolkit
                                        scope.launch {
                                            runCatching {
                                                termuxClient.linkComposioService(toolkit)
                                            }.onSuccess { url ->
                                                connectingToolkit = null
                                                if (url.isNotBlank()) {
                                                    prefs.edit().putBoolean("connected_$toolkit", true).apply()
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    context.startActivity(intent)
                                                    Toast.makeText(context, "صفحه اتصال در مرورگر باز شد", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "لینک اتصال دریافت نشد", Toast.LENGTH_SHORT).show()
                                                }
                                            }.onFailure { err ->
                                                connectingToolkit = null
                                                Toast.makeText(context, "خطا در اتصال: " + (err.message ?: "خطای ناشناخته").take(150), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    enabled = !isBusy,
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = if (isConnected) MaterialTheme.colorScheme.secondaryContainer else NeonOrange
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isConnected) "مدیریت" else "اتصال", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SocialLink(icon: Int, label: String, url: String, context: android.content.Context) {
    IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }, modifier = Modifier.size(48.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = NeonOrange,
            modifier = Modifier.size(25.dp)
        )
    }
}

@Composable
private fun SheetTitle(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Surface(color = NeonOrange.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
            Icon(icon, contentDescription = null, tint = NeonOrange, modifier = Modifier.padding(10.dp).size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}
