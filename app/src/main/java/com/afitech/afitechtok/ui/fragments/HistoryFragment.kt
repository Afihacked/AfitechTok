package com.afitech.afitechtok.ui.fragments

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity.CENTER
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.afitech.afitechtok.R
import com.afitech.afitechtok.databinding.FragmentHistoryBinding
import com.afitech.afitechtok.ui.adapters.HistoryPagerAdapter
import com.afitech.afitechtok.utils.setStatusBarColorRes
import com.google.android.material.tabs.TabLayoutMediator
import kotlin.math.max
import kotlin.math.min
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Adapter ViewPager2
        binding.viewPager.adapter = HistoryPagerAdapter(this)

        // Status bar
        setStatusBarColorRes(
            R.color.white,
            isLightStatusBar = true,
            drawBehind = true
        )

        // ===== FONT SCALE AWARE =====
        val fontScale = resources.configuration.fontScale

        // Ukuran dasar tab text (sp)
        val baseTextSizeSp = 14f

        // Hitung ukuran final:
        // - jangan terlalu kecil
        // - jangan terlalu besar walau font user besar
        val finalTextSizeSp = when {
            fontScale <= 1.0f -> baseTextSizeSp
            fontScale <= 1.2f -> baseTextSizeSp * 1.05f
            fontScale <= 1.4f -> baseTextSizeSp * 1.1f
            else -> baseTextSizeSp * 1.15f // MAX LIMIT
        }.let { size ->
            max(12f, min(size, 16f)) // clamp aman
        }

        // Hubungkan TabLayout + ViewPager2
        TabLayoutMediator(binding.tabLayoutHistory, binding.viewPager) { tab, position ->

            tab.text = when (position) {
                0 -> "Semua"
                1 -> "Video"
                2 -> "Audio"
                3 -> "Gambar"
                else -> "Lainnya"
            }

            val textColor = ContextCompat.getColor(
                tab.view.context,
                android.R.color.white
            )

            // ===== CUSTOM VIEW RESPONSIF =====
            val textView = TextView(tab.view.context).apply {
                text = tab.text
                setTextColor(textColor)
                setTypeface(null, Typeface.BOLD)
                gravity = CENTER
                includeFontPadding = false

                // SET TEXT SIZE RESPONSIF
                setTextSize(TypedValue.COMPLEX_UNIT_SP, finalTextSizeSp)

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            tab.customView = textView

        }.attach()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
