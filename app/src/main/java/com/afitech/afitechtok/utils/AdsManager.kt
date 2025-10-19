package com.afitech.afitechtok.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener

// Tambahkan ini
import com.afitech.afitechtok.utils.areAdsEnabled

class AdsManager(private val ctx: Context) {

    private val context: Context = ctx.applicationContext
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    @Volatile private var interstitialReady = false
    @Volatile private var rewardedReady = false
    @Volatile private var startAppBannerView: android.view.View? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        try {
            if (context.areAdsEnabled()) {
                MobileAds.initialize(context) { status ->
                    Log.d("AdsManager", "MobileAds initialized: $status")
                }
            } else {
                Log.d("AdsManager", "Ads disabled — skipping MobileAds.initialize()")
            }
        } catch (t: Throwable) {
            Log.w("AdsManager", "MobileAds.initialize failed: ${t.message}")
        }
    }

    // -----------------------
    // Banner (AdMob → Start.io fallback)
    // -----------------------
    fun loadBanner(adView: AdView, fallbackContainer: FrameLayout) {
        if (!context.areAdsEnabled()) {
            Log.d("AdsManager", "Ads disabled — banner not loaded.")
            adView.visibility = android.view.View.GONE
            fallbackContainer.removeAllViews()
            fallbackContainer.visibility = FrameLayout.GONE
            return
        }

        val adRequest = AdRequest.Builder().build()
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdsManager", "AdMob banner loaded ✅")
                mainHandler.post {
                    adView.visibility = android.view.View.VISIBLE
                    fallbackContainer.removeAllViews()
                    fallbackContainer.visibility = FrameLayout.GONE
                    startAppBannerView = null
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e("AdsManager", "AdMob banner failed ❌: ${error.message}")
                mainHandler.post { adView.visibility = android.view.View.GONE }
            }
        }

        try {
            adView.loadAd(adRequest)
        } catch (t: Throwable) {
            Log.e("AdsManager", "adView.loadAd exception: ${t.message}")
        }
    }

    fun destroyBanner(adView: AdView) {
        try { adView.destroy() } catch (_: Throwable) {}
        mainHandler.post {
            try {
                startAppBannerView?.let { view ->
                    (view.parent as? ViewGroup)?.removeView(view)
                }
            } catch (_: Throwable) {}
            startAppBannerView = null
        }
    }

    // -----------------------
    // Interstitial
    // -----------------------
    @Synchronized
    fun loadInterstitialAd(adUnitId: String) {
        if (!context.areAdsEnabled()) {
            Log.d("AdsManager", "Ads disabled — interstitial not loaded.")
            interstitialReady = false
            return
        }

        interstitialReady = false
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                interstitialReady = true
                Log.d("AdsManager", "Interstitial loaded ✅")
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                interstitialAd = null
                interstitialReady = false
                Log.e("AdsManager", "Interstitial failed ❌: ${error.message}")
            }
        })
    }

    fun showInterstitialAd(
        onAdComplete: () -> Unit,
        allowFallback: Boolean = true,
        onNotShown: (() -> Unit)? = null
    ) {
        if (!context.areAdsEnabled()) {
            Log.d("AdsManager", "Ads disabled — skipping interstitial.")
            onAdComplete()
            return
        }

        val activity = ctx as? Activity
        if (interstitialAd != null && activity != null && !activity.isFinishing && !activity.isDestroyed) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    interstitialReady = false
                    onAdComplete()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    interstitialReady = false
                    Log.e("AdsManager", "Interstitial failed ❌: ${error.message}")
                    onAdComplete()
                }
            }
            try {
                interstitialAd?.show(activity)
            } catch (t: Throwable) {
                Log.e("AdsManager", "showInterstitial exception: ${t.message}")
                onAdComplete()
            }
        } else {
            Log.d("AdsManager", "Interstitial not ready.")
            onAdComplete()
        }
    }

    fun isInterstitialReady(): Boolean = interstitialReady

    // -----------------------
    // Rewarded
    // -----------------------
    @Synchronized
    fun loadRewardedAd(adUnitId: String) {
        if (!context.areAdsEnabled()) {
            Log.d("AdsManager", "Ads disabled — rewarded not loaded.")
            rewardedReady = false
            return
        }

        rewardedReady = false
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                rewardedReady = true
                Log.d("AdsManager", "Rewarded loaded ✅")
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                rewardedAd = null
                rewardedReady = false
                Log.e("AdsManager", "Rewarded failed ❌: ${error.message}")
            }
        })
    }

    fun showRewardedAd(
        onResult: (Boolean) -> Unit,
        allowFallback: Boolean = true,
        onNotShown: (() -> Unit)? = null
    ) {
        if (!context.areAdsEnabled()) {
            Log.d("AdsManager", "Ads disabled — skipping rewarded.")
            onResult(true) // anggap sukses tanpa iklan
            return
        }

        val activity = ctx as? Activity
        if (rewardedAd != null && activity != null && !activity.isFinishing && !activity.isDestroyed) {
            var rewardGiven = false
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    rewardedReady = false
                    if (!rewardGiven) onResult(false)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    rewardedAd = null
                    rewardedReady = false
                    Log.e("AdsManager", "Rewarded show failed ❌: ${error.message}")
                    onResult(false)
                }
            }

            try {
                rewardedAd?.show(activity) { rewardItem ->
                    Log.d("AdsManager", "Reward earned: ${rewardItem.amount}")
                    rewardGiven = true
                    onResult(true)
                }
            } catch (t: Throwable) {
                Log.e("AdsManager", "showRewardedAd exception: ${t.message}")
                rewardedAd = null
                rewardedReady = false
                onResult(false)
            }
        } else {
            Log.d("AdsManager", "Rewarded not ready.")
            onNotShown?.invoke()
        }
    }

    fun isRewardedReady(): Boolean = rewardedReady
}
