package com.afitech.sosmedtoolkit.ui.fragments

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.afitech.sosmedtoolkit.R
import com.afitech.sosmedtoolkit.data.database.AppDatabase
import com.afitech.sosmedtoolkit.data.model.DownloadHistory
import com.afitech.sosmedtoolkit.databinding.FragmentHistoryListBinding
import com.afitech.sosmedtoolkit.ui.adapters.HistoryAdapter
import com.afitech.sosmedtoolkit.utils.setStatusBarColor
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import java.io.File

class HistoryListFragment : Fragment() {

    private var _binding: FragmentHistoryListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HistoryAdapter
    private val db by lazy { AppDatabase.getDatabase(requireContext()).downloadHistoryDao() }

    private var filterType: String = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filterType = arguments?.getString("filterType") ?: "All"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Set warna animasi swipe refresh
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorAccent,
            R.color.colorSurface
        )

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadHistoryData()
        }

        binding.recyclerViewHistory.layoutAnimation =
            AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_fall_down)


        adapter = HistoryAdapter(
            context = requireContext(),
            historyList = emptyList(),
            onDelete = { history ->
                lifecycleScope.launch {
                    if (deleteFile(history)) {
                        db.deleteDownload(history)
                        adapter.updateData(adapter.getCurrentList().filter { it != history })
                    } else {
                        Toast.makeText(requireContext(), "Gagal menghapus data", Toast.LENGTH_SHORT).show()
                    }
                }
            },

                    onMultipleDelete = { listToDelete ->
                lifecycleScope.launch {
                    var successCount = 0
                    listToDelete.forEach { history ->
                        if (deleteFile(history)) {
                            db.deleteDownload(history)
                            successCount++
                        }
                    }
                    loadHistoryData()
                    Toast.makeText(requireContext(), "$successCount item dihapus", Toast.LENGTH_SHORT).show()
                }
            },
            onSelectionChanged = {
                updateSelectionUI()
            }
        )

        binding.recyclerViewHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HistoryListFragment.adapter
        }

        binding.btnDeleteSelected.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isNotEmpty()) {
                lifecycleScope.launch {
                    var successCount = 0
                    selected.forEach {
                        if (deleteFile(it)) {
                            db.deleteDownload(it)
                            successCount++
                        }
                    }
                    adapter.clearSelection()
                    loadHistoryData()
                    Toast.makeText(requireContext(), "$successCount item dihapus", Toast.LENGTH_SHORT).show()
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (adapter.isSelectionMode()) {
                adapter.cancelSelection()
            } else {
                isEnabled = false
                requireActivity().onBackPressed()
            }
        }
// Start loading with spinner animation
        binding.swipeRefreshLayout.isRefreshing = true
        loadHistoryData()
    }


    private fun updateSelectionUI() {
        val selectedCount = adapter.getSelectedItems().size
        binding.btnDeleteSelected.visibility = if (selectedCount > 0) View.VISIBLE else View.GONE
    }

    private fun isFileExist(filePath: String): Boolean {
        return try {
            val uri = Uri.parse(filePath)
            requireContext().contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            val file = File(filePath)
            file.exists()
        }
    }


    private fun loadHistoryData() {
        lifecycleScope.launch {
            val flow = if (filterType == "All") db.getAllDownloads() else db.getDownloadsByType(filterType)

            flow.onStart {
                binding.progressBar.visibility = View.VISIBLE
            }.catch {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
                Toast.makeText(requireContext(), "Gagal memuat data", Toast.LENGTH_SHORT).show()
            }.collectLatest { data ->
                binding.progressBar.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false

                val filtered = data.filter { isFileExist(it.filePath) }
                adapter.updateData(filtered)

                binding.textEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerViewHistory.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
                binding.recyclerViewHistory.scheduleLayoutAnimation()
                updateSelectionUI()
            }
        }
    }

    private fun deleteFile(history: DownloadHistory): Boolean {
        return try {
            val path = history.filePath
            if (path.startsWith("content://")) {
                // Jika ini URI content provider, gunakan contentResolver
                val uri = Uri.parse(path)
                val rowsDeleted = requireContext().contentResolver.delete(uri, null, null)
                if (rowsDeleted > 0) {
                    true
                } else {
                    // Jika gagal hapus lewat contentResolver, coba hapus manual file jika bisa
                    val file = File(uri.path ?: "")
                    if (!file.exists()) true else file.delete()
                }
            } else {
                // Jika ini file path biasa, hapus langsung file
                val file = File(path)
                if (!file.exists()) {
                    true // Sudah tidak ada file, berarti berhasil
                } else {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }



//    private fun deleteFile(history: DownloadHistory): Boolean {
//        return try {
//            val uri = Uri.parse(history.filePath)
//            val rowsDeleted = requireContext().contentResolver.delete(uri, null, null)
//
//            if (rowsDeleted == 0) {
//                val file = File(history.filePath)
//                if (file.exists()) {
//                    file.delete()
//                } else {
//                    false
//                }
//            } else {
//                true
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            false
//        }
//    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        lifecycleScope.coroutineContext.cancelChildren()
    }

    override fun onResume() {
        super.onResume()
        setStatusBarColor(R.color.colorPrimary, isLightStatusBar = false)
    }

    companion object {
        fun newInstance(filterType: String) = HistoryListFragment().apply {
            arguments = Bundle().apply {
                putString("filterType", filterType)
            }
        }
    }
}
