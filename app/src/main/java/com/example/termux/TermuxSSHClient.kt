package com.example.termux

import com.example.security.SecretRedactor
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Properties

data class CommandResult(
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long
)

class TermuxSSHClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 8022,
    private val username: String = "termux"
) {

    suspend fun executeCommand(
        command: String,
        privateKeyPem: String,
        timeoutMs: Long = 60000,
        onStdoutLine: ((String) -> Unit)? = null,
        onStderrLine: ((String) -> Unit)? = null
    ): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val jsch = JSch()

        // Load private key into JSch
        jsch.addIdentity("shen_identity", privateKeyPem.toByteArray(Charsets.UTF_8), null, null)

        var session: Session? = null
        var channel: ChannelExec? = null

        val stdoutBuilder = StringBuilder()
        val stderrBuilder = StringBuilder()
        var exitCode = -1

        try {
            session = jsch.getSession(username, host, port).apply {
                val config = Properties()
                config["StrictHostKeyChecking"] = "no"
                setConfig(config)
                timeout = timeoutMs.toInt()
                connect(15000)
            }

            channel = (session.openChannel("exec") as ChannelExec).apply {
                setCommand(command)
                inputStream = null
            }

            val stdoutReader = BufferedReader(InputStreamReader(channel.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(channel.errStream))

            channel.connect(10000)

            val deadLine = System.currentTimeMillis() + timeoutMs

            while (!channel.isClosed) {
                if (System.currentTimeMillis() > deadLine) {
                    channel.disconnect()
                    stderrBuilder.append("\n[Command timed out after ${timeoutMs / 1000}s]")
                    break
                }

                while (stdoutReader.ready()) {
                    val line = stdoutReader.readLine() ?: break
                    val redacted = SecretRedactor.redact(line)
                    stdoutBuilder.append(redacted).append("\n")
                    onStdoutLine?.invoke(redacted)
                }

                while (stderrReader.ready()) {
                    val line = stderrReader.readLine() ?: break
                    val redacted = SecretRedactor.redact(line)
                    stderrBuilder.append(redacted).append("\n")
                    onStderrLine?.invoke(redacted)
                }

                Thread.sleep(50)
            }

            // Drain remaining
            while (stdoutReader.ready()) {
                val line = stdoutReader.readLine() ?: break
                val redacted = SecretRedactor.redact(line)
                stdoutBuilder.append(redacted).append("\n")
                onStdoutLine?.invoke(redacted)
            }
            while (stderrReader.ready()) {
                val line = stderrReader.readLine() ?: break
                val redacted = SecretRedactor.redact(line)
                stderrBuilder.append(redacted).append("\n")
                onStderrLine?.invoke(redacted)
            }

            exitCode = channel.exitStatus
        } catch (e: Exception) {
            stderrBuilder.append("\nSSH Exception: ").append(e.localizedMessage ?: e.message)
            exitCode = -1
        } finally {
            try {
                channel?.disconnect()
                session?.disconnect()
            } catch (_: Exception) {}
        }

        val duration = System.currentTimeMillis() - startTime
        CommandResult(
            command = command,
            stdout = SecretRedactor.redact(stdoutBuilder.toString().trimEnd()),
            stderr = SecretRedactor.redact(stderrBuilder.toString().trimEnd()),
            exitCode = if (exitCode < 0) 1 else exitCode,
            durationMs = duration
        )
    }

    suspend fun testConnection(privateKeyPem: String): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("echo SHEN_SSH_OK", privateKeyPem, timeoutMs = 5000)
        result.stdout.contains("SHEN_SSH_OK")
    }
}
