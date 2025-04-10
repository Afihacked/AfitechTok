package com.afitech.tikdownloader.ui.fragments

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Outline
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity.CENTER
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import com.google.android.gms.ads.MobileAds
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.afitech.tikdownloader.R
import com.afitech.tikdownloader.data.Downloader
import com.afitech.tikdownloader.data.database.AppDatabase
import com.afitech.tikdownloader.data.database.DownloadHistoryDao
import com.afitech.tikdownloader.network.TikTokDownloader
import com.afitech.tikdownloader.ui.components.GuideDialogFragment
import com.afitech.tikdownloader.ui.helpers.ThemeHelper
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdError
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class DownloaderFragment : Fragment(R.layout.fragment_downloader) {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var inputLayout: TextInputLayout
    private lateinit var editText: TextInputEditText
    private lateinit var downloadButton: LinearLayout
    private lateinit var themeSwitch: SwitchCompat
    private lateinit var switchAd: SwitchCompat
    private lateinit var arrowIcon: ImageView
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var progressDownload: ProgressBar
    private lateinit var textProgress: TextView
    private lateinit var adView: AdView
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var isAdShowing = false
    private lateinit var downloadHistoryDao: DownloadHistoryDao

    private val PREFS_NAME = "AdSettings"
    private val KEY_AD_STATUS = "ad_status"

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        fun onResume() {
            super.onResume()
            checkClipboardOnStart() // Fungsi ini kamu panggil saat fragment kembali tampil
        }

        // Inisialisasi database
        val database = AppDatabase.getDatabase(requireContext())
        downloadHistoryDao = database.downloadHistoryDao()

        // Inisialisasi AdMob
        MobileAds.initialize(requireContext()) {}

        // Init Views
        inputLayout = view.findViewById(R.id.inputLayout)
        editText = view.findViewById(R.id.inputLink)
        downloadButton = view.findViewById(R.id.btnDownload)
        themeSwitch = view.findViewById(R.id.themeSwitch)
        switchAd = view.findViewById(R.id.switchAd) // Switch untuk mengaktifkan/menonaktifkan iklan
        arrowIcon = view.findViewById(R.id.arrowIcon)
        progressDownload = view.findViewById(R.id.progressDownload)
        textProgress = view.findViewById(R.id.textProgress)
        adView = view.findViewById(R.id.adView)
        val guideText: TextView = view.findViewById(R.id.guideText)

        val textCount = view.findViewById<TextView>(R.id.textCount)
        val maxCharacters = 35
        val tolerance = 5
        val maxWithTolerance = maxCharacters + tolerance

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val currentLength = s?.length ?: 0
                textCount.text = "$currentLength/$maxCharacters"

                // Warna merah jika melebihi batas normal
                if (currentLength > maxCharacters) {
                    textCount.setTextColor(ContextCompat.getColor(requireActivity(), android.R.color.holo_red_dark))
                } else {
                    textCount.setTextColor(ContextCompat.getColor(requireActivity(), android.R.color.darker_gray))
                }

                // Jika melebihi batas toleransi, hapus karakter yang kelebihan
                if (currentLength > maxWithTolerance) {
                    val trimmed = s?.substring(0, maxWithTolerance)
                    editText.setText(trimmed)
                    editText.setSelection(trimmed?.length ?: 0) // Pindahkan kursor ke akhir
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })



        // Memuat Iklan Banner
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        // Load Iklan Rewarded & Interstitial
        loadRewardedAd()
        loadInterstitialAd()

        // Inisialisasi SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAdEnabled = sharedPreferences.getBoolean(KEY_AD_STATUS, true)

// Atur Switch sesuai dengan status tersimpan
        switchAd.isChecked = isAdEnabled
        updateSwitchUI(isAdEnabled)

// Kontrol visibilitas iklan saat pertama kali aplikasi dibuka
        adView.visibility = if (isAdEnabled) View.VISIBLE else View.GONE

