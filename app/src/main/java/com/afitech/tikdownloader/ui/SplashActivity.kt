package com.afitech.tikdownloader.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.afitech.tikdownloader.R

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val appName = findViewById<View>(R.id.app_name)
        val progressBar = findViewById<View>(R.id.progress_bar)
        val splashIcon = findViewById<View>(R.id.splash_icon) // Tambahkan ikon

        // 🔹 Animasi ikon masuk dengan scale-up
        splashIcon?.scaleX = 0f
        splashIcon?.scaleY = 0f
        splashIcon?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(800)?.start()

        // 🔹 Animasi ProgressBar berjalan dari kiri ke kanan
        progressBar?.let {
            val animator = ObjectAnimator.ofFloat(it, "translationX", -200f, 800f)
            animator.duration = 2500
            animator.repeatMode = ValueAnimator.RESTART
            animator.repeatCount = ValueAnimator.INFINITE
            animator.interpolator = AccelerateDecelerateInterpolator()
            animator.start()
        }

        // 🔹 Animasi Fade-In Nama Aplikasi
        appName?.let {
            it.alpha = 0f
            it.animate().alpha(1f).setDuration(1500).start()
        }

        // 🔹 Setelah 2.5 detik, mulai animasi fade-out sebelum pindah ke MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            fadeOutAnimation(appName, progressBar, splashIcon)
        }, 1500)
    }

    private fun fadeOutAnimation(appName: View?, progressBar: View?, splashIcon: View?) {
        appName?.animate()?.alpha(0f)?.setDuration(500)?.start()
        progressBar?.animate()?.alpha(0f)?.setDuration(500)?.start()
        splashIcon?.animate()?.alpha(0f)?.setDuration(500)?.start() // Fade out ikon juga

        // 🔹 Setelah fade-out selesai, pindah ke MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 100)
    }
}
