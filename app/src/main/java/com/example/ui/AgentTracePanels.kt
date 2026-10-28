package com.example.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonOrangeSoft
import com.example.ui.theme.TextMuted

@Composable
internal fun ReasoningTracePanel(reasoning: String) {
    var expanded by remember(reasoning) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("◈  Thinking", color = NeonOrangeSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(if (expanded) "−" else "+", color = TextMuted, fontSize = 14.sp)
            }
            if (expanded) {
                Spacer(Modifier.height(7.dp))
                Text(
                    reasoning,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
internal fun TerminalTracePanel(command: String, output: String, exitCode: Int) {
    var expanded by remember(command, output) { mutableStateOf(true) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("⌁  TERMINAL LOG", color = NeonOrangeSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (exitCode == 0) "Completed · exit 0" else if (exitCode < 0) "Failed" else "Exit $exitCode",
                        color = if (exitCode == 0) AccentGreen else AccentRed,
                        fontSize = 10.sp
                    )
                }
                Text(if (expanded) "−" else "+", color = TextMuted, fontSize = 14.sp)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text("COMMAND", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(
                    command,
                    color = NeonOrangeSoft,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp)).padding(8.dp)
                )
                Spacer(Modifier.height(7.dp))
                Text("OUTPUT", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(
                    output.ifBlank { "(no output)" },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp)).padding(8.dp)
                )
            }
        }
    }
}

@Composable
internal fun TermuxPermissionPanel(
    command: String,
    onAllowOnce: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onDeny: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .8f),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("⌑  اجازه اجرای فرمان", color = NeonOrangeSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                "این فرمان قبل از اجرا نیاز به تأیید تو دارد. فقط اگر دستور را می‌شناسی اجازه بده.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Text(
                command,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp)).padding(8.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAllowOnce, colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)) {
                        Text("فقط این بار")
                    }
                    OutlinedButton(onClick = onDeny) {
                        Text("رد کردن")
                    }
                }
                TextButton(onClick = onAlwaysAllow) {
                    Text("همیشه برای همین دستور", color = NeonOrangeSoft, fontSize = 11.sp)
                }
            }
        }
    }
}
