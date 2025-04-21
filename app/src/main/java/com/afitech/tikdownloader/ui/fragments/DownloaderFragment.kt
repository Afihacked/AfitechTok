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
import androidx.core.widget.addTextChangedListener
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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.button.MaterialButton
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
    private lateinit var themeToggleButton: MaterialButton
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
        themeToggleButton = view.findViewById(R.id.themeToggleButton)
//        themeSwitch = view.findViewById(R.id.themeSwitch)

        switchAd = view.findViewById(R.id.switchAd) // Switch untuk mengaktifkan/menonaktifkan iklan
        arrowIcon = view.findViewById(R.id.arrowIcon)
        progressDownload = view.findViewById(R.id.progressDownload)
        textProgress = view.findViewById(R.id.textProgress)
        adView = view.findViewById(R.id.adView)
        val guideText: TextView = view.findViewById(R.id.guideText)

        // Inisialisasi SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAdEnabled = sharedPreferences.getBoolean(KEY_AD_STATUS, true)

//BLOK KONFIGURASI IKLAN
// Variabel untuk menandai proses inisialisasi agar listener tidak langsung terpanggil
        var isInitializing = true

// Matikan sementara listener sebelum setChecked
        switchAd.setOnCheckedChangeListener(null)

// Atur posisi switch sesuai dengan status tersimpan
        switchAd.isChecked = isAdEnabled
        updateSwitchUI(isAdEnabled)

// Kontrol visibilitas iklan saat pertama kali aplikasi dibuka
        adView.visibility = if (isAdEnabled) View.VISIBLE else View.GONE

// Memuat Iklan Banner
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

// Load Iklan Rewarded & Interstitial jika diaktifkan
        if (isAdEnabled) {
            loadRewardedAd()
            loadInterstitialAd()
        }

// Pasang ulang listener setelah inisialisasi selesai
        switchAd.setOnCheckedChangeListener { _, isChecked ->
            if (isInitializing) return@setOnCheckedChangeListener

            // Simpan status ke SharedPreferences
            sharedPreferences.edit().putBoolean(KEY_AD_STATUS, isChecked).apply()

            // Update UI dan visibilitas iklan
            adView.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateSwitchUI(isChecked)

            if (isChecked) {
                loadInterstitialAd()
                loadRewardedAd()
            } else {
                interstitialAd = null
                rewardedAd = null
            }

            Log.d("AdsSwitch", "Status iklan diubah: $isChecked")
        }

// Selesai proses inisialisasi
        isInitializing = false
