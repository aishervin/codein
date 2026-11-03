package com.example.termux

import android.content.Context
import com.example.security.KeystoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Connecting : ConnectionStatus()
    object ConnectedSSH : ConnectionStatus()
    object ConnectedIntentFallback : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

class TermuxManager(
    private val context: Context,
    private val keystoreManager: KeystoreManager
) {
    private val sshClient = TermuxSSHClient()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    fun getOrGenerateSSHKeys(): SSHKeyPair {
        var privateKey = keystoreManager.decryptAndGet(KeystoreManager.KEY_SSH_PRIVATE_KEY)
        var publicKey = keystoreManager.getUnencrypted(KeystoreManager.KEY_SSH_PUBLIC_KEY)

        if (privateKey.isEmpty() || publicKey.isEmpty()) {
            val generated = SSHKeyGenerator.generateKeyPair()
            keystoreManager.encryptAndSave(KeystoreManager.KEY_SSH_PRIVATE_KEY, generated.privateKeyPem)
            keystoreManager.saveUnencrypted(KeystoreManager.KEY_SSH_PUBLIC_KEY, generated.publicKeyAuthorizedFormat)
            privateKey = generated.privateKeyPem
            publicKey = generated.publicKeyAuthorizedFormat
        }

        return SSHKeyPair(privateKeyPem = privateKey, publicKeyAuthorizedFormat = publicKey)
    }

    suspend fun testConnection(): ConnectionStatus {
        _connectionStatus.value = ConnectionStatus.Connecting
        val keys = getOrGenerateSSHKeys()

        // 1. Try Primary SSH channel
        val sshConnected = sshClient.testConnection(keys.privateKeyPem)
        if (sshConnected) {
            _connectionStatus.value = ConnectionStatus.ConnectedSSH
            return ConnectionStatus.ConnectedSSH
        }

        // 2. Try Secondary RUN_COMMAND Intent trigger
        val intentSent = TermuxIntentRunner.executeViaIntent(
            context = context,
            arguments = arrayOf("-c", "sshd -p 8022 || true")
        )

        if (intentSent) {
            // Re-check SSH after triggering sshd start
            kotlinx.coroutines.delay(1000)
            if (sshClient.testConnection(keys.privateKeyPem)) {
                _connectionStatus.value = ConnectionStatus.ConnectedSSH
                return ConnectionStatus.ConnectedSSH
            }
            _connectionStatus.value = ConnectionStatus.ConnectedIntentFallback
            return ConnectionStatus.ConnectedIntentFallback
        }

        val err = ConnectionStatus.Error("SSH 127.0.0.1:8022 unreachable. Please run the bootstrap command in Termux.")
        _connectionStatus.value = err
        return err
    }

    suspend fun runShellCommand(
        command: String,
        timeoutMs: Long = 60000,
        onStdoutLine: ((String) -> Unit)? = null,
        onStderrLine: ((String) -> Unit)? = null
    ): CommandResult {
        val keys = getOrGenerateSSHKeys()

        // Always attempt primary SSH execution first
        val result = sshClient.executeCommand(
            command = command,
            privateKeyPem = keys.privateKeyPem,
            timeoutMs = timeoutMs,
            onStdoutLine = onStdoutLine,
            onStderrLine = onStderrLine
        )

        if (result.exitCode == 0 || result.stdout.isNotEmpty()) {
            _connectionStatus.value = ConnectionStatus.ConnectedSSH
            return result
        }

        // Secondary fallback
        TermuxIntentRunner.executeViaIntent(
            context = context,
            arguments = arrayOf("-c", command)
        )

        return result
    }
}
