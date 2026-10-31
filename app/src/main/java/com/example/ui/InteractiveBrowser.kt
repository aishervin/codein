package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume

internal const val BROWSER_ACTION_START = "[[CODEIN_BROWSER_REQUEST:v1]]"
internal const val BROWSER_ACTION_END = "[[/CODEIN_BROWSER_REQUEST]]"
internal val CodeinBrowserBlue = Color(0xFF42A5F5)

internal data class BrowserPageState(
    val url: String = "",
    val title: String = "",
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val error: String = ""
)

internal data class BrowserActionRequest(
    val action: String,
    val reason: String,
    val url: String = "",
    val selector: String = "",
    val value: String = "",
    val credentialName: String = "",
    val expectedUrl: String = ""
) {
    val requiresApproval: Boolean get() = action in setOf("click", "fill", "fill_saved_credential") ||
        (action == "navigate" && requiresNavigationApproval(Uri.parse(normalizeBrowserUrl(url).orEmpty()).host.orEmpty()))
}

private fun requiresNavigationApproval(rawHost: String): Boolean {
    val host = rawHost.lowercase(Locale.ROOT).trim('[', ']')
    if (host.isBlank() || host == "localhost" || host.endsWith(".localhost") ||
        host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".lan") || host.endsWith(".test")) return true
    if (':' in host || !host.contains('.') || host.all { it.isDigit() || it == '.' }) return true
    return false
}

internal fun hasBrowserActionMarker(text: String): Boolean =
    text.contains(BROWSER_ACTION_START) || text.contains(BROWSER_ACTION_END)

internal fun parseBrowserActionRequest(text: String): BrowserActionRequest? {
    if (Regex(Regex.escape(BROWSER_ACTION_START)).findAll(text).count() != 1 ||
        Regex(Regex.escape(BROWSER_ACTION_END)).findAll(text).count() != 1) return null
    val match = Regex(Regex.escape(BROWSER_ACTION_START) + "([\\s\\S]*?)" + Regex.escape(BROWSER_ACTION_END))
        .find(text) ?: return null
    if (text.substring(0, match.range.first).isNotBlank() || text.substring(match.range.last + 1).isNotBlank()) return null
    val payload = runCatching { JSONObject(match.groupValues[1].trim()) }.getOrNull() ?: return null
    if (payload.optString("protocol") != "codein.browser.v1") return null

    val action = payload.optString("action").trim().lowercase(Locale.ROOT)
    if (action !in setOf("navigate", "inspect", "click", "fill", "fill_saved_credential")) return null
    val reason = payload.optString("reason").trim().take(500)
    val url = payload.optString("url").trim()
    val selector = payload.optString("selector").trim()
    val value = payload.optString("value")
    val credentialName = payload.optString("credential_name").trim()

    if (reason.isBlank()) return null
    if (action == "navigate" && (url.isBlank() || url.length > 2048 || normalizeBrowserUrl(url) == null)) return null
    if (selector.length > 500 || '\u0000' in selector) return null
    if (action in setOf("click", "fill", "fill_saved_credential") && selector.isBlank()) return null
    if (action == "fill" && (value.length > 2000 || '\u0000' in value)) return null
    if (action == "fill_saved_credential" && !credentialName.matches(Regex("[A-Za-z0-9._-]{1,100}"))) return null

    return BrowserActionRequest(action, reason, url, selector, value, credentialName)
}

internal fun normalizeBrowserUrl(raw: String): String? {
    val candidate = raw.trim().takeIf { it.isNotEmpty() && it.length <= 2048 } ?: return null
    val hasScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(candidate)
    if (hasScheme && !candidate.startsWith("http://", true) && !candidate.startsWith("https://", true)) return null
    val withScheme = if (candidate.startsWith("http://", true) || candidate.startsWith("https://", true)) {
        candidate
    } else {
        "https://$candidate"
    }
    val uri = runCatching { Uri.parse(withScheme) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || !uri.userInfo.isNullOrBlank()) return null
    return uri.buildUpon().scheme(scheme).build().toString()
}

