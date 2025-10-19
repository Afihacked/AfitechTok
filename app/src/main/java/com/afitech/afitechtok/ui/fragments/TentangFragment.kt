package com.afitech.afitechtok.ui.fragments

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.afitech.afitechtok.R
import com.afitech.afitechtok.databinding.FragmentTentangBinding
import com.afitech.afitechtok.utils.AdsManager
import com.afitech.afitechtok.utils.areAdsEnabled
import com.afitech.afitechtok.utils.setStatusBarColorRes
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TentangFragment : Fragment() {

    private var _binding: FragmentTentangBinding? = null
    private val binding get() = _binding!!

    private lateinit var adsManager: AdsManager
    private var adView: AdView? = null

    private val donateUrl = "https://saweria.co/afitech"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTentangBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Status bar style
        setStatusBarColorRes(R.color.sttsbar, isLightStatusBar = true, drawBehind = true)

        adsManager = AdsManager(requireContext())
        adView = binding.adView

        setupAboutInfo()
        setupButtons()

        // Inisialisasi SDK AdMob
        MobileAds.initialize(requireContext())

        // Setup Iklan
        setupAds()
    }

    private fun setupAds() {
        val fallbackContainer: FrameLayout = binding.fallbackContainer

        if (!requireContext().areAdsEnabled()) {
            adView?.visibility = View.GONE
            fallbackContainer.removeAllViews()
            fallbackContainer.visibility = View.GONE
            return
        }

        try {
            // Adaptive ad size agar responsif di berbagai layar
            adView?.setAdSize(getAdaptiveAdSize())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Muat banner (AdMob → fallback ke Start.io)
        try {
            adsManager.loadBanner(adView!!, fallbackContainer)
        } catch (t: Throwable) {
            adView?.visibility = View.GONE
            fallbackContainer.removeAllViews()
            fallbackContainer.visibility = View.GONE
        }
    }

    private fun setupAboutInfo() {
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.aboutVersion.text = getString(R.string.version_format, pInfo.versionName ?: "-")
        } catch (e: Exception) {
            binding.aboutVersion.text = getString(R.string.version_unknown)
        }

        val readme = readAssetFile("README.md")
        binding.aboutSummary.text = if (readme.isNotBlank()) {
            formatMarkdownExcerpt(readme)
        } else {
            getString(R.string.about_summary_placeholder)
        }
    }

    private fun setupButtons() {
        binding.btnReadme.setOnClickListener { showTextDialog(getString(R.string.view_readme), "README.md") }
        binding.btnLicense.setOnClickListener { showTextDialog(getString(R.string.view_license), "LICENSE.md") }
        binding.btnPrivacy.setOnClickListener { showTextDialog(getString(R.string.view_privacy), "PRIVACY.md") }
        binding.btnSource.setOnClickListener {
            val repoUrl = "https://github.com/Afihacked/AfitechTok"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
            } catch (e: ActivityNotFoundException) {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.view_source))
                    .setMessage(repoUrl)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }

        binding.btnDonate.setOnClickListener { showDonateOptions() }

        binding.btnFeedback.setOnClickListener {
            val email = "afitech.services@gmail.com"
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject))
            try {
                startActivity(Intent.createChooser(intent, getString(R.string.send_feedback)))
            } catch (e: Exception) {
                AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.no_email_client))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun showDonateOptions() {
        val items = arrayOf(
            getString(R.string.open_in_browser),
            getString(R.string.copy_link),
            getString(R.string.qris) // pastikan string qris ada
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.donate))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openDonateLink()
                    1 -> copyDonateLink()
                    2 -> showQrisDialog()
                }
            }.show()
    }

    private fun openDonateLink() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(donateUrl)))
        } catch (e: Exception) {
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.cannot_open_link))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun copyDonateLink() {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("donate_link", donateUrl)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(requireContext(), getString(R.string.link_copied), android.widget.Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            android.widget.Toast.makeText(requireContext(), getString(R.string.copy_failed), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun readAssetFile(name: String): String {
        return try {
            val input = requireContext().assets.open(name)
            val reader = BufferedReader(InputStreamReader(input))
            val sb = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                sb.append(line).append("\n")
                line = reader.readLine()
            }
            reader.close()
            sb.toString()
        } catch (t: Throwable) {
            ""
        }
    }

    private fun showTextDialog(title: String, assetName: String) {
        val content = readAssetFile(assetName).ifBlank { getString(R.string.about_credits_txt) }
        val view = layoutInflater.inflate(R.layout.dialog_text_scroll, null)
        val tv = view.findViewById<TextView>(R.id.dialog_text)
        tv.text = if (assetName.endsWith(".md")) formatMarkdown(readAssetFile(assetName)) else content

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            (resources.displayMetrics.heightPixels * 0.75).toInt()
        )
    }

    private fun formatMarkdownExcerpt(md: String): CharSequence {
        val excerpt = if (md.length > 700)
            md.substring(0, 700) + "...\n\n" + getString(R.string.view_readme)
        else md
        return formatMarkdown(excerpt)
    }

    private fun formatMarkdown(md: String): CharSequence {
        val lines = md.split("\n")
        val sb = StringBuilder()
        for (line in lines) {
            when {
                line.startsWith("# ") -> sb.append("<h3>").append(escapeHtml(line.removePrefix("# ").trim())).append("</h3>")
                line.startsWith("## ") -> sb.append("<h4>").append(escapeHtml(line.removePrefix("## ").trim())).append("</h4>")
                line.startsWith("- ") -> sb.append("• ").append(escapeHtml(line.removePrefix("- ").trim())).append("<br/>")
                line.trim().isEmpty() -> sb.append("<br/>")
                else -> sb.append("<p>").append(escapeHtml(line.trim())).append("</p>")
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            Html.fromHtml(sb.toString(), Html.FROM_HTML_MODE_LEGACY)
        else
            Html.fromHtml(sb.toString())
    }

    private fun escapeHtml(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ================= QRIS dialog + save & share =================

    private fun showQrisDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_qris, null)
        val ivQris = view.findViewById<ImageView>(R.id.iv_qris)

        // Set gambar QRIS (pastikan R.drawable.qris_image ada)
        try {
            ivQris.setImageResource(R.drawable.qris_image)
        } catch (t: Throwable) {
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.qris_not_found))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.qris))
            .setView(view)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                // Save
                try {
                    val bitmap = getBitmapFromImageView(ivQris)
                    if (bitmap != null) {
                        val saved = saveBitmapToGallery(bitmap, "qris_${timestampString()}.png")
                        if (saved) {
                            android.widget.Toast.makeText(requireContext(), getString(R.string.saved_to_gallery), android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(requireContext(), getString(R.string.save_failed), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        android.widget.Toast.makeText(requireContext(), getString(R.string.save_failed), android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(requireContext(), getString(R.string.save_failed), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.share)) { _, _ ->
                // Share
                try {
                    val bitmap = getBitmapFromImageView(ivQris)
                    if (bitmap != null) {
                        shareBitmap(bitmap)
                    } else {
                        android.widget.Toast.makeText(requireContext(), getString(R.string.share_failed), android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(requireContext(), getString(R.string.share_failed), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(android.R.string.ok, null)
            .create()

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // Ambil Bitmap dari ImageView (jika drawable adalah BitmapDrawable)
    private fun getBitmapFromImageView(iv: ImageView): Bitmap? {
        val drawable = iv.drawable ?: return null
        return if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            try {
                val bmp = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun timestampString(): String {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return fmt.format(Date())
    }

    /**
     * Save bitmap to gallery using MediaStore (recommended for API 29+).
     * For older devices it writes to Environment.DIRECTORY_PICTURES and triggers media scan.
     */
    private fun saveBitmapToGallery(bitmap: Bitmap, displayName: String): Boolean {
        return try {
            val resolver = requireContext().contentResolver
            val mimeType = "image/png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Afitech")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return false
                resolver.openOutputStream(uri).use { out ->
                    if (out == null) return false
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            } else {
                // API < 29
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "Afitech")
                if (!appDir.exists()) appDir.mkdirs()
                val file = File(appDir, displayName)
                var out: OutputStream? = null
                out = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
                out.close()

                // Trigger media scan
                val uri = Uri.fromFile(file)
                requireContext().sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Share bitmap via FileProvider (write to cache then share).
     * Requires FileProvider in manifest and provider_paths.xml.
     */
    private fun shareBitmap(bitmap: Bitmap) {
        try {
            // Tulis ke cache
            val cachePath = File(requireContext().cacheDir, "images")
            cachePath.mkdirs()
            val fileName = "qris_share_${timestampString()}.png"
            val file = File(cachePath, fileName)
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()

            // Ambil uri via FileProvider
            val authority = requireContext().packageName + ".fileprovider"
            val contentUri = FileProvider.getUriForFile(requireContext(), authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "image/png"
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri)
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(requireContext(), getString(R.string.share_failed), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ================================================================

    private fun getAdaptiveAdSize(): AdSize {
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val adWidthPixels = displayMetrics.widthPixels
        val adWidth = (adWidthPixels / density).toInt().coerceAtLeast(320)
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(requireContext(), adWidth)
    }

    override fun onResume() {
        super.onResume()
        try {
            adView?.resume()
        } catch (_: Throwable) { }
    }

    override fun onPause() {
        try {
            adView?.pause()
        } catch (_: Throwable) { }
        super.onPause()
    }

    override fun onDestroyView() {
        try {
            adView?.let { adsManager.destroyBanner(it) }
        } catch (_: Throwable) { }
        adView = null
        _binding = null
        super.onDestroyView()
    }
}
