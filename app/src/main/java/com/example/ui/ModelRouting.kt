package com.example.ui

internal data class CodeinModel(
    val id: String,
    val suffix: String,
    val providerId: String? = null
)

internal val retiredAiProviders = setOf("opencode")

private val builtInChatModelIds = setOf("zero", "hero")

internal fun modelFromStoredCatalog(id: String, suffix: String, provider: String): CodeinModel? {
    if (id.isBlank()) return null
    val normalizedProvider = provider.trim().takeIf(String::isNotEmpty)
    if (normalizedProvider != null && normalizedProvider in retiredAiProviders) return null
    if (normalizedProvider == null && id !in builtInChatModelIds) return null
    return CodeinModel(id, suffix.ifBlank { id }, normalizedProvider)
}

internal fun isBuiltInChatModel(model: CodeinModel): Boolean =
    model.providerId == null && model.id in builtInChatModelIds
