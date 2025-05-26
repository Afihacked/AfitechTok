package com.afitech.tikdownloader.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.fragment.app.Fragment
import com.afitech.tikdownloader.R
import com.google.android.gms.ads.AdView
import androidx.appcompat.app.AppCompatActivity
import com.afitech.tikdownloader.utils.CuanManager
import com.afitech.tikdownloader.utils.setStatusBarColor
import com.google.android.material.button.MaterialButton

private lateinit var adView: AdView
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

        // Di dalam onCreateView atau onViewCreated
        val btnOpenYoutubePopup = view.findViewById<MaterialButton>(R.id.btn_open_youtube_popup)
        val inlineWebView = view.findViewById<WebView>(R.id.inlineWebView)

// Konfigurasi WebView sekali saja
        with(inlineWebView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
        }

        inlineWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        inlineWebView.webChromeClient = object : WebChromeClient() {}

        // Fungsi untuk memuat video YouTube
        fun loadVideo(url: String) {
            val isShorts = url.contains("youtube.com/shorts")
            val embedUrl = if (isShorts) {
                val videoId = url.substringAfter("shorts/").substringBefore("?")
                "https://www.youtube.com/embed/$videoId?rel=0&autohide=1&showinfo=0"
            } else {
                val videoId = url.substringAfter("watch?v=").substringBefore("&")
                "https://www.youtube.com/embed/$videoId?rel=0&autohide=1&showinfo=0"
            }
            inlineWebView.loadUrl(embedUrl)
        }

// Handler klik tombol
        btnOpenYoutubePopup.setOnClickListener {
            if (inlineWebView.visibility == View.GONE) {
                // TAMPILKAN WebView dengan animasi slide-up
                inlineWebView.translationY = inlineWebView.height.toFloat() // Mulai di bawah
                inlineWebView.visibility = View.VISIBLE
                inlineWebView.animate().translationY(0f).setDuration(300).start()

                btnOpenYoutubePopup.text = getString(R.string.tutup_video_tutorial)

                // Load YouTube video
                val videoUrl = "https://www.youtube.com/watch?v=XI2te9OJ6DY"
                loadVideo(videoUrl)

            } else {
                // SEMBUNYIKAN WebView dengan animasi slide-down
                inlineWebView.animate().translationY(inlineWebView.height.toFloat()).setDuration(300).withEndAction {
                    inlineWebView.visibility = View.GONE
                }.start()

                btnOpenYoutubePopup.text = getString(R.string.tonton_video_tutorial)
            }
        }




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
            transaction(DownloadFragmentTT(), getString(R.string.nav_tt_offline))
        }
        view.findViewById<Button>(R.id.btn_yt_download).setOnClickListener {
            transaction(DownloadFragmentYT(), getString(R.string.nav_yt_offline))
        }
        view.findViewById<Button>(R.id.btn_wa_story).setOnClickListener {
            transaction(WhatsappStoryFragment(), getString(R.string.nav_wa_offline))
        }
        view.findViewById<Button>(R.id.btn_ig).setOnClickListener {
            transaction(DownloadFragmentIG(), getString(R.string.nav_ig))
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
