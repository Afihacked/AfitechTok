package com.afitech.afitechtok.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdsManager(private val ctx: Context) {

    private val context: Context = ctx.applicationContext

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    @Volatile
    private var interstitialReady = false

    @Volatile
    private var rewardedReady = false

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        try {
            if (context.areAdsEnabled()) {
                MobileAds.initialize(context) {
                    Log.d("AdsManager", "AdMob initialized")
                }
            } else {
                Log.d("AdsManager", "Ads disabled — skipping MobileAds.initialize()")
            }
        } catch (t: Throwable) {
            Log.w("AdsManager", "MobileAds.initialize failed: ${t.message}")
        }
    }

    // =========================
    // Banner Ads
    // =========================

    fun loadBanner(adView: AdView, fallbackContainer: FrameLayout) {

        if (!context.areAdsEnabled()) {
            adView.visibility = View.GONE
            fallbackContainer.removeAllViews()
            fallbackContainer.visibility = FrameLayout.GONE
            return
        }

        val adRequest = AdRequest.Builder().build()

        adView.adListener = object : AdListener() {

            override fun onAdLoaded() {
                Log.d("AdsManager", "Banner loaded")
                mainHandler.post {
                    adView.visibility = View.VISIBLE
                    fallbackContainer.removeAllViews()
                    fallbackContainer.visibility = FrameLayout.GONE
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e("AdsManager", "Banner failed: ${error.message}")
                mainHandler.post {
                    adView.visibility = View.GONE
                }
            }
        }

        try {
            adView.loadAd(adRequest)
        } catch (t: Throwable) {
            Log.e("AdsManager", "Banner load exception: ${t.message}")
        }
    }

    fun destroyBanner(adView: AdView) {
        try {
            adView.destroy()
        } catch (_: Throwable) {
        }
    }

    // =========================
    // Interstitial
    // =========================

    @Synchronized
    fun loadInterstitialAd(adUnitId: String) {

        if (!context.areAdsEnabled()) {
            interstitialReady = false
            return
        }

        interstitialReady = false

        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {

                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    interstitialReady = true
                    Log.d("AdsManager", "Interstitial loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    interstitialReady = false
                    Log.e("AdsManager", "Interstitial failed: ${error.message}")
                }
            })
    }

    fun showInterstitialAd(
        onAdComplete: () -> Unit,
        onNotShown: (() -> Unit)? = null
    ) {

        if (!context.areAdsEnabled()) {
            onAdComplete()
            return
        }

        val activity = ctx as? Activity

        if (interstitialAd != null &&
            activity != null &&
            !activity.isFinishing &&
            !activity.isDestroyed
        ) {

            interstitialAd?.fullScreenContentCallback =
                object : FullScreenContentCallback() {

                    override fun onAdDismissedFullScreenContent() {
                        interstitialAd = null
                        interstitialReady = false
                        onAdComplete()
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        interstitialAd = null
                        interstitialReady = false
                        onAdComplete()
                    }
                }

            try {
                interstitialAd?.show(activity)
            } catch (_: Throwable) {
                onAdComplete()
            }

        } else {
            onNotShown?.invoke() ?: onAdComplete()
        }
    }

    fun isInterstitialReady(): Boolean = interstitialReady

    // =========================
    // Rewarded Ads
    // =========================

    @Synchronized
    fun loadRewardedAd(adUnitId: String) {

        if (!context.areAdsEnabled()) {
            rewardedReady = false
            return
        }

        rewardedReady = false

        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {

                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    rewardedReady = true
                    Log.d("AdsManager", "Rewarded loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    rewardedReady = false
                    Log.e("AdsManager", "Rewarded failed: ${error.message}")
                }
            })
    }

    fun showRewardedAd(
        onResult: (Boolean) -> Unit,
        onNotShown: (() -> Unit)? = null
    ) {

        if (!context.areAdsEnabled()) {
            onResult(true)
            return
        }

        val activity = ctx as? Activity

        if (rewardedAd != null &&
            activity != null &&
            !activity.isFinishing &&
            !activity.isDestroyed
        ) {

            var rewardGiven = false

            rewardedAd?.fullScreenContentCallback =
                object : FullScreenContentCallback() {

                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        rewardedReady = false

                        if (!rewardGiven) {
                            onResult(false)
                        }
                    }

                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        rewardedAd = null
                        rewardedReady = false
                        onResult(false)
                    }
                }

            try {

                rewardedAd?.show(activity) {
                    rewardGiven = true
                    onResult(true)
                }

            } catch (_: Throwable) {

                rewardedAd = null
                rewardedReady = false
                onResult(false)
            }

        } else {

            onNotShown?.invoke()
        }
    }

    fun isRewardedReady(): Boolean = rewardedReady
}