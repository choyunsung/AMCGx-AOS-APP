package com.amcg.mcg.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AMCGxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize app-wide resources
    }
}
