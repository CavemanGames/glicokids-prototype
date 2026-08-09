package com.glicokids.prototype.di

import com.glicokids.prototype.data.sms.AndroidSmsGateway
import com.glicokids.prototype.domain.repository.SmsGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Module 6 — communication channels (SMS, e-mail, notifications). Kept apart from
 * [StorageModule], which is documented as persistence-only.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CommunicationModule {

    @Binds
    @Singleton
    abstract fun bindSmsGateway(gateway: AndroidSmsGateway): SmsGateway
}
