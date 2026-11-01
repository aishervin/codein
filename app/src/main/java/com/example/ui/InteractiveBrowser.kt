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

internal data class BrowserActionParseResult(
    val request: BrowserActionRequest? = null,
    val error: String? = null
)

private fun requiresNavigationApproval(rawHost: String): Boolean {
    val host = rawHost.lowercase(Locale.ROOT).trim('[', ']')
    if (host.isBlank() || host == "localhost" || host.endsWith(".localhost") ||
        host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".lan") || host.endsWith(".test")) return true
    if (':' in host || !host.contains('.') || host.all { it.isDigit() || it == '.' }) return true
    return false
}

internal fun hasBrowserActionMarker(text: String): Boolean =
    text.contains(BROWSER_ACTION_START) || text.contains(BROWSER_ACTION_END)

internal fun parseBrowserActionRequest(text: String): BrowserActionParseResult {
    val markerStart = text.indexOf(BROWSER_ACTION_START)
    if (markerStart < 0) return BrowserActionParseResult(error = "The browser request start marker is missing.")
    if (text.substring(0, markerStart).isNotBlank()) {
        return BrowserActionParseResult(error = "Unexpected text outside the browser request markers.")
    }

    var jsonStart = markerStart + BROWSER_ACTION_START.length
    while (jsonStart < text.length && text[jsonStart].isWhitespace()) jsonStart++
    if (jsonStart >= text.length || text[jsonStart] != '{') {
        return BrowserActionParseResult(error = "The browser request payload must be a JSON object.")
    }
    var depth = 0
    var inString = false
    var escaped = false
    var jsonEnd = -1
    for (index in jsonStart until text.length) {
        val char = text[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
        } else {
            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        jsonEnd = index
                        break
                    }
                }
            }
        }
    }
    if (jsonEnd < 0) return BrowserActionParseResult(error = "The browser request payload is not valid JSON.")
    val payload = runCatching { JSONObject(text.substring(jsonStart, jsonEnd + 1)) }.getOrNull()
        ?: return BrowserActionParseResult(error = "The browser request payload is not valid JSON.")
    var markerEnd = jsonEnd + 1
    while (markerEnd < text.length && text[markerEnd].isWhitespace()) markerEnd++
    if (!text.startsWith(BROWSER_ACTION_END, markerEnd)) {
        return BrowserActionParseResult(error = "The browser request end marker is missing or misplaced.")
    }
    val trailingText = text.substring(markerEnd + BROWSER_ACTION_END.length)
    if (trailingText.isNotBlank()) {
        return BrowserActionParseResult(
            error = if (trailingText.contains(BROWSER_ACTION_START) || trailingText.contains(BROWSER_ACTION_END)) {
                "Expected exactly one browser request marker pair."
            } else {
                "Unexpected text outside the browser request markers."
            }
        )
    }
    if (payload.opt("protocol") !is String || payload.optString("protocol") != "codein.browser.v1") {
        return BrowserActionParseResult(error = "The browser request protocol must be codein.browser.v1.")
    }

    fun stringField(name: String): String? {
        if (!payload.has(name) || payload.isNull(name)) return ""
        return payload.opt(name) as? String
    }

    val actionValue = stringField("action")
        ?: return BrowserActionParseResult(error = "The browser action must be a string.")
    val reasonValue = stringField("reason")
        ?: return BrowserActionParseResult(error = "The browser reason must be a string.")
    val urlValue = stringField("url")
        ?: return BrowserActionParseResult(error = "The browser URL must be a string.")
    val selectorValue = stringField("selector")
        ?: return BrowserActionParseResult(error = "The browser selector must be a string.")
    val valueValue = stringField("value")
        ?: return BrowserActionParseResult(error = "The fill value must be a string.")
    val credentialValue = stringField("credential_name")
        ?: return BrowserActionParseResult(error = "The credential name must be a string.")

    val action = actionValue.trim().lowercase(Locale.ROOT)
    if (action !in setOf("navigate", "inspect", "click", "fill", "fill_saved_credential")) {
        return BrowserActionParseResult(error = "Unsupported browser action: $action.")
    }
    val reason = reasonValue.trim().take(500)
    val url = urlValue.trim()
    val selector = selectorValue.trim()
    val value = valueValue
    val credentialName = credentialValue.trim()

    if (reason.isBlank()) return BrowserActionParseResult(error = "A non-empty reason is required.")
    if (action == "navigate" && (url.isBlank() || url.length > 2048 || normalizeBrowserUrl(url) == null)) {
        return BrowserActionParseResult(error = "The navigation URL must be a valid HTTP or HTTPS address.")
    }
    if (selector.length > 500 || '\u0000' in selector) {
        return BrowserActionParseResult(error = "The browser selector is invalid or too long.")
    }
    if (action in setOf("click", "fill_saved_credential") && selector.isBlank()) {
        return BrowserActionParseResult(error = "A CSS selector is required for $action.")
    }
    if (action == "fill" && (value.length > 2000 || '\u0000' in value)) {
        return BrowserActionParseResult(error = "The fill value is invalid or too long.")
    }
    if (action == "fill_saved_credential" && !credentialName.matches(Regex("[A-Za-z0-9._-]{1,100}"))) {
        return BrowserActionParseResult(error = "A valid credential_name is required for saved-credential entry.")
    }

    return BrowserActionParseResult(
        request = BrowserActionRequest(action, reason, url, selector, value, credentialName)
    )
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
            view.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = WebSettings.getDefaultUserAgent(context)
                    .replace("; wv", "")
                    .replace("Version/4.0 ", "")
                allowFileAccess = false
                allowContentAccess = true
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
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
                        val description = error?.description?.toString()?.takeIf(String::isNotBlank)
                        val message = buildString {
                            append("Page failed to load (code ${error?.errorCode ?: "unknown"})")
                            if (description != null) append(": ").append(description)
                            append('.')
                        }
                        navigationWaiter?.completeExceptionally(IllegalStateException(message))
                        navigationWaiter = null
                        view?.let { publish(it, loading = false, error = message) }
                    }
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.cancel()
                    val code = error?.primaryError?.let { " (SSL error $it)" }.orEmpty()
                    val message = "Secure connection failed$code; navigation was blocked."
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
            "fill" -> evaluate(view, browserFillScript(request.selector, request.value, secret = false))
            "fill_saved_credential" -> {
                val secret = savedCredential?.takeIf(String::isNotEmpty)
                    ?: error("The saved credential is unavailable; no value was entered.")
                evaluate(view, browserFillScript(request.selector, secret, secret = true))
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

    private suspend fun evaluate(view: WebView, script: String): JSONObject = withTimeout(15_000) {
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                view.evaluateJavascript(script) { value ->
                    if (!continuation.isActive) return@evaluateJavascript
                    val result = runCatching { JSONObject(value ?: "{}") }.getOrElse {
                        JSONObject().put("ok", false).put("error", "The page returned an invalid result.")
                    }
                    if (result.optBoolean("ok")) continuation.resume(result)
                    else {
                        val message = result.optString("error").takeIf(String::isNotBlank)
                            ?: "The page did not provide an error message."
                        continuation.resumeWith(Result.failure(IllegalStateException(message)))
                    }
                }
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
            ?.let(::browserElementExpression)
            ?: "null"
        val missingElement = if (selector.isBlank()) "" else "if(!e)return {ok:false,error:'Element not found'};"
        return """(function(){try{
            let idCounter=0;
            function elementInfo(el){
                let id=el.getAttribute('data-codein-id');
                if(!id){id='el_'+(++idCounter);el.setAttribute('data-codein-id',id);}
                return {id:id,tag:el.tagName.toLowerCase(),text:(el.innerText||el.textContent||'').trim().slice(0,200),role:el.getAttribute('role')||'',ariaLabel:el.getAttribute('aria-label')||'',placeholder:el.getAttribute('placeholder')||'',type:el.getAttribute('type')||''};
            }
            function isVisible(el){const style=window.getComputedStyle(el);return style.display!=='none'&&style.visibility!=='hidden'&&style.opacity!=='0'&&el.getClientRects().length>0;}
            const interactive=[];
            document.querySelectorAll('a,button,input,select,textarea,[role="button"],[role="link"],[onclick]').forEach(el=>{if(isVisible(el)&&interactive.length<100)interactive.push(elementInfo(el));});
            const e=$lookup;
            $missingElement
            const target=e?elementInfo(e):null;
            const pageText=(document.body?.innerText||'').slice(0,12000);
            return {ok:true,site:location.hostname,title:document.title,url:location.href,text:pageText,page_text:pageText,interactive_elements:interactive,element:target,target_element:target};
        }catch(x){return {ok:false,error:String(x).slice(0,300)}}})()"""
    }

    private fun clickScript(selector: String): String {
        val select = browserElementExpression(selector)
        return """(function(){try{const e=$select;if(!e)return {ok:false,error:'Element not found'};if(e.disabled)return {ok:false,error:'Element is disabled'};if(!e.getClientRects().length)return {ok:false,error:'Target is not visible'};const label=(e.innerText||e.getAttribute('aria-label')||e.textContent||e.tagName).trim().slice(0,300);const originalOutline=e.style.outline;e.style.outline='3px solid #42A5F5';e.style.outlineOffset='2px';setTimeout(()=>{e.style.outline=originalOutline;},1000);e.scrollIntoView({behavior:'smooth',block:'center'});e.click();return {ok:true,action:'click',target:label,site:location.hostname};}catch(x){return {ok:false,error:String(x).slice(0,300)}}})()"""
    }

}

