package com.afitech.afitechtok.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.afitech.afitechtok.R
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

        // Navigasi antar fragment
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

            (activity as AppCompatActivity).supportActionBar?.title = title
        }

        view.findViewById<Button>(R.id.btn_tt_download).setOnClickListener {
            transaction(DownloadFragmentTT(), getString(R.string.nav_tt_offline))
        }
        view.findViewById<Button>(R.id.btn_wa_story).setOnClickListener {
            transaction(WhatsappStoryFragment(), getString(R.string.nav_wa_offline))
        }
        view.findViewById<Button>(R.id.btn_history).setOnClickListener {
            transaction(HistoryFragment(), getString(R.string.nav_history))
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        setStatusBarColor(R.color.sttsbar, isLightStatusBar = false)

        val appName = getString(R.string.nav_home)
        (activity as AppCompatActivity).supportActionBar?.title = appName
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adsManager.destroyBanner(adView)
    }
}