// Event Listener untuk SwitchAd
        switchAd.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_AD_STATUS, isChecked)
                .apply() // Simpan status switch
            adView.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateSwitchUI(isChecked)

            if (isChecked) {
                // Muat ulang iklan jika diaktifkan
                loadInterstitialAd()
                loadRewardedAd()
            } else {
                // Hapus referensi iklan jika dinonaktifkan
                interstitialAd = null
                rewardedAd = null
            }
        }


        // Event Listener untuk input teks
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                guideText.visibility = if (s.isNullOrEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup Theme
        setupTheme()

        // Clipboard Manager
        clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        checkClipboardOnStart()     // Cek saat pertama kali fragment muncul
        checkClipboardForLink()     // Listener kalau ada copy setelahnya

        // Tombol Download dengan Dropdown
        downloadButton.setOnClickListener {
            showDownloadMenu(it)
        }

        // Tema Gelap/Terang Switch
        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            ThemeHelper.toggleTheme(sharedPreferences, isChecked)
        }

        // Klik Panduan
        guideText.setOnClickListener {
            GuideDialogFragment().show(parentFragmentManager, "GuideDialog")
        }
    }

    // Fungsi untuk memperbarui warna switch agar sesuai statusnya
    private fun updateSwitchUI(isChecked: Boolean) {
        val thumbColor = if (isChecked) android.R.color.holo_red_dark else android.R.color.white
        val trackColor = if (isChecked) R.color.dark_gray else R.color.light_gray

        switchAd.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), thumbColor))
        switchAd.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), trackColor))
    }

    private fun loadInterstitialAd() {
        if (!switchAd.isChecked) return // Jangan load iklan jika switch mati

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(requireContext(), getString(R.string.admob_interstitial_id), adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                Log.d("AdMob", "Iklan Interstitial berhasil dimuat.")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("AdMob", "Gagal memuat iklan Interstitial: ${adError.message}")
                interstitialAd = null
            }
        })
    }

    private fun showInterstitialAd(onAdComplete: () -> Unit) {
        if (!switchAd.isChecked || interstitialAd == null) { // Cek switch sebelum tampilkan iklan
            Log.e("AdMob", "Iklan Interstitial tidak tersedia atau switch mati, lanjutkan download.")
            onAdComplete()
            return
        }

        interstitialAd?.let { ad ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("AdMob", "Iklan Interstitial selesai, lanjutkan download.")
                    interstitialAd = null
                    loadInterstitialAd() // Muat ulang setelah ditutup
                    onAdComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e("AdMob", "Gagal menampilkan iklan: ${adError.message}")
                    onAdComplete()
                }
            }
            ad.show(requireActivity())
        }
    }

    private fun loadRewardedAd() {
        if (!switchAd.isChecked) return // Jangan load iklan jika switch mati

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(requireContext(), getString(R.string.admob_rewarded_id), adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                Log.d("AdMob", "Iklan Rewarded berhasil dimuat.")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("AdMob", "Gagal memuat iklan Rewarded: ${adError.message}")
                rewardedAd = null
            }
        })
    }

    private fun showRewardedAd(onAdComplete: () -> Unit) {
        if (!switchAd.isChecked || rewardedAd == null) { // Cek switch sebelum tampilkan iklan
            Log.e("AdMob", "Iklan Rewarded tidak tersedia atau switch mati, langsung mulai download.")
            onAdComplete()
            return
        }

        rewardedAd?.let { ad ->
            ad.show(requireActivity()) { rewardItem: RewardItem ->
                Log.d("AdMob", "User mendapatkan reward: ${rewardItem.amount} ${rewardItem.type}")
                onAdComplete() // Jalankan download setelah iklan selesai
            }
        }

        // Muat ulang iklan setelah ditampilkan
        loadRewardedAd()
    }



    private fun setupTheme() {
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        themeSwitch.isChecked = isDarkMode
        ThemeHelper.applyTheme(isDarkMode)

        // Pastikan warna switch sesuai dengan mode tema saat ini
        if (isDarkMode) {
            themeSwitch.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.black))
            themeSwitch.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.dark_gray))
        } else {
            themeSwitch.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.white))
            themeSwitch.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.light_gray))
        }
    }

    private fun checkClipboardOnStart() {
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val copiedText = clipData.getItemAt(0).text.toString()
            if (copiedText.isNotEmpty() && detectPlatform(copiedText) != "unknown") {
                editText.setText(copiedText)
            }
        }
    }
    private fun checkClipboardForLink() {
        clipboardManager.addPrimaryClipChangedListener {
            val clipData = clipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val copiedText = clipData.getItemAt(0).text.toString()
                if (copiedText.isNotEmpty() && detectPlatform(copiedText) != "unknown") {
                    editText.setText(copiedText)
                }
            }
        }
    }


    private fun detectPlatform(url: String): String {
        return when {
            url.contains("tiktok.com", true) -> "tiktok"
            url.contains("youtube.com", true) || url.contains("youtu.be", true) -> "youtube"
            else -> "unknown"
        }
    }


    private fun showDownloadMenu(view: View) {
        val url = editText.text.toString().trim()
        val platform = detectPlatform(url)

        if (platform == "unknown") {
            Toast.makeText(requireContext(), "Masukkan link TikTok yang valid!", Toast.LENGTH_SHORT).show()
            return
        }

        val buttonLayout = requireActivity().findViewById<LinearLayout>(R.id.btnDownload) // Pastikan ID benar
        buttonLayout.isEnabled = false // Nonaktifkan sementara agar tidak bisa diklik dua kali

        // **Buat ProgressBar**
        val progressBar = ProgressBar(requireContext()).apply {
            isIndeterminate = true
            visibility = View.VISIBLE
        }

        // **Cek parent layout tombol**
        val parent = buttonLayout.parent as? ViewGroup
        if (parent == null) {
            buttonLayout.isEnabled = true // Aktifkan kembali jika gagal mendapatkan parent
            return
        }

        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = CENTER
            topMargin = 16
        }

        // **Tambahkan ProgressBar di bawah tombol**
        Handler(Looper.getMainLooper()).post {
            parent.addView(progressBar, parent.indexOfChild(buttonLayout) + 1, layoutParams)
        }

        // **Mulai proses di Coroutine**
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val isSlide = withContext(Dispatchers.IO) { TikTokDownloader.isTikTokSlide(url) }

                val formats = when {
                    platform == "tiktok" && isSlide -> listOf("JPG")
                    platform == "tiktok" -> listOf("MP4", "MP3")
                    else -> emptyList()
                }

                val popupMenu = PopupMenu(
                    ContextThemeWrapper(requireContext(), R.style.PopupMenuStyle),
                    view,
                    android.view.Gravity.END
                )
                val menu = popupMenu.menu

                formats.forEachIndexed { index, format -> menu.add(0, index, index, format) }

                popupMenu.setOnMenuItemClickListener { item ->
                    val selectedFormat = formats[item.itemId]
                    if (selectedFormat == "JPG" && isSlide) {
                        showSlideSelectionPopup(url)
                    } else {
                        startDownload(url, selectedFormat)
                    }
                    true
                }

                popupMenu.show()
            } finally {
                // **Hapus ProgressBar & Aktifkan kembali tombol**
                Handler(Looper.getMainLooper()).post {
                    parent.removeView(progressBar)
                    buttonLayout.isEnabled = true
                }
            }
        }
    }

    private fun showSlideSelectionPopup(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val slideImages = TikTokDownloader.getSlideImages(url)

            withContext(Dispatchers.Main) {
                if (slideImages.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "Tidak ada gambar slide yang tersedia!", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                Log.d("MainActivity", "Menampilkan popup dengan ${slideImages.size} gambar.")

                val selectedImages = mutableSetOf<String>()
                val builder = AlertDialog.Builder(requireContext())
                builder.setTitle("Pilih Gambar yang Akan Diunduh")

                val gridView = GridView(requireContext()).apply {
                    numColumns = 3
                    stretchMode = GridView.STRETCH_COLUMN_WIDTH
                    verticalSpacing = 8
                    horizontalSpacing = 8
                    setPadding(16, 16, 16, 16)

                    adapter = object : BaseAdapter() {
                        override fun getCount(): Int = slideImages.size
                        override fun getItem(position: Int): String = slideImages[position]
                        override fun getItemId(position: Int): Long = position.toLong()

                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val view = convertView ?: layoutInflater.inflate(R.layout.item_image_selection, parent, false)
                            val imageView = view.findViewById<ImageView>(R.id.imageView)
                            val checkOverlay = view.findViewById<ImageView>(R.id.checkOverlay)

                            val imageUrl = getItem(position)
                            imageView.outlineProvider = object : ViewOutlineProvider() {
                                override fun getOutline(view: View, outline: Outline) {
                                    outline.setRoundRect(0, 0, view.width, view.height, 16f)
                                }
                            }
                            imageView.clipToOutline = true

                            checkOverlay.visibility = if (selectedImages.contains(imageUrl)) View.VISIBLE else View.GONE

                            Glide.with(context)
                                .load(imageUrl)
                                .placeholder(R.drawable.ic_placeholder)
                                .into(imageView)

                            view.setOnClickListener {
                                if (selectedImages.contains(imageUrl)) {
                                    selectedImages.remove(imageUrl)
                                    checkOverlay.visibility = View.GONE
                                } else {
                                    selectedImages.add(imageUrl)
                                    checkOverlay.visibility = View.VISIBLE
                                }
                            }

                            return view
                        }
                    }
                }

                builder.setView(gridView)
                builder.setPositiveButton("Unduh yang Dipilih") { _, _ ->
                    if (selectedImages.isNotEmpty()) {
                        if (!isAdShowing) {  // Cek apakah iklan sedang tayang
                            isAdShowing = true  // Set flag agar tidak memulai download sebelum iklan selesai

                            showInterstitialAd {
                                isAdShowing = false  // Reset flag setelah iklan selesai
                                downloadSelectedImages(selectedImages.toList())  // Mulai proses download setelah iklan selesai
                            }
                        }
                    } else {
                        Toast.makeText(requireContext(), "Pilih setidaknya satu gambar!", Toast.LENGTH_SHORT).show()
                    }
                }

                builder.setNegativeButton("Batal") { dialog, _ -> dialog.dismiss() }
                builder.show()
            }
        }
    }

    private fun downloadSelectedImages(images: List<String>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val timeStamp = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())
            var isToastShown = false  // Flag untuk mencegah notifikasi ganda

            for ((index, imageUrl) in images.withIndex()) {
                try {
                    val fileName = "IMG_$timeStamp$index.jpg" // Nama file unik

                    Downloader.downloadFile(
                        context = requireContext(),
                        fileUrl = imageUrl,
                        fileName = fileName,
                        mimeType = "image/jpeg",
                        onProgressUpdate = { _ ->
                            lifecycleScope.launch(Dispatchers.Main) {
                                // Bisa tambahkan UI update jika perlu
                            }
                        },
                        downloadHistoryDao // Simpan ke database
                    )

                    // Tampilkan toast hanya sekali setelah semua gambar selesai diunduh
                    if (!isToastShown && (index == images.lastIndex || images.size == 1)) {
                        isToastShown = true // Set flag agar toast hanya muncul sekali
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(),
                                getString(R.string.gambar_berhasil), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(),
                            getString(R.string.gambar_gagal), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }



    private fun startDownload(url: String, format: String) {
        showRewardedAd {
            if (!isAdShowing) {  // Pastikan download hanya dimulai setelah iklan selesai
                isAdShowing = true  // Set flag agar download tidak dimulai sebelum iklan selesai

                requireActivity().runOnUiThread {
                    progressDownload.visibility = View.VISIBLE
                    textProgress.visibility = View.VISIBLE
                    arrowIcon.visibility = View.GONE

                    progressDownload.progress = 0
                    textProgress.text = "0%"
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    val downloadUrl = TikTokDownloader.getDownloadUrl(url, format)

                    if (downloadUrl != null) {
                        val mimeType = when (format) {
                            "MP4" -> "video/mp4"
                            "MP3" -> "audio/mp3"
                            "JPG" -> "image/jpeg"
                            else -> "application/octet-stream"
                        }

                        val username = extractUsernameFromUrl(url) ?: "unknown"
                        val timeStamp = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                        val finalFileName = "${username}_${timeStamp}.$format".lowercase()

                        try {
                            val uniqueFileName = Downloader.generateUniqueFileName(
                                context = requireContext(),
                                fileName = finalFileName,
                                mimeType = mimeType
                            )

                            val uri = Downloader.downloadFile(
                                context = requireContext(),
                                fileUrl = downloadUrl,
                                fileName = uniqueFileName,
                                mimeType = mimeType,
                                onProgressUpdate = { progress ->
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        progressDownload.progress = progress
                                        textProgress.text = "$progress%"
                                    }
                                },
                                downloadHistoryDao
                            )


                            withContext(Dispatchers.Main) {
                                if (uri != null) {
                                    Toast.makeText(
                                        requireContext(),
                                        "Unduhan $format selesai dan tersimpan di Galeri!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    throw Exception("File path tidak ditemukan setelah download.")
                                }

                                progressDownload.visibility = View.GONE
                                textProgress.visibility = View.GONE
                                arrowIcon.visibility = View.VISIBLE
                            }
                        } catch (e: Exception) {
                            Log.e("Download", "Gagal mengunduh file: ${e.message}")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Gagal mengunduh $format!", Toast.LENGTH_SHORT).show()
                                progressDownload.visibility = View.GONE
                                textProgress.visibility = View.GONE
                                arrowIcon.visibility = View.VISIBLE
                            }
                        }
                    } else {
                        Log.e("Download", "Gagal mendapatkan link unduhan untuk format $format!")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Gagal mendapatkan link unduhan!", Toast.LENGTH_SHORT).show()
                            progressDownload.visibility = View.GONE
                            textProgress.visibility = View.GONE
                            arrowIcon.visibility = View.VISIBLE
                        }
                    }

                    isAdShowing = false  // Reset flag setelah proses selesai
                }
            }
        }
    }

    private fun extractUsernameFromUrl(url: String): String? {
        // Jika URL adalah short link TikTok (vt.tiktok.com)
        if (url.contains("vt.tiktok.com")) {
            val resolvedUrl = resolveShortLink(url)
            return resolvedUrl?.let { extractUsernameFromUrl(it) }
        }

        // Regex untuk mengambil username dari URL asli
        val regex = Regex("https?://(?:www\\.|m\\.)?tiktok\\.com/@([^/?]+)")
        return regex.find(url)?.groupValues?.get(1)
    }

    private fun resolveShortLink(shortUrl: String): String? {
        return try {
            val url = URL(shortUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connect()

            val resolvedUrl = connection.getHeaderField("Location") // Ambil URL tujuan
            connection.disconnect()
            resolvedUrl
        } catch (e: Exception) {
            null
        }
    }
}



