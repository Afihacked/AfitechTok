package com.afitech.sosmedtoolkit.ui.fragments

import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.afitech.sosmedtoolkit.R
import com.afitech.sosmedtoolkit.utils.areAdsEnabled
import com.afitech.sosmedtoolkit.utils.setStatusBarColor
import com.google.android.gms.ads.*

class TentangFragment : Fragment() {

    private lateinit var adView: AdView
    private lateinit var adContainer: FrameLayout

    // Ganti dengan Ad Unit ID milikmu
    private val adUnitId = "ca-app-pub-2025447201837747/8904457185"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_tentang, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adContainer = view.findViewById(R.id.adContainer)

        // Inisialisasi SDK AdMob
        MobileAds.initialize(requireContext())

        // ✅ Tambahan logika aktif/tidaknya iklan
        if (requireContext().areAdsEnabled()) {
            adView = AdView(requireContext())
            adView.setAdSize(getAdaptiveAdSize())
            adView.adUnitId = adUnitId

            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }

            adContainer.removeAllViews()
            adContainer.addView(adView, layoutParams)
            adView.loadAd(AdRequest.Builder().build())
            adContainer.visibility = View.VISIBLE
        } else {
            adContainer.removeAllViews()
            adContainer.visibility = View.GONE
        }
    }

    private fun getAdaptiveAdSize(): AdSize {
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val adWidthPixels = displayMetrics.widthPixels
        val adWidth = (adWidthPixels / density).toInt()

        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(requireContext(), adWidth)
    }

    override fun onDestroyView() {
        if (::adView.isInitialized) {
            adView.destroy()
        }
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        setStatusBarColor(R.color.sttsbar, isLightStatusBar = false)
    }
}
