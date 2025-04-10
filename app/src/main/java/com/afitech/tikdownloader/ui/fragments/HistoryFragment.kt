package com.afitech.tikdownloader.ui.fragments

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity.CENTER
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.afitech.tikdownloader.databinding.FragmentHistoryBinding
import com.afitech.tikdownloader.ui.adapters.HistoryPagerAdapter
import com.google.android.material.tabs.TabLayoutMediator

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(

        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Set adapter untuk ViewPager2
        binding.viewPager.adapter = HistoryPagerAdapter(this)

        // Hubungkan TabLayout dengan ViewPager2
        // Hubungkan TabLayout dengan ViewPager2
        TabLayoutMediator(binding.tabLayoutHistory, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Semua"
                1 -> "Video"
                2 -> "Audio"
                3 -> "Gambar"
                else -> "Lainnya"
            }

            // Warna teks selalu putih
            val textColor = ContextCompat.getColor(tab.view.context, android.R.color.white)

            // Buat custom view untuk tab
            val textView = TextView(tab.view.context).apply {
                text = tab.text
                setTextColor(textColor)
                textSize = 14f
                setTypeface(null, Typeface.BOLD) // Set teks tebal (bold)
                gravity = CENTER // Pusatkan teks
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
