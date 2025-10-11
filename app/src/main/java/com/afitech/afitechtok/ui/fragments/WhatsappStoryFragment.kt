package com.afitech.afitechtok.ui.fragments

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.*
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

/**
 * Fragment untuk menampilkan / mengatur akses ke folder WhatsApp .Statuses (story).
 *
 * Behavior ringkas:
 * - Jika belum ada izin (persisted URI permission) → tampilkan tutorial + tombol "Izinkan Akses".
 * - Saat user memilih folder via SAF:
 *    • Jika menunjuk .Statuses:
 *        - jika berisi media → simpan uri dan tampilkan stories (ViewPager)
 *        - jika kosong → tampilkan empty-state (info + tombol "Buka WhatsApp" + "Periksa Ulang")
 *    • Jika bukan .Statuses → tampilkan dialog panduan langkah berikutnya (dan langsung buka SAF lagi jika user tekan "Mengerti")
 *
 * Catatan: panduan sekarang menyebutkan kedua varian aplikasi WhatsApp (standard & Business)
 * sehingga user tahu jika harus memilih folder com.whatsapp atau com.whatsapp.w4b.
 */
class WhatsappStoryFragment : Fragment() {

    // ViewBinding
    private var _binding: FragmentWhatsappStoryBinding? = null
    private val binding get() = _binding!!

    // SharedPreferences untuk menyimpan URI pilihan
    private lateinit var sharedPreferences: SharedPreferences

    // DAO (dipakai StorySaver)
    private lateinit var downloadHistoryDao: DownloadHistoryDao

    // backup teks awal supaya bisa dikembalikan saat berpindah state
    private var originalTutorialText: CharSequence? = null
    private var originalGrantText: CharSequence? = null

