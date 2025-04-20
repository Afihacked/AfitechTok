package com.afitech.tikdownloader.ui.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.afitech.tikdownloader.R
import com.afitech.tikdownloader.ui.helpers.ThemeHelper
import com.google.android.material.button.MaterialButton

class WhatsappStoryFragment : Fragment(R.layout.fragment_whatsapp) {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var themeToggleButton: MaterialButton
    private val PREFS_NAME = "AdSettings"
    @SuppressLint("SourceLockedOrientationActivity")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        themeToggleButton = view.findViewById(R.id.themeToggleButton)
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupTheme()


    }//end oncreated
    private fun setupTheme() {
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        ThemeHelper.applyTheme(isDarkMode)
        updateThemeButtonIcon(isDarkMode)

        themeToggleButton.setOnClickListener {
            val newMode = !sharedPreferences.getBoolean("dark_mode", false)
            ThemeHelper.toggleTheme(sharedPreferences, newMode)
            updateThemeButtonIcon(newMode)
        }
    }
    private fun updateThemeButtonIcon(isDarkMode: Boolean) {
        val iconRes = if (isDarkMode) R.drawable.sun else R.drawable.moon
        themeToggleButton.setIconResource(iconRes)
    }

}



