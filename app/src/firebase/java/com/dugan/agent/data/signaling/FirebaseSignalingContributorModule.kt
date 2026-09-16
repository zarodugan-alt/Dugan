package com.dugan.agent.data.signaling

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseSignalingContributorModule {

    @Binds
    @IntoSet
    abstract fun bind(impl: FirebaseSignalingContributor): SignalingContributor
}
