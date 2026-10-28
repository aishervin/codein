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

    suspend fun health(): JSONObject = request("GET", "/health")

    suspend fun workspace(): JSONObject = request("GET", "/api/workspace")

    /** Loads the shared room contract, state, recent events, sessions, and skills. */
    suspend fun roomContext(): JSONObject = request("GET", "/api/room")

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
        val result = request("POST", "/api/providers/models", JSONObject()
            .put("provider", provider)
            .put("api_key", apiKey)
            .put("timeout", 25))
        val array = result.optJSONArray("models") ?: JSONArray()
        return (0 until array.length()).map { array.optString(it) }.filter(String::isNotBlank)
    }

    suspend fun chat(provider: String, model: String, messages: JSONArray, system: String): JSONObject =
        request("POST", "/api/chat", JSONObject()
            .put("provider", provider)
            .put("model", model)
            .put("messages", messages)
            .put("system", system)
            .put("timeout", 120))

    suspend fun credentialNames(): List<String> {
        val result = request("GET", "/api/keys")
        val array = result.optJSONArray("keys") ?: JSONArray()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }

    suspend fun readCredential(name: String): String {
        requireValidName(name)
        return request("GET", "/api/keys/${encodePath(name)}").optString("value")
    }

    suspend fun saveCredential(name: String, value: String): JSONObject {
        requireValidName(name)
        require(value.isNotBlank()) { "Credential value cannot be empty" }
        return request("POST", "/api/keys", JSONObject().put("name", name).put("value", value))
    }

    suspend fun execute(command: String, timeoutSeconds: Int = 120): JSONObject {
        require(command.isNotBlank()) { "Command cannot be empty" }
        return request("POST", "/api/exec", JSONObject()
            .put("command", command)
            .put("timeout", timeoutSeconds.coerceIn(1, 900)))
    }

    suspend fun saveSession(id: String, title: String, summary: String, nextAction: String = ""): JSONObject {
        return request("POST", "/api/sessions", JSONObject()
            .put("id", id)
            .put("title", title)
            .put("summary", summary)
            .put("next_action", nextAction))
    }

    suspend fun recordEvent(kind: String, summary: String): JSONObject =
        request("POST", "/api/events", JSONObject().put("kind", kind).put("data", JSONObject().put("summary", summary)))

    suspend fun updateState(fields: JSONObject): JSONObject = request("POST", "/api/state", fields)

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
