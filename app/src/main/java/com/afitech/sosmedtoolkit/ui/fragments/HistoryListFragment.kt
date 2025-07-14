package com.afitech.sosmedtoolkit.ui.fragments

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.afitech.sosmedtoolkit.R
import com.afitech.sosmedtoolkit.data.database.AppDatabase
import com.afitech.sosmedtoolkit.data.repository.DownloadHistoryRepository
import com.afitech.sosmedtoolkit.databinding.FragmentHistoryListBinding
import com.afitech.sosmedtoolkit.ui.adapters.HistoryAdapter
import com.afitech.sosmedtoolkit.ui.viewmodel.HistoryListViewModel
import com.afitech.sosmedtoolkit.ui.viewmodel.HistoryListViewModelFactory
import com.afitech.sosmedtoolkit.utils.setStatusBarColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
                viewModel.delete(history)
                reloadHistory()
            },
            onMultipleDelete = { list ->
                viewModel.deleteMultiple(list)
                reloadHistory()
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

    private fun reloadHistory() {
        historyJob?.cancel()
        binding.swipeRefreshLayout.isRefreshing = true
        historyJob = viewModel.historyList.onEach { data ->
            val filtered = data.filter { isFileExist(it.filePath) }
            adapter.updateData(filtered)

            binding.textEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerViewHistory.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
            binding.recyclerViewHistory.scheduleLayoutAnimation()

            updateSelectionUI()
            binding.swipeRefreshLayout.isRefreshing = false
        }.launchIn(lifecycleScope)
        viewModel.loadHistory(filterType)
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

    private fun isFileExist(filePath: String): Boolean {
        return try {
            val uri = Uri.parse(filePath)
            requireContext().contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            val file = File(filePath)
            file.exists()
        }
    }

    override fun onDestroyView() {
        historyJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
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