internal class CodeinBrowserController {
    private var webView: WebView? = null
    private var navigationWaiter: CompletableDeferred<BrowserPageState>? = null
    var onPageState: (BrowserPageState) -> Unit = {}

    @SuppressLint("SetJavaScriptEnabled")
    fun createOrReuse(context: Context): WebView {
        webView?.let { return it }
        return WebView(context).also { view ->
            webView = view
            view.setBackgroundColor(android.graphics.Color.WHITE)
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = WebSettings.getDefaultUserAgent(context)
                allowFileAccess = false
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) forceDark = WebSettings.FORCE_DARK_OFF
            }
            CookieManager.getInstance().setAcceptCookie(true)
            view.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    view?.let { publish(it, loading = true) }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.let { publish(it, loading = false) }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    view?.let { publish(it, loading = it.progress < 100) }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        val message = "Page failed to load (code ${error?.errorCode ?: "unknown"})."
                        navigationWaiter?.completeExceptionally(IllegalStateException(message))
                        navigationWaiter = null
                        view?.let { publish(it, loading = false, error = message) }
                    }
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.cancel()
                    val message = "Secure connection failed; navigation was blocked."
                    navigationWaiter?.completeExceptionally(IllegalStateException(message))
                    navigationWaiter = null
                    view?.let { publish(it, loading = false, error = message) }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                    request?.url?.let { it.scheme !in setOf("http", "https") } ?: true
            }
            view.webChromeClient = WebChromeClient()
        }
    }

    fun currentUrl(): String = webView?.url.orEmpty()

    fun navigateFromUser(raw: String): Boolean {
        val normalized = normalizeBrowserUrl(raw) ?: return false
        webView?.loadUrl(normalized) ?: return false
        return true
    }

    fun goBack() { webView?.takeIf(WebView::canGoBack)?.goBack() }
    fun goForward() { webView?.takeIf(WebView::canGoForward)?.goForward() }
    fun reload() { webView?.reload() }

    suspend fun execute(request: BrowserActionRequest, savedCredential: String? = null): JSONObject {
        val view = withContext(Dispatchers.Main.immediate) {
            webView ?: error("Open Codein Browser before asking the assistant to use a page.")
        }
        withTimeout(5_000) {
            while (!withContext(Dispatchers.Main.immediate) { view.isAttachedToWindow }) delay(50)
        }
        val currentPageUrl = withContext(Dispatchers.Main.immediate) { view.url.orEmpty() }
        if (request.requiresApproval && currentPageUrl != request.expectedUrl) {
            error("The page changed while the browser action was awaiting approval. Ask the assistant to inspect it again.")
        }
        return when (request.action) {
            "navigate" -> navigateAndWait(view, request.url)
            "inspect" -> evaluate(view, inspectScript(request.selector))
            "click" -> evaluate(view, clickScript(request.selector))
            "fill" -> evaluate(view, fillScript(request.selector, request.value, secret = false))
            "fill_saved_credential" -> {
                val secret = savedCredential?.takeIf(String::isNotEmpty)
                    ?: error("The saved credential is unavailable; no value was entered.")
                evaluate(view, fillScript(request.selector, secret, secret = true))
                    .put("credential_name", request.credentialName)
                    .put("value_returned", false)
            }
            else -> error("Unsupported browser action")
        }
    }

    private suspend fun navigateAndWait(view: WebView, rawUrl: String): JSONObject {
        val url = normalizeBrowserUrl(rawUrl) ?: error("Only valid HTTP and HTTPS addresses can be opened.")
        val pending = withContext(Dispatchers.Main.immediate) {
            CompletableDeferred<BrowserPageState>().also { waiter ->
                navigationWaiter?.completeExceptionally(IllegalStateException("Navigation was replaced"))
                navigationWaiter = waiter
                view.loadUrl(url)
            }
        }
        val state = try {
            withTimeout(30_000) { pending.await() }
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                if (navigationWaiter === pending) navigationWaiter = null
            }
        }
        return JSONObject()
            .put("ok", true)
            .put("action", "navigate")
            .put("site", runCatching { Uri.parse(state.url).host }.getOrNull().orEmpty())
            .put("title", state.title)
    }

    private suspend fun evaluate(view: WebView, script: String): JSONObject =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                view.evaluateJavascript(script) { value ->
                    if (!continuation.isActive) return@evaluateJavascript
                    val result = runCatching { JSONObject(value ?: "{}") }.getOrElse {
                        JSONObject().put("ok", false).put("error", "The page returned an invalid result.")
                    }
                    if (result.optBoolean("ok")) continuation.resume(result)
                    else continuation.resumeWith(Result.failure(IllegalStateException(result.optString("error"))))
                }
            }
        }

    private fun publish(view: WebView, loading: Boolean, error: String = "") {
        val state = BrowserPageState(
            url = view.url.orEmpty(),
            title = view.title.orEmpty(),
            loading = loading,
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward(),
            error = error
        )
        onPageState(state)
        if (!loading) {
            navigationWaiter?.complete(state)
            navigationWaiter = null
        }
    }

    fun destroy() {
        navigationWaiter?.cancel()
        navigationWaiter = null
        webView?.apply { stopLoading(); destroy() }
        webView = null
    }

    private fun inspectScript(selector: String): String {
        val lookup = selector.takeIf(String::isNotBlank)
            ?.let { "document.querySelector(${JSONObject.quote(it)})" }
            ?: "null"
        return """(function(){try{const e=$lookup;return {ok:true,site:location.hostname,title:document.title,text:(document.body?.innerText||"").slice(0,12000),element:e?{tag:e.tagName.toLowerCase(),text:(e.innerText||e.textContent||"").trim().slice(0,1000),ariaLabel:e.getAttribute('aria-label')||"",placeholder:e.getAttribute('placeholder')||"",type:e.getAttribute('type')||""}:null};}catch(x){return {ok:false,error:String(x).slice(0,300)}}})()"""
    }

    private fun clickScript(selector: String): String {
        val select = JSONObject.quote(selector)
        return """(function(){try{const e=document.querySelector($select);if(!e)return {ok:false,error:'No element matches selector'};if(e.disabled)return {ok:false,error:'Element is disabled'};if(!e.getClientRects().length)return {ok:false,error:'Target is not visible'};const label=(e.innerText||e.getAttribute('aria-label')||e.textContent||e.tagName).trim().slice(0,300);e.click();return {ok:true,action:'click',target:label,site:location.hostname};}catch(x){return {ok:false,error:String(x).slice(0,300)}}})()"""
    }

    private fun fillScript(selector: String, value: String, secret: Boolean): String {
        val select = JSONObject.quote(selector)
        val quotedValue = JSONObject.quote(value)
        val guard = if (secret) {
            "if(!(e instanceof HTMLInputElement)||e.type.toLowerCase()!=='password')return {ok:false,error:'Saved credentials can only be entered into a password field'};if(!e.getClientRects().length)return {ok:false,error:'Target is not visible'};"
        } else {
            "if((e instanceof HTMLInputElement)&&['password','hidden','file'].includes(e.type.toLowerCase()))return {ok:false,error:'This field requires a saved credential or cannot be filled'};if(!e.getClientRects().length)return {ok:false,error:'Target is not visible'};"
        }
        val catchResult = if (secret) {
            "return {ok:false,error:'Saved credential could not be entered'}"
        } else {
            "return {ok:false,error:String(x).slice(0,300)}"
        }
        return """(function(){try{const e=document.querySelector($select);if(!e)return {ok:false,error:'No element matches selector'};$guard if(e.disabled||e.readOnly)return {ok:false,error:'Field is disabled or read-only'};if(!(e instanceof HTMLInputElement||e instanceof HTMLTextAreaElement||e instanceof HTMLSelectElement))return {ok:false,error:'Target is not an editable form field'};e.focus();const proto=e instanceof HTMLTextAreaElement?HTMLTextAreaElement.prototype:e instanceof HTMLSelectElement?HTMLSelectElement.prototype:HTMLInputElement.prototype;const set=Object.getOwnPropertyDescriptor(proto,'value')?.set;if(set)set.call(e,$quotedValue);else e.value=$quotedValue;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));return {ok:true,action:'fill',tag:e.tagName.toLowerCase(),value_returned:false};}catch(x){$catchResult}})()"""
    }
}

