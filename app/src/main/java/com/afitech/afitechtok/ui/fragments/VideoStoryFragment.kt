package com.afitech.afitechtok.ui.fragments

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.afitech.afitechtok.data.model.StoryViewModel
import com.afitech.afitechtok.databinding.FragmentVideoStoryBinding
import com.afitech.afitechtok.ui.adapters.StoryAdapter

class VideoStoryFragment : Fragment() {

    private var _binding: FragmentVideoStoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: StoryAdapter
    private lateinit var storyViewModel: StoryViewModel
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoStoryBinding.inflate(inflater, container, false)
        prefs = requireContext().getSharedPreferences("TikDownloaderPrefs", Context.MODE_PRIVATE)

        adapter = StoryAdapter(emptyList(), requireContext())
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerView.adapter = adapter

        storyViewModel = ViewModelProvider(this)[StoryViewModel::class.java]
        storyViewModel.stories.observe(viewLifecycleOwner) { list ->
            // hanya video
            adapter.updateList(list.filter { it.type == "video" })
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        val saved = prefs.getString("savedUri", "") ?: ""
        if (saved.isNotEmpty()) {
            storyViewModel.loadStoriesFromUri(Uri.parse(saved))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
