package com.example.agent

import com.example.data.SnapshotEntity
import com.example.security.KeystoreManager
import com.example.security.SecretRedactor
import org.json.JSONArray
import org.json.JSONObject

class SkillRegistry {

    private val skills = mutableMapOf<String, AgentSkill>()

    init {
        registerSkill(RunShellSkill())
        registerSkill(ReadFileSkill())
        registerSkill(WriteFileSkill())
        registerSkill(EditFileSkill())
        registerSkill(ListDirSkill())
        registerSkill(DeletePathSkill())
        registerSkill(InstallPackageSkill())
        registerSkill(ServiceControlSkill())
        registerSkill(HttpRequestSkill())
        registerSkill(SystemInfoSkill())
        registerSkill(ManageCronBootSkill())
        registerSkill(ManageSshSkill())
        registerSkill(ManageTunnelSkill())
        registerSkill(ManageBridgeSkill())
        registerSkill(ManageMcpSkill())
        registerSkill(GitHubApiSkill())
        registerSkill(GitOpsSkill())
        registerSkill(CloudflareApiSkill())
        registerSkill(AskUserSkill())
    }

    fun registerSkill(skill: AgentSkill) {
        skills[skill.name] = skill
    }

    fun getSkill(name: String): AgentSkill? = skills[name]

    fun getAllSkills(): List<AgentSkill> = skills.values.toList()

    fun getToolsJsonSchema(): JSONArray {
        val array = JSONArray()
        for (skill in skills.values) {
            array.put(skill.getJsonSchema())
        }
        return array
    }
}

// 1. run_shell
class RunShellSkill : AgentSkill("run_shell", "Execute a bash shell command in Termux") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("command", JSONObject().put("type", "string").put("description", "Bash command to execute"))
                    put("timeout_ms", JSONObject().put("type", "integer").put("description", "Timeout in ms (default 60000)"))
                })
                put("required", JSONArray().put("command"))
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val command = args.optString("command")
        val timeout = args.optLong("timeout_ms", 60000)

        val res = context.termuxManager.runShellCommand(command, timeoutMs = timeout)
        return SkillResult(
            success = res.exitCode == 0,
            stdout = res.stdout,
            stderr = res.stderr,
            exitCode = res.exitCode,
            durationMs = res.durationMs,
            summary = if (res.exitCode == 0) "Command executed successfully" else "Command failed with exit code ${res.exitCode}"
        )
    }
}

// 2. read_file
class ReadFileSkill : AgentSkill("read_file", "Read file contents from Termux filesystem") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().put("type", "string").put("description", "Absolute or relative file path in Termux"))
                })
                put("required", JSONArray().put("path"))
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val path = args.optString("path")
        val cmd = "cat \"$path\""
        val res = context.termuxManager.runShellCommand(cmd)
        return SkillResult(
            success = res.exitCode == 0,
            stdout = res.stdout,
            stderr = res.stderr,
            exitCode = res.exitCode,
            durationMs = res.durationMs,
            summary = if (res.exitCode == 0) "Read $path successfully" else "Failed to read $path"
        )
    }
}

// 3. write_file
class WriteFileSkill : AgentSkill("write_file", "Write or overwrite a file in Termux (creates automatic snapshot)", isDestructive = true) {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().put("type", "string").put("description", "File path to write"))
                    put("content", JSONObject().put("type", "string").put("description", "File content"))
                })
                put("required", JSONArray().put("path").put("content"))
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val path = args.optString("path")
        val content = args.optString("content")

        // 1. Create automatic snapshot if file exists
        val checkRes = context.termuxManager.runShellCommand("cat \"$path\" || true")
        if (checkRes.exitCode == 0 && checkRes.stdout.isNotEmpty()) {
            context.appDatabase.snapshotDao().insertSnapshot(
                SnapshotEntity(
                    filePath = path,
                    originalContent = checkRes.stdout,
                    snapshotDescription = "Auto snapshot before write_file"
                )
            )
        }

        // 2. Write file safely via base64 to avoid quote escaping issues
        val base64Content = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val cmd = "mkdir -p \$(dirname \"$path\") && echo \"$base64Content\" | base64 -d > \"$path\""
        val res = context.termuxManager.runShellCommand(cmd)

        return SkillResult(
            success = res.exitCode == 0,
            stdout = res.stdout,
            stderr = res.stderr,
            exitCode = res.exitCode,
            durationMs = res.durationMs,
            summary = if (res.exitCode == 0) "Wrote $path successfully with snapshot created" else "Failed to write $path"
        )
    }
}

