package com.dugan.agent

import android.app.Application
import com.dugan.agent.data.local.TtsCache
import com.dugan.agent.domain.audio.SileroVadEngine
import com.dugan.agent.domain.audio.SmartTurnEotDetector
import com.dugan.agent.domain.audio.EotController
import com.dugan.agent.domain.model.DuganSettings
import com.dugan.agent.domain.telecom.PhoneAccountRegistrar
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App entry point.
 *
 * Deliberately does almost nothing on the main thread: the model probes, cache
 * pre-warm and PhoneAccount registration all happen off it, and every one of
 * them degrades silently when its optional asset is absent.
 */
@HiltAndroidApp
class DuganApplication : Application() {

    @Inject
    lateinit var registrar: PhoneAccountRegistrar

    @Inject
    lateinit var ttsCache: TtsCache

    @Inject
    lateinit var eotController: EotController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        scope.launch {
            // Optional on-device models. Both return null when their asset is not
            // bundled, in which case the fallbacks stay in place.
            val silero = SileroVadEngine.loadOrNull(assets)
            val smartTurn = SmartTurnEotDetector.loadOrNull(assets)
            eotController.attach(smartTurn)
            android.util.Log.i(
                "Dugan",
                "models: silero=${silero != null} smartTurn=${smartTurn != null}",
            )
        }

        scope.launch {
            registrar.register()
            // Pre-warm the phrases the agent says constantly so the first hit in a
            // call is a cache hit rather than a 300ms network round-trip.
            ttsCache.prewarm(
                warm = TtsCache.WarmPhrases,
                settings = DuganSettings(),
                synthesize = { null }, // populated lazily; no key may exist yet
            )
        }
    }
}
