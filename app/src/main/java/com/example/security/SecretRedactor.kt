package com.example.security

object SecretRedactor {

    private val secretsToMask = mutableSetOf<String>()

    fun registerSecret(secret: String?) {
        if (!secret.isNullOrEmpty() && secret.trim().length >= 4) {
            secretsToMask.add(secret.trim())
        }
    }

    fun redact(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var sanitized: String = text

        // Redact registered secrets
        for (secret in secretsToMask) {
            if (secret.length >= 4 && sanitized.contains(secret)) {
                val replacement = "[REDACTED_SECRET_${secret.takeLast(4)}]"
                sanitized = sanitized.replace(secret, replacement)
            }
        }

        // Regex patterns for standard token forms
        sanitized = sanitized.replace(Regex("(ghp_[a-zA-Z0-9]{36}|github_pat_[a-zA-Z0-9_]{80,})")) {
            "[REDACTED_GITHUB_TOKEN]"
        }

        sanitized = sanitized.replace(Regex("Bearer\\s+[a-zA-Z0-9_\\-=]{20,}"), "Bearer [REDACTED_TOKEN]")

        sanitized = sanitized.replace(
            Regex("-----BEGIN (RSA|EC|OPENSSH|DSA) PRIVATE KEY-----([\\s\\S]*?)-----END \\1 PRIVATE KEY-----"),
            "[REDACTED_SSH_PRIVATE_KEY]"
        )

        sanitized = sanitized.replace(Regex("(Authorization:\\s*Bearer\\s+)[^\\s'\"\\n]+", RegexOption.IGNORE_CASE)) {
            "${it.groupValues[1]}[REDACTED]"
        }

        return sanitized
    }

    fun maskToken(token: String?): String {
        if (token.isNullOrEmpty()) return "Not Set"
        if (token.length <= 8) return "****" + token.takeLast(2)
        return token.take(4) + "...." + token.takeLast(4)
    }
}
