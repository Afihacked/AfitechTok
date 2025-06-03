package com.afitech.sosmedtoolkit.utils

import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment

fun Fragment.setStatusBarColor(colorResId: Int, isLightStatusBar: Boolean = false) {
    val window = requireActivity().window
    val color = ContextCompat.getColor(requireContext(), colorResId)
    window.statusBarColor = color

    val decorView = window.decorView
    WindowInsetsControllerCompat(window, decorView).isAppearanceLightStatusBars = isLightStatusBar
}
