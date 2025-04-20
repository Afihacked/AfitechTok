package com.afitech.tikdownloader.ui.adapters

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.afitech.tikdownloader.ui.fragments.DownloaderFragment
import com.afitech.tikdownloader.ui.fragments.HistoryFragment
import com.afitech.tikdownloader.ui.fragments.WhatsappStoryFragment

class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DownloaderFragment()
            1 -> WhatsappStoryFragment() // tab WA Story
            2 -> HistoryFragment()
            else -> throw IllegalStateException("Posisi tidak valid: $position")
        }
    }
}
