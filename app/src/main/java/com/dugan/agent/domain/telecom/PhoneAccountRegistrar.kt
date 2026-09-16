package com.dugan.agent.domain.telecom

import com.dugan.agent.util.AgentLog
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers the self-managed PhoneAccount that lets the Telecom framework route
 * VoIP calls to us, and requests the default-dialer role that makes
 * [VoiceInCallService] the system's call UI.
 */
@Singleton
class PhoneAccountRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val accountHandle: PhoneAccountHandle
        get() = PhoneAccountHandle(
            ComponentName(context, VoiceConnectionService::class.java),
            ACCOUNT_ID,
        )

    /**
     * Idempotent. Safe to call on every app start; re-registering the same
     * handle replaces the previous account rather than duplicating it.
     */
    fun register() {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return
        runCatching {
            val account = PhoneAccount.builder(accountHandle, "Voice Agent")
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                // VoIP only -- we must not claim the ability to place carrier calls.
                .setIsSelfManaged(true)
                .build()
            telecom.registerPhoneAccount(account)
        }.onFailure { AgentLog.w(TAG, "PhoneAccount registration failed: ${it.message}") }
    }

    fun isDefaultDialer(): Boolean {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            ?: return false
        return runCatching { telecom.defaultDialerPackage == context.packageName }.getOrDefault(false)
    }

    /** Intent to hand to `startActivityForResult`; null when the role is unavailable. */
    fun requestDefaultDialerIntent(): Intent? {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) return null
        if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    }

    fun isRoleHeld(): Boolean {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return runCatching { roleManager.isRoleHeld(RoleManager.ROLE_DIALER) }.getOrDefault(false)
    }

    /** Build version guard kept explicit so the restriction is visible at the call site. */
    val supportsSelfManagedCalls: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    companion object {
        const val ACCOUNT_ID = "VoiceAgent"
        private const val TAG = "PhoneAccount"
    }
}
