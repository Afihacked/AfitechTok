package com.afitech.sosmedtoolkit.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.afitech.sosmedtoolkit.R
import com.afitech.sosmedtoolkit.utils.areAdsEnabled
import com.afitech.sosmedtoolkit.utils.setAdsEnabled
import com.afitech.sosmedtoolkit.utils.setStatusBarColor

class SettingsFragment : Fragment() {

    private lateinit var switchAds: SwitchCompat
    private lateinit var tvDescription: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inisialisasi UI
        switchAds = view.findViewById(R.id.switchAds)
        tvDescription = view.findViewById(R.id.tvAdsDescription)

        // Ambil status dari SharedPreferences
        val adsDisabled = !requireContext().areAdsEnabled()
        switchAds.isChecked = adsDisabled
        updateDescription(adsDisabled)

        // Saat switch diubah
        switchAds.setOnCheckedChangeListener { _, isChecked ->
            requireContext().setAdsEnabled(!isChecked)
            updateDescription(isChecked)
        }
    }

    private fun updateDescription(adsDisabled: Boolean) {
        tvDescription.text = if (adsDisabled) {
            "Iklan telah dimatikan selama penggunaan aplikasi."
        } else {
            "Jika diaktifkan, iklan akan dimatikan selama penggunaan aplikasi."
        }
    }
    override fun onResume() {
        super.onResume()
        setStatusBarColor(R.color.sttsbar, isLightStatusBar = false)
    }
}
