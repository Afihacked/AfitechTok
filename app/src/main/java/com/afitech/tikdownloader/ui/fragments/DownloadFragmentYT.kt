// File: DownloadFragmentYT.kt
package com.afitech.tikdownloader.ui.fragments

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.afitech.tikdownloader.R
import com.afitech.tikdownloader.network.NetworkHelper
import com.afitech.tikdownloader.ui.components.LoadingDialogYT
import com.afitech.tikdownloader.ui.services.DownloadService
import com.afitech.tikdownloader.utils.setStatusBarColor
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class DownloadFragmentYT : Fragment() {

    companion object {
        private const val ARG_VIDEO_URL = "video_url"
        fun newInstance(url: String?) = DownloadFragmentYT().apply {
            arguments = Bundle().apply {
                putString(ARG_VIDEO_URL, url)
            }
        }
    }

    private lateinit var editTextUrl: TextInputEditText
    private lateinit var btnDownload: MaterialButton
    private lateinit var radioMp4: RadioButton
    private lateinit var radioMp3: RadioButton
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var adView: AdView
    private var dialogDismissed = false
    private lateinit var localBroadcastManager: LocalBroadcastManager

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                DownloadService.ACTION_PROGRESS -> {
                    val p = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0)
                    Log.d("DownloadFragmentYT", "Progress received: $p%")

                    if (!dialogDismissed && p > 0) {
                        Handler(Looper.getMainLooper()).post {
                            Log.d("DownloadFragmentYT", "Dismissing dialog at progress $p%")
                            LoadingDialogYT.dismiss()
                        }
                        dialogDismissed = true
                    }
                    btnDownload.text = "Mengunduh... $p%"
                }
                DownloadService.ACTION_COMPLETE -> {
                    val success = intent.getBooleanExtra(DownloadService.EXTRA_SUCCESS, false)
                    Log.d("DownloadFragmentYT", "Complete received: success=$success")

                    if (!dialogDismissed) {
                        Handler(Looper.getMainLooper()).post {
                            Log.d("DownloadFragmentYT", "Dismissing dialog on complete")
                            LoadingDialogYT.dismiss()
                        }
                        dialogDismissed = true
                    }
                    btnDownload.apply {
                        text = "Download"
                        isEnabled = true
                    }
                    val msg = if (success) "Download selesai!" else "Download gagal!"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_download_yt, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        // init UI
        editTextUrl = view.findViewById(R.id.editTextUrl)
        arguments?.getString(ARG_VIDEO_URL)?.let { editTextUrl.setText(it) }
        btnDownload = view.findViewById(R.id.btnDownload)
        radioMp4 = view.findViewById(R.id.radioMp4)
        radioMp3 = view.findViewById(R.id.radioMp3)
        adView = view.findViewById(R.id.adView)
        clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // AdMob
        AdRequest.Builder().build().also(adView::loadAd)

        // Clipboard
        checkClipboardOnStart()
        checkClipboardForLink()

        // URL validation
        editTextUrl.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val url = s.toString().trim()
                if (url.isNotEmpty() && !isYoutubeLink(url)) {
                    showToast("Link tidak valid. Harus link YouTube.")
                    editTextUrl.setText("")
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Register receiver
        localBroadcastManager = LocalBroadcastManager.getInstance(requireContext())
        val filter = IntentFilter().apply {
            addAction(DownloadService.ACTION_PROGRESS)
            addAction(DownloadService.ACTION_COMPLETE)
        }
        localBroadcastManager.registerReceiver(downloadReceiver, filter)

        // Download button
        btnDownload.setOnClickListener {
            val videoUrl = editTextUrl.text?.toString()?.trim().orEmpty()
            if (videoUrl.isEmpty()) {
                showToast("Masukkan URL terlebih dahulu!"); return@setOnClickListener
            }
            if (!NetworkHelper.isInternetAvailable(requireContext())) {
                showToast("Tidak ada koneksi internet!"); return@setOnClickListener
            }

            val format = when {
                radioMp3.isChecked -> "mp3"
                radioMp4.isChecked -> "mp4"
                else -> { showToast("Pilih format terlebih dahulu!"); return@setOnClickListener }
            }

            dialogDismissed = false
            LoadingDialogYT.show(requireContext())
            btnDownload.apply {
                isEnabled = false
                text = "Mengunduh... 0%"
            }

            Intent(requireContext(), DownloadService::class.java).apply {
                putExtra("video_url", videoUrl)
                putExtra("format", format)
            }.also(requireContext()::startService)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        localBroadcastManager.unregisterReceiver(downloadReceiver)
        clipboardManager.removePrimaryClipChangedListener {}
    }

    private fun checkClipboardOnStart() {
        clipboardManager.primaryClip?.let { clip ->
            if (clip.itemCount > 0) {
                val txt = clip.getItemAt(0).text.toString()
                if (txt.isNotEmpty() && isYoutubeLink(txt)) editTextUrl.setText(txt)
            }
        }
    }

    private fun checkClipboardForLink() {
        clipboardManager.addPrimaryClipChangedListener {
            clipboardManager.primaryClip?.let { clip ->
                if (clip.itemCount > 0) {
                    val txt = clip.getItemAt(0).text.toString()
                    if (txt.isNotEmpty() && isYoutubeLink(txt)) editTextUrl.setText(txt)
                }
            }
        }
    }

    private fun isYoutubeLink(text: String): Boolean {
        val regex = Regex("^(https?://)?(www\\.)?(youtube\\.com|youtu\\.be)/.+")
        return regex.containsMatchIn(text)
    }

    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }
    override fun onResume() {
        super.onResume()

        setStatusBarColor(R.color.colorPrimary, isLightStatusBar = false)

    }
}
