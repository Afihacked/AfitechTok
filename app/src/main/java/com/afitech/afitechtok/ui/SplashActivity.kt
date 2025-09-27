package com.afitech.afitechtok.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.afitech.afitechtok.R

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Animasi fade-in sederhana (opsional)
        val logo = findViewById<ImageView>(R.id.logoImage)
        logo.alpha = 0f
        logo.animate().alpha(1f).setDuration(800).start()

        // Delay 2 detik lalu masuk ke MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2000)
    }

    @Suppress("MissingSuperCall", "DEPRECATION")
    @Deprecated("Deprecated in Android 13+, gunakan onBackPressedDispatcher jika perlu")
    override fun onBackPressed() {
        // Dibiarkan kosong agar tidak bisa kembali
    }
}
