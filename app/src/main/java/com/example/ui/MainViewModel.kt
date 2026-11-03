package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.AgentEngine
import com.example.agent.AgentExecutionState
import com.example.agent.SystemPrompt
import com.example.bootstrap.BootstrapGenerator
import com.example.data.ActionLogEntity
import com.example.data.AppDatabase
import com.example.data.ChatMessageEntity
import com.example.data.MemoryNoteEntity
import com.example.data.SnapshotEntity
import com.example.integrations.CloudflareApiClient
import com.example.integrations.GitHubApiClient
import com.example.security.KeystoreManager
import com.example.termux.ConnectionStatus
import com.example.termux.TermuxManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val db = AppDatabase.getInstance(application)
    val keystoreManager = KeystoreManager(application)
    val termuxManager = TermuxManager(application, keystoreManager)
    val agentEngine = AgentEngine(termuxManager, keystoreManager, db)

    val gitHubClient = GitHubApiClient()
    val cloudflareClient = CloudflareApiClient()

    val chatMessages: StateFlow<List<ChatMessageEntity>> = db.chatDao().getAllMessages()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val actionLogs: StateFlow<List<ActionLogEntity>> = db.actionLogDao().getAllLogs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val snapshots: StateFlow<List<SnapshotEntity>> = db.snapshotDao().getAllSnapshots()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val memoryNotes: StateFlow<List<MemoryNoteEntity>> = db.memoryNoteDao().getAllMemoryNotes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val connectionStatus: StateFlow<ConnectionStatus> = termuxManager.connectionStatus
    val executionState: StateFlow<AgentExecutionState> = agentEngine.executionState

    private val _currentLanguage = MutableStateFlow(keystoreManager.getUnencrypted(KeystoreManager.KEY_LANGUAGE, "fa"))
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(keystoreManager.getUnencrypted(KeystoreManager.KEY_DARK_THEME, "true").toBoolean())
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    init {
        viewModelScope.launch {
            termuxManager.testConnection()
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            termuxManager.testConnection()
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return
        viewModelScope.launch {
            val userMsg = ChatMessageEntity(sender = "user", content = userText)
            db.chatDao().insertMessage(userMsg)
            agentEngine.runAgentLoop(userText) { msg ->
                db.chatDao().insertMessage(msg)
            }
        }
    }

    fun stopAgentExecution() {
        agentEngine.stopAgent()
    }

    fun clearChat() {
        viewModelScope.launch {
            db.chatDao().clearAllMessages()
        }
    }

    fun getBootstrapOneLiner(): String {
        val keys = termuxManager.getOrGenerateSSHKeys()
        return BootstrapGenerator.generateOneLinerCommand(keys.publicKeyAuthorizedFormat)
    }

    fun getBootstrapFullScript(): String {
        val keys = termuxManager.getOrGenerateSSHKeys()
        return BootstrapGenerator.generateFullScript(keys.publicKeyAuthorizedFormat)
    }

    fun restoreSnapshot(snapshot: SnapshotEntity) {
        viewModelScope.launch {
            termuxManager.runShellCommand("cat << 'EOF' > \"${snapshot.filePath}\"\n${snapshot.originalContent}\nEOF")
        }
    }

    fun addMemoryNote(category: String, key: String, value: String) {
        viewModelScope.launch {
            db.memoryNoteDao().insertOrUpdate(
                MemoryNoteEntity(category = category, keyName = key, valueContent = value)
            )
        }
    }

    fun deleteMemoryNote(id: Long) {
        viewModelScope.launch {
            db.memoryNoteDao().deleteNote(id)
        }
    }

    fun toggleLanguage() {
        val newLang = if (_currentLanguage.value == "fa") "en" else "fa"
        _currentLanguage.value = newLang
        keystoreManager.saveUnencrypted(KeystoreManager.KEY_LANGUAGE, newLang)
    }

    fun toggleTheme() {
        val newTheme = !_isDarkTheme.value
        _isDarkTheme.value = newTheme
        keystoreManager.saveUnencrypted(KeystoreManager.KEY_DARK_THEME, newTheme.toString())
    }

    fun syncTokensToTermux() {
        viewModelScope.launch {
            val ghToken = keystoreManager.decryptAndGet(KeystoreManager.KEY_GITHUB_TOKEN)
            val cfToken = keystoreManager.decryptAndGet(KeystoreManager.KEY_CLOUDFLARE_TOKEN)

            val script = """
mkdir -p ~/.config/shen
cat << 'EOF' > ~/.config/shen/env
export GH_TOKEN="$ghToken"
export GITHUB_TOKEN="$ghToken"
export CLOUDFLARE_API_TOKEN="$cfToken"
EOF
chmod 600 ~/.config/shen/env
""".trimIndent()
            termuxManager.runShellCommand(script)
        }
    }
}
