package com.dugan.agent.data.repository

import com.dugan.agent.data.local.SettingsStore
import com.dugan.agent.domain.model.DuganSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val store: SettingsStore,
) {
    val settings: Flow<DuganSettings> get() = store.settings

    suspend fun update(transform: (DuganSettings) -> DuganSettings) = store.update(transform)
}
