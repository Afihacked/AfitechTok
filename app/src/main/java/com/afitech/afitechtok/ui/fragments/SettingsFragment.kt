package com.afitech.afitechtok.ui.fragments

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.afitech.afitechtok.R
import com.afitech.afitechtok.ui.MainActivity
import com.afitech.afitechtok.ui.viewmodel.ThemeViewModel
import com.afitech.afitechtok.utils.areAdsEnabled
import com.afitech.afitechtok.utils.setAdsEnabled
import com.afitech.afitechtok.utils.setStatusBarColorRes

class SettingsFragment : Fragment() {

    private lateinit var switchAds: SwitchCompat
    private lateinit var tvDescription: TextView

    private var previousChecked = false
    private var internalChange = false

    private lateinit var themeViewModel: ThemeViewModel
    private lateinit var spinnerTheme: Spinner

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setStatusBarColorRes(R.color.white, isLightStatusBar = true, drawBehind = true)

        switchAds = view.findViewById(R.id.switchAds)
        tvDescription = view.findViewById(R.id.tvAdsDescription)

        val adsDisabled = !requireContext().areAdsEnabled()
        switchAds.isChecked = adsDisabled
        previousChecked = adsDisabled
        updateDescription(adsDisabled)

        switchAds.setOnCheckedChangeListener { _, isChecked ->
            if (internalChange) {
                internalChange = false
                return@setOnCheckedChangeListener
            }

            val currentActual = !requireContext().areAdsEnabled()
            showRestartConfirmDialog(isChecked, currentActual)
        }

        // ===== THEME =====
        themeViewModel = ViewModelProvider(this)[ThemeViewModel::class.java]
        spinnerTheme = view.findViewById(R.id.spinnerTheme)

        val themes = listOf("Sistem", "Terang", "Gelap")
        val adapter = ArrayAdapter(requireContext(), R.layout.item_spinner_theme, themes)
        adapter.setDropDownViewResource(R.layout.item_spinner_theme)
        spinnerTheme.adapter = adapter

        spinnerTheme.setSelection(themeViewModel.selectedTheme.value ?: 0)

        spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (themeViewModel.selectedTheme.value == position) return

                themeViewModel.setTheme(position)
                restartAppTask()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        view.findViewById<View>(R.id.itemWaWeb).setOnClickListener {
            (activity as? MainActivity)?.replaceFragment(
                WaWebFragment(),
                getString(R.string.whatsapp_web)
            )
        }

        view.findViewById<View>(R.id.itemAbout).setOnClickListener {
            (activity as? MainActivity)?.replaceFragment(
                TentangFragment(),
                getString(R.string.nav_about)
            )
        }
    }

    // =========================================================
    // RESTART APP (dipakai untuk theme & toggle ads)
    // =========================================================
    private fun restartAppTask() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        requireActivity().overridePendingTransition(0, 0)
    }

    /**
     * isChecked = nilai baru switch (true = adsDisabled)
     * oldChecked = nilai sebelumnya
     */
    private fun showRestartConfirmDialog(isChecked: Boolean, oldChecked: Boolean) {
        val dialog = AlertDialog.Builder(requireContext())
            .setMessage("Perubahan akan diterapkan setelah aplikasi dimulai ulang.")
            .setPositiveButton("Restart Sekarang", null)
            .setNegativeButton("Nanti", null)
            .setNeutralButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            val primary = ContextCompat.getColor(requireContext(), R.color.colorPrimary)

            // ===== RESTART SEKARANG =====
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                setTextColor(primary)
                setOnClickListener {
                    requireContext().setAdsEnabled(!isChecked)
                    updateDescription(isChecked)
                    previousChecked = isChecked

                    restartAppTask()
                    dialog.dismiss()
                }
            }

            // ===== NANTI =====
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                setTextColor(primary)
                setOnClickListener {
                    requireContext().setAdsEnabled(!isChecked)
                    updateDescription(isChecked)
                    previousChecked = isChecked

                    Toast.makeText(
                        requireContext(),
                        "Perubahan akan aktif setelah aplikasi dibuka ulang",
                        Toast.LENGTH_SHORT
                    ).show()

                    dialog.dismiss()
                }
            }

            // ===== BATAL =====
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).apply {
                setTextColor(primary)
                setOnClickListener {
                    internalChange = true
                    switchAds.isChecked = oldChecked
                    dialog.dismiss()
                }
            }
        }

        dialog.setOnCancelListener {
            internalChange = true
            switchAds.isChecked = oldChecked
        }

        dialog.show()
    }

    private fun updateDescription(disabled: Boolean) {
        tvDescription.text = if (disabled) {
            "Iklan telah dimatikan selama penggunaan aplikasi."
        } else {
            "Jika diaktifkan, iklan akan dimatikan selama penggunaan aplikasi."
        }
    }
}