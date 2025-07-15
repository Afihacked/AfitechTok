package com.afitech.sosmedtoolkit.ui.fragments

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.afitech.sosmedtoolkit.R
import com.afitech.sosmedtoolkit.data.database.AppDatabase
import com.afitech.sosmedtoolkit.data.database.DownloadHistoryDao
import com.afitech.sosmedtoolkit.network.TikTokDownloader
import com.afitech.sosmedtoolkit.ui.services.DownloadServiceTT
import com.afitech.sosmedtoolkit.utils.CuanManager
import com.afitech.sosmedtoolkit.utils.openAppWithFallback
import com.afitech.sosmedtoolkit.utils.setStatusBarColor
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


class DownloadFragmentTT : Fragment(R.layout.fragment_download_tt) {

    private lateinit var inputLayout: TextInputLayout
    private lateinit var editText: TextInputEditText
    private lateinit var downloadButton: LinearLayout
    private lateinit var arrowIcon: ImageView
    private lateinit var clipboardManager: ClipboardManager
    private var lastClipboard: String? = null
    private lateinit var progressDownload: ProgressBar
    private lateinit var textProgress: TextView
    private lateinit var unduhtext: TextView
    private lateinit var adView: AdView
    private val cuanManager = CuanManager()
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var isAdShowing = false
    private var toastCooldown = false
    private var hasUserInput = false
    private lateinit var downloadHistoryDao: DownloadHistoryDao

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                DownloadServiceTT.ACTION_PROGRESS -> {
                    val progress = intent.getIntExtra(DownloadServiceTT.EXTRA_PROGRESS, 0)
                    Log.d("DownloadFragmentTikTok", "Progress: $progress%")

                    if (isAdded) {
                        unduhtext.text = "Mengunduh...$progress%"
                        progressDownload.apply {
                            visibility = View.VISIBLE
                            this.progress = progress
                        }
                        arrowIcon.visibility = View.GONE
                    }
                }

                DownloadServiceTT.ACTION_COMPLETE -> {
                    val success = intent.getBooleanExtra(DownloadServiceTT.EXTRA_SUCCESS, false)
                    Log.d("DownloadFragmentTikTok", "Download selesai: $success")

                    if (isAdded) {
                        unduhtext.text = if (success) "Unduh Selesai" else "Coba Lagi"
                        downloadButton.isEnabled = true

                        progressDownload.visibility = View.GONE
                        textProgress.visibility = View.GONE
                        arrowIcon.visibility = View.VISIBLE

                        val msg = if (success) "Unduh TikTok selesai!" else "Unduhan TikTok gagal!"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Inisialisasi database
        val database = AppDatabase.getDatabase(requireContext())
        downloadHistoryDao = database.downloadHistoryDao()

        // Init Views
        inputLayout = view.findViewById(R.id.inputLayout)
        editText = view.findViewById(R.id.inputLink)
        downloadButton = view.findViewById(R.id.btnDownload)

//        switchAd = view.findViewById(R.id.switchAd)
        arrowIcon = view.findViewById(R.id.arrowIcon)
        progressDownload = view.findViewById(R.id.progressDownload)
        textProgress = view.findViewById(R.id.textProgress)
        unduhtext = view.findViewById(R.id.unduhtext)

//        val unduhtext = view.findViewById<TextView>(R.id.unduhtext)


        cuanManager.initializeAdMob(requireContext())
        // Mendapatkan referensi untuk AdView dan memuat iklan
        adView = view.findViewById(R.id.adView)
        cuanManager.loadAd(adView)  // Memuat iklan dengan AdMobManager

        loadRewardedAd()
        loadInterstitialAd()

        val btnOpenTikTok = view.findViewById<AppCompatImageView>(R.id.btnOpenTiktok)
        btnOpenTikTok.setOnClickListener {
            openAppWithFallback(
                context = requireContext(),
                primaryPackage = "com.ss.android.ugc.trill",
                primaryFallbackActivity = "com.ss.android.ugc.aweme.splash.SplashActivity",
                fallbackPackage = "com.zhiliaoapp.musically.go",
                fallbackFallbackActivity = "com.ss.android.ugc.aweme.main.homepage.MainActivity",
                notFoundMessage = "Aplikasi TikTok tidak ditemukan"
            )
        }

        // Event Listener untuk input teks
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Clipboard Manager
        clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        checkClipboardOnStart()     // Cek saat pertama kali fragment muncul
        checkClipboardForLink()
        clipboardManager.addPrimaryClipChangedListener {
            checkClipboardRealTime()
        }

        setupPasteButton()// Listener kalau ada copy setelahnya

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


        editText.addTextChangedListener {
            hasUserInput = !it.isNullOrBlank()
        }

        val textCount = view.findViewById<TextView>(R.id.textCount)
        val maxCharacters = 99
        val tolerance = 1
        val maxWithTolerance = maxCharacters + tolerance

// Regex ketat di luar listener
        val TIKTOK_REGEX = Regex("^https://(vt\\.|www\\.)?tiktok\\.com/[^\\s]+$")

        fun detectPlatformPrecise(url: String): String {
            return when {
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

                val btnPaste = view.findViewById<LinearLayout>(R.id.btnPaste)
                btnPaste.setOnClickListener {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = clipboard.primaryClip

                    if (clipData != null && clipData.itemCount > 0) {
                        val pasteData = clipData.getItemAt(0).coerceToText(requireContext()).toString()
                        editText.setText(pasteData)
                        editText.setSelection(pasteData.length) // Memindahkan kursor ke akhir teks
                    } else {
                        Toast.makeText(requireContext(), "Clipboard kosong", Toast.LENGTH_SHORT).show()
                    }
                }

            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }    //end oncreated

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            downloadReceiver,
            IntentFilter().apply {
                addAction(DownloadServiceTT.ACTION_PROGRESS)
                addAction(DownloadServiceTT.ACTION_COMPLETE)
            }
        )
    }
    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(downloadReceiver)
    }

