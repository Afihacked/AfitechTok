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
import com.google.android.gms.ads.*

class VideoStoryFragment : Fragment() {

    private var _binding: FragmentVideoStoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: StoryAdapter
    private lateinit var storyViewModel: StoryViewModel
    private lateinit var prefs: SharedPreferences

    private var adView: AdView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoStoryBinding.inflate(inflater, container, false)
        prefs = requireContext()
            .getSharedPreferences("TikDownloaderPrefs", Context.MODE_PRIVATE)

        // ===== RecyclerView =====
        adapter = StoryAdapter(emptyList(), requireContext())
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerView.adapter = adapter

        // ===== ViewModel =====
        storyViewModel = ViewModelProvider(requireActivity())[StoryViewModel::class.java]

        storyViewModel.stories.observe(viewLifecycleOwner) { list ->
            adapter.updateList(list.filter { it.type == "video" })
            binding.swipeRefresh.isRefreshing = false
        }

        // ===== Pull to refresh =====
        binding.swipeRefresh.setOnRefreshListener {
            refreshStories()
        }

        // ===== Ads =====
        try { MobileAds.initialize(requireContext()) } catch (_: Throwable) {}
        adView = try { binding.adView } catch (_: Throwable) { null }
        setupAdView()

        return binding.root
    }

    // =====================
    // REFRESH LOGIC
    // =====================
    private fun refreshStories() {
        val saved = prefs.getString("savedUri", "") ?: ""
        if (saved.isNotEmpty()) {
            binding.swipeRefresh.isRefreshing = true
            storyViewModel.clearCache() // 🔥 penting
            storyViewModel.loadStoriesFromUri(Uri.parse(saved))
        } else {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    // =====================
    // ADS
    // =====================
    private fun setupAdView() {
        if (adView == null || !requireContext().areAdsEnabled()) {
            binding.adContainer.visibility = View.GONE
            return
        }

        try {
            val displayMetrics = resources.displayMetrics
            val density = displayMetrics.density
            val adWidth = (displayMetrics.widthPixels / density)
                .toInt()
                .coerceAtLeast(320)

            val adSize =
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    requireContext(),
                    adWidth
                )

            adView?.setAdSize(adSize)
            adView?.loadAd(AdRequest.Builder().build())
            binding.adContainer.visibility = View.VISIBLE
        } catch (_: Throwable) {
            binding.adContainer.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        try { adView?.resume() } catch (_: Throwable) {}
    }

    override fun onPause() {
        try { adView?.pause() } catch (_: Throwable) {}
        super.onPause()
    }

    override fun onDestroyView() {
        try { adView?.destroy() } catch (_: Throwable) {}
        adView = null
        _binding = null
        super.onDestroyView()
    }
}