internal fun browserFillScript(selector: String, value: String, secret: Boolean): String {
    val select = selector.takeIf(String::isNotBlank)
        ?.let(::browserElementExpression)
        ?: "document.activeElement"
    val missingElement = if (selector.isBlank()) "No focused element found" else "Element not found"
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
    val setValue = if (secret) {
        "const set=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value')?.set;if(set)set.call(e,$quotedValue);else e.value=$quotedValue;"
    } else {
        "if(e.isContentEditable){e.textContent=$quotedValue;}else{const proto=e instanceof HTMLTextAreaElement?HTMLTextAreaElement.prototype:e instanceof HTMLSelectElement?HTMLSelectElement.prototype:HTMLInputElement.prototype;const set=Object.getOwnPropertyDescriptor(proto,'value')?.set;if(set)set.call(e,$quotedValue);else e.value=$quotedValue;}"
    }
    val editableCheck = if (secret) {
        ""
    } else {
        "if(!(e instanceof HTMLInputElement||e instanceof HTMLTextAreaElement||e instanceof HTMLSelectElement||e.isContentEditable))return {ok:false,error:'Target is not an editable form field or contenteditable element'};"
    }
    return """(function(){try{const e=$select;if(!e||e===document.body||e===document.documentElement)return {ok:false,error:${JSONObject.quote(missingElement)}};$guard if(e.disabled||e.readOnly)return {ok:false,error:'Field is disabled or read-only'};$editableCheck e.scrollIntoView({behavior:'smooth',block:'center'});e.focus();$setValue e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));return {ok:true,action:'fill',tag:e.tagName.toLowerCase(),value_returned:false};}catch(x){$catchResult}})()"""
}

internal fun browserElementExpression(selector: String): String =
    if (selector.matches(Regex("el_[0-9]+"))) {
        "document.querySelector('[data-codein-owned-id=\\\"true\\\"][data-codein-id=\\\"$selector\\\"]')"
    } else {
        "document.querySelector(${JSONObject.quote(selector)})"
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
                if (request.selector.isNotBlank()) {
                    Text("Target: ${request.selector}", fontSize = 11.sp, maxLines = 2)
                } else if (request.action == "fill") {
                    Text("Target: the currently focused field", fontSize = 11.sp)
                }
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
