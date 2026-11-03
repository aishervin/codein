package com.example.agent

import com.example.data.ActionLogEntity
import com.example.data.ChatMessageEntity
import com.example.data.MemoryNoteEntity
import com.example.integrations.CloudflareApiClient
import com.example.integrations.GitHubApiClient
import com.example.security.KeystoreManager
import com.example.security.SecretRedactor
import com.example.termux.TermuxManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed class AgentExecutionState {
    object Idle : AgentExecutionState()
    data class Planning(val userMessage: String) : AgentExecutionState()
    data class ExecutingTool(val toolName: String, val details: String) : AgentExecutionState()
    data class WaitingUserApproval(val toolName: String, val prompt: String, val onDecision: (Boolean) -> Unit) : AgentExecutionState()
    data class Error(val message: String) : AgentExecutionState()
    data class Completed(val summary: String) : AgentExecutionState()
}

class AgentEngine(
    private val termuxManager: TermuxManager,
    private val keystoreManager: KeystoreManager,
    private val appDatabase: com.example.data.AppDatabase,
    private val gitHubClient: GitHubApiClient = GitHubApiClient(),
    private val cloudflareClient: CloudflareApiClient = CloudflareApiClient()
) {
    private val skillRegistry = SkillRegistry()
    private val llmClient = LLMClient()
    private val autonomyManager = AutonomyManager()

    private val _executionState = MutableStateFlow<AgentExecutionState>(AgentExecutionState.Idle)
    val executionState: StateFlow<AgentExecutionState> = _executionState.asStateFlow()

    private var isCancelled = false

    fun stopAgent() {
        isCancelled = true
        _executionState.value = AgentExecutionState.Idle
    }

    suspend fun runAgentLoop(
        userPrompt: String,
        onMessageAdded: suspend (ChatMessageEntity) -> Unit
    ) = withContext(Dispatchers.IO) {
        isCancelled = false
        _executionState.value = AgentExecutionState.Planning(userPrompt)

        val provider = keystoreManager.getUnencrypted(KeystoreManager.KEY_LLM_PROVIDER, "gemini")
        val apiKey = keystoreManager.decryptAndGet(KeystoreManager.KEY_LLM_API_KEY)
        val baseUrl = keystoreManager.getUnencrypted(KeystoreManager.KEY_LLM_BASE_URL)
        val model = keystoreManager.getUnencrypted(KeystoreManager.KEY_LLM_MODEL)
        val systemPrompt = keystoreManager.getUnencrypted(KeystoreManager.KEY_SYSTEM_PROMPT, SystemPrompt.DEFAULT_PROMPT)

        // Register secrets for redacting
        SecretRedactor.registerSecret(apiKey)
        SecretRedactor.registerSecret(keystoreManager.decryptAndGet(KeystoreManager.KEY_GITHUB_TOKEN))
        SecretRedactor.registerSecret(keystoreManager.decryptAndGet(KeystoreManager.KEY_CLOUDFLARE_TOKEN))

        val conversationHistory = mutableListOf<LLMMessage>()

        // Include memory context
        val memoryNotes = appDatabase.memoryNoteDao().getAllMemoryNotesList()
        val memoryContext = if (memoryNotes.isNotEmpty()) {
            "\n══ LONG-TERM ENVIRONMENT MEMORY ══\n" + memoryNotes.joinToString("\n") { "- ${it.category}: ${it.keyName} = ${it.valueContent}" }
        } else ""

        conversationHistory.add(LLMMessage(role = "user", content = userPrompt + memoryContext))

        var iteration = 0
        val maxIterations = 10
        val commandHistoryTracker = mutableMapOf<String, Int>()

        while (iteration < maxIterations && !isCancelled) {
            iteration++

            val response = llmClient.generateResponse(
                provider = provider,
                apiKey = apiKey,
                baseUrl = baseUrl,
                modelName = model,
                systemPrompt = systemPrompt,
                messages = conversationHistory,
                toolsJsonSchema = skillRegistry.getToolsJsonSchema()
            )

            if (response.errorMessage != null) {
                _executionState.value = AgentExecutionState.Error(response.errorMessage)
                onMessageAdded(ChatMessageEntity(sender = "agent", content = "خطا در ارتباط با هوش مصنوعی: ${response.errorMessage}", isError = true))
                return@withContext
            }

            if (response.content.isNotEmpty()) {
                onMessageAdded(ChatMessageEntity(sender = "agent", content = response.content))
                conversationHistory.add(LLMMessage(role = "assistant", content = response.content))
            }

            // No tool calls -> Agent finished planning/responding
            if (response.toolCalls.isEmpty()) {
                _executionState.value = AgentExecutionState.Completed(response.content)
                return@withContext
            }

            // Execute tool calls
            for (tc in response.toolCalls) {
                if (isCancelled) break

                val skill = skillRegistry.getSkill(tc.name)
                if (skill == null) {
                    val errMsg = "ابزار ${tc.name} پیدا نشد."
                    conversationHistory.add(LLMMessage(role = "tool", content = errMsg, toolCallId = tc.id))
                    continue
                }

                _executionState.value = AgentExecutionState.ExecutingTool(skill.name, tc.argsJson.toString())

                // Stuck loop check: if exact command failed 3 times, alert
                val cmdSig = tc.argsJson.optString("command", tc.name)
                val count = commandHistoryTracker.getOrDefault(cmdSig, 0) + 1
                commandHistoryTracker[cmdSig] = count
                if (count >= 3) {
                    onMessageAdded(ChatMessageEntity(sender = "system", content = "⚠️ شناساگر حلقه تکراری: دستور '$cmdSig' ۳ بار تکرار شد. تغییر استراتژی..."))
                }

                val context = AgentContext(
                    termuxManager = termuxManager,
                    gitHubClient = gitHubClient,
                    cloudflareClient = cloudflareClient,
                    keystoreManager = keystoreManager,
                    appDatabase = appDatabase,
                    autonomyMode = keystoreManager.getUnencrypted(KeystoreManager.KEY_AUTONOMY_MODE, "SMART_AUTO"),
                    onUserAsk = { q ->
                        onMessageAdded(ChatMessageEntity(sender = "agent", content = "❓ پرسش از کاربر: $q"))
                        "کاربر تأیید کرد."
                    }
                )

                val result = skill.execute(tc.argsJson, context)

                // Log into Room
                appDatabase.actionLogDao().insertLog(
                    ActionLogEntity(
                        actionName = skill.name,
                        command = cmdSig,
                        status = if (result.success) "SUCCESS" else "FAILED",
                        stdout = result.stdout,
                        stderr = result.stderr,
                        exitCode = result.exitCode,
                        durationMs = result.durationMs
                    )
                )

                val outputFormatted = if (result.success) {
                    "✅ [Tool ${skill.name} Success]\n${result.stdout}"
                } else {
                    "❌ [Tool ${skill.name} Failed - Exit Code ${result.exitCode}]\n${result.stderr}\n${result.stdout}"
                }

                onMessageAdded(
                    ChatMessageEntity(
                        sender = "tool",
                        content = outputFormatted,
                        toolCallsJson = tc.argsJson.toString(),
                        toolCallId = tc.id,
                        isError = !result.success,
                        executionTimeMs = result.durationMs
                    )
                )

                conversationHistory.add(LLMMessage(role = "tool", content = outputFormatted, toolCallId = tc.id))
            }
        }

        if (iteration >= maxIterations) {
            onMessageAdded(ChatMessageEntity(sender = "system", content = "حداکثر ۱۰ مرحله اجرا انجام شد. اجرای عامل به پایان رسید."))
        }
        _executionState.value = AgentExecutionState.Idle
    }
}
