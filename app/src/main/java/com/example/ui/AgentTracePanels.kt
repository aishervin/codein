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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import android.os.SystemClock
import kotlinx.coroutines.delay

internal data class AgentTraceEvent(val phase: String, val label: String)

@Composable
internal fun AgentLivePanel(
    events: List<AgentTraceEvent>,
    isStreaming: Boolean,
    startedAtElapsed: Long,
    onStop: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(isStreaming, startedAtElapsed) {
        if (isStreaming) {
            while (true) {
                elapsedSeconds = ((SystemClock.elapsedRealtime() - startedAtElapsed) / 1000).toInt().coerceAtLeast(0)
                delay(1000)
            }
        }
    }
    val current = events.lastOrNull()
    val currentColor = when (current?.phase) {
        "waiting" -> NeonOrangeSoft
        "receiving" -> MaterialTheme.colorScheme.primary
        "tool" -> NeonOrange
        "done" -> AccentGreen
        "approval" -> NeonOrangeSoft
        "error", "cancelled" -> AccentRed
        else -> TextMuted
    }
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
                Text("⌁  گزارش زندهٔ Agent · ${events.size}", color = NeonOrangeSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (onStop != null) {
                    TextButton(onClick = onStop) { Text("توقف", color = AccentRed, fontSize = 11.sp) }
                }
                Text(if (expanded) "−" else "+", color = TextMuted, fontSize = 14.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("●", color = currentColor, fontSize = 12.sp)
                Text(
                    current?.label ?: "در حال آماده‌سازی درخواست…",
                    color = currentColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isStreaming) {
                    Text("%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60), color = TextMuted, fontSize = 10.sp)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(7.dp))
                Column(Modifier.height(150.dp).verticalScroll(rememberScrollState())) {
                    events.takeLast(40).forEach { event ->
                        val color = when (event.phase) {
                            "waiting" -> NeonOrangeSoft
                            "receiving" -> MaterialTheme.colorScheme.primary
                            "tool" -> NeonOrange
                            "done" -> AccentGreen
                            "approval" -> NeonOrangeSoft
                            "error", "cancelled" -> AccentRed
                            else -> TextMuted
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("●", color = color, fontSize = 9.sp, lineHeight = 16.sp)
                            Text(event.label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

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
