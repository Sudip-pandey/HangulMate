package com.hangulmate

import android.app.Application
import com.hangulmate.di.SeedOnFirstLaunch
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HangulMateApp : Application() {

    @Inject
    lateinit var seedOnFirstLaunch: SeedOnFirstLaunch

    override fun onCreate() {
        super.onCreate()
        // No-op access forces Hilt to create the singleton, which starts the
        // one-time seed import (guarded by the "seed_imported_v1" flag).
        seedOnFirstLaunch.state
    }
}
