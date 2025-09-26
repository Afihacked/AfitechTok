package com.afitech.sosmedtoolkit.ui.fragments

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.afitech.sosmedtoolkit.R
import com.afitech.sosmedtoolkit.data.database.AppDatabase
import com.afitech.sosmedtoolkit.data.model.DownloadHistory
import com.afitech.sosmedtoolkit.data.repository.DownloadHistoryRepository
import com.afitech.sosmedtoolkit.databinding.FragmentHistoryListBinding
import com.afitech.sosmedtoolkit.ui.adapters.HistoryAdapter
import com.afitech.sosmedtoolkit.ui.viewmodel.HistoryListViewModel
import com.afitech.sosmedtoolkit.ui.viewmodel.HistoryListViewModelFactory
import com.afitech.sosmedtoolkit.utils.setStatusBarColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

class HistoryListFragment : Fragment() {

    private var _binding: FragmentHistoryListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HistoryAdapter

    private val db by lazy { AppDatabase.getDatabase(requireContext()).downloadHistoryDao() }
    private val viewModel by lazy {
        val repository = DownloadHistoryRepository(db)
        val factory = HistoryListViewModelFactory(repository)
        ViewModelProvider(this, factory)[HistoryListViewModel::class.java]
    }

    private var filterType: String = "All"
    private var historyJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filterType = arguments?.getString("filterType") ?: "All"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorAccent,
            R.color.colorSurface
        )

        binding.swipeRefreshLayout.setOnRefreshListener {
            reloadHistory()
        }

        adapter = HistoryAdapter(
            context = requireContext(),
            historyList = emptyList(),
            onDelete = { history ->
                val deleted = deleteFilePhysical(history)
                Log.d("DeleteFile", "Hapus filePath: ${history.filePath}, success: $deleted")
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.deleteMultiple(listOf(history)) {
                        Toast.makeText(requireContext(), "Berhasil menghapus 1 item", Toast.LENGTH_SHORT).show()
                        reloadHistory(suppressToast = true)
                    }
                }
            },
            onMultipleDelete = { list ->
                list.forEach {
                    val deleted = deleteFilePhysical(it)
                    Log.d("DeleteFile", "Hapus filePath: ${it.filePath}, success: $deleted")
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.deleteMultiple(list)
                    Toast.makeText(requireContext(), "Berhasil menghapus ${list.size} item", Toast.LENGTH_SHORT).show()
                    reloadHistory(suppressToast = true)
                }
            },
            onSelectionChanged = { updateSelectionUI() }
        )

        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewHistory.adapter = adapter
        binding.recyclerViewHistory.layoutAnimation = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_fall_down)

        binding.btnDeleteSelected.setOnClickListener {
            adapter.deleteSelectedItems()
        }

        binding.btnSelectAll.setOnClickListener {
            val totalItems = adapter.getCurrentList()
            val selectedItems = adapter.getSelectedItems()
            if (selectedItems.size == totalItems.size) {
                adapter.clearSelection()
            } else {
                adapter.updateData(totalItems)
                totalItems.forEach { adapter.selectItem(it) }
            }
            updateSelectionUI()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (adapter.isSelectionMode()) {
                adapter.cancelSelection()
            } else {
                isEnabled = false
                requireActivity().onBackPressed()
            }
        }

        reloadHistory()
    }

    private fun deleteFilePhysical(history: DownloadHistory): Boolean {
        return try {
            val uri = Uri.parse(history.filePath)
            val path = uri.path
            val file = path?.let { File(it) }

            Log.d("DeleteFile", "Coba hapus file: $path")

            val deleted = file?.delete() ?: false

            if (!deleted) {
                val contentDeleted = requireContext().contentResolver.delete(uri, null, null) > 0
                Log.d("DeleteFile", "Hapus via ContentResolver: $contentDeleted")
                contentDeleted
            } else {
                Log.d("DeleteFile", "Hapus via File API: true")
                true
            }
        } catch (e: Exception) {
            Log.e("DeleteFile", "Gagal hapus file: ${history.filePath}", e)
            false
        }
    }

    private fun reloadHistory(suppressToast: Boolean = false) {
        historyJob?.cancel()
        binding.swipeRefreshLayout.isRefreshing = true

        viewModel.loadHistory(filterType)

        historyJob = viewModel.historyList.onEach { data ->
            val validData = mutableListOf<DownloadHistory>()
            val deletedData = mutableListOf<DownloadHistory>()

            data.forEach {
                val exists = isFileExist(it.filePath)
                Log.d("FileCheck", "filePath: ${it.filePath}, exists: $exists")
                if (exists) {
                    validData.add(it)
                } else {
                    deletedData.add(it)
                }
            }

            if (deletedData.isNotEmpty()) {
                if (!suppressToast) {
                    Toast.makeText(requireContext(), "${deletedData.size} Item tidak ditemukan (dihapus dari galeri)", Toast.LENGTH_SHORT).show()
                }
                Log.w("FileCheck", "${deletedData.size} item dihapus otomatis karena file hilang")
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.deleteMultiple(deletedData)
                }
            }

            adapter.updateData(validData)
            binding.textEmpty.visibility = if (validData.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerViewHistory.visibility = if (validData.isEmpty()) View.GONE else View.VISIBLE
            binding.recyclerViewHistory.scheduleLayoutAnimation()

            updateSelectionUI()
            binding.swipeRefreshLayout.isRefreshing = false
        }.launchIn(lifecycleScope)
    }

    private fun isFileExist(filePath: String): Boolean {
        return try {
            val uri = Uri.parse(filePath)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri.scheme == "content") {
                // Cek apakah file di-trash-kan (Android 11+)
                val projection = arrayOf(MediaStore.MediaColumns.IS_TRASHED)
                requireContext().contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val isTrashedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
                        val isTrashed = cursor.getInt(isTrashedIndex) == 1
                        if (isTrashed) {
                            Log.d("FileCheck", "filePath: $filePath, STATUS: TRASHED")
                            return false
                        }
                    }
                }
            }

            when (uri.scheme) {
                "content" -> {
                    requireContext().contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                        Log.d("FileCheck", "filePath: $filePath, STATUS: SUCCESS open stream")
                        return true
                    } ?: false
                }
                "file" -> {
                    val file = File(uri.path ?: "")
                    file.exists()
                }
                null -> {
                    val file = File(filePath)
                    file.exists()
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e("FileCheck", "Error cek filePath: $filePath", e)
            false
        }
    }

    private fun View.fadeIn() {
        startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_in))
        visibility = View.VISIBLE
    }

    private fun View.fadeOut() {
        startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_out))
        visibility = View.GONE
    }

    private fun updateSelectionUI() {
        val selectedCount = adapter.getSelectedItems().size
        val totalItems = adapter.getCurrentList().size
        if (selectedCount > 0) {
            if (binding.layoutSelectionActions.visibility != View.VISIBLE) binding.layoutSelectionActions.fadeIn()
            binding.btnDeleteSelected.text = "Hapus Dipilih ($selectedCount)"
            binding.btnSelectAll.text = if (selectedCount == totalItems) "Batalkan Semua" else "Pilih Semua"
        } else {
            if (binding.layoutSelectionActions.visibility == View.VISIBLE) binding.layoutSelectionActions.fadeOut()
        }
    }

    override fun onDestroyView() {
        historyJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        reloadHistory()
        setStatusBarColor(R.color.sttsbar, isLightStatusBar = false)
    }

    companion object {
        fun newInstance(filterType: String) = HistoryListFragment().apply {
            arguments = Bundle().apply {
                putString("filterType", filterType)
            }
        }
    }
}
