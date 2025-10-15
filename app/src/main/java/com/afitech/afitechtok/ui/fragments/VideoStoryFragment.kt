package com.afitech.afitechtok.ui.fragments

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.afitech.afitechtok.data.model.StoryViewModel
import com.afitech.afitechtok.databinding.FragmentVideoStoryBinding
import com.afitech.afitechtok.ui.adapters.StoryAdapter
import com.afitech.afitechtok.utils.areAdsEnabled
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

class VideoStoryFragment : Fragment() {

    private var _binding: FragmentVideoStoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: StoryAdapter
    private lateinit var storyViewModel: StoryViewModel
    private lateinit var prefs: SharedPreferences

    // AdView reference (from layout)
    private var adView: AdView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoStoryBinding.inflate(inflater, container, false)
        prefs = requireContext().getSharedPreferences("TikDownloaderPrefs", Context.MODE_PRIVATE)

        adapter = StoryAdapter(emptyList(), requireContext())
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerView.adapter = adapter

        storyViewModel = ViewModelProvider(this)[StoryViewModel::class.java]
        storyViewModel.stories.observe(viewLifecycleOwner) { list ->
            adapter.updateList(list.filter { it.type == "video" })
        }

        // initialize MobileAds once (safe to call multiple times)
        try { MobileAds.initialize(requireContext()) } catch (_: Throwable) {}

        // get adView from binding
        adView = try { binding.adView } catch (_: Throwable) { null }

        setupAdView()

        return binding.root
    }

    private fun setupAdView() {
        // if adView not present in layout or ads disabled => hide container
        if (adView == null || !requireContext().areAdsEnabled()) {
            try {
                binding.adContainer.visibility = View.GONE
            } catch (_: Throwable) {}
            return
        }

        // set adaptive size (best-effort)
        try {
            val displayMetrics = resources.displayMetrics
            val density = displayMetrics.density
            val adWidthPixels = displayMetrics.widthPixels
            val adWidth = (adWidthPixels / density).toInt().coerceAtLeast(320)
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(requireContext(), adWidth)
            adView?.setAdSize(adSize)
        } catch (_: Throwable) { /* ignore, fallback to XML adSize */ }

        // load AdMob ad directly (no StartApp fallback)
        try {
            val request = AdRequest.Builder().build()
            adView?.loadAd(request)
            binding.adContainer.visibility = View.VISIBLE
        } catch (t: Throwable) {
            // if load fails, hide container (do not attempt StartApp fallback)
            binding.adContainer.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        try { adView?.resume() } catch (_: Throwable) {}
        // reload saved stories if any
        val saved = prefs.getString("savedUri", "") ?: ""
        if (saved.isNotEmpty()) {
            storyViewModel.loadStoriesFromUri(Uri.parse(saved))
        }
    }

    override fun onPause() {
        try { adView?.pause() } catch (_: Throwable) {}
        super.onPause()
    }

    override fun onDestroyView() {
        try {
            adView?.destroy()
        } catch (_: Throwable) {}
        adView = null
        _binding = null
        super.onDestroyView()
    }
}
