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

    private lateinit var adView: AdView
    private lateinit var adContainer: FrameLayout

    // Ganti dengan Ad Unit ID milikmu jika perlu
    private val adUnitId = "ca-app-pub-2025447201837747/8904457185"

    // Link donasi
    private val donateUrl = "https://saweria.co/afitech"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_tentang, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Jika fragment menempatkan toolbar yang menjulur ke statusbar, drawBehind = true
        setStatusBarColorRes(R.color.sttsbar, isLightStatusBar = true, drawBehind = true)

        adContainer = view.findViewById(R.id.adContainer)

        // init views (MaterialButton)
        val tvAppName = view.findViewById<TextView>(R.id.about_app_name)
        val tvVersion = view.findViewById<TextView>(R.id.about_version)
        val tvSummary = view.findViewById<TextView>(R.id.about_summary)
        val btnReadme = view.findViewById<MaterialButton>(R.id.btn_readme)
        val btnLicense = view.findViewById<MaterialButton>(R.id.btn_license)
        val btnPrivacy = view.findViewById<MaterialButton>(R.id.btn_privacy)
        val btnSource = view.findViewById<MaterialButton>(R.id.btn_source)
        val btnDonate = view.findViewById<MaterialButton>(R.id.btn_donate)
        val btnFeedback = view.findViewById<MaterialButton>(R.id.btn_feedback)

        // set version dinamically
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            tvVersion.text = getString(R.string.version_format, pInfo.versionName ?: "-")
        } catch (e: Exception) {
            tvVersion.text = getString(R.string.version_unknown)
        }

        // ringkasan: baca README.md asset (format markdown ringan)
        val readme = readAssetFile("README.md")
        tvSummary.text = if (readme.isNotBlank()) formatMarkdownExcerpt(readme) else getString(R.string.about_summary_placeholder)

        btnReadme.setOnClickListener { showTextDialog(getString(R.string.view_readme), "README.md") }
        btnLicense.setOnClickListener { showTextDialog(getString(R.string.view_license), "LICENSE.md") }
        btnPrivacy.setOnClickListener { showTextDialog(getString(R.string.view_privacy), "PRIVACY.md") }
        btnSource.setOnClickListener {
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
        btnDonate.setOnClickListener {
            // Dialog: Open / Copy link
            val items = arrayOf(getString(R.string.open_in_browser), getString(R.string.copy_link))
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.donate))
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> { // open in browser
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(donateUrl)))
                            } catch (e: Exception) {
                                AlertDialog.Builder(requireContext())
                                    .setMessage(getString(R.string.cannot_open_link))
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show()
                            }
                        }
                        1 -> { // copy link
                            try {
                                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("donate_link", donateUrl)
                                clipboard.setPrimaryClip(clip)
                                androidx.core.content.ContextCompat.getMainExecutor(requireContext()).execute {
                                    // show simple confirmation
                                    android.widget.Toast.makeText(requireContext(), getString(R.string.link_copied), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (t: Throwable) {
                                android.widget.Toast.makeText(requireContext(), getString(R.string.copy_failed), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }.show()
        }

        btnFeedback.setOnClickListener {
            val email = "afitech.services@gmail.com" // ganti dengan email asli
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

        // Inisialisasi SDK AdMob
        MobileAds.initialize(requireContext())

        if (requireContext().areAdsEnabled()) {
            adView = AdView(requireContext())
            adView.setAdSize(getAdaptiveAdSize())
            adView.adUnitId = adUnitId

            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }

            adContainer.removeAllViews()
            adContainer.addView(adView, layoutParams)
            adView.loadAd(AdRequest.Builder().build())
            adContainer.visibility = View.VISIBLE
        } else {
            adContainer.removeAllViews()
            adContainer.visibility = View.GONE
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
        // Jika file adalah markdown, buat render sederhana
        tv.text = if (assetName.endsWith(".md")) formatMarkdown(readAssetFile(assetName)) else content

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.show()

        // ukuran dialog nyaman
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.95).toInt(), (resources.displayMetrics.heightPixels * 0.75).toInt())
    }

    private fun formatMarkdownExcerpt(md: String): CharSequence {
        val excerpt = if (md.length > 700) md.substring(0, 700) + "...\n\n" + getString(R.string.view_readme) else md
        return formatMarkdown(excerpt)
    }

    private fun formatMarkdown(md: String): CharSequence {
        // very small markdown-to-html helper: # headings and - list -> bullets
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
        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(requireContext(), adWidth)
    }

    override fun onDestroyView() {
        if (::adView.isInitialized) adView.destroy()
        super.onDestroyView()
    }
}