    private fun checkClipboardRealTime() {
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val clipText = clipData.getItemAt(0).coerceToText(requireContext()).toString()
            if (clipText.isNotBlank() && isLinkValid(clipText) && clipText != lastClipboard) {
                editText.setText(clipText)
                editText.setSelection(clipText.length)
                lastClipboard = clipText
                Toast.makeText(requireContext(), "Link otomatis ditempel dari clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPasteButton() {
        val btnPaste = view?.findViewById<LinearLayout>(R.id.btnPaste)
        btnPaste?.setOnClickListener {
            val clipData = clipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val clipText = clipData.getItemAt(0).coerceToText(requireContext()).toString()
                if (clipText != lastClipboard) {
                    editText.setText(clipText)
                    editText.setSelection(clipText.length)
                    lastClipboard = clipText
                } else {
                    Toast.makeText(requireContext(), "Link sudah ditempel", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Clipboard kosong", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isLinkValid(link: String): Boolean {
        val pattern = Regex("^(https?://)?(www\\.)?(tiktok\\.com|vt\\.tiktok\\.com)/.+")
        return pattern.matches(link)
    }

    private fun setDownloadButtonEnabled(enabled: Boolean) {
        downloadButton.isEnabled = enabled         // Tetap dipakai biar aman walau LinearLayout
        downloadButton.isClickable = enabled
        downloadButton.isFocusable = enabled
        downloadButton.alpha = if (enabled) 1f else 0.5f  // Visual efek: buram saat nonaktif
    }

    private fun loadInterstitialAd() {
        if (!isAdded) return

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
        if (!isAdded) {
            onAdComplete()
            return
        }

        if (interstitialAd == null) {
            Log.d("AdMob", "Iklan tidak tersedia, lanjutkan proses.")
            onAdComplete()
            return
        }

        interstitialAd?.let { ad ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("AdMob", "Iklan ditutup.")
                    interstitialAd = null
                    loadInterstitialAd()
                    onAdComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e("AdMob", "Gagal tampilkan iklan: ${adError.message}")
                    onAdComplete()
                }
            }

            ad.show(requireActivity())
        }
    }

    private fun loadRewardedAd() {
        if (!isAdded) return

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
        if (!isAdded) {
            onAdComplete()
            return
        }

        if (rewardedAd == null) {
            Log.d("AdMob", "Iklan tidak tersedia, lanjutkan proses.")
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

    private fun checkClipboardOnStart() {
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val copiedText = clipData.getItemAt(0).coerceToText(requireContext()).toString().trim()
            if (copiedText == lastClipboard) return // Hindari duplikat tempel

            when (detectPlatform(copiedText)) {
                "tiktok" -> {
                    editText.setText(copiedText)
                    editText.setSelection(copiedText.length)
                    lastClipboard = copiedText
                }
                "invalid" -> {
                    if (!toastCooldown) {
                        toastCooldown = true
                        Toast.makeText(
                            requireContext(),
                            "Link tidak valid. Hanya TikTok yang didukung.",
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
                    "tiktok" -> {
                        editText.setText(copiedText)
                    }

                    "invalid" -> {
                        if (!toastCooldown) {
                            toastCooldown = true
                            Toast.makeText(
                                requireContext(),
                                "Link yang disalin bukan dari TikTok.",
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
        val tiktokPattern = Regex("""^https:\/\/(vm|vt)\.tiktok\.com\/[A-Za-z0-9]{8,}\/?$""")

        return when {
            tiktokPattern.matches(url) -> "tiktok"
            else -> "invalid"
        }
    }

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

        val buttonLayout = requireActivity().findViewById<LinearLayout>(R.id.btnDownload)
        buttonLayout.isEnabled = false

        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
            visibility = View.VISIBLE
        }

        val parent = buttonLayout.parent as? ViewGroup
        if (parent == null) {
            buttonLayout.isEnabled = true
            return
        }

        val layoutParams = LinearLayout.LayoutParams(
            requireContext().dpToPx(27),
            requireContext().dpToPx(27)
        ).apply {
            gravity = CENTER
            topMargin = requireContext().dpToPx(11)
            leftMargin = requireContext().dpToPx(5)
        }

        Handler(Looper.getMainLooper()).post {
            parent.addView(progressBar, parent.indexOfChild(buttonLayout) + 1, layoutParams)
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val isSlide = withContext(Dispatchers.IO) { TikTokDownloader.isTikTokSlide(url) }

                val formats = when {
                    platform == "tiktok" && isSlide -> listOf("Gambar")
                    platform == "tiktok" -> listOf("Videos", "Music")
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
                        startDownloadWithNotification(url, selectedFormat)  // ✅ pakai service di sini
                        // Optional: ubah tombol jadi "Menunggu..."
                        val unduhText = buttonLayout.findViewById<TextView>(R.id.unduhtext)
                        unduhText.text = "Menunggu..."
                        buttonLayout.isEnabled = false
                    }
                    true
                }

                popupMenu.show()
            } finally {
                Handler(Looper.getMainLooper()).post {
                    parent.removeView(progressBar)
                    buttonLayout.isEnabled = true
                }
            }
        }
    }

    private fun startDownloadWithNotification(videoUrl: String, format: String) {
        if (isAdShowing) return
        isAdShowing = true

        showRewardedAd {
            val downloadButton = requireActivity().findViewById<LinearLayout>(R.id.btnDownload)
            val unduhtext = downloadButton.findViewById<TextView>(R.id.unduhtext)

            downloadButton.isEnabled = false
            unduhtext.text = "Menunggu..."

            val intent = Intent(requireContext(), DownloadServiceTT::class.java).apply {
                putExtra(DownloadServiceTT.EXTRA_VIDEO_URL, videoUrl)
                putExtra(DownloadServiceTT.EXTRA_FORMAT, format)
            }

            requireContext().startService(intent)

            DownloadServiceTT.setDoneCallback { success ->
                Handler(Looper.getMainLooper()).post {
                    downloadButton.isEnabled = true
                    unduhtext.text = if (success) "Unduh Selesai" else "Coba Lagi"

                    if (!success) {
                        showError("Gagal mengunduh $format!")
                    }
                }
                isAdShowing = false
            }
        }
    }

    private fun showSlideSelectionPopup(url: String) {
        showSpinnerLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            val slideImages = TikTokDownloader.getImageUrlsIfSlide(url)

            withContext(Dispatchers.Main) {
                showSpinnerLoading(false)
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

                // Build dialog
                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle("Pilih Gambar yang Akan Diunduh")
                    .setView(gridView)
                    .setPositiveButton("Unduh yang Dipilih", null)
                    .setNeutralButton("Pilih Semua", null)
                    .setNegativeButton("Batal", null)
                    .create()

                dialog.show()

                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    if (selectedImages.size == slideImages.size) {
                        selectedImages.clear()
                    } else {
                        selectedImages.clear()
                        selectedImages.addAll(slideImages)
                    }
                    imageAdapter.notifyDataSetChanged()
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (selectedImages.isEmpty()) {
                        Toast.makeText(requireContext(), "Pilih setidaknya satu gambar!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    dialog.dismiss()
                    if (!isAdShowing) {
                        isAdShowing = true
                        showInterstitialAd {
                            isAdShowing = false
                            downloadSelectedImagesWithService(selectedImages.toList(), url)
                        }
                    }
                }

                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                    dialog.dismiss()
                }
            }
        }
    }

    private fun showSpinnerLoading(show: Boolean) {
        progressDownload?.apply {
            isIndeterminate = show
            visibility = if (show) View.VISIBLE else View.GONE
            if (!show) progress = 0 // reset saat selesai
        }
    }

    private fun downloadSelectedImagesWithService(images: List<String>, originalUrl: String) {
        val intent = Intent(requireContext(), DownloadServiceTT::class.java).apply {
            putExtra(DownloadServiceTT.EXTRA_VIDEO_URL, originalUrl)
            putExtra(DownloadServiceTT.EXTRA_FORMAT, "Gambar")
            putExtra(DownloadServiceTT.EXTRA_IS_SLIDE, true)
            putStringArrayListExtra(DownloadServiceTT.EXTRA_IMAGE_URLS, ArrayList(images))
        }
        requireContext().startService(intent)
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

    override fun onDestroyView() {
        super.onDestroyView()
        cuanManager.destroyAd(adView) // Menghancurkan iklan saat view dihancurkan
    }

    override fun onResume() {
        super.onResume()
        setStatusBarColor(R.color.sttsbar , isLightStatusBar = false)
        checkClipboardOnStart()  // ini akan berjalan tiap fragment kembali ke foreground
    }
}



