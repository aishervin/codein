package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.AgentExecutionState
import com.example.data.ChatMessageEntity
import com.example.termux.ConnectionStatus
import com.example.ui.MainViewModel

@Composable
fun ChatScreen(viewModel: MainViewModel) {
    val messages by viewModel.chatMessages.collectAsState()
    val connStatus by viewModel.connectionStatus.collectAsState()
    val execState by viewModel.executionState.collectAsState()
    val isFa = viewModel.currentLanguage.collectAsState().value == "fa"

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp)
    ) {
        // Status Bar Chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (statusText, statusColor) = when (connStatus) {
                is ConnectionStatus.ConnectedSSH -> Pair(
                    if (isFa) "اتصال مستقیم SSH (پورت ۸۰۲۲)" else "Connected SSH (8022)",
                    Color(0xFF00E676)
                )
                is ConnectionStatus.ConnectedIntentFallback -> Pair(
                    if (isFa) "اتصال جانبی Intent RUN_COMMAND" else "Intent RUN_COMMAND Fallback",
                    Color(0xFFFFB300)
                )
                is ConnectionStatus.Connecting -> Pair(
                    if (isFa) "در حال اتصال..." else "Connecting...",
                    Color(0xFF00E5FF)
                )
                else -> Pair(
                    if (isFa) "قطع اتصال به ترموکس" else "Disconnected from Termux",
                    Color(0xFFFF5252)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .height(10.dp)
                        .width(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (execState is AgentExecutionState.ExecutingTool || execState is AgentExecutionState.Planning) {
                IconButton(
                    onClick = { viewModel.stopAgentExecution() },
                    modifier = Modifier.testTag("stop_agent_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Agent",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Execution Indicator Bar
        if (execState is AgentExecutionState.ExecutingTool || execState is AgentExecutionState.Planning) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(20.dp)
                            .width(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    val label = when (val state = execState) {
                        is AgentExecutionState.Planning -> if (isFa) "در حال برنامه‌ریزی..." else "Planning next step..."
                        is AgentExecutionState.ExecutingTool -> if (isFa) "اجرای ابزار ${state.toolName}..." else "Executing ${state.toolName}..."
                        else -> ""
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                ChatMessageBubble(msg = msg, isFa = isFa)
            }
        }

        // Quick Suggestion Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = if (isFa) listOf(
                "nodejs رو نصب کن و یه سرور بالا بیار",
                "وضعیت پورت‌ها و پروسه‌ها رو بررسی کن",
                "یه تانل کلودفلر جدید بساز",
                "ریپوی گیت‌هاب من رو بررسی کن"
            ) else listOf(
                "Install nodejs & start HTTP server",
                "Check running processes & open ports",
                "Create a Cloudflare tunnel",
                "Check my GitHub repository"
            )

            items(suggestions) { sug ->
                AssistChip(
                    onClick = {
                        inputText = sug
                        viewModel.sendMessage(sug)
                        inputText = ""
                    },
                    label = { Text(sug, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                placeholder = {
                    Text(
                        if (isFa) "دستور خود را بنویسید (مثلاً: فایل config رو ادیت کن)..."
                        else "Enter prompt (e.g. edit config file)..."
                    )
                },
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        val textToSend = inputText
                        inputText = ""
                        viewModel.sendMessage(textToSend)
                    }
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun ChatMessageBubble(msg: ChatMessageEntity, isFa: Boolean) {
    val isUser = msg.sender == "user"
    val isTool = msg.sender == "tool"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isTool) {
            ToolCallCard(msg = msg)
        } else {
            val bgColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = bgColor),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isUser) (if (isFa) "شما" else "You") else "SHΞN",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = msg.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun ToolCallCard(msg: ChatMessageEntity) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(
            containerColor = if (msg.isError) Color(0xFF331616) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Terminal Tool",
                        tint = if (msg.isError) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (msg.isError) "اجرای ابزار با خطا مواجه شد" else "اجرای موفق ابزار",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (msg.isError) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (msg.executionTimeMs > 0) {
                        Text(
                            text = "${msg.executionTimeMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = "Expand"
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = msg.content,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
