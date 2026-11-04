package com.example.termux

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ensureActive

/**
 * Client for the user's own Termux Agent Room.
 * The endpoint and bearer token are stored locally on this device.
 */
data class TermuxProvider(val id: String, val name: String, val defaultModel: String, val models: List<String>, val configured: Boolean, val credentialName: String, val accountCredential: String = "", val keyOptional: Boolean = false)
class MissingProviderCredential(val credentialName: String, val providerName: String) : IllegalStateException("Missing API key for $providerName")

class TermuxBridgeClient(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("codein_termux_bridge", Context.MODE_PRIVATE)
    private var consecutivePingFailures = 0
    private var pingHasSucceeded = false

    var baseUrl: String
        get() = prefs.getString("base_url", "http://127.0.0.1:8765")!!.trimEnd('/')
        set(value) { prefs.edit().putString("base_url", value.trim().trimEnd('/')).apply() }

    var bearerToken: String
        get() = prefs.getString("bearer_token", "")!!
        set(value) { prefs.edit().putString("bearer_token", value.trim()).apply() }

    val isConfigured: Boolean get() = bearerToken.isNotBlank() && baseUrl.startsWith("http://127.0.0.1")

    /** Initialize MCP and confirm its tool catalog is reachable. */
    suspend fun health(): JSONObject {
        val init = mcpRequest("initialize", JSONObject()
            .put("protocolVersion", "2025-03-26")
            .put("capabilities", JSONObject().put("logging", JSONObject()))
            .put("clientInfo", JSONObject().put("name", "Codein Android").put("version", "1.0.5")))
        mcpNotification("notifications/initialized")
        val tools = mcpRequest("tools/list", JSONObject())
        val toolArray = tools.optJSONArray("tools") ?: JSONArray()
        val toolNames = (0 until toolArray.length()).mapNotNull { toolArray.optJSONObject(it)?.optString("name") }.toSet()
        val requiredTools = setOf("termux_exec", "termux_command_approve", "agent_room_get", "provider_models_list", "provider_model_test", "provider_chat", "composio_platform_configure", "composio_tools_refresh", "run_start", "run_subscribe", "run_status", "run_cancel", "run_approve", "run_list")
        val missing = requiredTools - toolNames
        check(missing.isEmpty()) {
            "پل MCP ترموکس قدیمی است (ابزارهای لازم را ندارد). از تنظیمات، تب اتصال Termux، دستور نصب/به‌روزرسانی را کپی و در Termux اجرا کن."
        }
        val serverVersion = init.optJSONObject("serverInfo")?.optString("version").orEmpty()
        check(versionAtLeast(serverVersion, "1.7.0")) {
            "نسخهٔ پل MCP ترموکس قدیمی است ($serverVersion). از تنظیمات، تب اتصال Termux، دستور نصب/به‌روزرسانی را کپی و در Termux اجرا کن؛ کلیدها و توکن قبلی حفظ می‌شوند."
        }
        return JSONObject()
            .put("ok", true)
            .put("service", init.optJSONObject("serverInfo")?.optString("name", "MCP Termux") ?: "MCP Termux")
            .put("version", serverVersion)
            .put("protocolVersion", init.optString("protocolVersion"))
            .put("tools", toolArray)
    }

    private fun versionAtLeast(actual: String, minimum: String): Boolean {
        fun parts(value: String) = value.split('.').map { it.toIntOrNull() ?: 0 }
        val left = parts(actual)
        val right = parts(minimum)
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return actual.isNotBlank()
    }

    /** Lightweight MCP liveness probe for the periodic connection indicator. */
    suspend fun ping(): JSONObject {
        return try {
            mcpRequest("ping", JSONObject(), timeoutSeconds = 2).also {
                consecutivePingFailures = 0
                pingHasSucceeded = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            consecutivePingFailures++
            if (pingHasSucceeded && consecutivePingFailures < 3) {
                JSONObject().put("ok", true).put("stale", true)
            } else {
                throw error
            }
        }
    }

    suspend fun workspace(): JSONObject = request("GET", "/api/workspace")

    /** Read Agent Room through MCP tools/call, not a private REST endpoint. */
    suspend fun roomContext(): JSONObject = mcpTool("agent_room_get", JSONObject())

    suspend fun providers(): List<TermuxProvider> {
        val result = request("GET", "/api/providers")
        val array = result.optJSONArray("providers") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val modelsJson = item.optJSONArray("models") ?: JSONArray()
            TermuxProvider(
                id = item.optString("id"),
                name = item.optString("name"),
                defaultModel = item.optString("default_model"),
                models = (0 until modelsJson.length()).map { modelsJson.optString(it) },
                configured = item.optBoolean("configured"),
                credentialName = item.optString("credential_name"),
                accountCredential = item.optString("account_credential"),
                keyOptional = item.optBoolean("key_optional")
            )
        }
    }

    /** Parallel transports kept alive by codein-links (bridge, Quick Tunnel, OpenCode). */
    suspend fun links(): List<JSONObject> {
        val result = request("GET", "/api/links")
        val array = result.optJSONArray("links") ?: JSONArray()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    /** Query the provider's live model endpoint using the entered key without saving or logging it. */
    suspend fun discoverModels(provider: String, apiKey: String): List<String> {
        require(provider.isNotBlank()) { "Select an AI provider first" }
        val result = mcpTool("provider_models_list", JSONObject()
            .put("provider", provider)
            .put("api_key", apiKey), 45)
        val array = result.optJSONArray("models") ?: JSONArray()
        return (0 until array.length()).map { array.optString(it) }.filter(String::isNotBlank)
    }

    /** Discover models using the provider API key already stored in Termux. */
    suspend fun discoverSavedModels(provider: String): List<String> {
        require(provider.isNotBlank()) { "Select an AI provider first" }
        val result = mcpTool("provider_models_saved_list", JSONObject().put("provider", provider), 45)
        val array = result.optJSONArray("models") ?: JSONArray()
        return (0 until array.length()).map { array.optString(it) }.filter(String::isNotBlank)
    }

    /** Verify one model with a short text prompt; a supplied key is never saved. */
    suspend fun testProviderModel(provider: String, model: String, testApiKey: String = ""): JSONObject {
        val arguments = JSONObject().put("provider", provider).put("model", model)
        if (testApiKey.isNotBlank()) arguments.put("test_api_key", testApiKey)
        return mcpTool("provider_model_test", arguments, 45)
    }

    /** Resume a provider turn that paused for command approval; returns the final answer. */
    /** Resume a durable run approval; legacy continuation IDs remain supported. */
    suspend fun resumeChat(continuationId: String, decision: String, always: Boolean = false): JSONObject {
        require(continuationId.isNotBlank()) { "Missing approval continuation" }
        require(decision in setOf("approve", "deny")) { "Invalid approval decision" }
        if (continuationId.contains("|")) {
            val parts = continuationId.split("|", limit = 2)
            require(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) { "Invalid durable approval ID" }
            val response = mcpTool("run_approve", JSONObject()
                .put("run_id", parts[0])
                .put("call_id", parts[1])
                .put("decision", decision)
                .put("always", always), 300)
            return response.optJSONObject("result") ?: response
        }
        return mcpTool("provider_chat_resume", JSONObject()
            .put("continuation_id", continuationId)
            .put("decision", decision)
            .put("always", always), 300)
    }

    /** Run provider chat through the local MCP server; its tool loop dispatches native MCP tools. */
    suspend fun chat(provider: String, model: String, messages: JSONArray, system: String): JSONObject {
        val result = mcpTool("provider_chat", JSONObject()
            .put("provider", provider)
            .put("model", model)
            .put("messages", messages)
            .put("system", system)
            .put("timeout", 120), 180)
        if (!result.optBoolean("ok", true)) {
            if (result.optString("error") == "missing_api_key") {
                throw MissingProviderCredential(result.optString("credential_name"), result.optString("provider_name"))
            }
            throw IllegalStateException(result.optString("error").ifBlank { "MCP provider chat failed" } +
                result.optString("detail").takeIf { it.isNotBlank() }?.let { ": " + it.take(600) }.orEmpty())
        }
        return result
    }

    /** Start a durable run, then reconnect to its sequenced event log until completion or approval. */
    @OptIn(InternalCoroutinesApi::class)
    suspend fun chatStream(
        provider: String,
        model: String,
        messages: JSONArray,
        system: String,
        onEvent: suspend (JSONObject) -> Unit
    ): JSONObject = withContext(Dispatchers.IO) {
        check(bearerToken.isNotBlank()) { "توکن پل MCP ترموکس وارد نشده است." }
        val runStart = mcpTool("run_start", JSONObject()
            .put("provider", provider)
            .put("model", model)
            .put("messages", messages)
            .put("system", system)
            .put("timeout", 120)
            .put("total_timeout", 1800)
            .put("max_turns", 40), 45)
        val runId = runStart.optString("run_id")
        check(runId.isNotBlank()) { "پل نتوانست اجرای پایدار را ایجاد کند." }
        val activeRun = AtomicReference(runId)
        val activeConnection = AtomicReference<HttpURLConnection?>(null)
        val requestJob = currentCoroutineContext()[Job]
        val cancellation = requestJob?.invokeOnCompletion(onCancelling = true) { cause ->
            activeConnection.getAndSet(null)?.disconnect()
            if (cause is CancellationException) {
                Thread {
                    runCatching {
                        val connection = (URL(baseUrl + "/mcp").openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 2_000
                            readTimeout = 2_000
                            setRequestProperty("Authorization", "Bearer " + bearerToken)
                            setRequestProperty("MCP-Protocol-Version", "2025-03-26")
                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            doOutput = true
                        }
                        try {
                            val payload = JSONObject()
                                .put("jsonrpc", "2.0")
                                .put("id", UUID.randomUUID().toString())
                                .put("method", "tools/call")
                                .put("params", JSONObject()
                                    .put("name", "run_cancel")
                                    .put("arguments", JSONObject().put("run_id", activeRun.get())))
                            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                            connection.responseCode
                        } finally { connection.disconnect() }
                    }
                }.apply { isDaemon = true; start() }
            }
        }
        val answer = StringBuilder()
        var lastSeq = 0L
        var retryDelay = 1L
        var finalResult: JSONObject? = null

        suspend fun publish(event: JSONObject) {
            val seq = event.optLong("seq", 0L)
            if (seq > 0L && seq <= lastSeq) return
            if (seq > 0L) lastSeq = seq
            when (event.optString("type")) {
                "token" -> answer.append(event.optString("text"))
                "approval_required" -> {
                    val callId = event.optString("call_id")
                    if (callId.isNotBlank()) {
                        finalResult = JSONObject()
                            .put("ok", true)
                            .put("permission_required", true)
                            .put("command", event.optString("command"))
                            .put("continuation_id", runId + "|" + callId)
                            .put("run_id", runId)
                    }
                }
                "done" -> if (event.has("content")) {
                    finalResult = JSONObject()
                        .put("ok", true)
                        .put("provider", provider)
                        .put("model", model)
                        .put("content", event.optString("content").ifBlank { answer.toString() })
                        .put("run_id", runId)
                }
                "error" -> finalResult = JSONObject()
                    .put("ok", false)
                    .put("error", event.optString("error").ifBlank { "اجرای ایجنت ناموفق بود." })
                    .put("run_id", runId)
                "status" -> if (event.optString("status") == "INTERRUPTED") {
                    finalResult = JSONObject().put("ok", false)
                        .put("error", "پل هنگام اجرای کار راه‌اندازی مجدد شد؛ اجرای قبلی برای بازیابی نگهداری شده است.")
                        .put("run_id", runId)
                }
            }
            withContext(Dispatchers.Main) { onEvent(event) }
        }

        try {
            while (finalResult == null) {
                currentCoroutineContext().ensureActive()
                val connection = (URL(baseUrl + "/mcp").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8_000
                    readTimeout = 45_000
                    setRequestProperty("Authorization", "Bearer " + bearerToken)
                    setRequestProperty("MCP-Protocol-Version", "2025-03-26")
                    setRequestProperty("Accept", "text/event-stream")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Last-Event-ID", lastSeq.toString())
                    doOutput = true
                }
                activeConnection.set(connection)
                try {
                    val payload = JSONObject()
                        .put("jsonrpc", "2.0")
                        .put("id", UUID.randomUUID().toString())
                        .put("method", "tools/call")
                        .put("params", JSONObject()
                            .put("name", "run_subscribe")
                            .put("arguments", JSONObject().put("run_id", runId).put("after_seq", lastSeq)))
                    connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    val status = connection.responseCode
                    if (status !in 200..299) {
                        val detail = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                        throw IllegalStateException("MCP run stream HTTP $status: " + detail.take(500))
                    }
                    check(connection.contentType?.contains("text/event-stream", ignoreCase = true) == true) {
                        "پل Termux استریم پایدار Run را پشتیبانی نمی‌کند؛ آن را به‌روزرسانی کن."
                    }
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val dataLines = mutableListOf<String>()
                        var frameSeq = 0L
                        suspend fun dispatchFrame() {
                            if (dataLines.isEmpty()) return
                            val event = runCatching { JSONObject(dataLines.joinToString("\n")) }.getOrNull()
                            dataLines.clear()
                            if (event != null) publish(event)
                        }
                        while (finalResult == null) {
                            val line = reader.readLine() ?: break
                            when {
                                line.startsWith("id:") -> frameSeq = line.removePrefix("id:").trim().toLongOrNull() ?: 0L
                                line.startsWith("data:") -> dataLines += line.removePrefix("data:").trimStart()
                                line.isEmpty() -> {
                                    if (dataLines.isNotEmpty()) {
                                        if (frameSeq > 0L) {
                                            val parsed = runCatching { JSONObject(dataLines.joinToString("\n")) }.getOrNull()
                                            if (parsed != null && !parsed.has("seq")) parsed.put("seq", frameSeq)
                                            if (parsed != null) publish(parsed)
                                            dataLines.clear()
                                        } else dispatchFrame()
                                    }
                                }
                            }
                        }
                        if (dataLines.isNotEmpty() && finalResult == null) dispatchFrame()
                    }
                    retryDelay = 1L
                } finally {
                    activeConnection.compareAndSet(connection, null)
                    connection.disconnect()
                }

                if (finalResult == null) {
                    val status = runCatching { mcpTool("run_status", JSONObject().put("run_id", runId), 10) }.getOrNull()
                    when (status?.optString("status")) {
                        "DONE" -> finalResult = JSONObject().put("ok", true).put("provider", provider)
                            .put("model", model).put("content", status.optString("result", answer.toString())).put("run_id", runId)
                        "FAILED", "CANCELLED" -> finalResult = JSONObject().put("ok", false)
                            .put("error", status.optString("error").ifBlank { status.optString("status") }).put("run_id", runId)
                        "AWAITING_APPROVAL" -> {
                            val pending = status.optJSONObject("pending_approval") ?: JSONObject()
                            val callId = pending.optString("call_id")
                            if (callId.isNotBlank()) finalResult = JSONObject().put("ok", true)
                                .put("permission_required", true).put("command", pending.optString("command"))
                                .put("continuation_id", runId + "|" + callId).put("run_id", runId)
                        }
                        "INTERRUPTED" -> finalResult = JSONObject().put("ok", false)
                            .put("error", "پل هنگام اجرای کار راه‌اندازی مجدد شد؛ اجرای قبلی برای بازیابی نگهداری شده است.")
                            .put("run_id", runId)
                    }
                }
                if (finalResult == null) {
                    Thread.sleep(retryDelay * 1000L)
                    retryDelay = (retryDelay * 2L).coerceAtMost(16L)
                }
            }
            finalResult ?: error("اجرای پایدار بدون نتیجه پایان یافت.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            cancellation?.dispose()
            activeConnection.getAndSet(null)?.disconnect()
        }
    }

    suspend fun credentialNames(): List<String> {
        val result = mcpTool("credentials_list", JSONObject())
        val array = result.optJSONArray("credentials") ?: JSONArray()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }

    suspend fun readCredential(name: String): String {
        requireValidName(name)
        return request("GET", "/api/keys/${encodePath(name)}").optString("value")
    }

    suspend fun saveCredential(name: String, value: String): JSONObject {
        requireValidName(name)
        require(value.isNotBlank()) { "Credential value cannot be empty" }
        return mcpTool("credential_save", JSONObject().put("name", name).put("value", value))
    }

    /** Approve one exact command for a single execution after the user confirms it in the app. */
    suspend fun approveCommand(command: String, always: Boolean = false): JSONObject {
        require(command.isNotBlank()) { "Command cannot be empty" }
        return mcpTool("termux_command_approve", JSONObject()
            .put("command", command)
            .put("always", always))
    }

    /** Execute a local shell command using the standard MCP tools/call method. */
    suspend fun execute(command: String, timeoutSeconds: Int = 120): JSONObject {
        require(command.isNotBlank()) { "Command cannot be empty" }
        return mcpTool("termux_exec", JSONObject()
            .put("command", command)
            .put("timeout", timeoutSeconds.coerceIn(1, 900)), timeoutSeconds.coerceIn(1, 900))
    }

    /** Manage the optional OpenCode service from an explicit Agent Room control. */
    suspend fun manageOpenCode(action: String): JSONObject {
        require(action in setOf("install", "start", "status", "restart", "stop")) { "Invalid OpenCode action" }
        val result = mcpTool("opencode_manage", JSONObject().put("action", action), 300)
        check(result.optBoolean("ok")) {
            result.optString("error").ifBlank { result.optString("status").ifBlank { "OpenCode action failed" } }
        }
        return result
    }

    suspend fun saveSession(id: String, title: String, summary: String, nextAction: String = ""): JSONObject =
        mcpTool("session_save", JSONObject()
            .put("id", id)
            .put("title", title)
            .put("summary", summary)
            .put("next_action", nextAction))

    suspend fun recordEvent(kind: String, summary: String): JSONObject =
        mcpTool("event_record", JSONObject().put("kind", kind).put("summary", summary))

    suspend fun updateState(fields: JSONObject): JSONObject =
        mcpTool("state_update", JSONObject().put("fields", fields))

    private var mcpRequestId = 0

    private suspend fun mcpRequest(method: String, params: JSONObject, timeoutSeconds: Int = 45): JSONObject =
        withContext(Dispatchers.IO) {
            check(bearerToken.isNotBlank()) { "MCP token is missing. Run the Termux installer and copy its local token." }
            val id = synchronized(this@TermuxBridgeClient) { ++mcpRequestId }
            val payload = JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params)
            val connection = (URL(baseUrl + "/mcp").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutSeconds.coerceIn(1, 8) * 1000
                readTimeout = timeoutSeconds.coerceIn(1, 900) * 1000
                setRequestProperty("Authorization", "Bearer " + bearerToken)
                setRequestProperty("MCP-Protocol-Version", "2025-03-26")
                setRequestProperty("Accept", "application/json, text/event-stream")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
            }
            try {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw IllegalStateException("MCP HTTP " + status + ": " + text.take(700))
                val response = JSONObject(text)
                if (response.has("error")) {
                    val error = response.optJSONObject("error") ?: JSONObject()
                    throw IllegalStateException("MCP " + error.optInt("code") + ": " + error.optString("message"))
                }
                response.optJSONObject("result") ?: JSONObject()
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun mcpNotification(method: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("jsonrpc", "2.0").put("method", method)
        val connection = (URL(baseUrl + "/mcp").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer " + bearerToken)
            setRequestProperty("MCP-Protocol-Version", "2025-03-26")
            setRequestProperty("Accept", "application/json, text/event-stream")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) throw IllegalStateException("MCP notification rejected")
            connection.inputStream?.close()
        } finally {
            connection.disconnect()
        }
    }

    /** Configure the public Codein Worker; its Composio project key stays server-side. */
    suspend fun configureComposioPlatform(workerUrl: String): JSONObject {
        require(workerUrl.trim().startsWith("https://") && workerUrl.trim().contains(".workers.dev")) {
            "Codein Composio Worker is not configured in this app build."
        }
        return mcpTool("composio_platform_configure", JSONObject().put("worker_url", workerUrl.trim()), 45)
    }

    /** Refresh the live Composio tool list from the connected Termux MCP server. */
    suspend fun refreshComposioTools(): JSONObject =
        mcpTool("composio_tools_refresh", JSONObject(), 90)

    private suspend fun mcpTool(name: String, arguments: JSONObject, timeoutSeconds: Int = 45): JSONObject {
        val result = mcpRequest("tools/call", JSONObject().put("name", name).put("arguments", arguments), timeoutSeconds)
        if (result.optBoolean("isError")) {
            val content = result.optJSONArray("content")?.optJSONObject(0)?.optString("text").orEmpty()
            throw IllegalStateException(content.ifBlank { "MCP tool " + name + " failed" })
        }
        val content = result.optJSONArray("content")?.optJSONObject(0)?.optString("text").orEmpty()
        return if (content.isBlank()) JSONObject() else JSONObject(content)
    }

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            check(bearerToken.isNotBlank()) { "Termux bridge token is missing. Start the Termux bridge and enter its token in Codein Settings." }
            val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8_000
                readTimeout = 45_000
                setRequestProperty("Authorization", "Bearer ${bearerToken}")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            try {
                if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val result = if (text.isBlank()) JSONObject() else JSONObject(text)
                if (status == 428 && result.optString("error") == "missing_api_key") {
                    throw MissingProviderCredential(result.optString("credential_name"), result.optString("provider_name"))
                }
                if (status !in 200..299) {
                    val detail = result.optString("detail")
                    throw IllegalStateException(result.optString("error").ifBlank { "Termux bridge returned HTTP $status" } + if (detail.isNotBlank()) ": " + detail.take(500) else "")
                }
                result
            } finally {
                connection.disconnect()
            }
        }

    private fun requireValidName(name: String) {
        require(name.isNotBlank() && name.none { it == '/' || it == '\\' }) { "Invalid credential name" }
    }

    private fun encodePath(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
