package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}

@Dao
interface ActionLogDao {
    @Query("SELECT * FROM action_logs ORDER BY id DESC")
    fun getAllLogs(): Flow<List<ActionLogEntity>>

    @Query("SELECT * FROM action_logs WHERE command LIKE '%' || :query || '%' OR actionName LIKE '%' || :query || '%' ORDER BY id DESC")
    fun searchLogs(query: String): Flow<List<ActionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActionLogEntity): Long

    @Query("DELETE FROM action_logs")
    suspend fun clearAllLogs()
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots ORDER BY id DESC")
    fun getAllSnapshots(): Flow<List<SnapshotEntity>>

    @Query("SELECT * FROM snapshots WHERE id = :id")
    suspend fun getSnapshotById(id: Long): SnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: SnapshotEntity): Long

    @Query("DELETE FROM snapshots WHERE id = :id")
    suspend fun deleteSnapshot(id: Long)
}

@Dao
interface MemoryNoteDao {
    @Query("SELECT * FROM memory_notes ORDER BY category ASC, keyName ASC")
    fun getAllMemoryNotes(): Flow<List<MemoryNoteEntity>>

    @Query("SELECT * FROM memory_notes")
    suspend fun getAllMemoryNotesList(): List<MemoryNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(note: MemoryNoteEntity): Long

    @Query("DELETE FROM memory_notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("DELETE FROM memory_notes")
    suspend fun clearAllNotes()
}

@Database(
    entities = [
        ChatMessageEntity::class,
        ActionLogEntity::class,
        SnapshotEntity::class,
        MemoryNoteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun actionLogDao(): ActionLogDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun memoryNoteDao(): MemoryNoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shen_termutoxic.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
