package com.afitech.tikdownloader.ui.fragments

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.afitech.tikdownloader.data.database.AppDatabase
import com.afitech.tikdownloader.databinding.FragmentHistoryListBinding
import com.afitech.tikdownloader.ui.adapters.HistoryAdapter
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

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

        adapter = HistoryAdapter(requireContext(), emptyList()) { history ->
            lifecycleScope.launch {
                db.deleteDownload(history)
                loadHistoryData()
            }
        }

        binding.recyclerViewHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HistoryListFragment.adapter
        }

        loadHistoryData()
    }

    private fun loadHistoryData() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val flow = if (filterType == "All") db.getAllDownloads() else db.getDownloadsByType(filterType)

            flow.onStart {
                binding.progressBar.visibility = View.VISIBLE
            }.catch {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Gagal memuat data", Toast.LENGTH_SHORT).show()
            }.collectLatest { data ->
                binding.progressBar.visibility = View.GONE
                adapter.updateData(data)

                if (data.isEmpty()) {
                    binding.textEmpty.visibility = View.VISIBLE
                    binding.recyclerViewHistory.visibility = View.GONE
                } else {
                    binding.textEmpty.visibility = View.GONE
                    binding.recyclerViewHistory.visibility = View.VISIBLE
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(filterType: String) = HistoryListFragment().apply {
            arguments = Bundle().apply {
                putString("filterType", filterType)
            }
        }
    }
}
