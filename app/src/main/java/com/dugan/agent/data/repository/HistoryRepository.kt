package com.dugan.agent.data.repository

import com.dugan.agent.data.local.ChatHistoryDatabase
import com.dugan.agent.data.local.TranscriptRow
import com.dugan.agent.data.local.toEntry
import com.dugan.agent.domain.model.Speaker
import com.dugan.agent.domain.model.TranscriptEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    database: ChatHistoryDatabase,
) {
    private val dao = database.transcriptDao()

    /** Id of the conversation currently on screen. */
    @Volatile
    var sessionId: Long = System.currentTimeMillis()
        private set

    fun observeSession(): Flow<List<TranscriptEntry>> =
        dao.observeSession(sessionId).map { rows -> rows.map(TranscriptRow::toEntry) }

    suspend fun append(speaker: Speaker, text: String, latencyMs: Long? = null): Long =
        dao.insert(
            TranscriptRow(
                speaker = speaker.name,
                text = text,
                timestampMs = System.currentTimeMillis(),
                latencyMs = latencyMs,
                sessionId = sessionId,
            ),
        )

    /** Last [turns] user/agent exchanges, oldest first, for the prompt context. */
    suspend fun contextWindow(turns: Int = 12): List<TranscriptEntry> =
        dao.contextWindow(turns * 2).map(TranscriptRow::toEntry)

    suspend fun newSession() {
        sessionId = System.currentTimeMillis()
    }

    suspend fun clearSession() = dao.clearSession(sessionId)

    suspend fun clearAll() = dao.clearAll()
}
