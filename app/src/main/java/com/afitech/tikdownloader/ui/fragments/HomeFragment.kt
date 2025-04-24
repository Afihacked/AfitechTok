package com.afitech.tikdownloader.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.afitech.tikdownloader.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import androidx.appcompat.app.AppCompatActivity

private lateinit var adView: AdView

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val transaction = { fragment: Fragment, title: String ->
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left,
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()

            // Mengubah judul toolbar sesuai dengan fragment yang dipilih
            (activity as AppCompatActivity).supportActionBar?.title = title
        }

        // Inisialisasi AdMob
        MobileAds.initialize(requireContext()) {}

        adView = view.findViewById(R.id.adView)

        // Memuat Iklan Banner
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        // Tombol-tombol untuk berpindah ke fragment lain dengan judul yang sesuai
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
        // Mengatur judul ke default "Home" menggunakan string dari resources
        val appName = getString(R.string.nav_home)
        (activity as AppCompatActivity).supportActionBar?.title = appName
    }
}
