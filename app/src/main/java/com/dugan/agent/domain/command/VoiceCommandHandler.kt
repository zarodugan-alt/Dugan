package com.dugan.agent.domain.command

/**
 * Seam between the agent loop and the Telecom layer.
 *
 * The orchestrator depends on this interface rather than on CallManager, which
 * keeps the pipeline free of Android Telecom types and makes it testable.
 */
interface VoiceCommandHandler {
    /** @return true when the transcript was consumed as a command. */
    suspend fun handle(transcript: String): Boolean
}

/** Default when no call handling is wired up (e.g. in unit tests). */
object NoOpVoiceCommandHandler : VoiceCommandHandler {
    override suspend fun handle(transcript: String): Boolean = false
}
