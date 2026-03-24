package com.afitech.afitechtok.ui.adapters

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.afitech.afitechtok.ui.fragments.ImageStoryFragment
import com.afitech.afitechtok.ui.fragments.VideoStoryFragment

class StoryPagerAdapter(
    fragment: Fragment
) : FragmentStateAdapter(fragment.childFragmentManager, fragment.lifecycle) {

    private val fragments = listOf(
        ImageStoryFragment(),
        VideoStoryFragment()
    )

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }

}

