package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.SystemPrompt
import com.example.security.KeystoreManager
import com.example.security.SecretRedactor
import com.example.ui.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val isFa = viewModel.currentLanguage.collectAsState().value == "fa"
    val isDark = viewModel.isDarkTheme.collectAsState().value

    var provider by remember { mutableStateOf(viewModel.keystoreManager.getUnencrypted(KeystoreManager.KEY_LLM_PROVIDER, "gemini")) }
    var apiKey by remember { mutableStateOf(viewModel.keystoreManager.decryptAndGet(KeystoreManager.KEY_LLM_API_KEY)) }
    var baseUrl by remember { mutableStateOf(viewModel.keystoreManager.getUnencrypted(KeystoreManager.KEY_LLM_BASE_URL)) }
    var modelName by remember { mutableStateOf(viewModel.keystoreManager.getUnencrypted(KeystoreManager.KEY_LLM_MODEL, "gemini-2.5-flash")) }
    var autonomyMode by remember { mutableStateOf(viewModel.keystoreManager.getUnencrypted(KeystoreManager.KEY_AUTONOMY_MODE, "SMART_AUTO")) }
    var systemPromptText by remember { mutableStateOf(viewModel.keystoreManager.getUnencrypted(KeystoreManager.KEY_SYSTEM_PROMPT, SystemPrompt.DEFAULT_PROMPT)) }

    var saveNotice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (isFa) "⚙️ تنظیمات پیشرفته عامل و مدل" else "⚙️ Settings & Agent Config",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Appearance & Language Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { viewModel.toggleLanguage() }) {
                    Icon(imageVector = Icons.Default.Language, contentDescription = "Lang")
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(if (isFa) "تغییر زبان به English" else "Switch to Persian (فارسی)")
                }

                Button(onClick = { viewModel.toggleTheme() }) {
                    Icon(imageVector = Icons.Default.Nightlight, contentDescription = "Theme")
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(if (isDark) "Light" else "Dark")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // LLM Provider Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isFa) "🤖 ارائه دهنده هوش مصنوعی (LLM Provider)" else "🤖 LLM Provider Settings",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                val providers = listOf("gemini", "openai", "anthropic", "openrouter")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    providers.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = provider == p,
                                onClick = { provider = p }
                            )
                            Text(text = p.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("llm_api_key_input"),
                    label = { Text("API Key") },
                    placeholder = { Text("Masked: ${SecretRedactor.maskToken(apiKey)}") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model Name (e.g. gemini-2.5-flash, gpt-4o)") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom Base URL (Optional)") }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Autonomy Mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isFa) "🛡️ سطح خودکارسازی عامل (Autonomy Mode)" else "🛡️ Autonomy Mode",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                val modes = listOf("ASK_EVERY_TIME", "SMART_AUTO", "FULL_AUTO")
                modes.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = autonomyMode == mode,
                            onClick = { autonomyMode = mode }
                        )
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text(
                            text = when (mode) {
                                "ASK_EVERY_TIME" -> if (isFa) "پرسش برای هر اکشن (Ask Every Time)" else "Ask Every Time"
                                "SMART_AUTO" -> if (isFa) "خودکار هوشمند (Smart Auto - پیش‌فرض)" else "Smart Auto (Default)"
                                else -> if (isFa) "خودکار کامل (Full Auto)" else "Full Auto"
                            },
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System Prompt Editor
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isFa) "📝 دستورالعمل سیستم (System Prompt)" else "📝 System Prompt",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedButton(
                        onClick = {
                            systemPromptText = SystemPrompt.DEFAULT_PROMPT
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = "Reset")
                        Spacer(modifier = Modifier.padding(2.dp))
                        Text(if (isFa) "ریست" else "Reset")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = systemPromptText,
                    onValueChange = { systemPromptText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    textStyle = TextStyle(fontSize = 11.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save All Button
        Button(
            onClick = {
                viewModel.keystoreManager.saveUnencrypted(KeystoreManager.KEY_LLM_PROVIDER, provider)
                viewModel.keystoreManager.encryptAndSave(KeystoreManager.KEY_LLM_API_KEY, apiKey)
                viewModel.keystoreManager.saveUnencrypted(KeystoreManager.KEY_LLM_BASE_URL, baseUrl)
                viewModel.keystoreManager.saveUnencrypted(KeystoreManager.KEY_LLM_MODEL, modelName)
                viewModel.keystoreManager.saveUnencrypted(KeystoreManager.KEY_AUTONOMY_MODE, autonomyMode)
                viewModel.keystoreManager.saveUnencrypted(KeystoreManager.KEY_SYSTEM_PROMPT, systemPromptText)
                saveNotice = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_settings_button")
        ) {
            Text(if (saveNotice) (if (isFa) "تنظیمات ذخیره شد!" else "Settings Saved!") else (if (isFa) "ذخیره تغییرات" else "Save Settings"))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Regenerate SSH Key Pair
        OutlinedButton(
            onClick = {
                val newKeys = com.example.termux.SSHKeyGenerator.generateKeyPair()
                viewModel.keystoreManager.encryptAndSave(KeystoreManager.KEY_SSH_PRIVATE_KEY, newKeys.privateKeyPem)
                viewModel.keystoreManager.saveUnencrypted(KeystoreManager.KEY_SSH_PUBLIC_KEY, newKeys.publicKeyAuthorizedFormat)
                viewModel.testConnection()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Key, contentDescription = "Key")
            Spacer(modifier = Modifier.padding(4.dp))
            Text(if (isFa) "تولید مجدد کلید SSH دستگاه" else "Regenerate SSH Key Pair")
        }
    }
}