// 4. edit_file
class EditFileSkill : AgentSkill("edit_file", "Search and replace target string in a file with snapshot protection", isDestructive = true) {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().put("type", "string").put("description", "File path"))
                    put("target", JSONObject().put("type", "string").put("description", "String to search for"))
                    put("replacement", JSONObject().put("type", "string").put("description", "Replacement string"))
                })
                put("required", JSONArray().put("path").put("target").put("replacement"))
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val path = args.optString("path")
        val target = args.optString("target")
        val replacement = args.optString("replacement")

        val readRes = context.termuxManager.runShellCommand("cat \"$path\"")
        if (readRes.exitCode != 0) {
            return SkillResult(success = false, stderr = "File $path not found")
        }

        val original = readRes.stdout
        if (!original.contains(target)) {
            return SkillResult(success = false, stderr = "Target string not found in $path")
        }

        // Create snapshot
        context.appDatabase.snapshotDao().insertSnapshot(
            SnapshotEntity(
                filePath = path,
                originalContent = original,
                snapshotDescription = "Snapshot before edit_file ($target)"
            )
        )

        val updated = original.replace(target, replacement)
        val base64Content = android.util.Base64.encodeToString(updated.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val cmd = "echo \"$base64Content\" | base64 -d > \"$path\""
        val writeRes = context.termuxManager.runShellCommand(cmd)

        return SkillResult(
            success = writeRes.exitCode == 0,
            stdout = writeRes.stdout,
            stderr = writeRes.stderr,
            exitCode = writeRes.exitCode,
            durationMs = writeRes.durationMs,
            summary = "Patched $path successfully"
        )
    }
}

// 5. list_dir
class ListDirSkill : AgentSkill("list_dir", "List directory contents with details in Termux") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().put("type", "string").put("description", "Directory path (default ~)"))
                })
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val path = args.optString("path", "~")
        val cmd = "ls -la \"$path\""
        val res = context.termuxManager.runShellCommand(cmd)
        return SkillResult(
            success = res.exitCode == 0,
            stdout = res.stdout,
            stderr = res.stderr,
            exitCode = res.exitCode,
            durationMs = res.durationMs,
            summary = "Listed $path"
        )
    }
}

// 6. delete_path
class DeletePathSkill : AgentSkill("delete_path", "Delete a file or directory in Termux (with automatic snapshot)", isDestructive = true) {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().put("type", "string").put("description", "Path to delete"))
                })
                put("required", JSONArray().put("path"))
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val path = args.optString("path")

        // Snapshot if file
        val checkRes = context.termuxManager.runShellCommand("cat \"$path\" || true")
        if (checkRes.exitCode == 0 && checkRes.stdout.isNotEmpty()) {
            context.appDatabase.snapshotDao().insertSnapshot(
                SnapshotEntity(
                    filePath = path,
                    originalContent = checkRes.stdout,
                    snapshotDescription = "Snapshot before delete_path"
                )
            )
        }

        val res = context.termuxManager.runShellCommand("rm -rf \"$path\"")
        return SkillResult(
            success = res.exitCode == 0,
            stdout = res.stdout,
            stderr = res.stderr,
            exitCode = res.exitCode,
            durationMs = res.durationMs,
            summary = "Deleted $path"
        )
    }
}

// 7. install_package
class InstallPackageSkill : AgentSkill("install_package", "Install package via pkg, pip, or npm") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("manager", JSONObject().put("type", "string").put("description", "pkg, pip, or npm"))
                    put("packageName", JSONObject().put("type", "string").put("description", "Name of package"))
                })
                put("required", JSONArray().put("manager").put("packageName"))
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val mgr = args.optString("manager", "pkg").lowercase()
        val pkg = args.optString("packageName")

        val cmd = when (mgr) {
            "pip", "pip3" -> "pip install \"$pkg\""
            "npm" -> "npm install -g \"$pkg\""
            else -> "pkg install -y \"$pkg\""
        }

        val res = context.termuxManager.runShellCommand(cmd)
        return SkillResult(
            success = res.exitCode == 0,
            stdout = res.stdout,
            stderr = res.stderr,
            exitCode = res.exitCode,
            durationMs = res.durationMs,
            summary = "Installed $pkg via $mgr"
        )
    }
}

