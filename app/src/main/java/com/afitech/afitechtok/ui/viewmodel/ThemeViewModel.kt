package com.afitech.afitechtok.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.afitech.afitechtok.utils.ThemeManager

class ThemeViewModel(app: Application) : AndroidViewModel(app) {

    val selectedTheme = MutableLiveData<Int>()

    init {
        selectedTheme.value = ThemeManager.getTheme(app)
    }

    fun setTheme(theme: Int) {
        ThemeManager.setTheme(getApplication(), theme)
        selectedTheme.value = theme
    }
}
