package com.afitech.afitechtok.utils

import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment

/**
 * Mengatur warna status bar dan apakah ikon status bar terang atau gelap.
 *
 * @param colorRes warna status bar (resource id, misal: R.color.black)
 * @param isLightStatusBar true jika ikon status bar ingin berwarna gelap (untuk background terang)
 */
fun Fragment.setStatusBarColor(colorRes: Int, isLightStatusBar: Boolean) {
    val activity = requireActivity()
    val window = activity.window

    // Pakai API modern untuk Android 11+ agar layout tidak overlap status bar
    WindowCompat.setDecorFitsSystemWindows(window, true)

    // Ubah warna status bar
    window.statusBarColor = ContextCompat.getColor(requireContext(), colorRes)

    // Atur apakah ikon status bar gelap (untuk background terang)
    val insetsController = WindowInsetsControllerCompat(window, window.decorView)
    insetsController.isAppearanceLightStatusBars = isLightStatusBar
}
