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
import android.view.Gravity
import android.view.Gravity.CENTER
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.delay


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
                    val successCount = intent.getIntExtra("success_count", -1)
                    val totalCount = intent.getIntExtra("total_count", -1)

                    Log.d("DownloadFragmentTikTok", "Download selesai: $success")

                    if (isAdded) {
                        unduhtext.text = if (success) "Unduh Selesai" else "Coba Lagi"
                        downloadButton.isEnabled = true

                        progressDownload.visibility = View.GONE
                        textProgress.visibility = View.GONE
                        arrowIcon.visibility = View.VISIBLE

                        val message = when {
                            successCount >= 0 && totalCount > 0 -> {
                                if (successCount == totalCount)
                                    "Berhasil mengunduh semua gambar ($totalCount)"
                                else
                                    "Berhasil $successCount dari $totalCount gambar"
                            }

                            else -> if (success) "Unduh TikTok selesai!" else "Unduhan TikTok gagal!"
                        }

                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }



    @SuppressLint("SourceLockedOrientationActivity")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Inisialisasi database & DAO
        downloadHistoryDao = AppDatabase.getDatabase(requireContext()).downloadHistoryDao()

        // Inisialisasi View
        inputLayout = view.findViewById(R.id.inputLayout)
        editText = view.findViewById(R.id.inputLink)
        downloadButton = view.findViewById(R.id.btnDownload)
        arrowIcon = view.findViewById(R.id.arrowIcon)
        progressDownload = view.findViewById(R.id.progressDownload)
        textProgress = view.findViewById(R.id.textProgress)
        unduhtext = view.findViewById(R.id.unduhtext)
        adView = view.findViewById(R.id.adView)
        val textCount = view.findViewById<TextView>(R.id.textCount)
        val btnOpenTikTok = view.findViewById<AppCompatImageView>(R.id.btnOpenTiktok)

        // Inisialisasi iklan
        cuanManager.initializeAdMob(requireContext())
        cuanManager.loadAd(adView)
        loadRewardedAd()
        loadInterstitialAd()

        // Buka aplikasi TikTok
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

        // Clipboard
        clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        checkClipboardOnStart()
        checkClipboardForLink()
        checkClipboardRealTime()
        setupPasteButton()

        val maxCharacters = 99
        val tolerance = 1
        val maxWithTolerance = maxCharacters + tolerance

        // TextWatcher Gabungan
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                hasUserInput = !s.isNullOrBlank()
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val url = s?.toString()?.trim().orEmpty()
                val currentLength = url.length

                // Hitung karakter
                textCount.text = "$currentLength/$maxCharacters"
                textCount.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (currentLength > maxCharacters) android.R.color.holo_red_dark
                        else android.R.color.darker_gray
                    )
                )

                // Batasi teks dengan toleransi
                if (currentLength > maxWithTolerance) {
                    val trimmed = url.substring(0, maxWithTolerance)
                    editText.setText(trimmed)
                    editText.setSelection(trimmed.length)
                }

                // Validasi URL
                val platform = if (isLinkValid(url)) "tiktok" else "invalid"
                inputLayout.error = when {
                    url.isEmpty() -> null
                    platform == "invalid" -> "Link tidak valid atau formatnya salah (pastikan lengkap)"
                    else -> null
                }
                setDownloadButtonEnabled(platform != "invalid")
            }
        })

        // Tombol Paste


        // Tombol Download
        downloadButton.setOnClickListener {
            val link = editText.text.toString().trim()
            if (!hasUserInput && link.isEmpty()) {
                Toast.makeText(requireContext(), "Silakan masukkan link terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isLinkValid(link)) {
                if (downloadButton.isClickable) {
                    showDownloadMenu(it)
                }
            } else {
                Toast.makeText(requireContext(), "Link tidak valid", Toast.LENGTH_SHORT).show()
            }
        }
    }
    //end oncreated

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
    btnPaste?.apply {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            val clipData = clipboardManager.primaryClip
            if (clipData == null || clipData.itemCount == 0) {
                Toast.makeText(requireContext(), "Anda belum salin link TikTok", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val clipText = clipData.getItemAt(0).coerceToText(requireContext()).toString().trim()
            val currentText = editText.text.toString().trim()

            when {
                clipText.isEmpty() -> {
                    Toast.makeText(requireContext(), "Anda belum salin link TikTok", Toast.LENGTH_SHORT).show()
                }

                clipText == currentText -> {
                    Toast.makeText(requireContext(), "Link sudah ditempel", Toast.LENGTH_SHORT).show()
                }

                isValidTiktokUrl(clipText) -> {
                    editText.setText(clipText)
                    editText.setSelection(clipText.length)
                    lastClipboard = clipText
                    Toast.makeText(requireContext(), "Link berhasil ditempel", Toast.LENGTH_SHORT).show()
                }

                else -> {
                    Toast.makeText(requireContext(), "Link tidak valid. Harus link TikTok.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}


    private fun isLinkValid(link: String): Boolean {
        val pattern = Regex("""^https?://(www\.|m\.)?(tiktok\.com|vt\.tiktok\.com)/.+""", RegexOption.IGNORE_CASE)
        return pattern.matches(link.trim())
    }



    private fun setDownloadButtonEnabled(enabled: Boolean) {
        downloadButton.isEnabled = enabled
        downloadButton.isClickable = enabled
        downloadButton.isFocusable = enabled
        downloadButton.alpha = if (enabled) 1f else 0.5f
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
        val ad = interstitialAd
        if (!isAdded || ad == null) {
            onAdComplete()
            return
        }

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

    private fun showRewardedAd(onResult: (Boolean) -> Unit) {
    val ad = rewardedAd
    if (!isAdded) {
        // Fragment belum ter-attach → hindari crash
        onResult(true) // anggap berhasil, jangan blokir
        return
    }

    if (ad == null) {
        // Iklan belum siap, tapi unduhan tetap boleh jalan
        Log.w("AdMob", "Iklan belum siap, lanjutkan unduhan tanpa iklan.")
        onResult(true)
        return
    }

    var isRewardEarned = false

    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
            Log.d("AdMob", "Iklan Rewarded ditutup.")
            rewardedAd = null
            loadRewardedAd()

            if (!isRewardEarned) {
                Toast.makeText(
                    requireContext(),
                    "❗ Tonton iklan sampai selesai untuk melanjutkan unduhan.",
                    Toast.LENGTH_LONG
                ).show()
            }

            onResult(isRewardEarned)
        }

        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
            Log.e("AdMob", "Gagal menampilkan iklan: ${adError.message}")
            // Jika gagal tampilkan, tetap lanjutkan unduhan
            onResult(true)
        }
    }

    ad.show(requireActivity()) { rewardItem ->
        Log.d("AdMob", "User mendapat reward: ${rewardItem.amount} ${rewardItem.type}")
        isRewardEarned = true
    }
}

    private fun checkClipboardOnStart() {
        val clipData = clipboardManager.primaryClip ?: return
        if (clipData.itemCount <= 0) return

        val copiedText = clipData.getItemAt(0).coerceToText(requireContext()).toString().trim()
        if (copiedText == lastClipboard) return

        when (detectPlatform(copiedText)) {
            "tiktok" -> {
                editText.setText(copiedText)
                editText.setSelection(copiedText.length)
                lastClipboard = copiedText
            }
            "invalid" -> {
                if (!toastCooldown) {
                    toastCooldown = true
                    Toast.makeText(requireContext(), "Link tidak valid. Hanya TikTok yang didukung.", Toast.LENGTH_SHORT).show()
                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(2000)
                        toastCooldown = false
                    }
                }
            }
        }
    }


    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clipData = clipboardManager.primaryClip ?: return@OnPrimaryClipChangedListener
        if (clipData.itemCount <= 0) return@OnPrimaryClipChangedListener

        val copiedText = clipData.getItemAt(0).coerceToText(requireContext()).toString().trim()
        when (detectPlatform(copiedText)) {
            "tiktok" -> editText.setText(copiedText)
            "invalid" -> {
                if (!toastCooldown) {
                    toastCooldown = true
                    Toast.makeText(requireContext(), "Link yang disalin bukan dari TikTok.", Toast.LENGTH_SHORT).show()
                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(2000)
                        toastCooldown = false
                    }
                }
            }
        }
    }

    private fun checkClipboardForLink() {
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }


    private fun detectPlatform(url: String): String {
        val tiktokPattern = Regex("""^https://(vm|vt)\.tiktok\.com/[A-Za-z0-9]{8,}/?$""")
        return when {
            tiktokPattern.matches(url) -> "tiktok"
            else -> "invalid"
        }
    }

    private fun Context.dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun startDownloadWithNotification(videoUrl: String, format: String) {
        if (isAdShowing || !isAdded) return
        isAdShowing = true

        val downloadButton = requireActivity().findViewById<LinearLayout>(R.id.btnDownload)
        val unduhText = downloadButton.findViewById<TextView>(R.id.unduhtext)

        showRewardedAd { rewardGranted ->
            if (!isAdded) {
                isAdShowing = false
                return@showRewardedAd
            }

            if (!rewardGranted) {
                // Gagal mendapat reward, aktifkan tombol lagi
                requireActivity().runOnUiThread {
                    updateDownloadButtonState(downloadButton, unduhText, isEnabled = true, text = "Coba Lagi")
                    isAdShowing = false
                }
                return@showRewardedAd
            }

            // Jika reward berhasil, lanjut unduh
            requireActivity().runOnUiThread {
                updateDownloadButtonState(downloadButton, unduhText, isEnabled = false, text = "Menunggu...")
            }

            val intent = Intent(requireContext(), DownloadServiceTT::class.java).apply {
                putExtra(DownloadServiceTT.EXTRA_VIDEO_URL, videoUrl)
                putExtra(DownloadServiceTT.EXTRA_FORMAT, format)
            }
            ContextCompat.startForegroundService(requireContext(), intent)

            DownloadServiceTT.setDoneCallback { success ->
                if (!isAdded) return@setDoneCallback

                requireActivity().runOnUiThread {
                    updateDownloadButtonState(
                        downloadButton,
                        unduhText,
                        isEnabled = true,
                        text = if (success) "Unduh Lagi?" else "Coba Lagi!"
                    )
                    if (!success) showError("Gagal mengunduh $format!")
                    isAdShowing = false
                    DownloadServiceTT.setDoneCallback(null)
                }
            }
        }
    }

    private fun updateDownloadButtonState(
        layout: LinearLayout,
        textView: TextView,
        isEnabled: Boolean,
        text: String
    ) {
        layout.isEnabled = isEnabled
        textView.text = text
    }


    private fun showDownloadMenu(view: View) {
        val url = editText.text.toString().trim()
        val platform = detectPlatform(url)

        if (platform == "unknown") {
            showToastSafe("Masukkan link yang valid!")
            return
        }

        val buttonLayout = requireActivity().findViewById<LinearLayout>(R.id.btnDownload)
        val parent = buttonLayout.parent as? ViewGroup ?: run {
            buttonLayout.isEnabled = true
            return
        }

        buttonLayout.isEnabled = false
        val progressBar = createInlineProgressBar()

        val layoutParams = LinearLayout.LayoutParams(requireContext().dpToPx(27), requireContext().dpToPx(27)).apply {
            gravity = CENTER
            topMargin = requireContext().dpToPx(11)
            leftMargin = requireContext().dpToPx(5)
        }

        parent.addView(progressBar, parent.indexOfChild(buttonLayout) + 1, layoutParams)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val isSlide = withContext(Dispatchers.IO) { TikTokDownloader.isTikTokSlide(url) }
                val formats = buildFormatOptions(platform, isSlide)
                showFormatPopup(view, formats, url, isSlide, buttonLayout)
            } finally {
                parent.removeView(progressBar)
                buttonLayout.isEnabled = true
            }
        }
    }

    private fun createInlineProgressBar(): ProgressBar {
        return ProgressBar(requireContext(), null, android.R.attr.progressBarStyleSmall).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            visibility = View.VISIBLE
        }
    }

    private fun buildFormatOptions(platform: String, isSlide: Boolean): List<String> {
        return when {
            platform == "tiktok" && isSlide -> listOf("Gambar")
            platform == "tiktok" -> listOf("Videos", "Music")
            else -> emptyList()
        }
    }

    private fun showFormatPopup(
        anchor: View,
        formats: List<String>,
        url: String,
        isSlide: Boolean,
        buttonLayout: LinearLayout
    ) {
        val popupMenu = PopupMenu(ContextThemeWrapper(requireContext(), R.style.PopupMenuStyle), anchor, Gravity.END)
        formats.forEachIndexed { index, format -> popupMenu.menu.add(0, index, index, format) }

        popupMenu.setOnMenuItemClickListener { item ->
            val selectedFormat = formats[item.itemId]

            if (selectedFormat == "Gambar" && isSlide) {
                showSlideSelectionPopup(url, buttonLayout)
            } else {
                startDownloadWithNotification(url, selectedFormat)
                buttonLayout.findViewById<TextView>(R.id.unduhtext).text = "Menunggu..."
                buttonLayout.isEnabled = false
            }
            true
        }

        popupMenu.show()
    }

    private fun showSlideSelectionPopup(url: String, buttonLayout: LinearLayout) {
        showSpinnerLoading(true)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val slideImages = TikTokDownloader.getImageUrlsIfSlide(url)

            withContext(Dispatchers.Main) {
                showSpinnerLoading(false)

                if (slideImages.isNullOrEmpty()) {
                    showToastSafe("Tidak ada gambar slide yang tersedia!")
                    return@withContext
                }

                buildImageSelectionDialog(slideImages, url, buttonLayout).show()
            }
        }
    }

    private fun buildImageSelectionDialog(
        imageUrls: List<String>,
        originalUrl: String,
        buttonLayout: LinearLayout
    ): AlertDialog {
        val selectedImages = mutableSetOf<String>()

        val gridView = GridView(requireContext()).apply {
            numColumns = 3
            verticalSpacing = 8
            horizontalSpacing = 8
            setPadding(16, 16, 16, 16)
            adapter = createImageAdapter(imageUrls, selectedImages)
        }

        return AlertDialog.Builder(requireContext())
            .setTitle("Pilih Gambar yang Akan Diunduh")
            .setView(gridView)
            .setPositiveButton("Unduh yang Dipilih", null)
            .setNeutralButton("Pilih Semua", null)
            .setNegativeButton("Batal", null)
            .create().apply {
                setOnShowListener {
                    val btnUnduh = getButton(AlertDialog.BUTTON_POSITIVE)
                    val btnPilihSemua = getButton(AlertDialog.BUTTON_NEUTRAL)

                    btnUnduh.setOnClickListener {
                        if (selectedImages.isEmpty()) {
                            showToastSafe("Pilih setidaknya satu gambar!")
                            return@setOnClickListener
                        }

                        // ✅ Feedback ke tombol utama
                        buttonLayout.findViewById<TextView>(R.id.unduhtext).text = "Menunggu..."
                        buttonLayout.isEnabled = false

                        dismiss()

                        if (!isAdShowing) {
                            isAdShowing = true
                            showInterstitialAd {
                                isAdShowing = false
                                downloadSelectedImagesWithService(selectedImages.toList(), originalUrl, buttonLayout)
                            }
                        }
                    }

                    btnPilihSemua.setOnClickListener {
                        if (selectedImages.size == imageUrls.size) {
                            selectedImages.clear()
                        } else {
                            selectedImages.clear()
                            selectedImages.addAll(imageUrls)
                        }
                        (gridView.adapter as BaseAdapter).notifyDataSetChanged()
                    }

                    getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                        dismiss()
                    }
                }
            }
    }

    private fun createImageAdapter(imageUrls: List<String>, selected: MutableSet<String>): BaseAdapter {
        return object : BaseAdapter() {
            override fun getCount() = imageUrls.size
            override fun getItem(position: Int) = imageUrls[position]
            override fun getItemId(position: Int) = position.toLong()

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

                Glide.with(requireContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_placeholder)
                    .into(imageView)

                checkOverlay.visibility = if (selected.contains(imageUrl)) View.VISIBLE else View.GONE

                view.setOnClickListener {
                    if (selected.contains(imageUrl)) {
                        selected.remove(imageUrl)
                        checkOverlay.visibility = View.GONE
                    } else {
                        selected.add(imageUrl)
                        checkOverlay.visibility = View.VISIBLE
                    }
                }

                return view
            }
        }
    }


    private fun showToastSafe(message: String) {
        if (!isAdded) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }


    private fun showSpinnerLoading(show: Boolean) {
        if (!isAdded || view == null) return

        progressDownload.let {
            it.isIndeterminate = show
            it.visibility = if (show) View.VISIBLE else View.GONE
            if (!show) it.progress = 0
        }
    }


    private fun downloadSelectedImagesWithService(
        images: List<String>,
        originalUrl: String,
        buttonLayout: LinearLayout
    ) {
        val intent = Intent(requireContext(), DownloadServiceTT::class.java).apply {
            putExtra(DownloadServiceTT.EXTRA_VIDEO_URL, originalUrl)
            putExtra(DownloadServiceTT.EXTRA_FORMAT, "Gambar")
            putExtra(DownloadServiceTT.EXTRA_IS_SLIDE, true)
            putStringArrayListExtra(DownloadServiceTT.EXTRA_IMAGE_URLS, ArrayList(images))
        }

        ContextCompat.startForegroundService(requireContext(), intent)

        DownloadServiceTT.setDoneCallback { success ->
            if (!isAdded) return@setDoneCallback

            requireActivity().runOnUiThread {
                val unduhText = buttonLayout.findViewById<TextView>(R.id.unduhtext)
                updateDownloadButtonState(
                    buttonLayout,
                    unduhText,
                    isEnabled = true,
                    text = if (success) "Unduh Lagi?" else "Coba Lagi"
                )
                if (!success) showError("Gagal mengunduh gambar!")
                DownloadServiceTT.setDoneCallback(null)
            }
        }
    }



    private fun showError(message: String) {
        if (!isAdded || view == null) return

        lifecycleScope.launch(Dispatchers.Main) {
            if (!isAdded || view == null) return@launch

            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            progressDownload.visibility = View.GONE
            textProgress.visibility = View.GONE
            arrowIcon.visibility = View.VISIBLE
            isAdShowing = false
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        cuanManager.destroyAd(adView)
        DownloadServiceTT.setDoneCallback(null)
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
// Menghancurkan iklan saat view dihancurkan
    }

    override fun onResume() {
        super.onResume()
        setStatusBarColor(R.color.sttsbar , isLightStatusBar = false)
        checkClipboardOnStart()  // ini akan berjalan tiap fragment kembali ke foreground
    }
}



