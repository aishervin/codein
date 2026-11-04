package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.NeonOrange

private const val TERMUX_UPDATE_COMMAND = "~/.codein/bin/codein-links restart"

/** The local bridge update command is copied for the user to run in Termux. */
private fun copyTermuxCommand(context: android.content.Context, title: String, command: String) {
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(title, command))
    Toast.makeText(context, "دستور Termux کپی شد", Toast.LENGTH_SHORT).show()
}

private fun copyText(context: android.content.Context, message: String) {
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Codein", message))
    Toast.makeText(context, "کپی شد", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeinSettingsSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onClearSession: () -> Unit,
    modelActive: Boolean,
    termuxConnected: Boolean,
    onOpenHistory: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenTermux: () -> Unit,
    onOpenIntegrations: () -> Unit,
    onOpenToolTokens: () -> Unit,
    onOpenPatches: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val context = LocalContext.current
    // Keep settings controls aligned with the user's language instead of forcing
    // English layout direction on Persian/Arabic devices.
    val settingsDirection = if (
        context.resources.configuration.locales[0].language in setOf("fa", "ar", "he", "ur")
    ) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides settingsDirection) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                SheetTitle(Icons.Default.Settings, "Settings", "پیکربندی و مدیریت سیستم")
                Spacer(Modifier.height(18.dp))
                SettingSection("AI MODELS & TOKENS")
                SettingRow(
                    icon = Icons.Default.Extension,
                    title = "مدل‌های هوش مصنوعی",
                    subtitle = "انتخاب سرویس، مدل و استعلام API",
                    onClick = onOpenModels
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                SettingRow(
                    icon = Icons.Default.Extension,
                    title = "ذخیره توکن و اعتبارنامه‌ها",
                    subtitle = "مدیریت کلیدها در خزانه محلی Termux",
                    enabled = termuxConnected,
                    onClick = onOpenToolTokens
                )

                Spacer(Modifier.height(16.dp))
                SettingSection("TERMINAL")
                SettingRow(
                    icon = painterResource(R.drawable.ic_termux),
                    title = "اتصال Termux",
                    subtitle = if (termuxConnected) "پل MCP متصل است · مشاهده یا تنظیم اتصال" else "تنظیم آدرس و توکن اتصال پل محلی",
                    onClick = onOpenTermux
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                SettingRow(
                    icon = Icons.Default.Language,
                    title = "سرویس‌های متصل (Composio)",
                    subtitle = if (modelActive && termuxConnected) "مدیریت اتصال‌های OAuth" else "نیاز به مدل فعال و پل متصل دارد",
                    enabled = modelActive && termuxConnected,
                    onClick = onOpenIntegrations
                )
                Spacer(Modifier.height(16.dp))
                SettingSection("HISTORY & DATA")
                SettingRow(
                    icon = Icons.Default.History,
                    title = "تاریخچه گفت‌وگوها",
                    subtitle = "مشاهده یا حذف نشست‌های ذخیره شده",
                    onClick = onOpenHistory
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                SettingRow(
                    icon = Icons.Default.DeleteSweep,
                    title = "پاکسازی داده‌های مرورگر",
                    subtitle = "حذف کوکی‌ها و نشست‌های مرورگر داخلی",
                    onClick = {
                        onClearSession()
                        Toast.makeText(context, "Browser data cleared", Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(Modifier.height(16.dp))
                SettingSection("SYSTEM")
                SettingRow(
                    icon = Icons.Default.Download,
                    title = "آپدیت‌ها و پچ‌های سیستمی",
                    subtitle = "دستور به‌روزرسانی پل محلی را کپی کن",
                    onClick = { copyTermuxCommand(context, "Codein Termux update", TERMUX_UPDATE_COMMAND) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                SettingRow(
                    icon = Icons.Default.Settings,
                    title = "پچ‌ها و به‌روزرسانی درون‌برنامه",
                    subtitle = "بررسی بسته‌های جدید و نصب پچ",
                    onClick = onOpenPatches
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                SettingRow(
                    icon = Icons.Default.Info,
                    title = "درباره Codein",
                    subtitle = "اطلاعات برنامه و لینک‌های توسعه‌دهنده",
                    onClick = onOpenAbout
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun CodeinAboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { SheetTitle(Icons.Default.Info, "About Codein", "A private interface by SHΞЯVIN™") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Designed and crafted for focused conversations and code.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("CODΞiN™  •  SHΞN™", color = NeonOrange, fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SocialLink(R.drawable.ic_social_telegram, "Telegram", "https://t.me/shervini", context)
                    SocialLink(R.drawable.ic_social_x, "X", "https://x.com/shervinonx", context)
                    SocialLink(R.drawable.ic_social_github, "GitHub", "https://github.com/aishervin", context)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
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
    icon: Any,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp)
    ) {
        when (icon) {
            is ImageVector -> Icon(icon, contentDescription = null, tint = if (enabled) NeonOrange else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f), modifier = Modifier.size(23.dp))
            is androidx.compose.ui.graphics.painter.Painter -> Icon(icon, contentDescription = null, tint = if (enabled) NeonOrange else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f), modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) .7f else .35f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingSection(title: String) {
    Text(title, color = NeonOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 3.dp))
}
