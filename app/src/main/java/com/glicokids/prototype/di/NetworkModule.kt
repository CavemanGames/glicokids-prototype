package com.glicokids.prototype.di

import com.glicokids.prototype.data.remote.AndroidHttpClient
import com.glicokids.prototype.data.remote.OpenFoodFactsFoodSearchRepository
import com.glicokids.prototype.data.remote.OverpassNearbyHealthPlacesRepository
import com.glicokids.prototype.domain.repository.FoodSearchRepository
import com.glicokids.prototype.domain.repository.HttpClient
import com.glicokids.prototype.domain.repository.NearbyHealthPlacesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Module 7 — the HTTP client and the two repositories built on it (nearby health places, food
 * search). Kept apart from [LocationModule]: that module is about where the device is, this one
 * is about what the app may ask the web once it knows.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindHttpClient(client: AndroidHttpClient): HttpClient

    @Binds
    @Singleton
    abstract fun bindNearbyHealthPlacesRepository(
        repository: OverpassNearbyHealthPlacesRepository
    ): NearbyHealthPlacesRepository

    @Binds
    @Singleton
    abstract fun bindFoodSearchRepository(
        repository: OpenFoodFactsFoodSearchRepository
    ): FoodSearchRepository
}
