package com.afitech.afitechtok.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.afitech.afitechtok.ui.fragments.DownloadFragmentTT
import com.afitech.afitechtok.ui.fragments.WhatsappStoryFragment
import com.afitech.afitechtok.ui.fragments.HistoryFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    // 🔥 registry fragment aktif
    private val fragments = HashMap<Int, Fragment>()

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {

        val fragment = when (position) {
            0 -> DownloadFragmentTT()
            1 -> WhatsappStoryFragment()
            2 -> HistoryFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }

        fragments[position] = fragment
        return fragment
    }

    // ✅ ambil fragment aktif
    fun getFragment(position: Int): Fragment? {
        return fragments[position]
    }

    // ✅ STABLE ID (PENTING)
    override fun getItemId(position: Int): Long {
        return when (position) {
            0 -> 100L
            1 -> 200L
            2 -> 300L
            else -> position.toLong()
        }
    }

    // ✅ jangan recreate fragment
    override fun containsItem(itemId: Long): Boolean {
        return itemId == 100L || itemId == 200L || itemId == 300L
    }
}