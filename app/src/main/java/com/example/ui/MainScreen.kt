package com.example.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.components.CodeinChatBridge
import com.example.ui.components.CodeinSettingsSheet
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonOrangeSoft
import com.example.ui.theme.TextMuted
import org.json.JSONArray
import org.json.JSONObject

private const val CODEIN_CHAT_URL = "https://chat.dphn.ai/"

private data class ChatMessage(
    val user: Boolean,
    val text: String,
    val attachmentName: String? = null,
    val requestText: String = text
)

private data class CodeinAttachment(val name: String, val content: String)

private data class DownloadPayload(val name: String, val content: String)

private sealed interface MessagePart {
    data class Text(val value: String) : MessagePart
    data class Code(val language: String, val value: String) : MessagePart
}

private data class CodeinModel(
    val label: String,
    val id: String,
    val description: String
)

private val codeinModels = listOf(
    CodeinModel("SHΞN™ Alpha", "dolphinserver:24B", "مدل متعادل برای استفاده روزمره"),
    CodeinModel("SHΞN™ Nano", "dolphinserver2:6b", "سریع و سبک برای پاسخ‌های کوتاه")
)

private const val MAX_ATTACHMENT_BYTES = 1024 * 1024

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val bridge = remember { CodeinChatBridge() }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isConnected by remember { mutableStateOf(false) }
    var connectionFailed by remember { mutableStateOf(false) }
    var isStreaming by remember { mutableStateOf(false) }
    var activeAnswer by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<CodeinAttachment?>(null) }
    var pendingDownload by remember { mutableStateOf<DownloadPayload?>(null) }
    var selectedModel by remember { mutableStateOf(codeinModels.first()) }
    var showModelMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val settingsSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { readTextAttachment(context, uri) }
            .onSuccess { attachment = it }
            .onFailure {
                android.widget.Toast.makeText(context, "فقط فایل متنی تا یک مگابایت قابل پیوست است", android.widget.Toast.LENGTH_SHORT).show()
            }
    }
    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val payload = pendingDownload
        pendingDownload = null
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(payload.content.toByteArray(Charsets.UTF_8))
                } ?: error("فایل باز نشد")
            }.onFailure {
                android.widget.Toast.makeText(context, "ذخیره فایل انجام نشد", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearChat() {
        messages.clear()
        activeAnswer = ""
        input = ""
        isStreaming = false
    }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Codein", text))
        android.widget.Toast.makeText(context, "کپی شد", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun downloadText(content: String, suggestedName: String) {
        pendingDownload = DownloadPayload(suggestedName, content)
        downloadLauncher.launch(suggestedName)
    }

    fun sendRequest(
        displayText: String,
        requestText: String,
        attachmentName: String?,
        history: List<ChatMessage>
    ) {
        if (isStreaming || !isConnected) return
        val requestMessages = JSONArray()
        history.forEach { message ->
            requestMessages.put(
                JSONObject()
                    .put("role", if (message.user) "user" else "assistant")
                    .put("content", if (message.user) message.requestText else message.text)
            )
        }
        requestMessages.put(JSONObject().put("role", "user").put("content", requestText))
        messages.add(ChatMessage(user = true, text = displayText, attachmentName = attachmentName, requestText = requestText))
        activeAnswer = ""
        isStreaming = true
        val target = webView
        if (target == null) {
            isStreaming = false
            messages.add(ChatMessage(user = false, text = "اتصال داخلی آماده نیست؛ دوباره تلاش کن."))
            return
        }
        val script = "if (typeof window.CodeinNativeSend === 'function') " +
            "window.CodeinNativeSend(${JSONObject.quote(requestMessages.toString())}," +
            "${JSONObject.quote(selectedModel.id)},${JSONObject.quote("logical")});" +
            " else CodeinBridge.error('پل اتصال آماده نیست');"
        target.evaluateJavascript(script, null)
    }

    fun sendMessage() {
        val text = input.trim()
        val selectedAttachment = attachment
        if ((text.isEmpty() && selectedAttachment == null) || isStreaming || !isConnected) return
        val requestText = buildRequestContent(text, selectedAttachment)
        val displayText = text.ifEmpty { "فایل: ${selectedAttachment?.name}" }
        input = ""
        attachment = null
        sendRequest(displayText, requestText, selectedAttachment?.name, messages.toList())
    }

    fun retryMessage(message: ChatMessage) {
        if (isStreaming || !isConnected) return
        val index = messages.indexOf(message)
        if (index < 0) return
        val history = messages.take(index)
        while (messages.size > index) messages.removeAt(messages.lastIndex)
        sendRequest(message.text, message.requestText, message.attachmentName, history)
    }

    bridge.onStarted = { isStreaming = true }
    bridge.onConnection = { connected ->
        isConnected = connected
        if (connected) connectionFailed = false
    }
    bridge.onToken = { activeAnswer += it }
    bridge.onFinished = {
        if (activeAnswer.isNotBlank()) messages.add(ChatMessage(user = false, text = activeAnswer))
        activeAnswer = ""
        isStreaming = false
    }
    bridge.onError = {
        if (activeAnswer.isNotBlank()) messages.add(ChatMessage(user = false, text = activeAnswer))
        activeAnswer = ""
        isStreaming = false
        messages.add(ChatMessage(user = false, text = "خطا در ارتباط با سرویس: $it"))
    }

    LaunchedEffect(messages.size, activeAnswer) {
        if (messages.isNotEmpty() || activeAnswer.isNotEmpty()) {
            val lastItem = messages.size - 1 + if (activeAnswer.isNotEmpty()) 1 else 0
            listState.animateScrollToItem(lastItem.coerceAtLeast(0))
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().alpha(0f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webView = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.userAgentString = WebSettings.getDefaultUserAgent(ctx).replace("; wv", "")
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            addJavascriptInterface(bridge, "CodeinBridge")
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    isConnected = false
                                    connectionFailed = false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    view?.evaluateJavascript(CODEIN_JS_BRIDGE, null)
                                    fun probeConnection(attempt: Int) {
                                        val target = view ?: return
                                        target.evaluateJavascript(CODEIN_CONNECTION_PROBE, null)
                                        if (attempt < 20) {
                                            target.postDelayed({
                                                if (!isConnected) probeConnection(attempt + 1)
                                            }, 400L)
                                        } else {
                                            target.postDelayed({
                                                if (!isConnected) connectionFailed = true
                                            }, 500L)
                                        }
                                    }
                                    probeConnection(0)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        isConnected = false
                                        connectionFailed = true
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val host = request?.url?.host ?: return false
                                    return !host.endsWith("dphn.ai") && !host.endsWith("cloudflare.com")
                                }
                            }
                            loadUrl(CODEIN_CHAT_URL)
                        }
                    },
                    update = { webView = it }
                )
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    CodeinHeader(
                        selectedModel = selectedModel,
                        showModelMenu = showModelMenu,
                        onModelMenuChange = { showModelMenu = it },
                        onSelectModel = { selectedModel = it; showModelMenu = false },
                        onNewChat = ::clearChat,
                        onSettings = { showSettings = true },
                        isConnected = isConnected,
                        connectionFailed = connectionFailed,
                        onRetry = {
                            connectionFailed = false
                            isConnected = false
                            webView?.reload()
                        }
                    )
                    if (messages.isEmpty() && activeAnswer.isEmpty()) {
                        EmptyState(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onSuggestion = { input = it }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            items(messages) { message ->
                                ChatBubble(
                                    message = message,
                                    modelLabel = selectedModel.label,
                                    onCopy = ::copyToClipboard,
                                    onRetry = ::retryMessage,
                                    onDownload = ::downloadText
                                )
                            }
                            if (activeAnswer.isNotEmpty() || isStreaming) {
                                item {
                                    ChatBubble(
                                        message = ChatMessage(false, activeAnswer),
                                        modelLabel = selectedModel.label,
                                        streaming = isStreaming,
                                        onCopy = ::copyToClipboard,
                                        onRetry = ::retryMessage,
                                        onDownload = ::downloadText
                                    )
                                }
                            }
                        }
                    }
                    Composer(
                        value = input,
                        onValueChange = { input = it },
                        onSend = ::sendMessage,
                        enabled = isConnected && !isStreaming,
                        modifier = Modifier.navigationBarsPadding(),
                        attachmentName = attachment?.name,
                        onAttach = {
                            attachmentLauncher.launch(arrayOf("text/*", "application/json", "application/xml", "application/javascript"))
                        },
                        onRemoveAttachment = { attachment = null }
                    )
                }

            }
        }
    }

    if (showSettings) {
        CodeinSettingsSheet(
            sheetState = settingsSheetState,
            onDismiss = { showSettings = false },
            onClearChat = { clearChat(); showSettings = false },
            onClearSession = {
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                webView?.clearCache(true)
                isConnected = false
                connectionFailed = false
                attachment = null
                webView?.reload()
            },
            isConnected = isConnected
        )
    }

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }
}

