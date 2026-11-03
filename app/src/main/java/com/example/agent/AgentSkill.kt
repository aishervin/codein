package com.example.agent

import org.json.JSONObject

data class SkillResult(
    val success: Boolean,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = 0,
    val durationMs: Long = 0,
    val summary: String = "",
    val requiresUserApproval: Boolean = false,
    val approvalPrompt: String? = null
)

abstract class AgentSkill(
    val name: String,
    val description: String,
    val isDestructive: Boolean = false
) {
    abstract fun getJsonSchema(): JSONObject

    abstract suspend fun execute(
        args: JSONObject,
        context: AgentContext
    ): SkillResult
}

data class AgentContext(
    val termuxManager: com.example.termux.TermuxManager,
    val gitHubClient: com.example.integrations.GitHubApiClient,
    val cloudflareClient: com.example.integrations.CloudflareApiClient,
    val keystoreManager: com.example.security.KeystoreManager,
    val appDatabase: com.example.data.AppDatabase,
    val autonomyMode: String,
    val onUserAsk: suspend (String) -> String
)
