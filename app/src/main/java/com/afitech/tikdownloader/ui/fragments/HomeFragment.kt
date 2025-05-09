package com.afitech.tikdownloader.ui.fragments

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.afitech.tikdownloader.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.afitech.tikdownloader.utils.CuanManager
import com.afitech.tikdownloader.utils.setStatusBarColor

private lateinit var adView: AdView
private lateinit var youtubeWebView: WebView
private val cuanManager = CuanManager()

class HomeFragment : Fragment() {

    // Override onCreateView untuk menambahkan logika untuk merubah warna status bar dan inisialisasi lainnya
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_home, container, false)
        // Menginisialisasi AdMob
        cuanManager.initializeAdMob(requireContext())
        // Mendapatkan referensi untuk AdView dan memuat iklan
        adView = view.findViewById(R.id.adView)
        cuanManager.loadAd(adView)  // Memuat iklan dengan AdMobManager

        // Inisialisasi WebView untuk memuat video YouTube
        youtubeWebView = view.findViewById(R.id.youtubeWebView)

        // Konfigurasi WebView
        val webSettings = youtubeWebView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false
        webSettings.allowFileAccess = false
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.setSupportMultipleWindows(false)

// WebViewClient untuk handle loading internal
        youtubeWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // tetap buka di WebView
            }
        }

// WebChromeClient tanpa dukungan fullscreen
        youtubeWebView.webChromeClient = object : WebChromeClient() {
            // Masih diperlukan untuk dukungan JavaScript alert, progress, dsb
        }

        // Fungsi untuk memuat video berdasarkan URL
        fun loadVideo(url: String) {
            val isShorts = url.contains("youtube.com/shorts")

            val embedUrl: String = if (isShorts) {
                val videoId = url.substringAfter("shorts/").substringBefore("?")
                "https://www.youtube.com/embed/$videoId?rel=0&autohide=1&showinfo=0"
            } else {
                val videoId = url.substringAfter("watch?v=").substringBefore("&")
                "https://www.youtube.com/embed/$videoId?rel=0&autohide=1&showinfo=0"
            }

            youtubeWebView.loadUrl(embedUrl)
        }

// Contoh pemanggilan fungsi
        val videoUrl = "https://www.youtube.com/watch?v=XI2te9OJ6DY"  // atau URL Shorts
        loadVideo(videoUrl)




        // Fungsi untuk berpindah antar fragment dengan animasi dan perubahan judul toolbar
        val transaction = { fragment: Fragment, title: String ->
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right, // animasi masuk fragment
                    R.anim.slide_out_left,  // animasi keluar fragment
                    R.anim.slide_in_left,   // animasi masuk fragment
                    R.anim.slide_out_right  // animasi keluar fragment
                )
                .replace(R.id.fragment_container, fragment)  // mengganti fragment
                .addToBackStack(null)  // menambah fragment ke back stack
                .commit()

            // Mengubah judul toolbar sesuai dengan fragment yang dipilih
            (activity as AppCompatActivity).supportActionBar?.title = title
        }

        // Menambahkan listener untuk tombol-tombol yang berpindah fragment
        view.findViewById<Button>(R.id.btn_tt_download).setOnClickListener {
            transaction(DownloaderFragment(), getString(R.string.nav_tt_offline))
        }
        view.findViewById<Button>(R.id.btn_yt_download).setOnClickListener {
            transaction(DownloadFragmentYT(), getString(R.string.nav_yt_offline))
        }
        view.findViewById<Button>(R.id.btn_wa_story).setOnClickListener {
            transaction(WhatsappStoryFragment(), getString(R.string.nav_wa_offline))
        }
        view.findViewById<Button>(R.id.btn_history).setOnClickListener {
            transaction(HistoryFragment(), getString(R.string.nav_history))
        }

        return view
    }

    // Pastikan judul kembali ke default ketika HomeFragment muncul
    override fun onResume() {
        super.onResume()

        setStatusBarColor(R.color.colorPrimary, isLightStatusBar = false)

        // Mengatur judul ke default "Home" menggunakan string dari resources
        val appName = getString(R.string.nav_home)
        (activity as AppCompatActivity).supportActionBar?.title = appName
    }
    override fun onDestroyView() {
        super.onDestroyView()
        cuanManager.destroyAd(adView) // Menghancurkan iklan saat view dihancurkan
    }
}
