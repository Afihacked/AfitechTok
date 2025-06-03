package com.afitech.sosmedtoolkit.utils

import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

class CuanManager {

    // Fungsi untuk menginisialisasi AdMob
    fun initializeAdMob(context: Context) {
        MobileAds.initialize(context) {
            // Callback setelah inisialisasi selesai
        }
    }

    // Fungsi untuk memuat iklan pada AdView
    fun loadAd(adView: AdView) {
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    // Fungsi untuk menghancurkan iklan ketika tidak digunakan lagi
    fun destroyAd(adView: AdView) {
        adView.destroy() // Membersihkan sumber daya yang digunakan oleh AdView
    }
}
