package com.afitech.afitechtok.ui.fragments

import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Outline
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.afitech.afitechtok.R
import com.afitech.afitechtok.data.Downloader
import com.afitech.afitechtok.data.database.AppDatabase
import com.afitech.afitechtok.data.database.DownloadHistoryDao
import com.afitech.afitechtok.data.model.InstagramContentInfo
import com.afitech.afitechtok.utils.setStatusBarColor
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadFragmentIG : Fragment(R.layout.fragment_download_ig) {
    private lateinit var inputLayout: TextInputLayout
    private lateinit var editText: TextInputEditText
    private lateinit var downloadButton: LinearLayout
    private lateinit var arrowIcon: ImageView
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var progressDownload: ProgressBar
    private lateinit var textProgress: TextView
    private lateinit var adView: AdView
    private lateinit var downloadHistoryDao: DownloadHistoryDao

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        val database = AppDatabase.getDatabase(requireContext())
        downloadHistoryDao = database.downloadHistoryDao()

        // Init Views
        inputLayout = view.findViewById(R.id.inputLayout)
        editText = view.findViewById(R.id.inputLink)
        downloadButton = view.findViewById(R.id.btnDownload)
        adView = view.findViewById(R.id.adView)
        arrowIcon = view.findViewById(R.id.arrowIcon)
        progressDownload = view.findViewById(R.id.progressDownload)
        textProgress = view.findViewById(R.id.textProgress)

        // Event Listener untuk input teks
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Clipboard Manager
        clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        checkClipboardOnStart() // Cek saat pertama kali fragment muncul
        checkClipboardForLink()

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
        val INSTAGRAM_REGEX = Regex("^https://(www\\.)?instagram\\.com/[^\\s]+$")

        fun detectPlatformPrecise(url: String): String {
            return when {
                INSTAGRAM_REGEX.matches(url) -> "istagram"
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
    }
    override fun onResume() {
        super.onResume()
        setStatusBarColor(R.color.colorPrimary, isLightStatusBar = false)
        checkClipboardOnStart() // ini akan berjalan tiap fragment kembali ke foreground
    }

    // inisialisasi
    private var toastCooldown = false
    private var hasUserInput = false

    private fun isLinkValid(link: String): Boolean {
        val pattern = Regex("^(https?://)?(www\\.)?instagram\\.com/[^\\s]+$")
        return pattern.matches(link)
    }

    private fun setDownloadButtonEnabled(enabled: Boolean) {
        downloadButton.isEnabled = enabled // Tetap dipakai biar aman walau LinearLayout
        downloadButton.isClickable = enabled
        downloadButton.isFocusable = enabled
        downloadButton.alpha = if (enabled) 1f else 0.5f // Visual efek: buram saat nonaktif
    }

    private fun checkClipboardOnStart() {
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val copiedText = clipData.getItemAt(0).text.toString().trim()
            when (detectPlatform(copiedText)) {
                "instagram" -> { // ubah dari "tiktok" ke "instagram"
                    editText.setText(copiedText)
                }
                "invalid" -> {
                    if (!toastCooldown) {
                        toastCooldown = true
                        Toast.makeText(
                            requireContext(),
                            "Link tidak valid. Hanya Instagram yang didukung.",
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
                    "instagram" -> { // ubah dari "tiktok" ke "instagram"
                        editText.setText(copiedText)
                    }

                    "invalid" -> {
                        if (!toastCooldown) {
                            toastCooldown = true
                            Toast.makeText(
                                requireContext(),
                                "Link yang disalin bukan dari Instagram.",
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
        // Regex yang lebih luas untuk Instagram, termasuk reels, stories, posts, dll.
        val instagramPattern = Regex("^https?://(www\\.)?instagram\\.com/[^\\s]+")
        return when {
            instagramPattern.containsMatchIn(url) -> "instagram"
            else -> "invalid"
        }
    }

    // Fungsi bantu konversi dp ke px
    private fun Context.dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun detectInstagramContentType(url: String): String {
        return when {
            url.contains("/reel/") -> "Reels"
            url.contains("/stories/") -> "Story"
            url.contains("/p/") -> "Foto" // biasanya post foto/video biasa
            url.matches(Regex("^https?://(www\\.)?instagram\\.com/.+")) -> "Video" // fallback ke Video
            else -> "invalid"
        }
    }

    private fun showDownloadMenu(view: View) {
        val url = editText.text.toString().trim()
        val contentType = detectInstagramContentType(url)

        if (contentType == "invalid") {
            Toast.makeText(requireContext(), "Masukkan link Instagram yang valid!", Toast.LENGTH_SHORT).show()
            return
        }

        val formats = when (contentType) {
            "Video" -> listOf("Video")
            "Foto" -> listOf("Foto")
            "Reels" -> listOf("Reels")
            "Story" -> listOf("Story")
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
            startDownload(url, selectedFormat)
            true
        }

        popupMenu.show()
    }

    private fun cleanInstagramUrl(rawUrl: String): String {
        return rawUrl.split("?").firstOrNull() ?: rawUrl
    }

    private suspend fun fetchInstagramContentInfo(instagramUrl: String): InstagramContentInfo? {
        return try {
            val cleanUrl = cleanInstagramUrl(instagramUrl)
            val backendUrl = "https://afitech.fun/info?url=${Uri.encode(cleanUrl)}"
            val urlConnection = URL(backendUrl).openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.connectTimeout = 10000
            urlConnection.readTimeout = 10000

            if (urlConnection.responseCode == 200) {
                val response = urlConnection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                InstagramContentInfo(
                    video = json.optString("video", null),
                    images = json.optJSONArray("images")?.let { array ->
                        List(array.length()) { idx -> array.getString(idx) }
                    } ?: emptyList()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("fetchInstagramContentInfo", "Error: ${e.message}")
            null
        }
    }

    private fun startDownload(instagramUrl: String, format: String) {
        val cleanUrl = cleanInstagramUrl(instagramUrl)
        progressDownload.progress = 0
        textProgress.text = "0%"

        progressDownload.visibility = View.VISIBLE
        textProgress.visibility = View.VISIBLE
        arrowIcon.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val contentInfo = fetchInstagramContentInfo(cleanUrl)
                if (contentInfo == null) {
                    withContext(Dispatchers.Main) {
                        showError("Gagal mendapatkan informasi konten.")
                    }
                    return@launch
                }

                when (format.lowercase()) {
                    "video", "reels", "story" -> {
                        val timeStamp = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(
                            Date()
                        )
                        val fileName = "instagram_video_${System.currentTimeMillis()}$timeStamp.mp4"
                        val backendDownloadUrl = "https://afitech.fun/download/instagram" +
                            "?url=${Uri.encode(cleanUrl)}&format=mp4"

                        Downloader.downloadFile(
                            context = requireContext(),
                            fileUrl = backendDownloadUrl,
                            fileName = fileName,
                            mimeType = "video/mp4",
                            onProgressUpdate = { progress, _, _ ->
                                lifecycleScope.launch(Dispatchers.Main) {
                                    progressDownload.progress = progress
                                    textProgress.text = "$progress%"
                                }
                            },
                            downloadHistoryDao = downloadHistoryDao,
                            source = "instagram"
                        )

                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Video berhasil diunduh", Toast.LENGTH_SHORT).show()
                        }
                    }

                    "foto" -> {
                        if (contentInfo.images.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                showError("Foto tidak ditemukan.")
                            }
                            return@launch
                        }

                        if (contentInfo.images.size > 1) {
                            withContext(Dispatchers.Main) {
                                showSlideSelectionPopup(cleanUrl)
                            }
                        } else {
                            val fileName = "instagram_img_${System.currentTimeMillis()}.jpg"
                            val backendDownloadUrl = "https://afitech.fun/download/instagram-photo" +
                                "?url=${Uri.encode(cleanUrl)}"

                            Downloader.downloadFile(
                                context = requireContext(),
                                fileUrl = backendDownloadUrl,
                                fileName = fileName,
                                mimeType = "video/mp4",
                                onProgressUpdate = { progress, _, _ ->
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        progressDownload.progress = progress
                                        textProgress.text = "$progress%"
                                    }
                                },
                                downloadHistoryDao = downloadHistoryDao,
                                source = "instagram"
                            )

                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Gambar berhasil diunduh", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    else -> {
                        withContext(Dispatchers.Main) {
                            showError("Format tidak dikenali.")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Terjadi kesalahan saat mengunduh: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    progressDownload.visibility = View.GONE
                    textProgress.visibility = View.GONE
                    arrowIcon.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun downloadSelectedImages(instagramUrl: String, selectedIndices: List<Int>) {
        val cleanUrl = cleanInstagramUrl(instagramUrl)

        lifecycleScope.launch(Dispatchers.Main) {
            progressDownload.visibility = View.VISIBLE
            textProgress.visibility = View.VISIBLE
            arrowIcon.visibility = View.GONE
            progressDownload.progress = 0
            textProgress.text = "0%"
        }

        val total = selectedIndices.size

        lifecycleScope.launch(Dispatchers.IO) {
            selectedIndices.forEachIndexed { idx, slideIndex ->
                try {
                    val backendDownloadUrl = "https://afitechapi-railway.up.railway.app/download/instagram-photo" +
                        "?url=${Uri.encode(cleanUrl)}&media=$slideIndex"

                    val fileName = "instagram_img_${System.currentTimeMillis()}_$slideIndex.jpg"

                    Downloader.downloadFile(
                        context = requireContext(),
                        fileUrl = backendDownloadUrl,
                        fileName = fileName,
                        mimeType = "video/mp4",
                        onProgressUpdate = { progress, _, _ ->
                            lifecycleScope.launch(Dispatchers.Main) {
                                progressDownload.progress = progress
                                textProgress.text = "$progress%"
                            }
                        },
                        downloadHistoryDao = downloadHistoryDao,
                        source = "instagram"
                    )

                    if (idx == total - 1) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Gambar berhasil diunduh", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "Gagal mengunduh gambar: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                progressDownload.visibility = View.GONE
                textProgress.visibility = View.GONE
                arrowIcon.visibility = View.VISIBLE
            }
        }
    }

    private fun showSlideSelectionPopup(instagramUrl: String) {
        progressDownload.visibility = View.VISIBLE
        textProgress.visibility = View.VISIBLE
        progressDownload.progress = 0
        textProgress.text = "0%"
        arrowIcon.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val contentInfo = fetchInstagramContentInfo(instagramUrl)

            withContext(Dispatchers.Main) {
                if (contentInfo == null || contentInfo.images.isEmpty()) {
                    Toast.makeText(requireContext(), "Tidak ada gambar slide yang tersedia!", Toast.LENGTH_SHORT).show()
                    progressDownload.visibility = View.GONE
                    textProgress.visibility = View.GONE
                    arrowIcon.visibility = View.VISIBLE

                    return@withContext
                }

                val slideImages = contentInfo.images
                val selectedIndices = mutableSetOf<Int>()

                val imageAdapter = object : BaseAdapter() {
                    override fun getCount(): Int = slideImages.size
                    override fun getItem(position: Int): String = slideImages[position]
                    override fun getItemId(position: Int): Long = position.toLong()

                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = convertView ?: layoutInflater.inflate(R.layout.item_image_selection_shimmer, parent, false)
                        val imageView = view.findViewById<ImageView>(R.id.imageView)
                        val checkOverlay = view.findViewById<ImageView>(R.id.checkOverlay)

                        val isSelected = selectedIndices.contains(position)
                        checkOverlay.visibility = if (selectedIndices.contains(position)) View.VISIBLE else View.GONE

                        imageView.outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(v: View, outline: Outline) {
                                outline.setRoundRect(0, 0, v.width, v.height, 16f)
                            }
                        }
                        imageView.clipToOutline = true

                        Glide.with(requireContext())
                            .load(getItem(position))
                            .placeholder(R.drawable.ic_placeholder)
                            .into(imageView)

                        view.setOnClickListener {
                            if (isSelected) {
                                selectedIndices.remove(position)
                                checkOverlay.visibility = View.GONE
                            } else {
                                selectedIndices.add(position)
                                checkOverlay.visibility = View.VISIBLE
                            }
                            notifyDataSetChanged()
                        }

                        return view
                    }
                }

                val gridView = GridView(requireContext()).apply {
                    numColumns = 3
                    verticalSpacing = 8
                    horizontalSpacing = 8
                    setPadding(16, 16, 16, 16)
                    adapter = imageAdapter
                }

                val dialog = AlertDialog.Builder(requireContext())
                    .setTitle("Pilih Gambar yang Akan Diunduh")
                    .setView(gridView)
                    .setPositiveButton("Unduh yang Dipilih", null)
                    .setNeutralButton("Pilih Semua", null)
                    .setNegativeButton("Batal", null)
                    .create()

                dialog.show()

                // Pilih Semua
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    if (selectedIndices.size == slideImages.size) {
                        selectedIndices.clear()
                    } else {
                        selectedIndices.clear()
                        selectedIndices.addAll(slideImages.indices)
                    }
                    imageAdapter.notifyDataSetChanged()
                }

                // Unduh yang Dipilih
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (selectedIndices.isEmpty()) {
                        Toast.makeText(requireContext(), "Pilih setidaknya satu gambar!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    dialog.dismiss()
                    downloadSelectedImages(instagramUrl, selectedIndices.toList())
                }

                // Batal
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                    dialog.dismiss()
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
        }
    }
}
