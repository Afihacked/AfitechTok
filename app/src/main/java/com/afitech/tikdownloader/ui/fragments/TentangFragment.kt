package com.afitech.tikdownloader.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.afitech.tikdownloader.R
import com.afitech.tikdownloader.utils.CuanManager
import com.afitech.tikdownloader.utils.setStatusBarColor
import com.google.android.gms.ads.AdView

class TentangFragment : Fragment() {
    private lateinit var adView: AdView
    private val cuanManager = CuanManager()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tentang, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Menginisialisasi AdMob
        cuanManager.initializeAdMob(requireContext())
        // Mendapatkan referensi untuk AdView dan memuat iklan
        adView = view.findViewById(R.id.adView)
        cuanManager.loadAd(adView)  // Memuat iklan dengan AdMobManager
    }
    override fun onResume() {
        super.onResume()

        setStatusBarColor(R.color.colorPrimary, isLightStatusBar = false)

    }
    override fun onDestroyView() {
        super.onDestroyView()
        cuanManager.destroyAd(adView) // Menghancurkan iklan saat view dihancurkan
    }
}
