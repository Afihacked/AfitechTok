package com.afitech.afitechtok.ui.fragments

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.afitech.afitechtok.R
import com.afitech.afitechtok.databinding.FragmentTentangBinding
import com.afitech.afitechtok.ui.services.DownloadServiceTT
import com.afitech.afitechtok.utils.AdsManager
import com.afitech.afitechtok.utils.areAdsEnabled
import com.afitech.afitechtok.utils.setStatusBarColorRes
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.material.button.MaterialButton
import java.io.BufferedReader
import java.io.InputStreamReader

class TentangFragment : Fragment() {

    private var _binding: FragmentTentangBinding? = null
    private val binding get() = _binding!!

    // AdView nullable — lebih aman
    private var adView: AdView? = null
    private lateinit var adsManager: AdsManager

    // Ganti dengan Ad Unit ID milikmu; saat testing gunakan AdMob test id:
    // val adUnitId = "ca-app-pub-3940256099942544/6300978111" // test banner id
    private val adUnitId = "ca-app-pub-2025447201837747/8904457185"

    // Link donasi
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

        // Jika fragment menempatkan toolbar yang menjulur ke statusbar, drawBehind = true
        setStatusBarColorRes(R.color.sttsbar, isLightStatusBar = true, drawBehind = true)

        adsManager = AdsManager(requireContext())

        // set version dinamically
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.aboutVersion.text = getString(R.string.version_format, pInfo.versionName ?: "-")
        } catch (e: Exception) {
            binding.aboutVersion.text = getString(R.string.version_unknown)
        }

        // ringkasan: baca README.md asset (format markdown ringan)
        val readme = readAssetFile("README.md")
        binding.aboutSummary.text = if (readme.isNotBlank()) formatMarkdownExcerpt(readme) else getString(R.string.about_summary_placeholder)

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

        // DONASI handler
        binding.btnDonate.setOnClickListener {
            val items = arrayOf(getString(R.string.open_in_browser), getString(R.string.copy_link))
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.donate))
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(donateUrl)))
                            } catch (e: Exception) {
                                AlertDialog.Builder(requireContext())
                                    .setMessage(getString(R.string.cannot_open_link))
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show()
                            }
                        }
                        1 -> {
                            try {
                                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("donate_link", donateUrl)
                                clipboard.setPrimaryClip(clip)
                                androidx.core.content.ContextCompat.getMainExecutor(requireContext()).execute {
                                    android.widget.Toast.makeText(requireContext(), getString(R.string.link_copied), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (t: Throwable) {
                                android.widget.Toast.makeText(requireContext(), getString(R.string.copy_failed), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }.show()
        }

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

        // Inisialisasi SDK AdMob — jalankan sebelum membuat AdView
        MobileAds.initialize(requireContext()) { /* optional callback */ }

        // kalau iklan diaktifkan, buat AdView dan load
        // Inisialisasi SDK AdMob — jalankan sebelum membuat AdView
        MobileAds.initialize(requireContext()) { /* optional callback */ }

        if (requireContext().areAdsEnabled()) {
            // Buat AdView sederhana tanpa adaptive size
            adView = AdView(requireContext()).apply {
                adUnitId = this@TentangFragment.adUnitId // atau AdSize.LARGE_BANNER / AdSize.FULL_BANNER sesuai yang kamu mau
            }

            // clear container dan tambahkan
            binding.adContainer.removeAllViews()
            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }

            binding.adContainer.addView(adView, layoutParams)

            // load ad
            adView?.loadAd(AdRequest.Builder().build())
            binding.adContainer.visibility = View.VISIBLE

            // inisialisasi manager untuk fallback
            initAds()
        } else {
            binding.adContainer.removeAllViews()
            binding.adContainer.visibility = View.GONE
        }

    }

    private fun initAds() {
        // pastikan adView tersedia
        if (requireContext().areAdsEnabled() && adView != null) {
            // load banner melalui AdsManager (admob -> fallback)
            adsManager.loadBanner(adView!!, binding.fallbackContainer)
            adView?.visibility = View.VISIBLE
        } else {
            adView?.visibility = View.GONE
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
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.95).toInt(), (resources.displayMetrics.heightPixels * 0.75).toInt())
    }

    private fun formatMarkdownExcerpt(md: String): CharSequence {
        val excerpt = if (md.length > 700) md.substring(0, 700) + "...\n\n" + getString(R.string.view_readme) else md
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(sb.toString(), Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(sb.toString())
        }
    }

    private fun escapeHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun getAdaptiveAdSize(): AdSize {
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val adWidthPixels = displayMetrics.widthPixels
        val adWidth = (adWidthPixels / density).toInt().coerceAtLeast(320) // minimal width
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(requireContext(), adWidth)
    }

    override fun onDestroyView() {
        // destroy banner safely
        try {
            adView?.let {
                adsManager.destroyBanner(it)
                it.destroy()
            }
        } catch (t: Throwable) {
            // ignore cleanup errors
        }
        adView = null

        _binding = null
        super.onDestroyView()
    }
}
