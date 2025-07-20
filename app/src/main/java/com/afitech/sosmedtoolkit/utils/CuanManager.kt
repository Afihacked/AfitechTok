package com.afitech.sosmedtoolkit.utils

import android.content.Context
import com.google.android.gms.ads.*

class CuanManager {

    // Inisialisasi SDK AdMob
    fun initializeAdMob(context: Context) {
        MobileAds.initialize(context) {
            // Callback opsional
        }
    }

    // Memuat iklan banner biasa (adUnitId dan adSize diset di XML)
    fun loadAd(adView: AdView) {
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    // Membersihkan AdView
    fun destroyAd(adView: AdView) {
        adView.destroy()
    }
}
