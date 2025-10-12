package com.afitech.afitechtok.ui.helpers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val launchIntent = context.packageManager?.getLaunchIntentForPackage(context.packageName)
            launchIntent?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(this)
            }
        } catch (e: Exception) {
            Log.e("RestartReceiver", "Failed to relaunch app", e)
        }
    }
}
