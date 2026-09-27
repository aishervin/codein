package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.ui.theme.NeonOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeinSettingsSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onClearChat: () -> Unit,
    onClearSession: () -> Unit,
    isConnected: Boolean
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            SheetTitle(Icons.Default.Settings, "تنظیمات Codein", "کنترل نشست و تجربه گفتگو")
            Spacer(Modifier.height(14.dp))
            SettingRow(
                icon = Icons.Default.DeleteSweep,
                title = "گفتگوی جدید",
                subtitle = "پیام‌های این گفتگو پاک می‌شوند",
                onClick = {
                    onClearChat()
                    Toast.makeText(context, "گفتگو پاک شد", Toast.LENGTH_SHORT).show()
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
            SettingRow(
                icon = Icons.Default.DeleteSweep,
                title = "پاک‌سازی نشست مرورگر",
                subtitle = "کوکی‌ها و داده‌های نشست داخلی پاک می‌شوند",
                onClick = {
                    onClearSession()
                    Toast.makeText(context, "نشست پاک شد", Toast.LENGTH_SHORT).show()
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
            SettingRow(
                icon = Icons.Default.Info,
                title = "وضعیت سرویس",
                subtitle = if (isConnected) "اتصال آماده است" else "در حال اتصال به سرویس",
                onClick = {}
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("☬Exclusive SHΞN™ made", color = NeonOrange, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("SHΞN™ Coder  •  Codein", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("Developer: SHΞN™", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                SocialLink("Telegram", "https://t.me/shervini", context)
                SocialLink("GitHub", "https://github.com/aishervin", context)
                SocialLink("X", "https://x.com/shervinonx", context)
            }
        }
    }
}

@Composable
private fun SocialLink(label: String, url: String, context: android.content.Context) {
    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
        Text(label, color = NeonOrange, fontSize = 12.sp)
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
