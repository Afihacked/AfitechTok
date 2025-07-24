package com.afitech.sosmedtoolkit.ui.helpers

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsLogger {

    object Events {
        const val DOWNLOAD_STARTED = "download_started"
        const val DOWNLOAD_COMPLETED = "download_completed"
        const val DOWNLOAD_FAILED = "download_failed"
        const val AD_DISPLAYED = "ad_displayed"
        const val REWARD_EARNED = "reward_earned"
    }

    object Params {
        const val DOWNLOAD_TYPE = "download_type"     // video, audio, image
        const val SOURCE = "source"                   // tiktok, instagram, etc.
        const val FROM = "from"                       // rewarded_ad, button_click
        const val TIMESTAMP = "timestamp"
        const val COUNT = "count"                     // jumlah item (khusus image slide)
        const val ERROR_MESSAGE = "error_message"     // jika gagal
        const val AD_TYPE = "ad_type"                 // interstitial, rewarded
        const val DEVICE_MODEL = "device_model"
        const val REWARD_TYPE = "reward_type"
        const val REWARD_AMOUNT = "reward_amount"
    }

    fun logDownloadStarted(
        analytics: FirebaseAnalytics,
        source: String,
        downloadType: String,
        from: String = "rewarded_ad",
        itemCount: Int? = null
    ) {
        val bundle = Bundle().apply {
            putString(Params.DOWNLOAD_TYPE, downloadType)
            putString(Params.SOURCE, source)
            putString(Params.FROM, from)
            putLong(Params.TIMESTAMP, System.currentTimeMillis())
            itemCount?.let { putInt(Params.COUNT, it) }
        }

        analytics.logEvent(Events.DOWNLOAD_STARTED, bundle)
    }

    fun logDownloadCompleted(
        analytics: FirebaseAnalytics,
        source: String,
        downloadType: String,
        itemCount: Int? = null
    ) {
        val bundle = Bundle().apply {
            putString(Params.DOWNLOAD_TYPE, downloadType)
            putString(Params.SOURCE, source)
            putLong(Params.TIMESTAMP, System.currentTimeMillis())
            itemCount?.let { putInt(Params.COUNT, it) }
        }

        analytics.logEvent(Events.DOWNLOAD_COMPLETED, bundle)
    }

    fun logDownloadFailed(
        analytics: FirebaseAnalytics,
        source: String,
        downloadType: String,
        errorMessage: String,
        itemCount: Int? = null
    ) {
        val bundle = Bundle().apply {
            putString(Params.DOWNLOAD_TYPE, downloadType)
            putString(Params.SOURCE, source)
            putString(Params.ERROR_MESSAGE, errorMessage)
            putLong(Params.TIMESTAMP, System.currentTimeMillis())
            itemCount?.let { putInt(Params.COUNT, it) }
        }

        analytics.logEvent(Events.DOWNLOAD_FAILED, bundle)
    }

    fun logAdDisplayed(analytics: FirebaseAnalytics, adType: String) {
        val deviceModel = android.os.Build.MODEL ?: "unknown"
        val bundle = Bundle().apply {
            putString(Params.AD_TYPE, adType)
            putString(Params.DEVICE_MODEL, deviceModel)
            putLong(Params.TIMESTAMP, System.currentTimeMillis())
        }
        analytics.logEvent(Events.AD_DISPLAYED, bundle)
    }

    fun logRewardEarned(
        analytics: FirebaseAnalytics,
        rewardType: String,
        rewardAmount: Int
    ) {
        val deviceModel = android.os.Build.MODEL ?: "unknown"
        val bundle = Bundle().apply {
            putString(Params.REWARD_TYPE, rewardType)
            putInt(Params.REWARD_AMOUNT, rewardAmount)
            putString(Params.DEVICE_MODEL, deviceModel)
            putLong(Params.TIMESTAMP, System.currentTimeMillis())
        }
        analytics.logEvent(Events.REWARD_EARNED, bundle)
    }
}
