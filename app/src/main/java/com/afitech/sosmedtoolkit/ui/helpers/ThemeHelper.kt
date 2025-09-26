package com.afitech.sosmedtoolkit.ui.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object ThemeHelper {
    private const val PREF_NAME = "theme_pref"
    private const val KEY_DARK_MODE = "dark_mode"

    fun applyTheme(context: Context) {
        val isDarkMode = getIsDarkMode(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE))
        setNightMode(isDarkMode)
    }

    fun toggleTheme(sharedPref: SharedPreferences, newMode: Boolean) {
        sharedPref.edit().putBoolean(KEY_DARK_MODE, newMode).apply()
        setNightMode(newMode)
    }

    fun getIsDarkMode(sharedPref: SharedPreferences): Boolean {
        return sharedPref.getBoolean(KEY_DARK_MODE, false)
    }

    private fun setNightMode(isDark: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
