package com.afitech.sosmedtoolkit.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import com.afitech.sosmedtoolkit.R
import com.airbnb.lottie.LottieAnimationView

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val rootLayout = findViewById<RelativeLayout>(R.id.root_splash)
        rootLayout.alpha = 0f
        rootLayout.animate().alpha(1f).setDuration(800).start()

        val lottieList = listOf(
            R.raw.lottie1, R.raw.lottie2, R.raw.lottie3, R.raw.lottie4, R.raw.lottie5,
            R.raw.lottie6, R.raw.lottie7, R.raw.lottie8, R.raw.lottie9, R.raw.lottie10
        )

        val sharedPref = getSharedPreferences("splash_pref", MODE_PRIVATE)
        val lastIndex = sharedPref.getInt("last_lottie_index", -1)
        val nextIndex = (lastIndex + 1) % lottieList.size
        sharedPref.edit().putInt("last_lottie_index", nextIndex).apply()

        val lottieView = findViewById<LottieAnimationView>(R.id.lottie_anim)
        lottieView.alpha = 0f
        lottieView.animate().alpha(1f).setDuration(800).start()
        lottieView.setAnimation(lottieList[nextIndex])
        lottieView.playAnimation()

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)
            finish()
        }, 3000)
    }

    @Suppress("MissingSuperCall", "DEPRECATION")
    @Deprecated("Deprecated in Android 13+, gunakan onBackPressedDispatcher jika perlu")
    override fun onBackPressed() {
        // Dibiarkan kosong agar tidak bisa kembali
    }
}
