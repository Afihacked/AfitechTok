package com.afitech.sosmedtoolkit.ui.helpers

import android.content.Context
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.firebase.ktx.Firebase

object RemoteConfigHelper {

    fun init(context: Context) {
        val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600L // bisa 10 detik untuk debug: 10L
        }

        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(
            mapOf(
                "welcome_message" to "Hello from Remote Config!"
            )
        )

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val message = remoteConfig.getString("welcome_message")
                Log.d("RemoteConfig", "Fetched message: $message")
            } else {
                Log.w("RemoteConfig", "Fetch failed: ${task.exception}")
            }
        }
    }
}
