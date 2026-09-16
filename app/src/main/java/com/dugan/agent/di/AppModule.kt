package com.dugan.agent.di

import android.content.Context
import androidx.room.Room
import com.dugan.agent.data.local.ChatHistoryDatabase
import com.dugan.agent.data.local.EncryptedKeyVault
import com.dugan.agent.data.local.KeyVault
import com.dugan.agent.data.local.TranscriptDao
import com.dugan.agent.data.signaling.LocalOnlySignalingClient
import com.dugan.agent.data.signaling.SignalingClient
import com.dugan.agent.data.signaling.SignalingContributor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @IoDispatcher
    fun io(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    // Not `default()`: Dagger generates code from the @Provides method name and
    // `default` is a Java reserved word, so KSP fails with
    // "java.lang.IllegalArgumentException: not a valid name: default".
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ChatHistoryDatabase =
        Room.databaseBuilder(context, ChatHistoryDatabase::class.java, ChatHistoryDatabase.NAME)
            // Personal-use app: a schema change is not worth a migration for a
            // transcript log the user can clear from Settings.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun transcriptDao(database: ChatHistoryDatabase): TranscriptDao = database.transcriptDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VaultModule {
    /** Swap this binding for [com.dugan.agent.data.local.InMemoryKeyVault] in tests. */
    @Binds
    @Singleton
    abstract fun bindVault(impl: EncryptedKeyVault): KeyVault
}

/**
 * VoIP signalling is contributed, not hard-wired.
 *
 * The default build ships [LocalOnlySignalingClient] -- no Firebase project, no
 * google-services.json, no VoIP. Building with `-Pdugan.firebase=true` adds the
 * `src/firebase` source set, whose contributor wins because it is the only
 * element in the set.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SignalingModule {
    @Multibinds
    abstract fun contributors(): Set<SignalingContributor>
}

@Module
@InstallIn(SingletonComponent::class)
object SignalingProviderModule {
    @Provides
    @Singleton
    fun signaling(
        @ApplicationContext context: Context,
        contributors: Set<@JvmSuppressWildcards SignalingContributor>,
    ): SignalingClient = contributors.firstOrNull()?.create(context) ?: LocalOnlySignalingClient
}
