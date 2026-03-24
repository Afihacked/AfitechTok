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
import com.afitech.afitechtok.databinding.FragmentVideoStoryBinding
import com.afitech.afitechtok.ui.MainActivity
import com.afitech.afitechtok.ui.adapters.StoryAdapter
import com.afitech.afitechtok.utils.areAdsEnabled
import com.google.android.gms.ads.*

class VideoStoryFragment : Fragment() {

    private var _binding: FragmentVideoStoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: StoryAdapter
    private lateinit var storyViewModel: StoryViewModel
    private lateinit var prefs: SharedPreferences

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
        binding.recyclerView.clipToPadding = false
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
//    private fun setupAdView() {
//        if (adView == null || !requireContext().areAdsEnabled()) {
//            binding.adContainer.visibility = View.GONE
//            return
//        }
//
//        try {
//            val displayMetrics = resources.displayMetrics
//            val density = displayMetrics.density
//            val adWidth = (displayMetrics.widthPixels / density)
//                .toInt()
//                .coerceAtLeast(320)
//
//            val adSize =
//                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
//                    requireContext(),
//                    adWidth
//                )
//
//            adView?.setAdSize(adSize)
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