    /**
     * ActivityResult untuk ACTION_OPEN_DOCUMENT_TREE (SAF).
     * Setelah user pilih folder, kita ambil URI persistable permission lalu:
     *  - jika URI menunjuk .Statuses -> cek media:
     *       - ada media -> simpan uri & tampilkan stories
     *       - tidak ada media -> tampilkan empty-state info (tanpa gambar)
     *  - jika URI bukan .Statuses -> tampilkan panduan/step (jangan tampilkan empty-state)
     */
    private val requestStorageAccessLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    // ambil permission persistable agar tetap bisa dibaca di sesi berikutnya
                    val takeFlags =
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)

                    // Validasi: bedakan antara "bukan .Statuses" dan "betul .Statuses tapi kosong"
                    showLoading()
                    binding.viewPager.postDelayed({
                        try {
                            val doc = DocumentFile.fromTreeUri(requireContext(), uri)
                            if (doc != null && isStatusesFolderDoc(doc)) {
                                // sudah menunjuk .Statuses -> cek isi
                                if (hasMediaFiles(doc)) {
                                    saveUri(uri.toString())
                                    Toast.makeText(requireContext(), "Akses folder berhasil disimpan", Toast.LENGTH_SHORT).show()
                                    hideLoading()
                                    showStoryUI()
                                } else {
                                    hideLoading()
                                    // .Statuses tapi kosong -> tampilkan empty-state (info, tanpa lottie)
                                    showEmptyStateUI()
                                }
                            } else {
                                // bukan .Statuses -> panduan langkah berikutnya (jangan tampilkan empty-state)
                                hideLoading()
                                showGuideDialogFor(doc)
                            }
                        } catch (e: Exception) {
                            hideLoading()
                            e.printStackTrace()
                            Toast.makeText(requireContext(), "Terjadi kesalahan saat memeriksa folder", Toast.LENGTH_SHORT).show()
                            showTutorial()
                        }
                    }, 350)
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
        // Inisialisasi DB/DAO dan hantarkan ke StorySaver
        val db = AppDatabase.getDatabase(requireContext())
        downloadHistoryDao = db.downloadHistoryDao()
        StorySaver.downloadHistoryDao = downloadHistoryDao
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWhatsappStoryBinding.inflate(inflater, container, false)
        sharedPreferences = requireContext().getSharedPreferences("TikDownloaderPrefs", Context.MODE_PRIVATE)

        // simpan teks awal supaya bisa dikembalikan saat keluar dari empty/tutorial
        originalTutorialText = binding.tutorialText.text
        originalGrantText = binding.btnGrantAccess.text

        // siapkan tombol
        binding.btnGrantAccess.setOnClickListener { showSAFTutorialDialog() }
        binding.btnRetry.setOnClickListener { retryScanSavedUri() }
        binding.btnRetry.visibility = View.GONE

        val uriSaved = getSavedUri()

        // jika belum ada izin/uri -> tutorial
        if (!hasStoragePermission() || uriSaved.isEmpty()) {
            showTutorial()
        } else {
            // ada saved uri -> periksa apakah titiknya sudah .Statuses dan ada media
            showLoading()
            binding.viewPager.postDelayed({
                try {
                    val uri = Uri.parse(uriSaved)
                    val doc = DocumentFile.fromTreeUri(requireContext(), uri)
                    if (doc != null && isStatusesFolderDoc(doc)) {
                        if (hasMediaFiles(doc)) {
                            hideLoading()
                            showStoryUI()
                        } else {
                            hideLoading()
                            showEmptyStateUI()
                        }
                    } else {
                        hideLoading()
                        // saved URI bukan .Statuses -> tunjukkan tutorial/guide (user harus melanjutkan)
                        showTutorial()
                    }
                } catch (e: Exception) {
                    hideLoading()
                    e.printStackTrace()
                    showTutorial()
                }
            }, 350)
        }

        return binding.root
    }

    // ---------------- UI states ----------------

    /** Tampilkan tutorial (state awal ketika belum ada izin) */
    private fun showTutorial() {
        restoreOriginalTutorial()
        binding.tutorialBlock.visibility = View.VISIBLE
        binding.viewPager.visibility = View.GONE
        binding.tabLayout.visibility = View.GONE

        binding.lottieGuide.visibility = View.VISIBLE
        binding.btnRetry.visibility = View.GONE

        binding.btnGrantAccess.text = originalGrantText ?: "Izinkan Akses"
        binding.btnGrantAccess.setOnClickListener { showSAFTutorialDialog() }
    }

    /** Tampilkan UI stories (ViewPager) */
    private fun showStoryUI() {
        restoreOriginalTutorial()
        binding.tutorialBlock.visibility = View.GONE
        binding.viewPager.visibility = View.VISIBLE
        binding.tabLayout.visibility = View.VISIBLE

        val pagerAdapter = StoryPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        setupViewPagerWithTabs()
    }

    /**
     * Empty-state: folder .Statuses sudah dipilih tetapi masih kosong.
     * Tampilkan informasi (tanpa lottie), tombol buka WhatsApp, dan tombol periksa ulang.
     */
    private fun showEmptyStateUI() {
        binding.tutorialBlock.visibility = View.VISIBLE
        binding.viewPager.visibility = View.GONE
        binding.tabLayout.visibility = View.GONE

        // sembunyikan lottie agar tampilan lebih informatif
        binding.lottieGuide.visibility = View.GONE

        // ubah teks & tombol
        binding.tutorialText.text = "Folder .Statuses sudah dipilih tetapi belum ditemukan story.\n\nSilakan buka WhatsApp/WhatsApp Business dan tonton story teman Anda. Setelah itu tekan 'Periksa Ulang'."

        binding.btnGrantAccess.text = "Buka WhatsApp"
        binding.btnGrantAccess.setOnClickListener { openWhatsApp() }

        binding.btnRetry.visibility = View.VISIBLE
        binding.btnRetry.setOnClickListener { retryScanSavedUri() }
    }

    /** Coba scan ulang folder yang sudah disimpan */
    private fun retryScanSavedUri() {
        val saved = getSavedUri()
        if (saved.isEmpty()) {
            Toast.makeText(requireContext(), "Belum ada folder yang disimpan.", Toast.LENGTH_SHORT).show()
            showTutorial()
            return
        }

        try {
            showLoading("Memeriksa ulang folder .Statuses...")
            binding.viewPager.postDelayed({
                val uri = Uri.parse(saved)
                val doc = DocumentFile.fromTreeUri(requireContext(), uri)
                if (doc != null && isStatusesFolderDoc(doc)) {
                    if (hasMediaFiles(doc)) {
                        hideLoading()
                        showStoryUI()
                    } else {
                        hideLoading()
                        Toast.makeText(requireContext(), "Masih belum ada status.", Toast.LENGTH_SHORT).show()
                        showEmptyStateUI()
                    }
                } else {
                    hideLoading()
                    Toast.makeText(requireContext(), "Folder yang disimpan bukan .Statuses. Silakan pilih ulang.", Toast.LENGTH_SHORT).show()
                    showTutorial()
                }
            }, 350)
        } catch (e: Exception) {
            hideLoading()
            e.printStackTrace()
            showTutorial()
        }
    }

    /** Kembalikan teks tutorial ke nilai awal (jika pernah diubah) */
    private fun restoreOriginalTutorial() {
        try { binding.tutorialText.text = originalTutorialText } catch (_: Throwable) {}
        try { binding.btnGrantAccess.text = originalGrantText } catch (_: Throwable) {}
    }

    // ---------------- Helpers ----------------

    /**
     * Buka aplikasi WhatsApp (standard). Untuk WhatsApp Business kebetulan proses akses folder sama,
     * jadi user cukup membuka aplikasi (Business atau standard) secara manual jika perlu.
     */
    private fun openWhatsApp() {
        val pm = requireContext().packageManager
        // coba buka WhatsApp standard terlebih dahulu; user bisa buka Business manual dari sana juga.
        val intent = pm.getLaunchIntentForPackage("com.whatsapp")
        if (intent != null) {
            try { startActivity(intent) }
            catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), "WhatsApp tidak ditemukan.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "WhatsApp tidak ditemukan.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Setup TabLayout + ViewPager (label Gambar/Video/Lainnya) */
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

    /** Cek apakah kita sudah menyimpan persisted URI permission sebelumnya */
    private fun hasStoragePermission(): Boolean {
        return requireContext().contentResolver.persistedUriPermissions.any()
    }

    /** Ambil savedUri dari SharedPreferences */
    private fun getSavedUri(): String {
        return sharedPreferences.getString("savedUri", "") ?: ""
    }

    /** Simpan savedUri ke SharedPreferences */
    private fun saveUri(uri: String) {
        sharedPreferences.edit().putString("savedUri", uri).apply()
    }

    /** Buka SAF diarahkan ke Android/media sebagai starting hint */
    private fun requestStoragePermission() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            // HINT: arahkan ke Android/media agar user tidak mulai di root storage (vendor bisa override)
            putExtra(
                "android.provider.extra.INITIAL_URI",
                Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fmedia")
            )
        }
        requestStorageAccessLauncher.launch(intent)
    }

    /**
     * Panduan SAF: sekarang menyebutkan kedua kemungkinan nama folder/package:
     * - com.whatsapp (WhatsApp standard)
     * - com.whatsapp.w4b (WhatsApp Business)
     *
     * Instruksi yang jelas membantu pengguna awam ketika manager berangkat dari folder yang kurang spesifik.
     */
    private fun showSAFTutorialDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Panduan")
            .setMessage(
                "Ikuti langkah berikut untuk memilih folder .Statuses (WhatsApp/Business):\n\n" +
                        "1. Masuk ke folder Android\n" +
                        "2. Pilih folder media (huruf kecil)\n" +
                        "3. Pilih folder com.whatsapp  atau com.whatsapp.w4b (jika Anda pakai WhatsApp Business)\n" +
                        "4. Masuk ke folder WhatsApp\n" +
                        "5. Pilih folder Media (M huruf besar)\n" +
                        "6. Tekan titik tiga di pojok kanan atas → aktifkan 'Tampilkan file/ folder tersembunyi'\n" +
                        "7. Pilih folder .Statuses lalu tekan 'Gunakan Folder Ini'"
            )
            .setPositiveButton("Mengerti") { d, _ ->
                d.dismiss()
                requestStoragePermission()
            }
            .setCancelable(true)
            .show()
    }

    /**
     * Deteksi apakah DocumentFile menunjuk folder .Statuses (cek nama)
     * - tidak menampilkan dialog di sini; hanya pengecekan boolean
     */
    private fun isStatusesFolderDoc(doc: DocumentFile): Boolean {
        return doc.name?.contains(".Statuses", ignoreCase = true) == true
    }

    /**
     * Periksa apakah folder DocumentFile memiliki file media (jpg/png/mp4)
     */
    private fun hasMediaFiles(folder: DocumentFile): Boolean {
        val files = folder.listFiles()
        val hasMedia = files.any { f ->
            f.isFile && (f.name?.endsWith(".jpg") == true ||
                    f.name?.endsWith(".png") == true ||
                    f.name?.endsWith(".mp4") == true)
        }
        return hasMedia
    }

    /**
     * Jika user memilih folder yang bukan .Statuses, tampilkan panduan / step yang sesuai.
     * (Dipanggil setelah SAF memilih folder non-.Statuses)
     *
     * Pesan step di sini juga menyebutkan com.whatsapp / com.whatsapp.w4b sehingga panduan berlaku
     * untuk pengguna WhatsApp Business maupun standar.
     */
    private fun showGuideDialogFor(doc: DocumentFile?) {
        val guide = when {
            doc == null -> "Folder tidak dapat dibaca. Silakan pilih ulang folder."
            doc.name.equals("Android", ignoreCase = true) ->
                "Anda memilih folder Android.\n\nLanjutkan dengan masuk ke folder 'media' (huruf kecil)."
            doc.name == "media" ->
                "Anda memilih folder 'media'.\n\nLanjutkan dengan pilih folder 'com.whatsapp' atau 'com.whatsapp.w4b' (jika Anda menggunakan WhatsApp Business)."
            doc.name.equals("com.whatsapp", ignoreCase = true) || doc.name.equals("com.whatsapp.w4b", ignoreCase = true) ->
                "Anda memilih folder package WhatsApp.\n\nLanjutkan dengan masuk ke folder 'WhatsApp'."
            doc.name.equals("WhatsApp", ignoreCase = true) ->
                "Anda memilih folder WhatsApp.\n\nLanjutkan dengan pilih folder 'Media' (M huruf besar)."
            doc.name == "Media" ->
                "Anda memilih folder 'Media'.\n\n📌 Lanjutkan dengan menampilkan folder tersembunyi:\n" +
                        "1. Tekan ikon titik tiga di kanan atas\n" +
                        "2. Aktifkan 'Tampilkan file/ folder tersembunyi'\n" +
                        "3. Pilih folder '.Statuses'."
            else ->
                "Folder yang dipilih bukan lokasi yang benar.\n\nIkuti urutan:\nAndroid > media > com.whatsapp (atau com.whatsapp.w4b) > WhatsApp > Media > .Statuses"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Panduan")
            .setMessage(guide)
            .setPositiveButton("Mengerti") { d, _ ->
                d.dismiss()
                // langsung buka SAF lagi agar user tidak perlu tekan tombol izin lagi
                requestStoragePermission()
            }
            .setCancelable(true)
            .show()
    }

    /** Overlay loading */
    private fun showLoading(message: String = "Memeriksa folder...") {
        try {
            binding.loadingOverlay.visibility = View.VISIBLE
            binding.loadingText.text = message
            binding.contentContainer.visibility = View.INVISIBLE
        } catch (_: Throwable) {}
    }

    private fun hideLoading() {
        try {
            binding.loadingOverlay.visibility = View.GONE
            binding.contentContainer.visibility = View.VISIBLE
        } catch (_: Throwable) {}
    }

    override fun onResume() {
        super.onResume()
        setStatusBarColor(R.color.sttsbar, isLightStatusBar = false)

        val uriSaved = getSavedUri()
        if (hasStoragePermission() && uriSaved.isNotEmpty()) {
            // hanya periksa savedUri — tampilkan empty-state hanya bila savedUri menunjuk .Statuses tapi kosong
            showLoading()
            binding.viewPager.postDelayed({
                try {
                    val uri = Uri.parse(uriSaved)
                    val doc = DocumentFile.fromTreeUri(requireContext(), uri)
                    if (doc != null && isStatusesFolderDoc(doc)) {
                        if (hasMediaFiles(doc)) {
                            hideLoading()
                            showStoryUI()
                        } else {
                            hideLoading()
                            showEmptyStateUI() // hanya tampil kalau .Statuses tapi kosong
                        }
                    } else {
                        hideLoading()
                        showTutorial() // savedUri bukan .Statuses -> user harus lanjut memilih
                    }
                } catch (e: Exception) {
                    hideLoading()
                    e.printStackTrace()
                    showTutorial()
                }
            }, 350)
        } else {
            showTutorial()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
