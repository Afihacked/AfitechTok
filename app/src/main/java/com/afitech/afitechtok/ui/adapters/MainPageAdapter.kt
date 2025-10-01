package com.afitech.afitechtok.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.afitech.afitechtok.ui.fragments.DownloadFragmentTT
import com.afitech.afitechtok.ui.fragments.WhatsappStoryFragment
import com.afitech.afitechtok.ui.fragments.HistoryFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DownloadFragmentTT()
            1 -> WhatsappStoryFragment()
            else -> HistoryFragment()
        }
    }
}
