package com.example.agent

enum class AutonomyMode {
    ASK_EVERY_TIME,
    SMART_AUTO,
    FULL_AUTO
}

class AutonomyManager(var currentMode: AutonomyMode = AutonomyMode.SMART_AUTO) {

    fun requiresApproval(skill: AgentSkill, commandOrPath: String = ""): Boolean {
        return when (currentMode) {
            AutonomyMode.ASK_EVERY_TIME -> true
            AutonomyMode.SMART_AUTO -> {
                // Smart Auto: auto-run safe commands, ask for highly destructive actions
                if (skill.isDestructive) return true
                if (commandOrPath.contains("rm -rf") ||
                    commandOrPath.contains("drop database") ||
                    commandOrPath.contains("git push --force") ||
                    commandOrPath.contains("delete_dns")
                ) {
                    return true
                }
                false
            }
            AutonomyMode.FULL_AUTO -> {
                // Full Auto: auto-run almost everything except critical infrastructure wipe
                if (commandOrPath.contains("rm -rf /") || commandOrPath.contains("git repo delete")) return true
                false
            }
        }
    }
}
