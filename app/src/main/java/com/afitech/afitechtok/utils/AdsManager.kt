package com.afitech.afitechtok.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener


class AdsManager(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    init {
        MobileAds.initialize(context)
    }

    // ✅ Banner (AdMob → fallback ke Start.io)
    fun loadBanner(adView: AdView, fallbackContainer: FrameLayout) {
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdsManager", "AdMob banner loaded ✅")
                adView.visibility = android.view.View.VISIBLE
                fallbackContainer.removeAllViews()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e("AdsManager", "AdMob banner failed ❌: ${error.message}")
                adView.visibility = android.view.View.GONE

                // 👉 fallback Start.io Banner
                val startBanner = Banner(context, object : BannerListener {
                    override fun onReceiveAd(view: android.view.View) {
                        Log.d("AdsManager", "Start.io banner loaded ✅")
                    }

                    override fun onFailedToReceiveAd(view: android.view.View) {
                        Log.e("AdsManager", "Start.io banner failed ❌")
                    }

                    override fun onImpression(view: android.view.View) {}
                    override fun onClick(view: android.view.View) {}
                })
                fallbackContainer.removeAllViews()
                fallbackContainer.addView(startBanner)
            }
        }
    }

    fun destroyBanner(adView: AdView) {
        adView.destroy()
    }

    // ✅ Pre-load Interstitial
    fun loadInterstitialAd(adUnitId: String) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("AdsManager", "Interstitial loaded ✅")
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("AdsManager", "Interstitial failed ❌: ${adError.message}")
                    interstitialAd = null
                }
            }
        )
    }

    fun showInterstitialAd(onAdComplete: () -> Unit) {
        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    onAdComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    Log.e("AdsManager", "Interstitial show failed ❌: ${adError.message}")
                    showStartIoInterstitial(onAdComplete)
                }
            }
            interstitialAd?.show(context as Activity)
        } else {
            Log.d("AdsManager", "Interstitial not ready → fallback Start.io")
            showStartIoInterstitial(onAdComplete)
        }
    }

    private fun showStartIoInterstitial(onAdComplete: () -> Unit) {
        val startAppAd = StartAppAd(context)
        startAppAd.showAd(object : AdDisplayListener {
            override fun adHidden(ad: Ad?) {
                // ✅ dipanggil saat user nutup iklan
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
                // fallback: langsung lanjut
                onAdComplete()
            }
        })
    }



    // ✅ Pre-load Rewarded
    fun loadRewardedAd(adUnitId: String) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d("AdsManager", "Rewarded loaded ✅")
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("AdsManager", "Rewarded failed ❌: ${adError.message}")
                    rewardedAd = null
                }
            }
        )
    }

    fun showRewardedAd(onResult: (Boolean) -> Unit) {
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    onResult(true) // ✅ unduh setelah iklan ditutup
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    Log.e("AdsManager", "Rewarded show failed ❌: ${adError.message}")
                    showStartIoInterstitial { onResult(true) }
                }
            }
            rewardedAd?.show(context as Activity) { rewardItem ->
                Log.d("AdsManager", "User earned reward: ${rewardItem.amount}")
                onResult(true)
            }
        } else {
            Log.d("AdsManager", "Rewarded not ready → fallback Start.io")
            showStartIoInterstitial { onResult(true) }
        }
    }

}
