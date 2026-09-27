package com.example.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.components.CodeinChatBridge
import com.example.ui.components.CodeinPrompt
import com.example.ui.components.CodeinSettingsSheet
import com.example.ui.components.PromptStudioSheet
import com.example.ui.theme.NeonOrange
import com.example.ui.theme.NeonOrangeDark
import com.example.ui.theme.NeonOrangeSoft
import com.example.ui.theme.TextMuted
import org.json.JSONArray
import org.json.JSONObject

private const val CODEIN_CHAT_URL = "https://chat.dphn.ai/"

private data class ChatMessage(val user: Boolean, val text: String)

private data class CodeinModel(
    val label: String,
    val id: String,
    val description: String
)

private val codeinModels = listOf(
    CodeinModel("SHΞN™ Alpha", "dolphinserver:24B", "مدل متعادل برای استفاده روزمره"),
    CodeinModel("SHΞN™ Nano", "dolphinserver2:6b", "سریع و سبک برای پاسخ‌های کوتاه"),
    CodeinModel("SHΞN™ Trinity", "dolphinserver3:trinity-mini-step225", "تحلیل عمیق و منطقی")
)

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
    var selectedModel by remember { mutableStateOf(codeinModels.first()) }
    var selectedPrompt by remember { mutableStateOf<CodeinPrompt?>(null) }
    var showModelMenu by remember { mutableStateOf(false) }
    var showPromptStudio by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val promptSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun clearChat() {
        messages.clear()
        activeAnswer = ""
        input = ""
        isStreaming = false
    }

    fun sendMessage() {
        val text = input.trim()
        if (text.isEmpty() || isStreaming || !isConnected) return
        val requestMessages = JSONArray()
        selectedPrompt?.let {
            requestMessages.put(JSONObject().put("role", "system").put("content", it.prompt))
        }
        messages.forEach { message ->
            requestMessages.put(
                JSONObject()
                    .put("role", if (message.user) "user" else "assistant")
                    .put("content", message.text)
            )
        }
        requestMessages.put(JSONObject().put("role", "user").put("content", text))
        messages.add(ChatMessage(user = true, text = text))
        input = ""
        activeAnswer = ""
        isStreaming = true
        val script = "window.CodeinNativeSend(${JSONObject.quote(requestMessages.toString())}," +
            "${JSONObject.quote(selectedModel.id)},${JSONObject.quote("logical")});"
        webView?.evaluateJavascript(script, null)
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
                        onPromptStudio = { showPromptStudio = true },
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
                            items(messages) { message -> ChatBubble(message, selectedModel.label) }
                            if (activeAnswer.isNotEmpty() || isStreaming) {
                                item { ChatBubble(ChatMessage(false, activeAnswer), selectedModel.label, isStreaming) }
                            }
                        }
                    }
                    Composer(
                        value = input,
                        onValueChange = { input = it },
                        onSend = ::sendMessage,
                        enabled = isConnected && !isStreaming,
                        modifier = Modifier.navigationBarsPadding()
                    )
                }

            }
        }
    }

    if (showPromptStudio) {
        PromptStudioSheet(
            sheetState = promptSheetState,
            onDismiss = { showPromptStudio = false },
            onSelect = { selectedPrompt = it; showPromptStudio = false }
        )
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
    onPromptStudio: () -> Unit,
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
            IconButton(onClick = onPromptStudio) { Icon(Icons.Default.AutoAwesome, "استودیو پرامپت", tint = NeonOrange) }
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
            DropdownMenu(expanded = showModelMenu, onDismissRequest = { onModelMenuChange(false) }) {
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
private fun ChatBubble(message: ChatMessage, modelLabel: String, streaming: Boolean = false) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxWidth(), contentAlignment = if (message.user) Alignment.CenterEnd else Alignment.CenterStart) {
            Surface(
                color = if (message.user) NeonOrangeDark else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(.88f)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(if (message.user) "شما" else modelLabel, color = if (message.user) Color.White else NeonOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (message.text.isEmpty() && streaming) CircularProgressIndicator(color = NeonOrange, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(message.text, style = TextStyle(textDirection = TextDirection.ContentOrRtl), lineHeight = 23.sp)
                }
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
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(26.dp),
            shadowElevation = 14.dp,
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, textDirection = TextDirection.ContentOrRtl),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp, max = 130.dp),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(end = 58.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    "پیامت را بنویس...",
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
                Surface(
                    color = if (enabled && value.isNotBlank()) NeonOrange else MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                    shadowElevation = 8.dp,
                    modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)
                ) {
                    IconButton(onClick = onSend, enabled = enabled && value.isNotBlank()) {
                        Icon(Icons.Default.Send, "ارسال", tint = if (enabled && value.isNotBlank()) Color.Black else TextMuted)
                    }
                }
            }
        }
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
