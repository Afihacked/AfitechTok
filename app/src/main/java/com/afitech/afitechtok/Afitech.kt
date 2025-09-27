package com.afitech.afitechtok

import android.app.Application
import com.startapp.sdk.adsbase.StartAppSDK

class Afitech : Application() {
    override fun onCreate() {
        super.onCreate()

        // ✅ Init Start.io dengan cara baru
        StartAppSDK.initParams(this, "208775679")

        // ✅ Matikan splash bawaan langsung dari config (parameter ketiga = false)
        StartAppSDK.setTestAdsEnabled(false) // kalau perlu test ads
    }
}

