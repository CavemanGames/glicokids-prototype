package com.glicokids.prototype.di

import com.glicokids.prototype.data.local.EncryptedStorage
import com.glicokids.prototype.domain.repository.StorageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * As três camadas de persistência do handoff §8.1:
 *  - [EncryptedStorage] (EncryptedSharedPreferences) — só o `parent_pin`;
 *  - `AppPreferences` (SharedPreferences comum) — preferências e parâmetros clínicos;
 *  - `GlicoKidsDbHelper` (SQLiteOpenHelper) — séries históricas.
 *
 * As duas últimas são `@Singleton` com `@Inject constructor`, então o Hilt as
 * fornece sem precisar de `@Provides` aqui.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindStorageRepository(storage: EncryptedStorage): StorageRepository
}
