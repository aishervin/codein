package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String, // "user", "agent", "system", "tool"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val isError: Boolean = false,
    val executionTimeMs: Long = 0
)

@Entity(tableName = "action_logs")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val actionName: String,
    val command: String,
    val status: String, // "SUCCESS", "FAILED", "RUNNING", "CANCELLED"
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
    val snapshotId: Long? = null
)

@Entity(tableName = "snapshots")
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String,
    val originalContent: String,
    val snapshotDescription: String
)

@Entity(tableName = "memory_notes")
data class MemoryNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String, // "fact", "installed_pkg", "active_port", "tunnel_url", "environment"
    val keyName: String,
    val valueContent: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val isEditable: Boolean = true
)
