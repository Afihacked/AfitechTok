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

class AdsManager(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    // readiness flags
    @Volatile
    private var interstitialReady = false

    @Volatile
    private var rewardedReady = false

    // keep reference to StartApp banner view so we can remove it safely
    @Volatile
    private var startAppBannerView: android.view.View? = null

    init {
        try {
            MobileAds.initialize(context)
        } catch (t: Throwable) {
            Log.w("AdsManager", "MobileAds.initialize failed: ${t.message}")
        }
    }

    // -----------------------
    // Banner (AdMob -> fallback Start.io)
    // -----------------------
    fun loadBanner(adView: AdView, fallbackContainer: FrameLayout) {
        val adRequest = AdRequest.Builder().build()

        // set listener (this will replace any previous listener on this adView)
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdsManager", "AdMob banner loaded ✅")
                try {
                    adView.visibility = android.view.View.VISIBLE
                } catch (_: Throwable) {}

                // remove any startapp fallback if present
                try {
                    Handler(Looper.getMainLooper()).post {
                        try {
                            fallbackContainer.removeAllViews()
                            fallbackContainer.visibility = FrameLayout.GONE
                            startAppBannerView = null
                        } catch (_: Throwable) { /* ignore */ }
                    }
                } catch (_: Throwable) { /* ignore */ }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e("AdsManager", "AdMob banner failed ❌: ${error.message}")
                try {
                    adView.visibility = android.view.View.GONE
                } catch (_: Throwable) {}

                val activity = (context as? Activity)
                if (activity == null || activity.isFinishing || activity.isDestroyed) {
                    Log.w("AdsManager", "Skip StartApp banner — invalid Activity context")
                    return
                }

                // Pastikan container sudah attach
                if (!fallbackContainer.isAttachedToWindow) {
                    Log.w("AdsManager", "Skip StartApp banner — container not attached")
                    return
                }

                // Jalankan sedikit tertunda agar layout stabil
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        // Jangan lanjut kalau Activity sudah ditutup
                        if (activity.isFinishing || activity.isDestroyed) return@postDelayed

                        // Pastikan container masih attach
                        if (!fallbackContainer.isAttachedToWindow) {
                            Log.w("AdsManager", "Skip StartApp banner — container detached")
                            return@postDelayed
                        }

                        // Bersihkan banner lama
                        fallbackContainer.removeAllViews()
                        startAppBannerView = null

                        val startBanner = Banner(activity, object : BannerListener {
                            override fun onReceiveAd(view: android.view.View) {
                                Log.d("AdsManager", "Start.io banner loaded ✅")
                            }

                            override fun onFailedToReceiveAd(view: android.view.View) {
                                Log.e("AdsManager", "Start.io banner failed ❌")
                            }

                            override fun onImpression(view: android.view.View) {}
                            override fun onClick(view: android.view.View) {}
                        })

                        val lp = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        )
                        startBanner.layoutParams = lp

                        fallbackContainer.addView(startBanner)
                        fallbackContainer.visibility = FrameLayout.VISIBLE
                        startAppBannerView = startBanner
                    } catch (t: Throwable) {
                        Log.e("AdsManager", "Failed attach StartApp banner safely: ${t.message}")
                        try {
                            fallbackContainer.removeAllViews()
                            fallbackContainer.visibility = FrameLayout.GONE
                        } catch (_: Throwable) {}
                        startAppBannerView = null
                    }
                }, 300) // delay 300ms
            }

        }

        try {
            adView.loadAd(adRequest)
        } catch (t: Throwable) {
            Log.e("AdsManager", "adView.loadAd exception: ${t.message}")
            // If AdMob load itself throws, try to attach StartApp fallback safely:
            try {
                val activity = (context as? Activity)
                if (activity != null) {
                    activity.runOnUiThread {
                        try {
                            fallbackContainer.removeAllViews()
                            val startBanner = Banner(activity, object : BannerListener {
                                override fun onReceiveAd(view: android.view.View) { Log.d("AdsManager", "Start.io banner loaded (fallback) ✅") }
                                override fun onFailedToReceiveAd(view: android.view.View) { Log.e("AdsManager", "Start.io banner failed (fallback) ❌") }
                                override fun onImpression(view: android.view.View) {}
                                override fun onClick(view: android.view.View) {}
                            })
                            val lp = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                            )
                            startBanner.layoutParams = lp
                            fallbackContainer.addView(startBanner)
                            fallbackContainer.visibility = FrameLayout.VISIBLE
                            startAppBannerView = startBanner
                        } catch (_: Throwable) {
                            try { fallbackContainer.removeAllViews(); fallbackContainer.visibility = FrameLayout.GONE } catch (_: Throwable) {}
                            startAppBannerView = null
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    fun destroyBanner(adView: AdView) {
        // destroy AdMob view
        try {
            adView.destroy()
        } catch (t: Throwable) {
            Log.w("AdsManager", "destroyBanner (AdMob) failed: ${t.message}")
        }

        // also remove StartApp banner view safely on main thread
        try {
            Handler(Looper.getMainLooper()).post {
                try {
                    startAppBannerView?.let { view ->
                        try {
                            val parent = view.parent as? ViewGroup
                            parent?.removeView(view)
                        } catch (_: Throwable) { /* ignore */ }
                    }
                } catch (t: Throwable) {
                    Log.w("AdsManager", "Failed to remove StartApp banner view: ${t.message}")
                } finally {
                    startAppBannerView = null
                }
            }
        } catch (t: Throwable) {
            Log.w("AdsManager", "destroyBanner (StartApp) failed: ${t.message}")
            startAppBannerView = null
        }
    }

    // -----------------------
    // Interstitial (preload + show)
    // -----------------------
    fun loadInterstitialAd(adUnitId: String) {
        interstitialReady = false
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("AdsManager", "Interstitial loaded ✅")
                    interstitialAd = ad
                    interstitialReady = true
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("AdsManager", "Interstitial failed ❌: ${adError.message}")
                    interstitialAd = null
                    interstitialReady = false
                }
            }
        )
    }

    // shows interstitial. If `allowFallback` == false, it will not show Start.io automatically;
    // instead it will call onNotShown callback so caller can decide.
    fun showInterstitialAd(
        onAdComplete: () -> Unit,
        allowFallback: Boolean = true,
        onNotShown: (() -> Unit)? = null
    ) {
        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    interstitialReady = false
                    onAdComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    interstitialReady = false
                    Log.e("AdsManager", "Interstitial show failed ❌: ${adError.message}")
                    if (allowFallback) showStartIoInterstitial(onAdComplete) else onNotShown?.invoke()
                }

                override fun onAdShowedFullScreenContent() {
                    // no-op
                }
            }
            try {
                interstitialAd?.show(context as Activity)
            } catch (t: Throwable) {
                Log.e("AdsManager", "showInterstitial exception: ${t.message}")
                interstitialAd = null
                interstitialReady = false
                if (allowFallback) showStartIoInterstitial(onAdComplete) else onNotShown?.invoke()
            }
        } else {
            Log.d("AdsManager", "Interstitial not ready -> ${if (allowFallback) "fallback" else "no fallback"}")
            if (allowFallback) showStartIoInterstitial(onAdComplete) else onNotShown?.invoke()
        }
    }

    fun isInterstitialReady(): Boolean = interstitialReady

    private fun showStartIoInterstitial(onAdComplete: () -> Unit) {
        try {
            val startAppAd = StartAppAd(context)
            startAppAd.showAd(object : AdDisplayListener {
                override fun adHidden(ad: Ad?) {
                    onAdComplete()
                }

                override fun adDisplayed(ad: Ad?) {
                    Log.d("AdsManager", "Start.io interstitial displayed")
                }

                override fun adClicked(ad: Ad?) {
                    Log.d("AdsManager", "Start.io interstitial clicked")
                }

                override fun adNotDisplayed(ad: Ad?) {
                    Log.e("AdsManager", "Start.io interstitial not displayed ❌")
                    onAdComplete()
                }
            })
        } catch (t: Throwable) {
            Log.e("AdsManager", "showStartIoInterstitial error: ${t.message}")
            onAdComplete()
        }
    }

    // -----------------------
    // Rewarded (preload + show)
    // -----------------------
    fun loadRewardedAd(adUnitId: String) {
        rewardedReady = false
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("AdsManager", "Rewarded loaded ✅")
                    rewardedAd = ad
                    rewardedReady = true
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("AdsManager", "Rewarded failed ❌: ${adError.message}")
                    rewardedAd = null
                    rewardedReady = false
                }
            }
        )
    }

    // show rewarded; allowFallback same semantics as interstitial
    fun showRewardedAd(
        onResult: (Boolean) -> Unit,
        allowFallback: Boolean = true,
        onNotShown: (() -> Unit)? = null
    ) {
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    rewardedReady = false
                    // If dismissed without reward, still consider success because we rely on reward callback below.
                    onResult(true)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    rewardedReady = false
                    Log.e("AdsManager", "Rewarded show failed ❌: ${adError.message}")
                    if (allowFallback) showStartIoInterstitial { onResult(true) } else onNotShown?.invoke()
                }
            }
            try {
                rewardedAd?.show(context as Activity) { rewardItem ->
                    Log.d("AdsManager", "User earned reward: ${rewardItem.amount}")
                    onResult(true)
                }
            } catch (t: Throwable) {
                Log.e("AdsManager", "showRewardedAd exception: ${t.message}")
                rewardedAd = null
                rewardedReady = false
                if (allowFallback) showStartIoInterstitial { onResult(true) } else onNotShown?.invoke()
            }
        } else {
            Log.d("AdsManager", "Rewarded not ready -> ${if (allowFallback) "fallback" else "no fallback"}")
            if (allowFallback) showStartIoInterstitial { onResult(true) } else onNotShown?.invoke()
        }
    }

    fun isRewardedReady(): Boolean = rewardedReady
}