// 8. service_control
class ServiceControlSkill : AgentSkill("service_control", "Start, stop, or check status of background services/processes in Termux") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().put("type", "string").put("description", "status, start, stop, or ports"))
                    put("serviceName", JSONObject().put("type", "string").put("description", "Name of service or command"))
                })
                put("required", JSONArray().put("action"))
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val action = args.optString("action").lowercase()
        val svc = args.optString("serviceName")

        val cmd = when (action) {
            "status" -> if (svc.isEmpty()) "ps aux" else "ps aux | grep \"$svc\""
            "ports" -> "netstat -tuln || ss -tuln"
            "stop" -> "pkill -f \"$svc\" || true"
            "start" -> "nohup $svc > ~/.config/shen/$svc.log 2>&1 &"
            else -> "ps aux"
        }

        val res = context.termuxManager.runShellCommand(cmd)
        return SkillResult(
            success = res.exitCode == 0,
            stdout = res.stdout,
            stderr = res.stderr,
            exitCode = res.exitCode,
            durationMs = res.durationMs,
            summary = "Service control action $action executed"
        )
    }
}

// 9. http_request
class HttpRequestSkill : AgentSkill("http_request", "Send HTTP request via curl in Termux") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("url", JSONObject().put("type", "string").put("description", "URL to fetch"))
                    put("method", JSONObject().put("type", "string").put("description", "GET, POST, etc."))
                })
                put("required", JSONArray().put("url"))
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val url = args.optString("url")
        val method = args.optString("method", "GET").uppercase()
        val cmd = "curl -sSL -X $method \"$url\""

        val res = context.termuxManager.runShellCommand(cmd)
        return SkillResult(
            success = res.exitCode == 0,
            stdout = res.stdout,
            stderr = res.stderr,
            exitCode = res.exitCode,
            durationMs = res.durationMs,
            summary = "HTTP $method $url executed"
        )
    }
}

// 10. system_info
class SystemInfoSkill : AgentSkill("system_info", "Retrieve Termux system CPU, memory, storage, and node/python info") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().put("type", "object"))
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val cmd = "echo '=== SYSTEM INFO ===' && uname -a && echo '=== STORAGE ===' && df -h && echo '=== MEMORY ===' && free -m || true && echo '=== NODE/PYTHON ===' && node -v || true && python --version || true"
        val res = context.termuxManager.runShellCommand(cmd)
        return SkillResult(
            success = true,
            stdout = res.stdout,
            summary = "Retrieved system info"
        )
    }
}

// 11. manage_cron_boot
class ManageCronBootSkill : AgentSkill("manage_cron_boot", "Manage crontab and boot auto-start scripts in Termux") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().put("type", "string").put("description", "list, add_cron, add_boot"))
                    put("cron_expression", JSONObject().put("type", "string").put("description", "Cron expression and command"))
                })
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val action = args.optString("action", "list")
        val expr = args.optString("cron_expression")

        val cmd = when (action) {
            "add_cron" -> "(crontab -l 2>/dev/null; echo \"$expr\") | crontab -"
            "add_boot" -> "mkdir -p ~/.termux/boot && echo \"$expr\" >> ~/.termux/boot/shen_auto.sh && chmod +x ~/.termux/boot/shen_auto.sh"
            else -> "crontab -l || true && ls -la ~/.termux/boot 2>/dev/null || true"
        }

        val res = context.termuxManager.runShellCommand(cmd)
        return SkillResult(
            success = res.exitCode == 0,
            stdout = res.stdout,
            summary = "Cron/Boot $action executed"
        )
    }
}

// 12. manage_ssh
class ManageSshSkill : AgentSkill("manage_ssh", "Manage SSH keys, ssh_config, authorized_keys and port forwarding in Termux") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().put("type", "string").put("description", "status, list_keys, add_key"))
                    put("key_data", JSONObject().put("type", "string").put("description", "Public key data"))
                })
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val action = args.optString("action", "status")
        val keyData = args.optString("key_data")

        val cmd = when (action) {
            "add_key" -> "mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo \"$keyData\" >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
            else -> "cat ~/.ssh/authorized_keys 2>/dev/null || echo 'No authorized_keys found'"
        }

        val res = context.termuxManager.runShellCommand(cmd)
        return SkillResult(success = res.exitCode == 0, stdout = res.stdout, summary = "SSH $action completed")
    }
}