//END IKLAN

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
            val link = editText.text.toString().trim()
            if (!hasUserInput && link.isEmpty()) {
                Toast.makeText(requireContext(), "Silakan masukkan link terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // lanjut proses jika tidak kosong
            if (isLinkValid(link)) {
                if (downloadButton.isClickable) {
                    // Jalankan download
                    showDownloadMenu(it)
                }
            } else {
                Toast.makeText(requireContext(), "Link tidak valid", Toast.LENGTH_SHORT).show()
            }

        }

        // Klik Panduan
        guideText.setOnClickListener {
            GuideDialogFragment().show(parentFragmentManager, "GuideDialog")
        }

        editText.addTextChangedListener {
            hasUserInput = !it.isNullOrBlank()
        }

        val textCount = view.findViewById<TextView>(R.id.textCount)
        val maxCharacters = 50
        val tolerance = 5
        val maxWithTolerance = maxCharacters + tolerance

        // Regex ketat di luar listener
        val YOUTUBE_REGEX = Regex("^https://(www\\.)?youtube\\.com/watch\\?v=[\\w-]{11}$")
        val YOUTUBE_SHORTS_REGEX = Regex("^https://(www\\.)?youtube\\.com/shorts/[\\w-]{11}$")
        val YOUTUBE_SHORTLINK_REGEX = Regex("^https://youtu\\.be/[\\w-]{11}$")
        val TIKTOK_REGEX = Regex("^https://(vt\\.|www\\.)?tiktok\\.com/[^\\s]+$")

        fun detectPlatformPrecise(url: String): String {
            return when {
                YOUTUBE_REGEX.matches(url) ||
                        YOUTUBE_SHORTS_REGEX.matches(url) ||
                        YOUTUBE_SHORTLINK_REGEX.matches(url) -> "youtube"

                TIKTOK_REGEX.matches(url) -> "tiktok"

                else -> "invalid"
            }
        }

        // TextWatcher
        editText.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val url = s?.toString()?.trim() ?: ""
                val currentLength = url.length
                textCount.text = "$currentLength/$maxCharacters"

                // Warna merah jika melebihi batas normal
                if (currentLength > maxCharacters) {
                    textCount.setTextColor(ContextCompat.getColor(requireActivity(), android.R.color.holo_red_dark))
                } else {
                    textCount.setTextColor(ContextCompat.getColor(requireActivity(), android.R.color.darker_gray))
                }

                // Potong otomatis jika melebihi batas toleransi
                if (currentLength > maxWithTolerance) {
                    val trimmed = url.substring(0, maxWithTolerance)
                    editText.setText(trimmed)
                    editText.setSelection(trimmed.length)
                }

                // 🌐 Validasi URL presisi
                val platform = detectPlatformPrecise(url)
                if (url.isEmpty()) {
                    inputLayout.error = null
                } else if (platform == "invalid") {
                    inputLayout.error = "Link tidak valid atau formatnya salah (pastikan lengkap)"
                } else {
                    inputLayout.error = null
                }
                setDownloadButtonEnabled(platform != "invalid")

            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }    //end oncreated

    //inisialisasi
    private var toastCooldown = false
    private var hasUserInput = false

    private fun isLinkValid(link: String): Boolean {
        val pattern = Regex("^(https?://)?(www\\.)?(tiktok\\.com|vt\\.tiktok\\.com|youtube\\.com|youtu\\.be)/.+")
        return pattern.matches(link)
    }

    private fun setDownloadButtonEnabled(enabled: Boolean) {
        downloadButton.isEnabled = enabled         // Tetap dipakai biar aman walau LinearLayout
        downloadButton.isClickable = enabled
        downloadButton.isFocusable = enabled
        downloadButton.alpha = if (enabled) 1f else 0.5f  // Visual efek: buram saat nonaktif
    }

    // Fungsi untuk memperbarui warna switch agar sesuai statusnya
    private fun updateSwitchUI(isChecked: Boolean) {
        context?.let {
            val thumbColor = if (isChecked) android.R.color.holo_red_dark else android.R.color.white
            val trackColor = if (isChecked) R.color.dark_gray else R.color.light_gray

            switchAd.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(it, thumbColor))
            switchAd.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(it, trackColor))
        }
    }

    private fun loadInterstitialAd() {
        if (!switchAd.isChecked || context == null) return

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(requireContext(), getString(R.string.admob_interstitial_id), adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                Log.d("AdMob", "Iklan Interstitial berhasil dimuat.")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                interstitialAd = null
                Log.e("AdMob", "Gagal memuat iklan Interstitial: ${adError.message}")
            }
        })
    }

    private fun showInterstitialAd(onAdComplete: () -> Unit) {
        if (!switchAd.isChecked || interstitialAd == null) {
            Log.e("AdMob", "Iklan Interstitial tidak tersedia atau switch mati, lanjutkan download.")
            onAdComplete()
            return
        }

        interstitialAd?.let { ad ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("AdMob", "Iklan Interstitial ditutup.")
                    interstitialAd = null
                    loadInterstitialAd()
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
        if (!switchAd.isChecked || context == null) return

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(requireContext(), getString(R.string.admob_rewarded_id), adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                Log.d("AdMob", "Iklan Rewarded berhasil dimuat.")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                rewardedAd = null
                Log.e("AdMob", "Gagal memuat iklan Rewarded: ${adError.message}")
            }
        })
    }

    private fun showRewardedAd(onAdComplete: () -> Unit) {
        if (!switchAd.isChecked || rewardedAd == null) {
            Log.e("AdMob", "Iklan Rewarded tidak tersedia atau switch mati, langsung mulai download.")
            onAdComplete()
            return
        }

        var isRewardEarned = false
        var isAdClosed = false

        rewardedAd?.let { ad ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("AdMob", "Iklan Rewarded ditutup.")
                    rewardedAd = null
                    loadRewardedAd()

                    isAdClosed = true
                    if (isRewardEarned) onAdComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e("AdMob", "Gagal menampilkan iklan: ${adError.message}")
                    onAdComplete()
                }
            }

            ad.show(requireActivity()) { rewardItem ->
                Log.d("AdMob", "User mendapat reward: ${rewardItem.amount} ${rewardItem.type}")
                isRewardEarned = true
                if (isAdClosed) onAdComplete()
            }
        }
    }

    private fun setupTheme() {
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        ThemeHelper.applyTheme(isDarkMode)
        updateThemeButtonIcon(isDarkMode)

        themeToggleButton.setOnClickListener {
            val newMode = !sharedPreferences.getBoolean("dark_mode", false)
            ThemeHelper.toggleTheme(sharedPreferences, newMode)
            updateThemeButtonIcon(newMode)
        }
    }

    private fun updateThemeButtonIcon(isDarkMode: Boolean) {
        val iconRes = if (isDarkMode) R.drawable.sun else R.drawable.moon
        themeToggleButton.setIconResource(iconRes)
    }

    private fun checkClipboardOnStart() {
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val copiedText = clipData.getItemAt(0).text.toString().trim()
            when (detectPlatform(copiedText)) {
                "youtube", "tiktok" -> {
                    editText.setText(copiedText)
                }
                "invalid" -> {
                    if (!toastCooldown) {
                        toastCooldown = true
                        Toast.makeText(
                            requireContext(),
                            "Link tidak valid. Hanya TikTok atau YouTube yang didukung.",
                            Toast.LENGTH_SHORT
                        ).show()
                        Handler(Looper.getMainLooper()).postDelayed({
                            toastCooldown = false
                        }, 2000)
                    }
                }

            }
        }
    }

    private fun checkClipboardForLink() {
        clipboardManager.addPrimaryClipChangedListener {
            val clipData = clipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val copiedText = clipData.getItemAt(0).text.toString().trim()
                when (detectPlatform(copiedText)) {
                    "youtube", "tiktok" -> {
                        editText.setText(copiedText)
                    }

                    "invalid" -> {
                        if (!toastCooldown) {
                            toastCooldown = true
                            Toast.makeText(
                                requireContext(),
                                "Link yang disalin bukan dari TikTok atau YouTube.",
                                Toast.LENGTH_SHORT
                            ).show()
                            Handler(Looper.getMainLooper()).postDelayed({
                                toastCooldown = false
                            }, 2000)
                        }
                    }
                }
            }
        }
    }

    private fun detectPlatform(url: String): String {
        val youtubePattern = Regex("""^https:\/\/(www\.)?(youtube\.com\/watch\?v=|youtu\.be\/)[\w\-]{11}.*$""")
        val tiktokPattern = Regex("""^https:\/\/(vm|vt)\.tiktok\.com\/[A-Za-z0-9]{8,}\/?$""")

        return when {
            youtubePattern.matches(url) -> "youtube"
            tiktokPattern.matches(url) -> "tiktok"
            else -> "invalid"
        }
    }
    // Fungsi bantu konversi dp ke px
    private fun Context.dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun showDownloadMenu(view: View) {
        val url = editText.text.toString().trim()
        val platform = detectPlatform(url)

        if (platform == "unknown") {
            Toast.makeText(requireContext(), "Masukkan link yang valid!", Toast.LENGTH_SHORT).show()
            return
        }

        val buttonLayout = requireActivity().findViewById<LinearLayout>(R.id.btnDownload) // Pastikan ID benar
        buttonLayout.isEnabled = false // Nonaktifkan sementara agar tidak bisa diklik dua kali

        // **Buat ProgressBar**
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
            visibility = View.VISIBLE
        }

        // **Cek parent layout tombol**
        val parent = buttonLayout.parent as? ViewGroup
        if (parent == null) {
            buttonLayout.isEnabled = true // Aktifkan kembali jika gagal mendapatkan parent
            return
        }

        // **Gunakan ukuran kecil eksplisit saat addView**
        val layoutParams = LinearLayout.LayoutParams(
            requireContext().dpToPx(27),  // <= kecilin di sini
            requireContext().dpToPx(27)
        ).apply {
            gravity = CENTER
            topMargin = requireContext().dpToPx(11)
            leftMargin = requireContext().dpToPx(5)
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
                    platform == "tiktok" && isSlide -> listOf("Gambar")
                    platform == "tiktok" -> listOf("MP4", "MP3")
                    platform == "youtube" -> listOf("MP4", "MP3")
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
                    if (selectedFormat == "Gambar" && isSlide) {
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

    private fun startDownload(url: String, format: String) {
        if (isAdShowing) return  // Cegah duplikat download
        isAdShowing = true       // Tandai bahwa proses sedang berjalan

        showRewardedAd {
            lifecycleScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    progressDownload.visibility = View.VISIBLE
                    textProgress.visibility = View.VISIBLE
                    arrowIcon.visibility = View.GONE
                    progressDownload.progress = 0
                    textProgress.text = "0%"
                }

                try {
                    val downloadUrl = TikTokDownloader.getDownloadUrl(url, format)

                    if (downloadUrl == null) {
                        showError("Gagal mendapatkan link unduhan untuk format $format!")
                        return@launch
                    }

                    val mimeType = when (format) {
                        "MP4" -> "video/mp4"
                        "MP3" -> "audio/mp3"
                        "Gambar" -> "image/jpeg"
                        else -> "application/octet-stream"
                    }

                    val username = extractUsernameFromUrl(url) ?: "unknown"
                    val timeStamp = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                    val finalFileName = "${username}_${timeStamp}.$format".lowercase()

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
                            showError("File path tidak ditemukan setelah download.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Download", "Gagal mengunduh file: ${e.message}")
                    showError("Gagal mengunduh $format!")
                } finally {
                    withContext(Dispatchers.Main) {
                        progressDownload.visibility = View.GONE
                        textProgress.visibility = View.GONE
                        arrowIcon.visibility = View.VISIBLE
                    }
                    isAdShowing = false  // Reset status
                }
            }
        }
    }

    private fun showError(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            progressDownload.visibility = View.GONE
            textProgress.visibility = View.GONE
            arrowIcon.visibility = View.VISIBLE
            isAdShowing = false
        }
    }

    //gambar
    private fun showSlideSelectionPopup(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val slideImages = TikTokDownloader.getSlideImages(url)

            withContext(Dispatchers.Main) {
                if (slideImages.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), "Tidak ada gambar slide yang tersedia!", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val selectedImages = mutableSetOf<String>()

                // Buat adapter untuk GridView
                val imageAdapter = object : BaseAdapter() {
                    override fun getCount(): Int = slideImages.size
                    override fun getItem(position: Int): String = slideImages[position]
                    override fun getItemId(position: Int): Long = position.toLong()

                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = convertView ?: layoutInflater.inflate(R.layout.item_image_selection, parent, false)
                        val imageView = view.findViewById<ImageView>(R.id.imageView)
                        val checkOverlay = view.findViewById<ImageView>(R.id.checkOverlay)
                        val imageUrl = getItem(position)

                        imageView.outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(v: View, outline: Outline) {
                                outline.setRoundRect(0, 0, v.width, v.height, 16f)
                            }
                        }
                        imageView.clipToOutline = true

                        checkOverlay.visibility = if (selectedImages.contains(imageUrl)) View.VISIBLE else View.GONE

                        Glide.with(requireContext())
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

                // Siapkan GridView
                val gridView = GridView(requireContext()).apply {
                    numColumns = 3
                    stretchMode = GridView.STRETCH_COLUMN_WIDTH
                    verticalSpacing = 8
                    horizontalSpacing = 8
                    setPadding(16, 16, 16, 16)
                    adapter = imageAdapter
                }

                // Build dialog tanpa listener langsung
                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle("Pilih Gambar yang Akan Diunduh")
                    .setView(gridView)
                    .setPositiveButton("Unduh yang Dipilih", null)
                    .setNeutralButton("Pilih Semua", null)
                    .setNegativeButton("Batal", null)
                    .create()

                dialog.show()

                // Override tombol "Pilih Semua" supaya tidak menutup dialog
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    if (selectedImages.size == slideImages.size) {
                        // Jika semua gambar sudah dipilih, batalkan pemilihan
                        selectedImages.clear()
                    } else {
                        // Pilih semua gambar
                        selectedImages.clear()
                        selectedImages.addAll(slideImages)
                    }
                    imageAdapter.notifyDataSetChanged() // Update adapter untuk menampilkan perubahan
                }

                // Override tombol "Unduh yang Dipilih" untuk validasi dan download
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (selectedImages.isEmpty()) {
                        Toast.makeText(requireContext(), "Pilih setidaknya satu gambar!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    dialog.dismiss()  // tutup dialog sebelum iklan+download
                    if (!isAdShowing) {
                        isAdShowing = true
                        showInterstitialAd {
                            isAdShowing = false
                            downloadSelectedImages(selectedImages.toList())
                        }
                    }
                }

                // Tombol batal tutup seperti biasa
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                    dialog.dismiss()
                }
            }
        }
    }

    //gambar
    private fun downloadSelectedImages(images: List<String>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val timeStamp = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())
            var isToastShown = false  // Flag untuk mencegah notifikasi ganda

            // Setup progres
            withContext(Dispatchers.Main) {
                progressDownload.visibility = View.VISIBLE
                textProgress.visibility = View.VISIBLE
                arrowIcon.visibility = View.GONE
                progressDownload.progress = 0
                textProgress.text = "0%"
            }

            for ((index, imageUrl) in images.withIndex()) {
                try {
                    val fileName = "IMG_$timeStamp$index.jpg" // Nama file unik

                    Downloader.downloadFile(
                        context = requireContext(),
                        fileUrl = imageUrl,
                        fileName = fileName,
                        mimeType = "image/jpeg",
                        onProgressUpdate = { progress -> // Update progress setiap unduhan
                            lifecycleScope.launch(Dispatchers.Main) {
                                val currentProgress = ((index + 1) * 100) / images.size // Total progres
                                progressDownload.progress = currentProgress
                                textProgress.text = "$currentProgress%"
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

            // Setelah selesai, sembunyikan progress bar dan text
            withContext(Dispatchers.Main) {
                progressDownload.visibility = View.GONE
                textProgress.visibility = View.GONE
                arrowIcon.visibility = View.VISIBLE
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