@Composable
private fun CodeinHeader(
    selectedModel: CodeinModel,
    showModelMenu: Boolean,
    onModelMenuChange: (Boolean) -> Unit,
    onSelectModel: (CodeinModel) -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    isConnected: Boolean,
    connectionFailed: Boolean,
    onRetry: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.shen_logo),
                contentDescription = "SHΞN™ Coder",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("SHΞN™ Coder", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Text(
                    when {
                        isConnected -> "Codein  •  آماده گفتگو"
                        connectionFailed -> "Codein  •  تلاش دوباره"
                        else -> "Codein  •  در حال اتصال"
                    },
                    color = when {
                        isConnected -> NeonOrange
                        connectionFailed -> NeonOrangeSoft
                        else -> TextMuted
                    },
                    fontSize = 11.sp,
                    modifier = Modifier.clickable(enabled = connectionFailed, onClick = onRetry)
                )
            }
            IconButton(onClick = onNewChat) { Icon(Icons.Default.Add, "گفتگوی جدید", tint = NeonOrange) }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "تنظیمات", tint = NeonOrange) }
        }
        Spacer(Modifier.height(10.dp))
        Box {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth().clickable { onModelMenuChange(true) }
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp).fillMaxWidth()
                ) {
                    Column {
                        Text(selectedModel.label, color = NeonOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(selectedModel.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.MoreVert, contentDescription = "انتخاب مدل", tint = NeonOrange)
                }
            }
            DropdownMenu(
                expanded = showModelMenu,
                onDismissRequest = { onModelMenuChange(false) },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 8.dp
            ) {
                codeinModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Column { Text(model.label, fontWeight = FontWeight.Bold); Text(model.description, fontSize = 11.sp) } },
                        onClick = { onSelectModel(model) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onSuggestion: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = 28.dp)
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.shen_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(88.dp)
        )
        Spacer(Modifier.height(18.dp))
        Text("شروع یک گفتگوی تازه", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("با SHΞN™ Coder ایده‌ات را به پاسخ تبدیل کن", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Suggestion("یک ایده بده", "برای یک پروژه جدید ایده بده", onSuggestion)
            Suggestion("کد بنویس", "یک تابع تمیز و کوتاه بنویس", onSuggestion)
        }
    }
}

