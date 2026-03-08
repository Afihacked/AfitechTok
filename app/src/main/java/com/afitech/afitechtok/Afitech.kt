package com.afitech.afitechtok

import android.app.Application
import com.afitech.afitechtok.utils.ThemeManager

class Afitech : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applySavedTheme(this)
    }
}