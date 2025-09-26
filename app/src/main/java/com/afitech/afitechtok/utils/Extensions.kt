package com.afitech.afitechtok.utils

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

fun Context.dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

fun Context.showToastSafe(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

fun LinearLayout.setDownloadState(textView: TextView, enabled: Boolean, text: String) {
    isEnabled = enabled
    textView.text = text
}
