package com.afitech.tikdownloader.ui.adapters

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.afitech.tikdownloader.ui.fragments.ImageStoryFragment
import com.afitech.tikdownloader.ui.fragments.VideoStoryFragment

class StoryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ImageStoryFragment()
            1 -> VideoStoryFragment()
            else -> throw IllegalStateException("Invalid tab position: $position")
        }
    }
}
