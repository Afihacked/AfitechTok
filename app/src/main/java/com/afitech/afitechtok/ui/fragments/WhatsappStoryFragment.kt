package com.afitech.afitechtok.ui.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.afitech.afitechtok.R
import com.afitech.afitechtok.data.StorySaver
import com.afitech.afitechtok.data.database.AppDatabase
import com.afitech.afitechtok.data.database.DownloadHistoryDao
import com.afitech.afitechtok.databinding.FragmentWhatsappStoryBinding
import com.afitech.afitechtok.ui.adapters.StoryPagerAdapter
import com.afitech.afitechtok.utils.setStatusBarColor
import com.google.android.material.tabs.TabLayoutMediator

class WhatsappStoryFragment : Fragment() {

    private lateinit var binding: FragmentWhatsappStoryBinding
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var downloadHistoryDao: DownloadHistoryDao

    private val requestStorageAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)

                saveUri(uri.toString())
                Toast.makeText(requireContext(), "Akses folder berhasil disimpan", Toast.LENGTH_SHORT).show()

                // ✅ Sembunyikan tutorial, tampilkan ViewPager
                binding.tutorialBlock.visibility = View.GONE
                binding.viewPager.visibility = View.VISIBLE
                binding.tabLayout.visibility = View.VISIBLE
                setupViewPagerWithTabs()
            } else {
                Toast.makeText(requireContext(), "URI tidak valid", Toast.LENGTH_SHORT).show()
                // ❌ Tampilkan tutorial ulang
                binding.tutorialBlock.visibility = View.VISIBLE
            }
        } else {
            Toast.makeText(requireContext(), "Permission dibatalkan", Toast.LENGTH_SHORT).show()
            // ❌ Tampilkan tutorial ulang
            binding.tutorialBlock.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inisialisasi database dan DAO
        val db = AppDatabase.getDatabase(requireContext())
        downloadHistoryDao = db.downloadHistoryDao()

        // Assign DAO ke StorySaver agar adapter bisa akses DAO ini
        StorySaver.downloadHistoryDao = downloadHistoryDao
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWhatsappStoryBinding.inflate(inflater, container, false)
        sharedPreferences = requireContext().getSharedPreferences("TikDownloaderPrefs", Context.MODE_PRIVATE)

        val uriSaved = getSavedUri()

        if (!hasStoragePermission() || uriSaved.isEmpty()) {
            // ❌ Belum punya akses folder: tampilkan video + tombol
            binding.tutorialBlock.visibility = View.VISIBLE
            binding.viewPager.visibility = View.GONE
            binding.tabLayout.visibility = View.GONE

            // ✅ Tombol "Saya Paham"
            binding.btnSayaPaham.setOnClickListener {
                requestStoragePermission()
            }

            setupTutorialVideo()
        } else {
            // ✅ Sudah punya akses: langsung tampilkan ViewPager
            showStoryUI()
        }

        return binding.root
    }

    private fun setupTutorialVideo() {
        val webView = binding.tutorialVideo
        val webSettings = webView.settings

        webSettings.javaScriptEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        webSettings.domStorageEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false

        webView.setBackgroundColor(0)

        val videoId = "bLWR7XqRhTU" // ← Ganti sesuai kebutuhan
        val isPortrait = when (videoId) {
            "bLWR7XqRhTU" -> true // ← Kamu tandai sendiri bahwa video ini portrait
            else -> false // default landscape
        }

        // Ubah tinggi WebView berdasarkan orientasi
        val params = webView.layoutParams as ViewGroup.MarginLayoutParams
        params.height = if (isPortrait) ViewGroup.LayoutParams.MATCH_PARENT else dpToPx(200)
        params.bottomMargin = dpToPx(16) // ← beri jarak 16dp dari tombol
        webView.layoutParams = params

        val html = """
        <html><body style="margin:0;padding:0;">
        <iframe width="100%" height="100%" src="https://www.youtube.com/embed/$videoId?rel=0" 
        frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" 
        allowfullscreen></iframe>
        </body></html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun showStoryUI() {
        binding.tutorialBlock.visibility = View.GONE
        binding.viewPager.visibility = View.VISIBLE
        binding.tabLayout.visibility = View.VISIBLE
        setupViewPagerWithTabs()
    }

    private fun setupViewPagerWithTabs() {
        val pagerAdapter = StoryPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Gambar"
                1 -> "Video"
                else -> "Lainnya"
            }

            val textColor = ContextCompat.getColor(tab.view.context, android.R.color.white)

            val textView = TextView(tab.view.context).apply {
                text = tab.text
                setTextColor(textColor)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            tab.customView = textView
        }.attach()
    }

    private fun hasStoragePermission(): Boolean {
        return requireContext().contentResolver.persistedUriPermissions.any()
    }

    private fun getSavedUri(): String {
        return sharedPreferences.getString("savedUri", "") ?: ""
    }

    private fun saveUri(uri: String) {
        sharedPreferences.edit().putString("savedUri", uri).apply()
    }

    private fun requestStoragePermission() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        requestStorageAccessLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        setStatusBarColor(R.color.sttsbar, isLightStatusBar = false)
    }
}
