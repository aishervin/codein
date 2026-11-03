package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.termux.ConnectionStatus
import com.example.ui.MainViewModel

@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val connStatus by viewModel.connectionStatus.collectAsState()
    val isFa = viewModel.currentLanguage.collectAsState().value == "fa"

    var showScript by remember { mutableStateOf(false) }
    var copiedNotice by remember { mutableStateOf(false) }

    val oneLiner = remember { viewModel.getBootstrapOneLiner() }
    val fullScript = remember { viewModel.getBootstrapFullScript() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (isFa) "🚀 راه‌اندازی و راه‌انداز خودکار SHΞN" else "🚀 SHΞN Onboarding & Bootstrap",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isFa)
                "برای اتصال کامل عامل هوشمند به ترموکس دستگاه شما، فقط یک دستور زیر را در ترموکس کپی و اجرا کنید."
            else
                "Copy and paste this single command into Termux to bootstrap SSH & access.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Status Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (connStatus) {
                    is ConnectionStatus.ConnectedSSH -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isFa) "وضعیت اتصال:" else "Connection Status:",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = when (connStatus) {
                            is ConnectionStatus.ConnectedSSH -> if (isFa) "✅ متصل شد (SSH 8022)" else "✅ Connected (SSH 8022)"
                            is ConnectionStatus.ConnectedIntentFallback -> if (isFa) "🟡 متصل شد (Intent Fallback)" else "🟡 Connected (Intent)"
                            is ConnectionStatus.Connecting -> if (isFa) "⏳ در حال بررسی..." else "⏳ Testing Connection..."
                            else -> if (isFa) "🔴 در انتظار اتصال..." else "🔴 Waiting for connection..."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier.testTag("test_ssh_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Test")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isFa) "تست اتصال" else "Test SSH")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Single Command Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isFa) "دستور تک‌خطی بوتاسترپ:" else "One-Liner Bootstrap Command:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background, shape = RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = oneLiner,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(oneLiner))
                            copiedNotice = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_command_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copiedNotice) (if (isFa) "کپی شد!" else "Copied!") else (if (isFa) "کپی دستور" else "Copy Command"))
                    }

                    OutlinedButton(
                        onClick = { openTermuxApp(context) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_termux_button")
                    ) {
                        Icon(imageVector = Icons.Default.Launch, contentDescription = "Open")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isFa) "باز کردن ترموکس" else "Open Termux")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Toggle Full Script Button
        OutlinedButton(
            onClick = { showScript = !showScript },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (showScript) (if (isFa) "پنهان‌سازی اسکریپت کامل" else "Hide Full Script")
                else (if (isFa) "مشاهده اسکریپت کامل بوتاسترپ" else "Show Full Script")
            )
        }

        AnimatedVisibility(visible = showScript) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = fullScript,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Troubleshooting Section
        Text(
            text = if (isFa) "راهنمای حل مشکلات احتمالی:" else "Troubleshooting Guide:",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        val steps = if (isFa) listOf(
            "۱. اگر دسترسی ذخیره‌سازی داده نشد: دستور termux-setup-storage را در ترموکس بزنید.",
            "۲. اگر پورت 8022 باز نشد: دستور sshd -p 8022 را دستی وارد کنید.",
            "۳. مطمئن شوید برنامه Termux نصب و اجازه اجرا در پس‌زمینه دارد."
        ) else listOf(
            "1. Storage permission: Run termux-setup-storage in Termux.",
            "2. SSH port closed: Manually execute sshd -p 8022.",
            "3. Ensure Termux app is installed with background execution allowed."
        )

        steps.forEach { step ->
            Text(
                text = step,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

fun openTermuxApp(context: Context) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.termux")
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            val marketIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://f-droid.org/en/packages/com.termux/"))
            context.startActivity(marketIntent)
        }
    } catch (_: Exception) {}
}