@Composable
private fun Suggestion(label: String, value: String, onClick: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
        modifier = Modifier.clickable { onClick(value) }
    ) { Text(label, color = NeonOrange, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    modelLabel: String,
    streaming: Boolean = false,
    onCopy: (String) -> Unit,
    onRetry: (ChatMessage) -> Unit,
    onDownload: (String, String) -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(Modifier.fillMaxWidth(), contentAlignment = if (message.user) Alignment.CenterEnd else Alignment.CenterStart) {
            Surface(
                color = if (message.user) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(.94f).animateContentSize()
            ) {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (message.user) "شما" else modelLabel,
                                color = if (message.user) NeonOrangeSoft else NeonOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            message.attachmentName?.let {
                                Text("فایل پیوست: $it", color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (message.user) Icon(Icons.Default.AttachFile, contentDescription = null, tint = TextMuted, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.height(7.dp))
                    if (message.text.isEmpty() && streaming) {
                        CircularProgressIndicator(color = NeonOrange, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        splitMessage(message.text).forEach { part ->
                            when (part) {
                                is MessagePart.Text -> if (part.value.isNotBlank()) {
                                    Text(
                                        part.value.trim(),
                                        style = TextStyle(textDirection = TextDirection.ContentOrRtl),
                                        lineHeight = 23.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                is MessagePart.Code -> CodeBlock(part.language, part.value, onCopy, onDownload)
                            }
                        }
                        if (message.text.isNotBlank()) {
                            MessageActions(
                                user = message.user,
                                onCopy = { onCopy(message.text) },
                                onRetry = { onRetry(message) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageActions(user: Boolean, onCopy: () -> Unit, onRetry: () -> Unit) {
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) {
        SmallActionButton(Icons.Default.ContentCopy, "کپی", onCopy)
        if (user) SmallActionButton(Icons.Default.Refresh, "تلاش دوباره", onRetry)
    }
}

@Composable
private fun SmallActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = label, tint = TextMuted, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun CodeBlock(
    language: String,
    code: String,
    onCopy: (String) -> Unit,
    onDownload: (String, String) -> Unit
) {
    val scrollState = rememberScrollState()
    val displayLanguage = language.ifBlank { "code" }
    Surface(
        color = Color(0xFF0B0D11),
        shape = RoundedCornerShape(15.dp),
        shadowElevation = 5.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) {
        Column(Modifier.padding(9.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(displayLanguage, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    SmallActionButton(Icons.Default.ContentCopy, "کپی کد") { onCopy(code) }
                    SmallActionButton(Icons.Default.Download, "دانلود کد") { onDownload(code, codeFileName(displayLanguage)) }
                }
                Text(
                    code.trimEnd(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState).padding(horizontal = 4.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    attachmentName: String?,
    onAttach: () -> Unit,
    onRemoveAttachment: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(26.dp),
            shadowElevation = 14.dp,
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 7.dp)) {
                attachmentName?.let { name ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(16.dp))
                        Text(name, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 5.dp))
                        IconButton(onClick = onRemoveAttachment, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "حذف فایل", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 140.dp)) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, textDirection = TextDirection.ContentOrRtl),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(start = 52.dp, end = 58.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                if (value.isEmpty()) {
                                    Text(
                                        if (attachmentName == null) "پیامت را بنویس..." else "متن همراه فایل (اختیاری)",
                                        color = TextMuted,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    IconButton(onClick = onAttach, enabled = enabled, modifier = Modifier.align(Alignment.CenterStart).size(42.dp)) {
                        Icon(Icons.Default.AttachFile, "پیوست فایل", tint = if (enabled) NeonOrange else TextMuted)
                    }
                    val canSend = enabled && (value.isNotBlank() || attachmentName != null)
                    Surface(
                        color = if (canSend) NeonOrange else MaterialTheme.colorScheme.surface,
                        shape = CircleShape,
                        shadowElevation = 8.dp,
                        modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)
                    ) {
                        IconButton(onClick = onSend, enabled = canSend) {
                            Icon(Icons.Default.Send, "ارسال", tint = if (canSend) Color.Black else TextMuted)
                        }
                    }
                }
            }
        }
    }
}

private fun readTextAttachment(context: Context, uri: Uri): CodeinAttachment {
    val resolver = context.contentResolver
    var name = "attachment.txt"
    var declaredSize: Long? = null
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { name = it }
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
        }
    }
    if (declaredSize != null && declaredSize!! > MAX_ATTACHMENT_BYTES) error("فایل بزرگ است")
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("فایل باز نشد")
    if (bytes.size > MAX_ATTACHMENT_BYTES) error("فایل بزرگ است")
    val content = bytes.toString(Charsets.UTF_8)
    if (content.contains('\u0000')) error("فایل متنی نیست")
    return CodeinAttachment(name, content)
}

private fun buildRequestContent(text: String, attachment: CodeinAttachment?): String {
    if (attachment == null) return text
    return buildString {
        if (text.isNotBlank()) {
            append(text)
            append("\n\n")
        }
        append("فایل پیوست‌شده: ")
        append(attachment.name)
        append("\n```\n")
        append(attachment.content)
        append("\n```")
    }
}

private fun splitMessage(text: String): List<MessagePart> {
    if (text.isEmpty()) return emptyList()
    val fence = Regex("""```([\w#+.\-]*)\s*\n?([\s\S]*?)```""")
    val parts = mutableListOf<MessagePart>()
    var cursor = 0
    fence.findAll(text).forEach { match ->
        if (match.range.first > cursor) parts += MessagePart.Text(text.substring(cursor, match.range.first))
        parts += MessagePart.Code(
            language = match.groupValues[1].ifBlank { "code" },
            value = match.groupValues[2].trim('\n')
        )
        cursor = match.range.last + 1
    }
    if (cursor < text.length) parts += MessagePart.Text(text.substring(cursor))
    return parts.ifEmpty { listOf(MessagePart.Text(text)) }
}

private fun codeFileName(language: String): String {
    return when (language.lowercase()) {
        "kotlin", "kt" -> "code.kt"
        "java" -> "code.java"
        "javascript", "js" -> "code.js"
        "typescript", "ts" -> "code.ts"
        "python", "py" -> "code.py"
        "json" -> "code.json"
        "xml" -> "code.xml"
        "html" -> "code.html"
        "css" -> "code.css"
        "sql" -> "code.sql"
        "bash", "sh", "shell" -> "code.sh"
        else -> "code.txt"
    }
}

private val CODEIN_CONNECTION_PROBE = """
(async function() {
  try {
    const response = await fetch('/api/models', {headers: {'Accept': 'application/json'}});
    if (!response.ok) {
      CodeinBridge.connection('false');
      return;
    }
    const payload = await response.json();
    CodeinBridge.connection(Array.isArray(payload.data) && payload.data.length > 0 ? 'true' : 'false');
  } catch (_) {
    CodeinBridge.connection('false');
  }
})();
""".trimIndent()

private val CODEIN_JS_BRIDGE = """
(function() {
  if (window.CodeinNativeSend) return;
  window.CodeinNativeSend = async function(messagesJson, model, template) {
    try {
      CodeinBridge.started();
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: {'Content-Type': 'application/json', 'Accept': 'text/event-stream'},
        body: JSON.stringify({messages: JSON.parse(messagesJson), model: model, template: template})
      });
      if (!response.ok) throw new Error('HTTP ' + response.status);
      if (!response.body) throw new Error('پاسخی از سرویس دریافت نشد');
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let finished = false;
      const textOf = value => {
        if (typeof value === 'string') return value;
        if (Array.isArray(value)) return value.map(item => typeof item === 'string' ? item : (item && item.text) || '').join('');
        return value && typeof value.text === 'string' ? value.text : '';
      };
      const consume = chunk => {
        buffer += chunk;
        const lines = buffer.split(/\r?\n/);
        buffer = lines.pop() || '';
        for (const rawLine of lines) {
          const line = rawLine.trim();
          if (!line || !line.startsWith('data:')) continue;
          const data = line.slice(line.indexOf(':') + 1).trimStart();
          if (data === '[DONE]') {
            finished = true;
            break;
          }
          try {
            const delta = (JSON.parse(data).choices || [])[0]?.delta || {};
            const token = textOf(delta.content);
            if (token) CodeinBridge.token(token);
          } catch (_) {
            // Ignore an incomplete SSE frame; the next chunk will finish it.
          }
        }
      };
      while (true) {
        const part = await reader.read();
        if (part.done) {
          consume(decoder.decode());
          if (buffer.trim()) consume('\n');
          break;
        }
        consume(decoder.decode(part.value, {stream: true}));
        if (finished) break;
      }
      CodeinBridge.finished();
    } catch (error) {
      CodeinBridge.error(String(error && error.message ? error.message : error));
    }
  };
})();
""".trimIndent()
