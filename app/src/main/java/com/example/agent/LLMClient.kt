package com.example.agent

import com.example.security.SecretRedactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LLMMessage(
    val role: String, // "system", "user", "assistant", "tool"
    val content: String,
    val toolCallId: String? = null,
    val toolCallsJson: String? = null
)

data class LLMResponse(
    val content: String,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val isFinish: Boolean = true,
    val errorMessage: String? = null
)

data class ToolCallRequest(
    val id: String,
    val name: String,
    val argsJson: JSONObject
)

class LLMClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateResponse(
        provider: String, // "gemini", "openai", "anthropic", "openrouter"
        apiKey: String,
        baseUrl: String,
        modelName: String,
        systemPrompt: String,
        messages: List<LLMMessage>,
        toolsJsonSchema: JSONArray
    ): LLMResponse = withContext(Dispatchers.IO) {

        if (apiKey.isBlank() && provider != "custom_local") {
            return@withContext LLMResponse(
                content = "",
                errorMessage = "API Key not configured. Please set API Key in Settings."
            )
        }

        val effectiveModel = if (modelName.isBlank()) {
            when (provider) {
                "gemini" -> "gemini-2.5-flash"
                "anthropic" -> "claude-3-5-sonnet-20241022"
                else -> "gpt-4o"
            }
        } else modelName

        return@withContext when (provider.lowercase()) {
            "gemini" -> callGeminiApi(apiKey, baseUrl, effectiveModel, systemPrompt, messages, toolsJsonSchema)
            "anthropic" -> callAnthropicApi(apiKey, baseUrl, effectiveModel, systemPrompt, messages, toolsJsonSchema)
            else -> callOpenAIApi(apiKey, baseUrl, effectiveModel, systemPrompt, messages, toolsJsonSchema)
        }
    }

    private fun callOpenAIApi(
        apiKey: String,
        baseUrl: String,
        model: String,
        systemPrompt: String,
        messages: List<LLMMessage>,
        toolsJsonSchema: JSONArray
    ): LLMResponse {
        try {
            val endpoint = if (baseUrl.isNotBlank()) {
                if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"
            } else "https://api.openai.com/v1/chat/completions"

            val bodyJson = JSONObject()
            bodyJson.put("model", model)

            val messagesArr = JSONArray()
            messagesArr.put(JSONObject().put("role", "system").put("content", SecretRedactor.redact(systemPrompt)))

            for (m in messages) {
                val obj = JSONObject()
                obj.put("role", m.role)
                obj.put("content", SecretRedactor.redact(m.content))
                if (m.role == "tool" && !m.toolCallId.isNullOrEmpty()) {
                    obj.put("tool_call_id", m.toolCallId)
                }
                messagesArr.put(obj)
            }
            bodyJson.put("messages", messagesArr)

            // Format tools for OpenAI
            if (toolsJsonSchema.length() > 0) {
                val openAiTools = JSONArray()
                for (i in 0 until toolsJsonSchema.length()) {
                    val skillObj = toolsJsonSchema.getJSONObject(i)
                    val toolItem = JSONObject().apply {
                        put("type", "function")
                        put("function", JSONObject().apply {
                            put("name", skillObj.optString("name"))
                            put("description", skillObj.optString("description"))
                            put("parameters", skillObj.optJSONObject("parameters") ?: JSONObject())
                        })
                    }
                    openAiTools.put(toolItem)
                }
                bodyJson.put("tools", openAiTools)
            }

            val requestBody = bodyJson.toString().toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody)

            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val err = SecretRedactor.redact("API Call Failed (${response.code}): $responseStr")
                    return LLMResponse(content = "", errorMessage = err)
                }

                val jsonResp = JSONObject(responseStr)
                val choices = jsonResp.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return LLMResponse(content = "No response choices returned.")
                }

                val firstChoice = choices.getJSONObject(0)
                val msgObj = firstChoice.optJSONObject("message") ?: JSONObject()
                val textContent = msgObj.optString("content", "")

                val toolCallsList = mutableListOf<ToolCallRequest>()
                val toolCallsArr = msgObj.optJSONArray("tool_calls")
                if (toolCallsArr != null) {
                    for (i in 0 until toolCallsArr.length()) {
                        val tc = toolCallsArr.getJSONObject(i)
                        val tcId = tc.optString("id", "call_$i")
                        val funcObj = tc.optJSONObject("function") ?: JSONObject()
                        val funcName = funcObj.optString("name")
                        val argsStr = funcObj.optString("arguments", "{}")
                        val argsJson = try { JSONObject(argsStr) } catch (_: Exception) { JSONObject() }

                        toolCallsList.add(
                            ToolCallRequest(
                                id = tcId,
                                name = funcName,
                                argsJson = argsJson
                            )
                        )
                    }
                }

                return LLMResponse(
                    content = SecretRedactor.redact(textContent),
                    toolCalls = toolCallsList
                )
            }
        } catch (e: Exception) {
            return LLMResponse(content = "", errorMessage = SecretRedactor.redact(e.localizedMessage ?: "Network Error"))
        }
    }

    private fun callGeminiApi(
        apiKey: String,
        baseUrl: String,
        model: String,
        systemPrompt: String,
        messages: List<LLMMessage>,
        toolsJsonSchema: JSONArray
    ): LLMResponse {
        try {
            val url = if (baseUrl.isNotBlank()) {
                "$baseUrl/v1beta/models/$model:generateContent?key=$apiKey"
            } else {
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            }

            val rootJson = JSONObject()

            // System Instruction
            rootJson.put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SecretRedactor.redact(systemPrompt)))))

            // Contents
            val contentsArr = JSONArray()
            for (m in messages) {
                if (m.role == "system") continue
                val roleStr = if (m.role == "user" || m.role == "tool") "user" else "model"
                val partObj = JSONObject().put("text", SecretRedactor.redact(m.content))
                contentsArr.put(JSONObject().put("role", roleStr).put("parts", JSONArray().put(partObj)))
            }
            rootJson.put("contents", contentsArr)

            val requestBody = rootJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return LLMResponse(content = "", errorMessage = SecretRedactor.redact("Gemini Error ${response.code}: $bodyStr"))
                }

                val jsonResp = JSONObject(bodyStr)
                val candidates = jsonResp.optJSONArray("candidates") ?: JSONArray()
                if (candidates.length() == 0) return LLMResponse(content = "Empty response")

                val cand = candidates.getJSONObject(0)
                val contentObj = cand.optJSONObject("content") ?: JSONObject()
                val parts = contentObj.optJSONArray("parts") ?: JSONArray()

                var textRes = ""
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    textRes += p.optString("text", "")
                }

                return LLMResponse(content = SecretRedactor.redact(textRes))
            }
        } catch (e: Exception) {
            return LLMResponse(content = "", errorMessage = SecretRedactor.redact(e.localizedMessage ?: "Gemini Exception"))
        }
    }

    private fun callAnthropicApi(
        apiKey: String,
        baseUrl: String,
        model: String,
        systemPrompt: String,
        messages: List<LLMMessage>,
        toolsJsonSchema: JSONArray
    ): LLMResponse {
        // Simple fallback to OpenAI compatible for custom Anthropic proxies or default format
        return callOpenAIApi(apiKey, baseUrl, model, systemPrompt, messages, toolsJsonSchema)
    }
}
