package com.afitech.afitechtok.ui.adapters
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.afitech.afitechtok.ui.fragments.HistoryListFragment

class HistoryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return HistoryListFragment.newInstance(
            when (position) {
                1 -> "Video"
                2 -> "Audio"
                3 -> "Image"
                else -> "All"
            }
        )
    }
}