// 13. manage_tunnel
class ManageTunnelSkill : AgentSkill("manage_tunnel", "Manage Cloudflare Tunnels (cloudflared quick/named) or SSH reverse tunnels") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("local_port", JSONObject().put("type", "integer").put("description", "Local HTTP port to expose (e.g. 8080)"))
                    put("action", JSONObject().put("type", "string").put("description", "start_quick_tunnel, list, stop"))
                })
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val port = args.optInt("local_port", 8080)
        val action = args.optString("action", "start_quick_tunnel")

        val cmd = when (action) {
            "start_quick_tunnel" -> "nohup cloudflared tunnel --url http://127.0.0.1:$port > ~/.config/shen/tunnel_$port.log 2>&1 & sleep 3 && cat ~/.config/shen/tunnel_$port.log | grep -o 'https://.*trycloudflare.com' || cat ~/.config/shen/tunnel_$port.log"
            "stop" -> "pkill -f cloudflared || true"
            else -> "ps aux | grep cloudflared"
        }

        val res = context.termuxManager.runShellCommand(cmd)
        return SkillResult(success = res.exitCode == 0, stdout = res.stdout, summary = "Tunnel action $action executed for port $port")
    }
}

// 14. manage_bridge
class ManageBridgeSkill : AgentSkill("manage_bridge", "Start or query local HTTP bridge exposing Termux metrics to SHΞN app") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().put("type", "object"))
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val res = context.termuxManager.runShellCommand("ps aux | grep 'node' || true")
        return SkillResult(success = true, stdout = res.stdout, summary = "Bridge status retrieved")
    }
}

// 15. manage_mcp
class ManageMcpSkill : AgentSkill("manage_mcp", "Manage Model Context Protocol (MCP) servers in Termux (~/.config/shen/mcp.json)") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().put("type", "string").put("description", "list, install_server, configure"))
                    put("server_name", JSONObject().put("type", "string").put("description", "Name of MCP server"))
                })
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val action = args.optString("action", "list")
        val server = args.optString("server_name")

        val cmd = when (action) {
            "install_server" -> "npm install -g \"$server\""
            else -> "cat ~/.config/shen/mcp.json 2>/dev/null || echo '{\"mcpServers\":{}}'"
        }

        val res = context.termuxManager.runShellCommand(cmd)
        return SkillResult(success = true, stdout = res.stdout, summary = "MCP $action executed")
    }
}

// 16. github_api
class GitHubApiSkill : AgentSkill("github_api", "Execute GitHub API operations (verify, list repos, list workflow runs, dispatch)") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().put("type", "string").put("description", "verify_token, list_repos"))
                })
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val token = context.keystoreManager.decryptAndGet(KeystoreManager.KEY_GITHUB_TOKEN)
        if (token.isBlank()) {
            return SkillResult(success = false, stderr = "GitHub token not configured in Integrations Vault")
        }

        val action = args.optString("action", "verify_token")
        if (action == "verify_token") {
            val res = context.gitHubClient.testToken(token)
            return if (res.isSuccess) {
                val user = res.getOrThrow()
                SkillResult(success = true, stdout = "Logged in as ${user.login} (${user.name}). Public Repos: ${user.publicRepos}, Scopes: ${user.scopes}")
            } else {
                SkillResult(success = false, stderr = res.exceptionOrNull()?.message ?: "GitHub error")
            }
        }

        val reposRes = context.gitHubClient.getUserRepos(token)
        return if (reposRes.isSuccess) {
            val list = reposRes.getOrThrow().joinToString("\n") { "${it.fullName} (${if (it.isPrivate) "Private" else "Public"})" }
            SkillResult(success = true, stdout = list)
        } else {
            SkillResult(success = false, stderr = reposRes.exceptionOrNull()?.message ?: "Failed to list repos")
        }
    }
}

// 17. git_ops
class GitOpsSkill : AgentSkill("git_ops", "Perform git clone, commit, push in Termux safely using stored GitHub token without echoing secret") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("operation", JSONObject().put("type", "string").put("description", "clone, commit_push, status"))
                    put("repo_url", JSONObject().put("type", "string").put("description", "Repository URL"))
                    put("commit_message", JSONObject().put("type", "string").put("description", "Commit message"))
                })
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val token = context.keystoreManager.decryptAndGet(KeystoreManager.KEY_GITHUB_TOKEN)
        val op = args.optString("operation", "status")
        val repo = args.optString("repo_url")
        val msg = args.optString("commit_message", "Automated commit via SHΞN agent")

        if (op == "clone" && repo.isNotEmpty()) {
            val authRepo = if (token.isNotEmpty() && repo.startsWith("https://github.com/")) {
                repo.replace("https://github.com/", "https://$token@github.com/")
            } else repo

            val cmd = "git clone \"$authRepo\""
            val res = context.termuxManager.runShellCommand(cmd)
            return SkillResult(
                success = res.exitCode == 0,
                stdout = SecretRedactor.redact(res.stdout),
                stderr = SecretRedactor.redact(res.stderr),
                exitCode = res.exitCode,
                summary = "Git clone executed"
            )
        }

        if (op == "commit_push") {
            val cmd = "git add . && git commit -m \"$msg\" && git push"
            val res = context.termuxManager.runShellCommand(cmd)
            return SkillResult(
                success = res.exitCode == 0,
                stdout = SecretRedactor.redact(res.stdout),
                stderr = SecretRedactor.redact(res.stderr),
                summary = "Git commit & push executed"
            )
        }

        val res = context.termuxManager.runShellCommand("git status")
        return SkillResult(success = res.exitCode == 0, stdout = res.stdout, summary = "Git status checked")
    }
}

