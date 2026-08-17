package com.glicokids.prototype.di

import android.content.Context
import android.location.LocationManager
import com.glicokids.prototype.BuildConfig
import com.glicokids.prototype.data.location.AndroidGeocoder
import com.glicokids.prototype.data.location.FusedLocationProvider
import com.glicokids.prototype.domain.repository.GeocodingRepository
import com.glicokids.prototype.domain.repository.LocationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Module 7 — location and geocoding. Kept apart from [StorageModule] and [CommunicationModule]
 * for the same reason those two are split from each other: distinct responsibility, distinct
 * module.
 *
 * The two `@Provides` functions below are new to this project — every other binding so far
 * only needed `@Binds`. `@Binds` tells Hilt which existing binding to hand back for an
 * interface it is already able to build (it still needs an `@Inject constructor` on the concrete
 * class, which [FusedLocationProvider] and [AndroidGeocoder] both have). [FusedLocationProviderClient]
 * and [LocationManager] are, respectively, a Play Services class and a system service class —
 * neither has a constructor Hilt could call, so something has to say how to obtain an instance.
 * That is what `@Provides` is for.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    @Singleton
    abstract fun bindLocationProvider(provider: FusedLocationProvider): LocationProvider

    @Binds
    @Singleton
    abstract fun bindGeocodingRepository(geocoder: AndroidGeocoder): GeocodingRepository

    companion object {

        @Provides
        @Singleton
        fun provideFusedLocationProviderClient(
            @ApplicationContext context: Context
        ): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

        @Provides
        @Singleton
        fun provideLocationManager(
            @ApplicationContext context: Context
        ): LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        /** Module 7 (map screen) — [BuildConfig.MAPS_API_KEY] itself is the build's own
         * missing-key sentinel already ("MISSING_MAPS_API_KEY" — see `app/build.gradle.kts`),
         * so this needs no extra fallback of its own; [AlertMapViewModel][com.glicokids.prototype.presentation.parents.AlertMapViewModel]
         * is what compares it against that sentinel. */
        @Provides
        @Named("mapsApiKey")
        fun provideMapsApiKey(): String = BuildConfig.MAPS_API_KEY
    }
}
