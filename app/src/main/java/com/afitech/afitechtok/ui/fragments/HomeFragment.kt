package com.afitech.afitechtok.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.afitech.afitechtok.R
import com.afitech.afitechtok.ui.MainActivity
import com.afitech.afitechtok.utils.AdsManager
import com.afitech.afitechtok.utils.areAdsEnabled
import com.afitech.afitechtok.utils.setStatusBarColor
import com.google.android.gms.ads.AdView

class HomeFragment : Fragment() {

    private lateinit var adsManager: AdsManager
    private lateinit var adView: AdView
    private lateinit var fallbackContainer: FrameLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // ✅ Ads Manager
        adsManager = AdsManager(requireContext())
        adView = view.findViewById(R.id.adView)
        fallbackContainer = view.findViewById(R.id.fallbackContainer)

        // ✅ Load Banner (AdMob → fallback ke Start.io)
        if (requireContext().areAdsEnabled()) {
            adsManager.loadBanner(adView, fallbackContainer)
            adView.visibility = View.VISIBLE
        } else {
            adView.visibility = View.GONE
            fallbackContainer.removeAllViews()
        }

        // ✅ Shortcut navigasi ke fragment lain
        view.findViewById<Button>(R.id.btn_tt_download).setOnClickListener {
            (activity as MainActivity).replaceFragment(
                DownloadFragmentTT(),
                getString(R.string.nav_tt_offline)
            )
        }

        view.findViewById<Button>(R.id.btn_wa_story).setOnClickListener {
            (activity as MainActivity).replaceFragment(
                WhatsappStoryFragment(),
                getString(R.string.nav_wa_offline)
            )
        }

        view.findViewById<Button>(R.id.btn_history).setOnClickListener {
            (activity as MainActivity).replaceFragment(
                HistoryFragment(),
                getString(R.string.nav_history)
            )
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        setStatusBarColor(R.color.sttsbar, isLightStatusBar = false)
        // ❌ Jangan set toolbar title manual di sini, biar MainActivity yang handle greeting
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adsManager.destroyBanner(adView)
    }
}
