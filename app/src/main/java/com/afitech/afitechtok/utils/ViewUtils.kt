package com.afitech.afitechtok.utils

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import android.util.Log

private const val TAG = "StatusBarUtil"

/**
 * Set status bar color safely (Activity version).
 *
 * @param color Int color (use ContextCompat.getColor(...) or Color.*)
 * @param isLightStatusBar true -> dark icons (good for light backgrounds)
 * @param drawBehind if true, content will be drawn behind status bar (useful for translucent toolbar)
 * @param forceApply jika true, util akan meng-apply ulang warna sekali lagi setelah delay singkat
 *                 untuk menghadapi situasi theme/toolbar yang menimpa warna di pass pertama.
 */
fun Activity.setStatusBarColorInt(
    @ColorInt color: Int,
    isLightStatusBar: Boolean,
    drawBehind: Boolean = false,
    forceApply: Boolean = true
) {
    val window = this.window

    // Jika drawBehind true -> biarkan layout mengisi area sistem (cont: toolbar transparent)
    // Jika false -> biarkan system menempatkan content di bawah status bar area (default safe)
    WindowCompat.setDecorFitsSystemWindows(window, !drawBehind)

    // Pastikan perubahan terjadi setelah layout pass agar tidak mudah ditimpa
    window.decorView.post {
        try {
            applyStatusBarColor(window.activityNameOrNull(), window, color, isLightStatusBar)
        } catch (t: Throwable) {
            Log.e(TAG, "apply initial status bar color error", t)
        }

        if (forceApply) {
            // Terapkan ulang setelah delay singkat — sering membantu ketika theme/scrim menimpa
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    applyStatusBarColor(window.activityNameOrNull(), window, color, isLightStatusBar)
                } catch (t: Throwable) {
                    Log.e(TAG, "apply delayed status bar color error", t)
                }
            }, 60L) // 60 ms delay; dapat disesuaikan jika diperlukan
        }
    }
}

/** Internal helper: set color + icon appearance + optional contrast enforcement handling */
private fun applyStatusBarColor(caller: String?, window: android.view.Window, @ColorInt color: Int, isLight: Boolean) {
    Log.d(TAG, "applyStatusBarColor caller=$caller color=#${Integer.toHexString(color)} isLight=$isLight")
    window.statusBarColor = color

    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.isAppearanceLightStatusBars = isLight

    // Optional: disable contrast enforcement on Android 13+ if present (use with caution)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            window.isStatusBarContrastEnforced = false
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to change isStatusBarContrastEnforced", t)
        }
    }
}

/** Overload yang menerima color resource id */
fun Activity.setStatusBarColorRes(
    @ColorRes colorRes: Int,
    isLightStatusBar: Boolean,
    drawBehind: Boolean = false,
    forceApply: Boolean = true
) {
    val color = ContextCompat.getColor(this, colorRes)
    setStatusBarColorInt(color, isLightStatusBar, drawBehind, forceApply)
}

/** Fragment helpers yang memanggil Activity extension */
fun Fragment.setStatusBarColorRes(
    @ColorRes colorRes: Int,
    isLightStatusBar: Boolean,
    drawBehind: Boolean = false,
    forceApply: Boolean = true
) {
    activity?.setStatusBarColorRes(colorRes, isLightStatusBar, drawBehind, forceApply)
}

fun Fragment.setStatusBarColorInt(
    @ColorInt color: Int,
    isLightStatusBar: Boolean,
    drawBehind: Boolean = false,
    forceApply: Boolean = true
) {
    activity?.setStatusBarColorInt(color, isLightStatusBar, drawBehind, forceApply)
}

/** small helper to get activity simple name (null-safe) */
private fun android.view.Window.activityNameOrNull(): String? {
    return try {
        this.context?.javaClass?.simpleName
    } catch (_: Throwable) {
        null
    }
}
