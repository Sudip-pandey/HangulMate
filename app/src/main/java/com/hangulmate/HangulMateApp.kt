package com.hangulmate

import android.app.Application
import com.hangulmate.data.preferences.SettingsRepository
import com.hangulmate.di.SeedOnFirstLaunch
import com.hangulmate.speech.TtsManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class HangulMateApp : Application() {

    @Inject
    lateinit var seedOnFirstLaunch: SeedOnFirstLaunch

    @Inject
    lateinit var ttsManager: TtsManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /**
     * Application lifetime scope for startup wiring only (seed import,
     * TTS settings). Cancelled never — the process owns it.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // No-op access forces Hilt to create the singleton, which starts the
        // one-time seed import (guarded by the "seed_imported_v2" flag).
        seedOnFirstLaunch.state
        // Apply the persisted TTS rate/pitch to the engine at startup, so
        // speech uses the saved values after a restart — not the 1.0f
        // defaults. The engine stores them until it finishes initialising,
        // and re-applies live whenever the settings change later.
        appScope.launch {
            settingsRepository.ttsRate.collect { ttsManager.setRate(it) }
        }
        appScope.launch {
            settingsRepository.ttsPitch.collect { ttsManager.setPitch(it) }
        }
    }
}
