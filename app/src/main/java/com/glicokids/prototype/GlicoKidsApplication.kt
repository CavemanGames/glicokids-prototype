package com.glicokids.prototype

import android.app.Application
import com.glicokids.prototype.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GlicoKidsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Module 6 — the notification channel must exist before the first notification
        // is posted; creating it here, once, covers every entry point that could post one.
        NotificationHelper.createChannel(this)
    }
}
