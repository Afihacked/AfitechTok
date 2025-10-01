package com.afitech.afitechtok.ui.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import com.afitech.afitechtok.R
import com.afitech.afitechtok.data.StorySaver
import com.afitech.afitechtok.data.database.AppDatabase
import com.afitech.afitechtok.data.database.DownloadHistoryDao
import com.afitech.afitechtok.databinding.FragmentWhatsappStoryBinding
import com.afitech.afitechtok.ui.adapters.StoryPagerAdapter
import com.afitech.afitechtok.utils.setStatusBarColor
import com.google.android.material.tabs.TabLayoutMediator

class WhatsappStoryFragment : Fragment() {

    private lateinit var binding: FragmentWhatsappStoryBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var downloadHistoryDao: DownloadHistoryDao

    private val requestStorageAccessLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    val takeFlags =
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)

                    // 🔍 Validasi folder & isi media
                    showLoading()
                    binding.viewPager.postDelayed({
                        if (isValidStatusesFolder(uri)) {
                            saveUri(uri.toString())
                            Toast.makeText(requireContext(), "Akses folder berhasil disimpan", Toast.LENGTH_SHORT).show()
                            hideLoading()
                            showStoryUI()
                        } else {
                            hideLoading()
                            showTutorial()
                        }
                    }, 400)
                } else {
                    Toast.makeText(requireContext(), "URI tidak valid", Toast.LENGTH_SHORT).show()
                    showTutorial()
                }
            } else {
                Toast.makeText(requireContext(), "Permission dibatalkan", Toast.LENGTH_SHORT).show()
                showTutorial()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        downloadHistoryDao = db.downloadHistoryDao()
        StorySaver.downloadHistoryDao = downloadHistoryDao
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentWhatsappStoryBinding.inflate(inflater, container, false)
        sharedPreferences =
            requireContext().getSharedPreferences("TikDownloaderPrefs", Context.MODE_PRIVATE)

        val uriSaved = getSavedUri()

        // ✅ Kalau belum ada izin atau URI → langsung tutorial
        if (!hasStoragePermission() || uriSaved.isEmpty()) {
            showTutorial()
        } else {
            val uri = Uri.parse(uriSaved)
            showLoading()
            binding.viewPager.postDelayed({
                if (isValidStatusesFolder(uri)) {
                    hideLoading()
                    showStoryUI()
                } else {
                    hideLoading()
                    showTutorial()
                }
            }, 400)
        }

        return binding.root

    }

    private fun showTutorial() {
        hideLoading()
        binding.tutorialBlock.visibility = View.VISIBLE
        binding.viewPager.visibility = View.GONE
        binding.tabLayout.visibility = View.GONE

        binding.btnGrantAccess.setOnClickListener {
            showSAFTutorialDialog()
        }
    }

    private fun showStoryUI() {
        hideLoading()
        binding.tutorialBlock.visibility = View.GONE
        binding.viewPager.visibility = View.VISIBLE
        binding.tabLayout.visibility = View.VISIBLE

        val pagerAdapter = StoryPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        setupViewPagerWithTabs()
    }

    private fun setupViewPagerWithTabs() {
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Gambar"
                1 -> "Video"
                else -> "Lainnya"
            }

            val textColor = ContextCompat.getColor(tab.view.context, android.R.color.white)

            val textView = TextView(tab.view.context).apply {
                text = tab.text
                setTextColor(textColor)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            tab.customView = textView
        }.attach()
    }

    private fun hasStoragePermission(): Boolean {
        return requireContext().contentResolver.persistedUriPermissions.any()
    }

    private fun getSavedUri(): String {
        return sharedPreferences.getString("savedUri", "") ?: ""
    }

    private fun saveUri(uri: String) {
        sharedPreferences.edit().putString("savedUri", uri).apply()
    }

    /** SAF diarahkan ke Android/media */
    private fun requestStoragePermission() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            putExtra(
                "android.provider.extra.INITIAL_URI",
                Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fmedia")
            )
        }
        requestStorageAccessLauncher.launch(intent)
    }

    private fun showSAFTutorialDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Panduan")
            .setMessage(
                "Ikuti langkah berikut:\n\n" +
                        "1. Masuk ke folder Android\n" +
                        "2. Pilih folder media\n" +
                        "3. Pilih folder com.whatsapp\n" +
                        "4. Pilih folder WhatsApp\n" +
                        "5. Pilih folder Media\n" +
                        "6. Tekan titik tiga kanan atas > aktifkan 'Tampilkan folder tersembunyi'\n" +
                        "7. Pilih folder .Statuses lalu tekan 'Gunakan Folder Ini'"
            )
            .setPositiveButton("Mengerti") { d, _ ->
                d.dismiss()
                requestStoragePermission()
            }
            .setCancelable(true)
            .show()
    }

    /** Validasi folder .Statuses atau beri panduan */
    private fun isValidStatusesFolder(uri: Uri): Boolean {
        try {
            val docFile = DocumentFile.fromTreeUri(requireContext(), uri) ?: return false

            // ✅ Kalau sudah .Statuses
            if (docFile.name?.contains(".Statuses") == true) {
                return hasMediaFiles(docFile)
            }

            // ❌ Kalau belum .Statuses → panduan step
            val guide = getFolderGuideMessage(docFile)
            if (guide != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Panduan")
                    .setMessage(guide)
                    .setPositiveButton("Mengerti") { d, _ ->
                        d.dismiss()
                        requestStoragePermission()
                    }
                    .show()
            }

            return false
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /** Deteksi posisi folder terakhir user */
    private fun getFolderGuideMessage(folder: DocumentFile): String? {
        return when {
            folder.name.equals("Android", ignoreCase = true) ->
                "Anda memilih folder Android.\n\nLanjutkan dengan masuk ke folder 'media' (m huruf kecil)."

            folder.name == "media" ->
                "Anda memilih folder 'media'.\n\nLanjutkan dengan pilih folder 'com.whatsapp'."

            folder.name.equals("com.whatsapp", ignoreCase = true) ->
                "Anda memilih folder com.whatsapp.\n\nLanjutkan dengan masuk ke folder 'WhatsApp'."

            folder.name.equals("WhatsApp", ignoreCase = true) ->
                "Anda memilih folder WhatsApp.\n\nLanjutkan dengan pilih folder 'Media' (M huruf besar)."

            folder.name == "Media" ->
                "Anda memilih folder 'Media'.\n\n📌 Lanjutkan dengan menampilkan folder tersembunyi:\n" +
                        "1. Tekan ikon titik tiga di kanan atas\n" +
                        "2. Aktifkan 'Tampilkan folder tersembunyi'\n" +
                        "3. Pilih folder '.Statuses'."

            else ->
                "Folder yang dipilih bukan lokasi yang benar.\n\nIkuti urutan:\nAndroid > media > com.whatsapp > WhatsApp > Media > .Statuses"
        }
    }

    /** Cek isi folder apakah ada media */
    private fun hasMediaFiles(folder: DocumentFile): Boolean {
        val files = folder.listFiles()
        val hasMedia = files.any { f ->
            f.isFile && (
                    f.name?.endsWith(".jpg") == true ||
                            f.name?.endsWith(".png") == true ||
                            f.name?.endsWith(".mp4") == true
                    )
        }
        if (!hasMedia) {
            AlertDialog.Builder(requireContext())
                .setTitle("Belum Ada Status")
                .setMessage(
                    "Tidak ditemukan status di folder ini.\n\n" +
                            "Silakan buka WhatsApp dan tonton story teman Anda terlebih dahulu. " +
                            "Setelah itu, kembali ke aplikasi ini."
                )
                .setPositiveButton("Mengerti") { d, _ -> d.dismiss() }
                .show()
            return false
        }
        return true
    }

    /** Overlay loading */
    private fun showLoading(message: String = "Memeriksa folder...") {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.loadingText.text = message
        binding.contentContainer.visibility = View.INVISIBLE
    }

    private fun hideLoading() {
        binding.loadingOverlay.visibility = View.GONE
        binding.contentContainer.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        setStatusBarColor(R.color.sttsbar, isLightStatusBar = false)

        val uriSaved = getSavedUri()

        if (hasStoragePermission() && uriSaved.isNotEmpty()) {
            showLoading()
            val uri = Uri.parse(uriSaved)
            binding.viewPager.postDelayed({
                if (isValidStatusesFolder(uri)) {
                    hideLoading()
                    showStoryUI()
                } else {
                    hideLoading()
                    showTutorial()
                }
            }, 400)
        } else {
            showTutorial()
        }
    }
}
