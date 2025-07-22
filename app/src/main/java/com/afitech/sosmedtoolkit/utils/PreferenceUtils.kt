package com.afitech.sosmedtoolkit.utils

import android.content.Context

fun Context.areAdsEnabled(): Boolean {
    return getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        .getBoolean("ads_enabled", true)
}

fun Context.setAdsEnabled(enabled: Boolean) {
    getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("ads_enabled", enabled)
        .apply()
}
