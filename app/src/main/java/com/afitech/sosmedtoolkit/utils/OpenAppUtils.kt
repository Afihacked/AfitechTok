package com.afitech.sosmedtoolkit.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast

fun openAppWithFallback(
    context: Context,
    primaryPackage: String,
    primaryFallbackActivity: String? = null,
    fallbackPackage: String,
    fallbackFallbackActivity: String? = null,
    notFoundMessage: String = "Aplikasi tidak ditemukan"
) {
    val pm = context.packageManager

    fun tryLaunchPackage(pkg: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(pkg)
        return if (intent != null) {
            context.startActivity(intent)
            true
        } else {
            false
        }
    }

    fun tryLaunchExplicit(pkg: String, activity: String): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName(pkg, activity)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    when {
        tryLaunchPackage(primaryPackage) -> Unit
        primaryFallbackActivity != null && tryLaunchExplicit(primaryPackage, primaryFallbackActivity) -> Unit
        tryLaunchPackage(fallbackPackage) -> Unit
        fallbackFallbackActivity != null && tryLaunchExplicit(fallbackPackage, fallbackFallbackActivity) -> Unit
        else -> {
            Toast.makeText(context, notFoundMessage, Toast.LENGTH_SHORT).show()
        }
    }
}
