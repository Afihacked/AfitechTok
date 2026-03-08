package com.afitech.afitechtok.ui.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.afitech.afitechtok.R
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class WaWebFragment : Fragment() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // native guard (anti double)
    private var isDownloading = false
    private val lastDownloadAt = AtomicLong(0L) // debounce native

    private val prefs by lazy {
        requireContext().getSharedPreferences("wa_web_pref", Context.MODE_PRIVATE)
    }

    // File chooser
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (filePathCallback == null) return@registerForActivityResult

            val results = if (result.resultCode == Activity.RESULT_OK) {
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            } else null

            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }

    // Permission launcher
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.values.all { it }

            if (granted) {
                prefs.edit().putBoolean("perm_granted", true).apply()
            } else {
                // hanya tampil jika benar-benar belum pernah granted
                if (!prefs.getBoolean("perm_granted", false)) {
                    Toast.makeText(requireContext(), "Perizinan diperlukan untuk upload & voice", Toast.LENGTH_LONG).show()
                }
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {

        val view = inflater.inflate(R.layout.fragment_wa_web, container, false)

        webView = view.findViewById(R.id.webview_wa)
        progressBar = view.findViewById(R.id.progress_wa)

        requestPermissionsIfNeeded()   // sekarang idempotent (lihat fungsi)
        setupWebView()

        webView.loadUrl("https://web.whatsapp.com")

        return view
    }

    /** Minta izin hanya jika belum granted.
     *  Jika sudah → tidak ada toast & tidak memanggil launcher.
     */
    private fun requestPermissionsIfNeeded() {
        if (prefs.getBoolean("perm_granted", false)) return

        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            // hanya minta izin, tanpa toast awal
            permissionLauncher.launch(notGranted.toTypedArray())
        } else {
            prefs.edit().putBoolean("perm_granted", true).apply()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {

        val ws = webView.settings

        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.allowFileAccess = true
        ws.allowContentAccess = true
        ws.databaseEnabled = true
        ws.mediaPlaybackRequiresUserGesture = false

        // Desktop browser spoof
        ws.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        ws.useWideViewPort = true
        ws.loadWithOverviewMode = false
        ws.setSupportZoom(true)
        ws.builtInZoomControls = true
        ws.displayZoomControls = false

        webView.setInitialScale(100)

        // Cookie
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webChromeClient = object : WebChromeClient() {

            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {

                this@WaWebFragment.filePathCallback?.onReceiveValue(null)
                this@WaWebFragment.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent()
                fileChooserLauncher.launch(intent)

                return true
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) progressBar.visibility = android.view.View.GONE
            }
        }

        // Matikan total DownloadListener agar tidak ada jalur kedua
        webView.setDownloadListener(null)

        // Blob handler (single pipeline)
        webView.addJavascriptInterface(JSDownloadInterface(), "AndroidDownloader")

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progressBar.visibility = android.view.View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = android.view.View.GONE
                injectDesktopViewportOnce()
                injectBlobDownloaderOnce()
            }
        }
    }

    /** Pasang viewport desktop (idempotent) */
    private fun injectDesktopViewportOnce() {
        val js = """
            (function() {
                if (window.__afitechViewportInstalled) return;
                window.__afitechViewportInstalled = true;

                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = "viewport";
                    document.head.appendChild(meta);
                }
                meta.content = "width=1280, initial-scale=1.0";
            })();
        """
        webView.evaluateJavascript(js, null)
    }

    /** Pasang interceptor blob (idempotent + anti-bubble) */
    private fun injectBlobDownloaderOnce() {
        val js = """
            (function() {
                if (window.__afitechBlobHookInstalled) return;
                window.__afitechBlobHookInstalled = true;

                document.addEventListener('click', function(e) {
                    var a = e.target.closest("a");
                    if (!a) return;

                    if (a.href && a.href.startsWith("blob:")) {
                        // hentikan bubbling & default agar tidak double
                        e.preventDefault();
                        e.stopPropagation();

                        var name =
                            a.getAttribute("download") ||
                            a.getAttribute("aria-label") ||
                            a.innerText ||
                            "wa_file";

                        fetch(a.href)
                            .then(r => r.blob())
                            .then(blob => {
                                var reader = new FileReader();
                                reader.onloadend = function() {
                                    AndroidDownloader.downloadFile(reader.result, name);
                                };
                                reader.readAsDataURL(blob);
                            });
                    }
                }, true);
            })();
        """
        webView.evaluateJavascript(js, null)
    }

    // Save file (single, with debounce)
    inner class JSDownloadInterface {

        @JavascriptInterface
        fun downloadFile(base64Data: String, fileNameRaw: String) {
            // native debounce (500ms) untuk mencegah trigger ganda
            val now = System.currentTimeMillis()
            if (now - lastDownloadAt.get() < 500) return
            lastDownloadAt.set(now)

            if (isDownloading) return
            isDownloading = true

            try {
                val pureBase64 = base64Data.substringAfter("base64,")
                val bytes = android.util.Base64.decode(pureBase64, android.util.Base64.DEFAULT)

                val cleanName = fileNameRaw.trim().replace("[\\\\/:*?\"<>|]".toRegex(), "")
                val fileName = if (cleanName.contains(".")) cleanName else "$cleanName.bin"

                val mime = getMimeFromFileName(fileName)

                val resolver = requireContext().contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Afitech-Web")
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                val out: OutputStream? = uri?.let { resolver.openOutputStream(it) }

                out?.use {
                    it.write(bytes)
                    it.flush()
                }

                requireActivity().runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Tersimpan: Download/Afitech-Web/$fileName",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isDownloading = false
            }
        }
    }

    private fun getMimeFromFileName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.getDefault())
        return when (ext) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    fun reloadPage() {
        if (::webView.isInitialized) {
            webView.reload()
        }
    }
    override fun onPause() {
        super.onPause()
        (activity as? com.afitech.afitechtok.ui.MainActivity)?.showBottomNav()
    }
    override fun onResume() {
        super.onResume()
        (activity as? com.afitech.afitechtok.ui.MainActivity)?.hideBottomNav()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        webView.destroy()
    }
}
