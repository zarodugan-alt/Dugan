package com.dugan.agent.data.signaling

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import javax.inject.Inject

/**
 * Registers the Firebase implementation into the multibinding set.
 *
 * Presence of this class in the compiled source set is the only thing that
 * switches signalling on, so the default build has no Firebase dependency at all.
 */
class FirebaseSignalingContributor @Inject constructor() : SignalingContributor {

    override fun create(context: Context): SignalingClient {
        val app = FirebaseApp.initializeApp(context) ?: return LocalOnlySignalingClient
        return FirebaseSignalingClient(FirebaseDatabase.getInstance(app))
    }
}
