package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("shen_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "shen_master_key_alias"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        const val KEY_LLM_API_KEY = "llm_api_key"
        const val KEY_LLM_BASE_URL = "llm_base_url"
        const val KEY_LLM_MODEL = "llm_model"
        const val KEY_LLM_PROVIDER = "llm_provider" // "gemini", "openai", "anthropic", "openrouter"
        const val KEY_GITHUB_TOKEN = "github_token"
        const val KEY_GITHUB_DEFAULT_REPO = "github_default_repo"
        const val KEY_CLOUDFLARE_TOKEN = "cloudflare_token"
        const val KEY_CLOUDFLARE_ACCOUNT_ID = "cloudflare_account_id"
        const val KEY_CLOUDFLARE_ZONE_ID = "cloudflare_zone_id"
        const val KEY_SSH_PRIVATE_KEY = "ssh_private_key"
        const val KEY_SSH_PUBLIC_KEY = "ssh_public_key"
        const val KEY_AUTONOMY_MODE = "autonomy_mode" // "ASK_EVERY_TIME", "SMART_AUTO", "FULL_AUTO"
        const val KEY_LANGUAGE = "app_language" // "fa", "en"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_SYSTEM_PROMPT = "system_prompt_custom"
    }

    init {
        getOrCreateSecretKey()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encryptAndSave(key: String, value: String) {
        if (value.isEmpty()) {
            prefs.edit().remove(key).apply()
            return
        }
        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
            prefs.edit().putString(key, encoded).apply()
        } catch (e: Exception) {
            // Fallback plain string if keystore issues arise
            prefs.edit().putString(key + "_plain", value).apply()
        }
    }

    fun decryptAndGet(key: String, defaultValue: String = ""): String {
        val encoded = prefs.getString(key, null) ?: return prefs.getString(key + "_plain", defaultValue) ?: defaultValue
        try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < 12) return defaultValue

            val iv = ByteArray(12)
            val encryptedBytes = ByteArray(combined.size - 12)
            System.arraycopy(combined, 0, iv, 0, 12)
            System.arraycopy(combined, 12, encryptedBytes, 0, encryptedBytes.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return prefs.getString(key + "_plain", defaultValue) ?: defaultValue
        }
    }

    fun saveUnencrypted(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getUnencrypted(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    fun removeKey(key: String) {
        prefs.edit().remove(key).remove(key + "_plain").apply()
    }
}
