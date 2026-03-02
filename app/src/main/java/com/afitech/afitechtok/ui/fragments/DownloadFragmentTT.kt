package com.afitech.afitechtok.ui.fragments

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.Gravity.CENTER
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afitech.afitechtok.R
import com.afitech.afitechtok.data.database.AppDatabase
import com.afitech.afitechtok.data.database.DownloadHistoryDao
import com.afitech.afitechtok.network.NetworkHelper
import com.afitech.afitechtok.ui.services.DownloadServiceTT
import com.afitech.afitechtok.ui.services.DownloadSession
import com.afitech.afitechtok.ui.viewmodel.TiktokViewModel
import com.afitech.afitechtok.utils.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.AdView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*
import kotlin.coroutines.resume

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
    private lateinit var adsManager: AdsManager

    private lateinit var fallbackContainer: FrameLayout
    private lateinit var downloadHistoryDao: DownloadHistoryDao

    private var isAdShowing = false
    private var toastCooldown = false
    private var hasUserInput = false

    private val viewModel: TiktokViewModel by viewModels()

    private var slideTotal = 0
    private var slideFinished = 0
    private var slideFailed = false
    private var isSlideDownload = false

    private var hasShownNoInternetToast = false
    private val TAG_DL = "TT_SLIDE_DEBUG"

    // === Broadcast Receiver ===
    // === Broadcast Receiver (SYNC WITH STATUS) ===
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {

                // ===============================
                // PROGRESS (PERSENTASE SAJA)
                // ===============================
                DownloadServiceTT.ACTION_PROGRESS -> {

                    val fileProgress = intent.getIntExtra(
                        DownloadServiceTT.EXTRA_PROGRESS, 0
                    )

                    Log.d(TAG_DL, "PROGRESS event | fileProgress=$fileProgress | finished=$slideFinished/$slideTotal | slideMode=$isSlideDownload")

                    // NORMAL MODE (video/audio)
                    if (!isSlideDownload) {
                        progressDownload.progress = fileProgress
                        unduhtext.text = "Mengunduh… $fileProgress%"
                        DownloadSession.lastProgress = fileProgress
                        return
                    }

                }

                // ===============================
                // COMPLETE (SEMUA SELESAI)
                // ===============================
                DownloadServiceTT.ACTION_COMPLETE -> {
                    if (!isAdded) return

                    val success = intent.getBooleanExtra(
                        DownloadServiceTT.EXTRA_SUCCESS,
                        false
                    )
                    val errorReason = intent.getStringExtra(
                        DownloadServiceTT.EXTRA_ERROR_REASON
                    )

                    // ===============================
                    // 🔥 MODE SLIDE (BANYAK GAMBAR)
                    // ===============================
                    if (isSlideDownload) {

                        slideFinished++

                        Log.d(TAG_DL, "COMPLETE event | success=$success | finished=$slideFinished/$slideTotal")

                        if (!success) slideFailed = true

                        if (slideTotal == 0) {
                            Log.w(TAG_DL, "slideTotal = 0 saat COMPLETE")
                            return
                        }

                        // 🔥 progress berdasarkan jumlah file selesai
                        val overall = (slideFinished * 100) / slideTotal

                        Log.d(TAG_DL, "UPDATED overall progress = $overall%")

                        progressDownload.setProgress(overall, true)
                        unduhtext.text = "Mengunduh… $overall% ($slideFinished/$slideTotal)"

                        if (slideFinished < slideTotal) return

                        Log.d(TAG_DL, "ALL SLIDES FINISHED")

                        // ===== SEMUA SELESAI =====
                        isSlideDownload = false
                        DownloadSession.isDownloading = false
                        DownloadSession.lastDownloadFinished = !slideFailed
                        DownloadSession.lastProgress = 100
                        isAdShowing = false

                        progressDownload.progress = 100
                        unduhtext.text = "Mengunduh… 100% ($slideTotal/$slideTotal)"

                        syncButtonState()

                        requireContext().showToastSafe(
                            if (slideFailed)
                                "Sebagian gambar gagal diunduh"
                            else
                                "Semua gambar berhasil diunduh"
                        )
                        hasShownNoInternetToast = false
                        return
                    }

                    // ===============================
                    // 🔹 MODE NORMAL (VIDEO / MUSIC)
                    // ===============================
                    DownloadSession.isDownloading = false
                    DownloadSession.lastDownloadFinished = success
                    isAdShowing = false

                    syncButtonState()

                    when {
                        errorReason == DownloadServiceTT.ERROR_NO_INTERNET -> {
                            requireContext().showToastSafe(
                                "Unduh gagal, internet tidak tersedia"
                            )
                        }

                        success -> {
                            requireContext().showToastSafe(
                                "Unduhan TikTok selesai"
                            )
                        }

                        else -> {
                            requireContext().showToastSafe(
                                "Unduhan TikTok gagal"
                            )
                        }
                    }

                    // ✅ reset flag di akhir NORMAL
                    hasShownNoInternetToast = false
                }
            }
        }
    }




    // === Lifecycle ===
    @SuppressLint("SourceLockedOrientationActivity")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setStatusBarColorRes(R.color.sttsbar, isLightStatusBar = true, drawBehind = true)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        arguments?.getString("video_url")?.let { url ->
            if (editText.text.isNullOrBlank()) {
                editText.setText(url)
                editText.setSelection(url.length)
            }
        }

        adsManager = AdsManager(requireContext())
        downloadHistoryDao = AppDatabase.getDatabase(requireContext()).downloadHistoryDao()

        initViews(view)
        initAds()
        initClipboard()
        initTextWatcher()
        initDownloadButton()

        syncButtonState()

    }

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

    override fun onDestroyView() {
        super.onDestroyView()
        isAdShowing = false
        try { adsManager.destroyBanner(adView) } catch (_: Throwable) {}
        try { clipboardManager.removePrimaryClipChangedListener(clipboardListener) } catch (_: Throwable) {}
    }

    override fun onResume() {
        super.onResume()
        if (
            DownloadSession.isDownloading &&
            !NetworkHelper.isInternetAvailable(requireContext()) &&
            !hasShownNoInternetToast
        ) {
            hasShownNoInternetToast = true
            showToastSafe("Koneksi internet terputus")
        }
        checkClipboardOnStart()
        syncButtonState()
    }

    // === Inisialisasi ===
    private fun initViews(view: View) {
        inputLayout = view.findViewById(R.id.inputLayout)
        editText = view.findViewById(R.id.inputLink)
        downloadButton = view.findViewById(R.id.btnDownload)
        arrowIcon = view.findViewById(R.id.arrowIcon)
        progressDownload = view.findViewById(R.id.progressDownload)
        textProgress = view.findViewById(R.id.textProgress)
        unduhtext = view.findViewById(R.id.unduhtext)
        adView = view.findViewById(R.id.adView)
        fallbackContainer = view.findViewById(R.id.fallbackContainer)

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
    }

    private fun initAds() {
        if (requireContext().areAdsEnabled()) {
            try {
                // banner: gunakan loadBanner (AdMob -> Start.io fallback)
                adsManager.loadBanner(adView, fallbackContainer)
                adView.visibility = View.VISIBLE
            } catch (_: Throwable) {
                adView.visibility = View.GONE
            }
        } else {
            adView.visibility = View.GONE
        }

        // Preload interstitial & rewarded ASAP
        adsManager.loadRewardedAd(getString(R.string.admob_rewarded_id))
        adsManager.loadInterstitialAd(getString(R.string.admob_interstitial_id))
    }

    private fun initClipboard() {
        clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        checkClipboardOnStart()
        checkClipboardForLink()
        checkClipboardRealTime()
        setupPasteButton()
    }

    private fun initTextWatcher() {
        val textCount = requireView().findViewById<TextView>(R.id.textCount)
        val maxCharacters = 100
        val tolerance = 1
        val maxWithTolerance = maxCharacters + tolerance

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { hasUserInput = !s.isNullOrBlank() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val url = s?.toString()?.trim().orEmpty()
                val currentLength = url.length

                textCount.text = "$currentLength/$maxCharacters"
                textCount.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (currentLength > maxCharacters) android.R.color.holo_red_dark
                        else android.R.color.darker_gray
                    )
                )

                if (currentLength > maxWithTolerance) {
                    val trimmed = url.substring(0, maxWithTolerance)
                    editText.setText(trimmed)
                    val safeIndex = trimmed.length.coerceAtMost(editText.text?.length ?: 0)
                    editText.setSelection(safeIndex)
                }

                val platform = if (viewModel.validateLink(url)) "tiktok" else "invalid"
                inputLayout.error = when {
                    url.isEmpty() -> null
                    platform == "invalid" -> "Link tidak valid atau formatnya salah (pastikan lengkap)"
                    else -> null
                }
                setDownloadButtonEnabled(platform != "invalid")
            }
        })
    }

    private fun initDownloadButton() {
        downloadButton.setOnClickListener {
            if (!NetworkHelper.isInternetAvailable(requireContext())) {
                requireContext().showToastSafe("Tidak ada koneksi internet")
                return@setOnClickListener
            }
            val link = editText.text.toString().trim()
            if (!hasUserInput && link.isEmpty()) {
                requireContext().showToastSafe("Silakan masukkan link terlebih dahulu")
                return@setOnClickListener
            }
            if (viewModel.validateLink(link)) showDownloadMenu(it)
            else requireContext().showToastSafe("Link tidak valid")
        }
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
        val iconPaste = view?.findViewById<ImageView>(R.id.iconPaste)

        iconPaste?.apply {
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

                    isLinkValid(clipText) -> {
                        val maxCharacters = 99
                        val tolerance = 1
                        val maxWithTolerance = maxCharacters + tolerance
                        val safeText = if (clipText.length > maxWithTolerance) {
                            clipText.substring(0, maxWithTolerance)
                        } else {
                            clipText
                        }

                        editText.setText(safeText)
                        val safeIndex = clipText.length.coerceAtMost(safeText.length)
                        editText.setSelection(safeIndex)

                        lastClipboard = safeText
                        Toast.makeText(requireContext(), "Link berhasil ditempel", Toast.LENGTH_SHORT).show()
                        this.setImageResource(R.drawable.ic_checked)
                    }

                    else -> {
                        Toast.makeText(
                            requireContext(),
                            "Link tidak valid. Harus link TikTok.",
                            Toast.LENGTH_SHORT
                        ).show()
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

    private fun checkClipboardOnStart() {
        val clipData = clipboardManager.primaryClip ?: return
        if (clipData.itemCount <= 0) return

        val copiedText = clipData.getItemAt(0).coerceToText(requireContext()).toString().trim()
        if (copiedText == lastClipboard) return

        when (detectPlatform(copiedText)) {
            "tiktok" -> {
                if (editText.text.toString().isNotBlank()) return
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
        val tiktokShortPattern = Regex("""^https://(vm|vt)\.tiktok\.com/[A-Za-z0-9\-_]+/?$""")
        return when {
            tiktokShortPattern.matches(url.trim()) -> "tiktok"
            else -> "invalid"
        }
    }

    // === Helpers: wait for readiness (polling with timeout) ===
    private suspend fun waitForRewardedReady(timeoutMillis: Long = 3000L, intervalMillis: Long = 300L): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMillis) {
            if (adsManager.isRewardedReady()) return true
            delay(intervalMillis)
        }
        return adsManager.isRewardedReady()
    }

    private suspend fun waitForInterstitialReady(timeoutMillis: Long = 3000L, intervalMillis: Long = 300L): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMillis) {
            if (adsManager.isInterstitialReady()) return true
            delay(intervalMillis)
        }
        return adsManager.isInterstitialReady()
    }

    // === Download & Ads ===

    private fun startDownloadService(
        videoUrl: String,
        format: String,
        unduhText: TextView
    ) {
        unduhText.text = "Mengunduh..."

        val intent = Intent(requireContext(), DownloadServiceTT::class.java).apply {
            putExtra(DownloadServiceTT.EXTRA_VIDEO_URL, videoUrl)
            putExtra(DownloadServiceTT.EXTRA_FORMAT, format)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && format != "Gambar") {
            // 🔥 VIDEO / MUSIC → FOREGROUND SERVICE
            ContextCompat.startForegroundService(requireContext(), intent)
        } else {
            // 🔥 GAMBAR → NORMAL SERVICE
            requireContext().startService(intent)
        }
    }


    private fun requestDownloadWithAdGate(
        url: String,
        format: String,
        unduhText: TextView
    ) {
        viewLifecycleOwner.lifecycleScope.launch {

            val fileType = when (format) {
                "Videos" -> "Video"
                "Music" -> "Audio"
                "Gambar" -> "Image"
                else -> "Other"
            }

            val count = withContext(Dispatchers.IO) {
                downloadHistoryDao.countByFileType(fileType)
            }

            if (count < 3 || !requireContext().areAdsEnabled()) {
                startDownloadService(url, format, unduhText)
                return@launch
            }

            unduhText.text = "Menunggu iklan..."
            downloadButton.isEnabled = false
            isAdShowing = true

            val rewardedReady = withContext(Dispatchers.IO) {
                waitForRewardedReady(timeoutMillis = 2000L)
            }

            if (rewardedReady) {
                adsManager.showRewardedAd(
                    onResult = {
                        isAdShowing = false
                        startDownloadService(url, format, unduhText)
                    },
                    allowFallback = false,
                    onNotShown = {
                        isAdShowing = false
                        startDownloadService(url, format, unduhText)
                    }
                )
            } else {
                isAdShowing = false
                startDownloadService(url, format, unduhText)
            }
        }
    }

    // helper to show interstitial then perform action
    private fun showInterstitialThen(actionAfter: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ready = withContext(Dispatchers.IO) { waitForInterstitialReady(timeoutMillis = 3000L) }
            if (ready) {
                adsManager.showInterstitialAd(onAdComplete = {
                    actionAfter()
                }, allowFallback = true, onNotShown = {
                    // not shown (no fallback) — still proceed
                    actionAfter()
                })
            } else {
                // not ready -> show with fallback immediately
                adsManager.showInterstitialAd(onAdComplete = {
                    actionAfter()
                }, allowFallback = true, onNotShown = null)
            }
        }
    }
    private fun showDownloadMenu(view: View) {
        val url = editText.text.toString().trim()
        val platform = detectPlatform(url)

        if (platform == "invalid") {
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
                val isSlide = viewModel.isTikTokSlide(url)
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
            platform == "tiktok" && isSlide -> listOf("Gambar", "Music")
            platform == "tiktok" -> listOf("Videos", "Music")
            else -> emptyList()
        }
    }

    private fun showFormatPopup(
        anchor: View,
        formats: List<String>,
        url: String,
        isSlide: Boolean,
        buttonLayout: LinearLayout,
    ) {
        val popupMenu = PopupMenu(
            ContextThemeWrapper(requireContext(), R.style.PopupMenuThemeOverlay),
            anchor,
            Gravity.END
        )
        popupMenu.setForceShowIcon(true)

        // 🔥 tambah menu
        formats.forEachIndexed { index, format ->
            popupMenu.menu.add(0, index, index, format)
        }

        // ✅ FORCE warna text (FIX PALING AMPUH)
        val menu = popupMenu.menu
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val span = SpannableString(item.title)
            span.setSpan(
                ForegroundColorSpan(
                    ContextCompat.getColor(requireContext(), R.color.colorOnSurface)
                ),
                0,
                span.length,
                0
            )
            item.title = span
        }

        popupMenu.setOnMenuItemClickListener { item ->
            val selectedFormat = formats[item.itemId]

            if (selectedFormat == "Gambar" && isSlide) {
                showSlideSelectionPopup(url, buttonLayout)
            } else {
                // ✅ AMBIL TEXTVIEW DARI BUTTON
                val unduhText = buttonLayout.findViewById<TextView>(R.id.unduhtext)

                unduhText.text = "Menunggu..."
                buttonLayout.isEnabled = false

                requestDownloadWithAdGate(
                    url = url,
                    format = selectedFormat,
                    unduhText = unduhText
                )
            }
            true
        }

        popupMenu.show()

        // 🔥 offset biar turun dikit
        try {
            val field = PopupMenu::class.java.getDeclaredField("mPopup")
            field.isAccessible = true
            val menuPopupHelper = field.get(popupMenu)

            val setVerticalOffset =
                menuPopupHelper.javaClass.getDeclaredMethod("setVerticalOffset", Int::class.java)
            val offset = (8 * resources.displayMetrics.density).toInt()
            setVerticalOffset.invoke(menuPopupHelper, offset)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    private fun showSlideSelectionPopup(url: String, buttonLayout: LinearLayout) {
        showSpinnerLoading(true)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val slideImages = viewModel.getImageUrlsIfSlide(url)

            withContext(Dispatchers.Main) {
                showSpinnerLoading(false)

                if (slideImages.isNullOrEmpty()) {
                    showToastSafe("Tidak ada gambar slide yang tersedia!")
                    return@withContext
                }

                buildImageSelectionDialog(slideImages, buttonLayout).show()
            }
        }
    }

    private fun buildImageSelectionDialog(
        imageUrls: List<String>,
        buttonLayout: LinearLayout,
    ): AlertDialog {
        val selectedImages = mutableSetOf<String>()

        lateinit var updateButtonLabels: () -> Unit
        var isSelectAllActive = false
        val totalCount = imageUrls.size

        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            setPadding(16, 16, 16, 16)
            clipToPadding = false
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = imageUrls.size

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val view = layoutInflater.inflate(R.layout.item_image_selection_shimmer, parent, false)
                    return object : RecyclerView.ViewHolder(view) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val view = holder.itemView
                    val imageView = view.findViewById<ImageView>(R.id.imageView)
                    val shimmerLayout = view.findViewById<ShimmerFrameLayout>(R.id.shimmerLayout)
                    val checkOverlay = view.findViewById<ImageView>(R.id.checkOverlay)
                    val imageUrl = imageUrls[position]

                    shimmerLayout.startShimmer()
                    shimmerLayout.visibility = View.VISIBLE
                    imageView.visibility = View.INVISIBLE

                    imageView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            imageView.viewTreeObserver.removeOnPreDrawListener(this)
                            imageView.outlineProvider = object : ViewOutlineProvider() {
                                override fun getOutline(v: View, outline: Outline) {
                                    outline.setRoundRect(0, 0, v.width, v.height, 16f)
                                }
                            }
                            imageView.clipToOutline = true
                            return true
                        }
                    })

                    Glide.with(view.context.applicationContext)
                        .load(imageUrl)
                        .centerCrop()
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>,
                                isFirstResource: Boolean
                            ): Boolean {
                                shimmerLayout.stopShimmer()
                                shimmerLayout.visibility = View.GONE
                                imageView.setImageResource(R.drawable.ic_error)
                                imageView.visibility = View.VISIBLE
                                return true
                            }

                            override fun onResourceReady(
                                resource: Drawable,
                                model: Any,
                                target: Target<Drawable>,
                                dataSource: DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                shimmerLayout.stopShimmer()
                                shimmerLayout.visibility = View.GONE
                                imageView.setImageDrawable(resource)
                                imageView.alpha = 0f
                                imageView.visibility = View.VISIBLE
                                imageView.animate().alpha(1f).setDuration(300).start()
                                return true
                            }
                        })
                        .into(imageView)

                    checkOverlay.visibility = if (selectedImages.contains(imageUrl)) View.VISIBLE else View.GONE

                    view.setOnClickListener {
                        if (selectedImages.contains(imageUrl)) {
                            selectedImages.remove(imageUrl)
                            checkOverlay.visibility = View.GONE
                        } else {
                            selectedImages.add(imageUrl)
                            checkOverlay.visibility = View.VISIBLE
                        }

                        isSelectAllActive = selectedImages.size == imageUrls.size
                        updateButtonLabels()
                    }
                }
            }
        }

        return AlertDialog.Builder(requireContext())
            .setTitle("Pilih Gambar")
            .setView(recyclerView)
            .setPositiveButton("Unduh", null)
            .setNeutralButton("Pilih", null)
            .setNegativeButton("Tutup", null)
            .create().apply {
                setOnShowListener {
                    val btnUnduh = getButton(AlertDialog.BUTTON_POSITIVE)
                    val btnPilihSemua = getButton(AlertDialog.BUTTON_NEUTRAL)
                    val btnBatal = getButton(AlertDialog.BUTTON_NEGATIVE)

                    val primaryColor = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
                    btnUnduh.setTextColor(primaryColor)
                    btnPilihSemua.setTextColor(primaryColor)
                    btnBatal.setTextColor(primaryColor)

                    updateButtonLabels = {
                        val selectedCount = selectedImages.size
                        btnUnduh.text = "Unduh ($selectedCount)"
                        btnPilihSemua.text = if (isSelectAllActive) {
                            "Batal Pilih ($totalCount)"
                        } else {
                            "Pilih ($totalCount)"
                        }
                    }

                    isSelectAllActive = selectedImages.size == totalCount
                    updateButtonLabels()

                    btnUnduh.setOnClickListener {
                        if (selectedImages.isEmpty()) {
                            showToastSafe("Pilih setidaknya satu gambar!")
                            return@setOnClickListener
                        }

                        buttonLayout.findViewById<TextView>(R.id.unduhtext).text = "Menunggu..."
                        buttonLayout.isEnabled = false
                        dismiss()

                        if (!isAdShowing) {
                            isAdShowing = true
                            // gunakan helper untuk memastikan prioritas AdMob sebelum fallback
                            showInterstitialThen {
                                isAdShowing = false
                                downloadSelectedImages(
                                    selectedImages.toList(),
                                    buttonLayout
                                )
                            }
                        } else {
                            showToastSafe("Mohon tunggu, iklan sedang ditampilkan.")
                        }
                    }

                    btnPilihSemua.setOnClickListener {
                        isSelectAllActive = if (isSelectAllActive) {
                            selectedImages.clear()
                            false
                        } else {
                            selectedImages.clear()
                            selectedImages.addAll(imageUrls)
                            true
                        }

                        recyclerView.adapter?.notifyDataSetChanged()
                        updateButtonLabels()
                    }

                    btnBatal.setOnClickListener {
                        dismiss()
                    }
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

    private fun downloadSelectedImages(
        images: List<String>,
        buttonLayout: LinearLayout
    ) {
        val unduhText = buttonLayout.findViewById<TextView>(R.id.unduhtext)
        unduhText.text = "Mengunduh ${images.size} gambar..."
        buttonLayout.isEnabled = false

        progressDownload.visibility = View.VISIBLE
        arrowIcon.visibility = View.GONE

        slideTotal = images.size
        Log.d(TAG_DL, "START slide download | total=$slideTotal")

        slideFinished = 0
        slideFailed = false
        isSlideDownload = true

        DownloadSession.lastProgress = 0
        progressDownload.progress = 0

        // tampil mulai dari 0%
        unduhtext.text = "Mengunduh… 0% (0/$slideTotal)"

        lifecycleScope.launch {
            for (imageUrl in images) {

                val intent = Intent(
                    requireContext(),
                    DownloadServiceTT::class.java
                ).apply {
                    putExtra(DownloadServiceTT.EXTRA_VIDEO_URL, imageUrl)
                    putExtra(DownloadServiceTT.EXTRA_FORMAT, "Gambar")
                    putExtra("SLIDE_TOTAL", slideTotal) // ⭐ PENTING
                }

                requireContext().startService(intent)

                // ⏳ tunggu sampai file selesai dulu
                waitUntilOneDownloadFinished()
            }
        }
    }
    private suspend fun waitUntilOneDownloadFinished() =
        suspendCancellableCoroutine { cont ->

            val lbm = LocalBroadcastManager.getInstance(requireContext())

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == DownloadServiceTT.ACTION_COMPLETE) {
                        lbm.unregisterReceiver(this)
                        if (!cont.isCompleted) cont.resume(Unit)
                    }
                }
            }

            lbm.registerReceiver(receiver, IntentFilter(DownloadServiceTT.ACTION_COMPLETE))

            cont.invokeOnCancellation {
                lbm.unregisterReceiver(receiver)
            }
        }
    fun onNotificationOpened(videoUrl: String?) {
        if (videoUrl.isNullOrEmpty() || !isAdded) return

        editText.setText(videoUrl)
        editText.setSelection(videoUrl.length)

        // hanya sync STATE, bukan teks
        syncButtonState()
    }

    private fun syncButtonState() {
        if (!isAdded) return

        when {
            // 🔵 DOWNLOAD MASIH BERJALAN
            DownloadSession.isDownloading -> {
                downloadButton.isEnabled = false
                progressDownload.visibility = View.VISIBLE
                arrowIcon.visibility = View.GONE

                // ❗ JANGAN set teks di sini
                // teks akan diupdate oleh ACTION_PROGRESS
                val p = DownloadSession.lastProgress
                if (p > 0) {
                    unduhtext.text = "Mengunduh… $p%"
                    progressDownload.progress = p
                }else {
                    unduhtext.text = "Menghubungkan..."
                }
            }

            // 🟢 DOWNLOAD SELESAI
            DownloadSession.lastDownloadFinished -> {
                unduhtext.text = "Unduh Lagi?"
                downloadButton.isEnabled = true
                progressDownload.visibility = View.GONE
                arrowIcon.visibility = View.VISIBLE
            }
            // ⚪ DEFAULT
            else -> {
                unduhtext.text = "Unduh"
                downloadButton.isEnabled = true
                progressDownload.visibility = View.GONE
                arrowIcon.visibility = View.VISIBLE
            }
        }
    }

}
