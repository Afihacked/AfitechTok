package com.afitech.afitechtok.ui.fragments

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afitech.afitechtok.data.model.StoryViewModel
import com.afitech.afitechtok.databinding.FragmentImageStoryBinding
import com.afitech.afitechtok.ui.MainActivity
import com.afitech.afitechtok.ui.adapters.StoryAdapter
import com.afitech.afitechtok.utils.areAdsEnabled
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

class ImageStoryFragment : Fragment() {

    private var _binding: FragmentImageStoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: StoryAdapter
    private lateinit var storyViewModel: StoryViewModel
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageStoryBinding.inflate(inflater, container, false)
        prefs = requireContext()
            .getSharedPreferences("TikDownloaderPrefs", Context.MODE_PRIVATE)

        adapter = StoryAdapter(emptyList(), requireContext())
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.clipToPadding = false
        storyViewModel = ViewModelProvider(this)[StoryViewModel::class.java]

        // 🔄 OBSERVER DATA
        storyViewModel.stories.observe(viewLifecycleOwner) { list ->
            adapter.updateList(list.filter { it.type == "image" })
            binding.swipeRefresh.isRefreshing = false // stop loading
        }

        // 🔽 PULL TO REFRESH
        binding.swipeRefresh.setOnRefreshListener {
            val saved = prefs.getString("savedUri", "") ?: ""
            if (saved.isNotEmpty()) {
                storyViewModel.loadStoriesFromUri(Uri.parse(saved))
            } else {
                binding.swipeRefresh.isRefreshing = false
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerView) { view, insets ->

            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            val activity = requireActivity() as MainActivity
            val bottomNavHeight = activity.getBottomNavHeight()

            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                navBar + bottomNavHeight + 24
            )
            insets

        }
        binding.recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {

                private var isNavVisible = true

                override fun onScrolled(
                    recyclerView: RecyclerView,
                    dx: Int,
                    dy: Int
                ) {

                    val activity = activity as? MainActivity ?: return

                    // 🔽 scroll ke bawah → hide
                    if (dy > 10 && isNavVisible) {
                        activity.hideBottomNav()
                        isNavVisible = false
                    }

                    // 🔼 scroll ke atas → show
                    else if (dy < -10 && !isNavVisible) {
                        activity.showBottomNav()
                        isNavVisible = true
                    }

                    // 🔥 mentok bawah → paksa hide (tanpa spam)
                    if (!recyclerView.canScrollVertically(1) && isNavVisible) {
                        activity.hideBottomNav()
                        isNavVisible = false
                    }

                    // 🔥 mentok atas → paksa show (tanpa spam)
                    if (!recyclerView.canScrollVertically(-1) && !isNavVisible) {
                        activity.showBottomNav()
                        isNavVisible = true
                    }
                }
            }
        )
        return binding.root
    }

//    private fun setupAdView() {
//        if (adView == null || !requireContext().areAdsEnabled()) {
//            binding.adContainer.visibility = View.GONE
//            return
//        }
//
//        try {
//            val dm = resources.displayMetrics
//            val adWidth = (dm.widthPixels / dm.density).toInt().coerceAtLeast(320)
//            val adSize =
//                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
//                    requireContext(), adWidth
//                )
//            adView?.setAdSize(adSize)
//        } catch (_: Throwable) {}
//
//        try {
//            adView?.loadAd(AdRequest.Builder().build())
//            binding.adContainer.visibility = View.VISIBLE
//        } catch (_: Throwable) {
//            binding.adContainer.visibility = View.GONE
//        }
//    }

    override fun onResume() {
        super.onResume()

        val saved = prefs.getString("savedUri", "") ?: ""
        if (saved.isNotEmpty()) {
            storyViewModel.loadStoriesFromUri(Uri.parse(saved))
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

