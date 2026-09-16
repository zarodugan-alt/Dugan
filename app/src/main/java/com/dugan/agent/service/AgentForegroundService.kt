package com.dugan.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dugan.agent.MainActivity
import com.dugan.agent.R
import com.dugan.agent.domain.model.AgentCommand
import com.dugan.agent.domain.model.AgentPhase
import com.dugan.agent.domain.orchestrator.VoiceAgentOrchestrator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the microphone and the pipeline alive for the duration of a call.
 *
 * Without a `microphone|phoneCall` foreground service the OS is free to kill the
 * capture loop the moment the screen turns off, which on a phone call is almost
 * immediately.
 */
@AndroidEntryPoint
class AgentForegroundService : android.app.Service() {

    @Inject
    lateinit var orchestrator: VoiceAgentOrchestrator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                orchestrator.dispatch(AgentCommand.Stop)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val notification = buildNotification(getString(R.string.notif_agent_active))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        scope.launch {
            orchestrator.state.collect { state ->
                val text = when (state.phase) {
                    AgentPhase.Listening -> "Listening…"
                    AgentPhase.Thinking -> "Thinking…"
                    AgentPhase.Speaking -> "Speaking…"
                    AgentPhase.Paused -> "Paused"
                    AgentPhase.Error -> "Error: ${state.errorMessage.orEmpty()}"
                    AgentPhase.Idle -> getString(R.string.notif_agent_active)
                }
                updateNotification(buildNotification(text))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AgentForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_AGENT)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setColor(getColor(R.color.notification_accent))
            .addAction(0, getString(R.string.notif_action_end), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun createChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val agent = NotificationChannel(
            CHANNEL_AGENT,
            getString(R.string.agent_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.agent_channel_desc)
            setShowBadge(false)
        }
        val calls = NotificationChannel(
            CHANNEL_CALLS,
            getString(R.string.call_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = getString(R.string.call_channel_desc) }
        manager.createNotificationChannel(agent)
        manager.createNotificationChannel(calls)
    }

    companion object {
        const val ACTION_STOP = "com.dugan.agent.action.STOP"
        private const val NOTIFICATION_ID = 0x0D0A
        private const val CHANNEL_AGENT = "agent_activity"
        private const val CHANNEL_CALLS = "calls"

        fun start(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AgentForegroundService::class.java)) }
        }
    }
}
