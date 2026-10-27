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

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            check(bearerToken.isNotBlank()) { "Termux bridge token is missing. Start the Termux bridge and enter its token in Codein Settings." }
            val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 4_000
                readTimeout = 30_000
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
                if (status !in 200..299) {
                    throw IllegalStateException(result.optString("error").ifBlank { "Termux bridge returned HTTP $status" })
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
