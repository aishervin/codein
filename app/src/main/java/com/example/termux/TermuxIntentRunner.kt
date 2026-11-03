package com.example.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object TermuxIntentRunner {

    private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"

    fun executeViaIntent(
        context: Context,
        commandPath: String = "/data/data/com.termux/files/usr/bin/bash",
        arguments: Array<String> = arrayOf("-c", "echo SHEN_INTENT_OK"),
        workDir: String = "/data/data/com.termux/files/home",
        inBackground: Boolean = true
    ): Boolean {
        try {
            val intent = Intent(ACTION_RUN_COMMAND).apply {
                setClassName("com.termux", TERMUX_SERVICE)
                putExtra(EXTRA_COMMAND_PATH, commandPath)
                putExtra(EXTRA_ARGUMENTS, arguments)
                putExtra(EXTRA_WORKDIR, workDir)
                putExtra(EXTRA_BACKGROUND, inBackground)
                putExtra(EXTRA_SESSION_ACTION, "0") // 0 = default session
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