@Composable
internal fun CodeinBrowserToolbar(
    page: BrowserPageState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    onClose: () -> Unit,
    canClose: Boolean = true
) {
    var address by remember { mutableStateOf(page.url) }
    LaunchedEffect(page.url) { address = page.url }

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 4.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, enabled = page.canGoBack, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onForward, enabled = page.canGoForward, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.ArrowForward, "Forward", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onReload, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Refresh, "Reload", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClose, enabled = canClose) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = CodeinBrowserBlue, modifier = Modifier.size(17.dp))
                            Text("Chat", color = CodeinBrowserBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (page.url.isNotBlank()) {
                        TextButton(onClick = { onOpenExternal(page.url) }) {
                            Text("External", color = CodeinBrowserBlue, fontSize = 11.sp)
                        }
                    }
                    IconButton(onClick = onClose, enabled = canClose, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, "Close browser", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("https://example.com", fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onNavigate(address) }),
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                    IconButton(onClick = { onNavigate(address) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Language, "Open address", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(
                text = when {
                    page.loading -> "Loading page…"
                    page.error.isNotBlank() -> page.error
                    page.title.isNotBlank() -> page.title
                    page.url.isNotBlank() -> page.url
                    else -> "Codein Browser · JavaScript enabled for modern sites"
                },
                color = if (page.error.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
internal fun BrowserToolResultPanel(action: String, output: String, succeeded: Boolean) {
    val json = remember(output) { runCatching { JSONObject(output) }.getOrNull() }
    val body = json?.optString("text")?.takeIf(String::isNotBlank)
        ?: json?.optString("error")?.takeIf(String::isNotBlank)
        ?: output
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (succeeded) "BROWSER · $action" else "BROWSER · action failed",
                color = if (succeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            json?.optString("title")?.takeIf(String::isNotBlank)?.let {
                Text(it.take(300), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            Text(
                body.take(5000).ifBlank { "No page text returned." },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 12,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun BrowserActionApprovalCard(
    request: BrowserActionRequest?,
    page: BrowserPageState,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 10.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .65f))
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(
                    if (request?.action == "fill_saved_credential") Icons.Default.Lock else Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text("Assistant requests browser action", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (request == null) {
                Text("This pending request expired. Ask the assistant to request it again.", fontSize = 12.sp)
            } else {
                Text(request.reason.ifBlank { "Review the requested action before allowing it." }, fontSize = 12.sp)
                val shownHost = if (request.action == "navigate") {
                    Uri.parse(normalizeBrowserUrl(request.url).orEmpty()).host
                } else Uri.parse(page.url).host
                Text("Site: ${shownHost.orEmpty().ifBlank { "no page" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (request.selector.isNotBlank()) Text("Target: ${request.selector}", fontSize = 11.sp, maxLines = 2)
                when (request.action) {
                    "navigate" -> Text("Open: ${request.url}", fontSize = 11.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    "click" -> Text("Click the selected page element.", fontSize = 11.sp)
                    "fill" -> Text("Enter: ${request.value.take(240)}", fontSize = 11.sp, maxLines = 3)
                    "fill_saved_credential" -> Text("Enter saved credential ‘${request.credentialName}’ into a password field. Its value will not be shown to the assistant.", fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDeny, modifier = Modifier.weight(1f)) { Text("Deny") }
                    Button(onClick = onApprove, modifier = Modifier.weight(1f)) { Text("Allow once") }
                }
            }
        }
    }
}

@Composable
internal fun BrowserEventPanel(events: List<String>, modifier: Modifier = Modifier) {
    if (events.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 6.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("BROWSER ACTIVITY", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            events.takeLast(3).forEach { event ->
                Text(event, color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}
