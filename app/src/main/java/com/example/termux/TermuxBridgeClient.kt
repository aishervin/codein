package com.example.termux

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the user's own Termux Agent Room.
 * The endpoint and bearer token are stored locally on this device.
 */
data class TermuxProvider(val id: String, val name: String, val defaultModel: String, val models: List<String>, val configured: Boolean, val credentialName: String)
class MissingProviderCredential(val credentialName: String, val providerName: String) : IllegalStateException("Missing API key for $providerName")

class TermuxBridgeClient(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("codein_termux_bridge", Context.MODE_PRIVATE)

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
            .put("capabilities", JSONObject())
            .put("clientInfo", JSONObject().put("name", "Codein Android").put("version", "1.0.5")))
        mcpNotification("notifications/initialized")
        val tools = mcpRequest("tools/list", JSONObject())
        val toolArray = tools.optJSONArray("tools") ?: JSONArray()
        val toolNames = (0 until toolArray.length()).mapNotNull { toolArray.optJSONObject(it)?.optString("name") }.toSet()
        val requiredTools = setOf("termux_exec", "agent_room_get", "provider_models_list", "provider_chat")
        val missing = requiredTools - toolNames
        check(missing.isEmpty()) {
            "The Termux MCP server is outdated (missing " + missing.joinToString() + "). Re-run the Codein Termux installer."
        }
        return JSONObject()
            .put("ok", true)
            .put("service", init.optJSONObject("serverInfo")?.optString("name", "MCP Termux") ?: "MCP Termux")
            .put("protocolVersion", init.optString("protocolVersion"))
            .put("tools", toolArray)
    }

    /** Lightweight MCP liveness probe for the periodic connection indicator. */
    suspend fun ping(): JSONObject = mcpRequest("ping", JSONObject())

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
                credentialName = item.optString("credential_name")
            )
        }
    }

    /** Query the provider's live model endpoint using the entered key without saving or logging it. */
    suspend fun discoverModels(provider: String, apiKey: String): List<String> {
        require(provider.isNotBlank()) { "Select an AI provider first" }
        require(apiKey.isNotBlank()) { "Enter the provider API key first" }
        val result = mcpTool("provider_models_list", JSONObject()
            .put("provider", provider)
            .put("api_key", apiKey), 45)
        val array = result.optJSONArray("models") ?: JSONArray()
        return (0 until array.length()).map { array.optString(it) }.filter(String::isNotBlank)
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

    /** Execute a local shell command using the standard MCP tools/call method. */
    suspend fun execute(command: String, timeoutSeconds: Int = 120): JSONObject {
        require(command.isNotBlank()) { "Command cannot be empty" }
        return mcpTool("termux_exec", JSONObject()
            .put("command", command)
            .put("timeout", timeoutSeconds.coerceIn(1, 900)), timeoutSeconds.coerceIn(1, 900))
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
                connectTimeout = 8_000
                readTimeout = (timeoutSeconds.coerceAtMost(900) * 1000).coerceAtLeast(45_000)
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
