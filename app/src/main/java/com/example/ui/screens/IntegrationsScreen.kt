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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.KeystoreManager
import com.example.security.SecretRedactor
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun IntegrationsScreen(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val isFa = viewModel.currentLanguage.collectAsState().value == "fa"

    var githubToken by remember { mutableStateOf(viewModel.keystoreManager.decryptAndGet(KeystoreManager.KEY_GITHUB_TOKEN)) }
    var githubTestResult by remember { mutableStateOf("") }
    var githubIsSuccess by remember { mutableStateOf<Boolean?>(null) }

    var cfToken by remember { mutableStateOf(viewModel.keystoreManager.decryptAndGet(KeystoreManager.KEY_CLOUDFLARE_TOKEN)) }
    var cfAccountId by remember { mutableStateOf(viewModel.keystoreManager.getUnencrypted(KeystoreManager.KEY_CLOUDFLARE_ACCOUNT_ID)) }
    var cfZoneId by remember { mutableStateOf(viewModel.keystoreManager.getUnencrypted(KeystoreManager.KEY_CLOUDFLARE_ZONE_ID)) }
    var cfTestResult by remember { mutableStateOf("") }
    var cfIsSuccess by remember { mutableStateOf<Boolean?>(null) }

    var syncNotice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (isFa) "🔐 گاوصندوق اتصال‌ها (GitHub & Cloudflare)" else "🔐 Integrations Vault",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // GitHub Integration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🐙 GitHub Personal Access Token (PAT)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = githubToken,
                    onValueChange = {
                        githubToken = it
                        viewModel.keystoreManager.encryptAndSave(KeystoreManager.KEY_GITHUB_TOKEN, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_token_input"),
                    label = { Text("GitHub Token (ghp_... / github_pat_...)") },
                    placeholder = { Text("Masked: ${SecretRedactor.maskToken(githubToken)}") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                githubTestResult = "Testing GitHub token..."
                                val res = viewModel.gitHubClient.testToken(githubToken)
                                if (res.isSuccess) {
                                    val user = res.getOrThrow()
                                    githubIsSuccess = true
                                    githubTestResult = "✅ Connected as ${user.login} (${user.name})\nScopes: ${user.scopes}"
                                } else {
                                    githubIsSuccess = false
                                    githubTestResult = "❌ Error: ${res.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_github_token_button")
                    ) {
                        Text(if (isFa) "تست اتصال" else "Test GitHub")
                    }

                    OutlinedButton(
                        onClick = {
                            githubToken = ""
                            viewModel.keystoreManager.removeKey(KeystoreManager.KEY_GITHUB_TOKEN)
                            githubTestResult = ""
                            githubIsSuccess = null
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                    }
                }

                if (githubTestResult.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = githubTestResult,
                        fontSize = 12.sp,
                        color = if (githubIsSuccess == true) Color(0xFF00E676) else Color(0xFFFF5252)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cloudflare Integration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "☁️ Cloudflare API Token",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = cfToken,
                    onValueChange = {
                        cfToken = it
                        viewModel.keystoreManager.encryptAndSave(KeystoreManager.KEY_CLOUDFLARE_TOKEN, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cloudflare_token_input"),
                    label = { Text("Cloudflare API Token") },
                    placeholder = { Text("Masked: ${SecretRedactor.maskToken(cfToken)}") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cfAccountId,
                        onValueChange = {
                            cfAccountId = it
                            viewModel.keystoreManager.saveUnencrypted(KeystoreManager.KEY_CLOUDFLARE_ACCOUNT_ID, it)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Account ID (Optional)") }
                    )

                    OutlinedTextField(
                        value = cfZoneId,
                        onValueChange = {
                            cfZoneId = it
                            viewModel.keystoreManager.saveUnencrypted(KeystoreManager.KEY_CLOUDFLARE_ZONE_ID, it)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Zone ID (Optional)") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                cfTestResult = "Testing Cloudflare token..."
                                val res = viewModel.cloudflareClient.verifyToken(cfToken)
                                if (res.isSuccess) {
                                    val status = res.getOrThrow()
                                    cfIsSuccess = true
                                    cfTestResult = "✅ Token Verified! Status: ${status.status}\nAccounts: ${status.accountsCount}, Zones: ${status.zonesCount}"
                                } else {
                                    cfIsSuccess = false
                                    cfTestResult = "❌ Error: ${res.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_cloudflare_token_button")
                    ) {
                        Text(if (isFa) "تست کلودفلر" else "Test Cloudflare")
                    }

                    OutlinedButton(
                        onClick = {
                            cfToken = ""
                            viewModel.keystoreManager.removeKey(KeystoreManager.KEY_CLOUDFLARE_TOKEN)
                            cfTestResult = ""
                            cfIsSuccess = null
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                    }
                }

                if (cfTestResult.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = cfTestResult,
                        fontSize = 12.sp,
                        color = if (cfIsSuccess == true) Color(0xFF00E676) else Color(0xFFFF5252)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sync Tokens to Termux Action
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isFa) "همگام‌سازی توکن‌ها با محیط ترموکس" else "Sync Tokens into Termux Environment",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isFa)
                        "این عمل توکن‌های شما را به صورت ایمن در ~/.config/shen/env (دسترسی chmod 600) قرار می‌دهد تا ابزارهای gh و cloudflared مستقیماً کار کنند."
                    else
                        "Export GH_TOKEN & CLOUDFLARE_API_TOKEN into ~/.config/shen/env (chmod 600) inside Termux.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel.syncTokensToTermux()
                        syncNotice = true
                    },
                    modifier = Modifier.testTag("sync_tokens_button")
                ) {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = "Sync")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (syncNotice) (if (isFa) "همگام‌سازی شد!" else "Synced!") else (if (isFa) "ارسال به ترموکس" else "Sync Now"))
                }
            }
        }
    }
}
