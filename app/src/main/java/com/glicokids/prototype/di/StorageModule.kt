package com.glicokids.prototype.di

import com.glicokids.prototype.data.local.EncryptedStorage
import com.glicokids.prototype.domain.repository.StorageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The three persistence layers, split by how sensitive and how long-lived the data is:
 *  - [EncryptedStorage] (EncryptedSharedPreferences) — `parent_pin` only;
 *  - `AppPreferences` (plain SharedPreferences) — settings and clinical parameters;
 *  - `GlicoKidsDbHelper` (SQLiteOpenHelper) — historical series.
 *
 * The last two are `@Singleton` with an `@Inject constructor`, so Hilt provides
 * them without needing `@Provides` here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindStorageRepository(storage: EncryptedStorage): StorageRepository
}
