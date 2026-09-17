package com.dugan.agent.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.dugan.agent.domain.model.Speaker
import com.dugan.agent.domain.model.TranscriptEntry
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transcript")
data class TranscriptRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val speaker: String,
    val text: String,
    val timestampMs: Long,
    val latencyMs: Long?,
    /** Session id groups a single conversation; lets us clear one call at a time. */
    val sessionId: Long,
)

@Dao
interface TranscriptDao {

    @Query("SELECT * FROM transcript WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun observeSession(sessionId: Long): Flow<List<TranscriptRow>>

    @Query("SELECT * FROM transcript ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<TranscriptRow>

    /** Last N finalized turns, oldest first -- this is the LLM context window. */
    @Query(
        "SELECT * FROM (SELECT * FROM transcript ORDER BY timestampMs DESC LIMIT :limit) " +
            "ORDER BY timestampMs ASC",
    )
    suspend fun contextWindow(limit: Int): List<TranscriptRow>

    @Insert
    suspend fun insert(row: TranscriptRow): Long

    @Query("DELETE FROM transcript WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: Long)

    @Query("DELETE FROM transcript")
    suspend fun clearAll()

    @Delete
    suspend fun delete(row: TranscriptRow)
}

@Database(entities = [TranscriptRow::class], version = 1, exportSchema = false)
abstract class ChatHistoryDatabase : RoomDatabase() {
    abstract fun transcriptDao(): TranscriptDao

    companion object {
        const val NAME = "dugan_history.db"
    }
}

fun TranscriptRow.toEntry(): TranscriptEntry = TranscriptEntry(
    id = id,
    speaker = runCatching { Speaker.valueOf(speaker) }.getOrDefault(Speaker.System),
    text = text,
    timestampMs = timestampMs,
    latencyMs = latencyMs,
)
