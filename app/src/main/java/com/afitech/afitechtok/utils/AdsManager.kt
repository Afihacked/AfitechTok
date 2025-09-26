package com.afitech.afitechtok.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.afitech.afitechtok.ui.helpers.AnalyticsLogger
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.firebase.analytics.FirebaseAnalytics

class AdsManager(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    init {
        MobileAds.initialize(context)
    }

    fun loadInterstitialAd(adUnitId: String, onLoaded: (() -> Unit)? = null) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d("AdsManager", "Interstitial loaded")
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                    Log.e("AdsManager", "Interstitial fail: ${adError.message}")
                }
            }
        )
    }

    fun loadRewardedAd(adUnitId: String, onLoaded: (() -> Unit)? = null) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.d("AdsManager", "Rewarded loaded")
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                    Log.e("AdsManager", "Rewarded fail: ${adError.message}")
                }
            }
        )
    }

    fun showInterstitialAd(adUnitId: String, onAdComplete: () -> Unit) {
        if (!context.areAdsEnabled()) {
            onAdComplete()
            return
        }

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            onAdComplete()
                        }

                        override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                            onAdComplete()
                        }
                    }
                    ad.show((context as? android.app.Activity) ?: return)
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("AdsManager", "Interstitial failed: ${adError.message}")
                    onAdComplete()
                }
            }
        )
    }


    fun showRewardedAd(
        adUnitId: String,
        onResult: (Boolean) -> Unit
    ) {
        val activity = context as? FragmentActivity ?: return
        val ad = rewardedAd
        if (ad == null) {
            onResult(true)
            return
        }

        var isRewardEarned = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadRewardedAd(adUnitId)
                if (!isRewardEarned) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        Toast.makeText(
                            context,
                            "❗ Tonton iklan sampai selesai untuk melanjutkan unduhan.",
                            Toast.LENGTH_LONG
                        ).show()
                    }, 500)
                }
                onResult(isRewardEarned)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                onResult(true)
            }

            override fun onAdShowedFullScreenContent() {
                AnalyticsLogger.logAdDisplayed(FirebaseAnalytics.getInstance(context), "rewarded")
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d("AdsManager", "User reward: ${rewardItem.type} ${rewardItem.amount}")
            isRewardEarned = true
            AnalyticsLogger.logRewardEarned(
                FirebaseAnalytics.getInstance(context),
                rewardItem.type,
                rewardItem.amount
            )
        }
    }
}
