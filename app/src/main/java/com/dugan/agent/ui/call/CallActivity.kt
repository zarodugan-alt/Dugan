package com.dugan.agent.ui.call

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dugan.agent.data.repository.SettingsRepository
import com.dugan.agent.ui.theme.AppTheme
import com.dugan.agent.ui.theme.DuganTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Full-screen in-call UI, launched by [com.dugan.agent.domain.telecom.VoiceInCallService].
 *
 * A separate activity rather than a navigation route: Telecom expects the UI to
 * come up on its own task, over the lockscreen, with the screen turned on.
 */
@AndroidEntryPoint
class CallActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // An incoming call arrives with the screen off.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        // Reading one DataStore value synchronously at activity start is the
        // pragmatic choice here: the alternative is a white flash under the
        // wrong theme on every incoming call.
        val theme = runCatching {
            runBlocking { settingsRepository.settings.first().themeId }
        }.getOrDefault("crimson_noir")

        setContent {
            DuganTheme(theme = AppTheme.fromId(theme)) {
                CallScreen(onFinish = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_HANDLE = "handle"
        const val EXTRA_DISPLAY_NAME = "display_name"
    }
}