// 18. cloudflare_api
class CloudflareApiSkill : AgentSkill("cloudflare_api", "Execute Cloudflare operations (verify token, list zones, DNS CRUD, list tunnels)") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().put("type", "string").put("description", "verify, list_zones, list_dns, create_dns, delete_dns"))
                    put("zone_id", JSONObject().put("type", "string").put("description", "Cloudflare Zone ID"))
                    put("record_type", JSONObject().put("type", "string").put("description", "A, CNAME, TXT, etc."))
                    put("record_name", JSONObject().put("type", "string").put("description", "Record domain name"))
                    put("record_content", JSONObject().put("type", "string").put("description", "IP or CNAME target"))
                })
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val token = context.keystoreManager.decryptAndGet(KeystoreManager.KEY_CLOUDFLARE_TOKEN)
        if (token.isBlank()) {
            return SkillResult(success = false, stderr = "Cloudflare API token not configured")
        }

        val action = args.optString("action", "verify")
        val zoneId = args.optString("zone_id", context.keystoreManager.getUnencrypted(KeystoreManager.KEY_CLOUDFLARE_ZONE_ID))

        when (action) {
            "verify" -> {
                val res = context.cloudflareClient.verifyToken(token)
                return if (res.isSuccess) {
                    val status = res.getOrThrow()
                    SkillResult(success = true, stdout = "Cloudflare Token Status: ${status.status}. Accounts: ${status.accountsCount}, Zones: ${status.zonesCount}")
                } else SkillResult(success = false, stderr = res.exceptionOrNull()?.message ?: "CF error")
            }
            "list_zones" -> {
                val res = context.cloudflareClient.getZones(token)
                return if (res.isSuccess) {
                    val list = res.getOrThrow().joinToString("\n") { "${it.name} (Zone ID: ${it.id}, Status: ${it.status})" }
                    SkillResult(success = true, stdout = list)
                } else SkillResult(success = false, stderr = res.exceptionOrNull()?.message ?: "Failed to get zones")
            }
            "list_dns" -> {
                if (zoneId.isBlank()) return SkillResult(success = false, stderr = "Zone ID required")
                val res = context.cloudflareClient.listDnsRecords(token, zoneId)
                return if (res.isSuccess) {
                    val list = res.getOrThrow().joinToString("\n") { "${it.type} ${it.name} -> ${it.content} (ID: ${it.id})" }
                    SkillResult(success = true, stdout = list)
                } else SkillResult(success = false, stderr = "Failed to list DNS")
            }
            "create_dns" -> {
                val type = args.optString("record_type", "A")
                val name = args.optString("record_name")
                val content = args.optString("record_content")
                val res = context.cloudflareClient.createDnsRecord(token, zoneId, type, name, content)
                return if (res.isSuccess) SkillResult(success = true, stdout = res.getOrThrow())
                else SkillResult(success = false, stderr = res.exceptionOrNull()?.message ?: "DNS creation failed")
            }
        }

        return SkillResult(success = false, stderr = "Unknown Cloudflare action")
    }
}

// 19. ask_user
class AskUserSkill : AgentSkill("ask_user", "Ask the human user a question or request confirmation") {
    override fun getJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("question", JSONObject().put("type", "string").put("description", "Question to present to user"))
                })
                put("required", JSONArray().put("question"))
            })
        }
    }

    override suspend fun execute(args: JSONObject, context: AgentContext): SkillResult {
        val q = args.optString("question")
        val answer = context.onUserAsk.invoke(q)
        return SkillResult(success = true, stdout = "User response: $answer", summary = "User answered: $answer")
    }
}
