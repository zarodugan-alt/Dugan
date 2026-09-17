package com.dugan.agent.domain.model

/**
 * Typed failures so the UI can pick a recovery action instead of showing a raw
 * exception string.
 */
sealed class PipelineError(open val message: String) {
    data class MissingKey(val provider: ApiProvider) :
        PipelineError("${provider.displayName} API key is not configured")

    data class QuotaExhausted(val provider: ApiProvider, override val message: String) :
        PipelineError(message)

    data class Network(override val message: String) : PipelineError(message)

    data class Audio(override val message: String) : PipelineError(message)

    data class Provider(val provider: ApiProvider, override val message: String) :
        PipelineError(message)

    data class Cancelled(override val message: String = "cancelled") : PipelineError(message)
}
