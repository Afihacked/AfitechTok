package com.afitech.afitechtok.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.afitech.afitechtok.ui.fragments.DownloadFragmentTT
import com.afitech.afitechtok.ui.fragments.WhatsappStoryFragment
import com.afitech.afitechtok.ui.fragments.HistoryFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DownloadFragmentTT()
            1 -> WhatsappStoryFragment()
            2 -> HistoryFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }

    // ✅ STABLE ID (INI PENTING)
    override fun getItemId(position: Int): Long {
        return when (position) {
            0 -> 100L // TikTok
            1 -> 200L // WhatsApp
            2 -> 300L // History
            else -> position.toLong()
        }
    }

    // ✅ JANGAN BIARKAN ADAPTER RECREATE FRAGMENT
    override fun containsItem(itemId: Long): Boolean {
        return itemId == 100L || itemId == 200L || itemId == 300L
    }
}

