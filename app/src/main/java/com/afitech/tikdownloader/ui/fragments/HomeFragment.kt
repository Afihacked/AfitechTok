package com.afitech.tikdownloader.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.Button
import androidx.fragment.app.Fragment
import com.afitech.tikdownloader.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.afitech.tikdownloader.utils.setStatusBarColor

private lateinit var adView: AdView

class HomeFragment : Fragment() {

    // Override onCreateView untuk menambahkan logika untuk merubah warna status bar dan inisialisasi lainnya
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_home, container, false)

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

        // Inisialisasi AdMob untuk menampilkan iklan
        MobileAds.initialize(requireContext()) {}

        // Mendapatkan referensi untuk AdView dan memuat iklan
        adView = view.findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

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
}
