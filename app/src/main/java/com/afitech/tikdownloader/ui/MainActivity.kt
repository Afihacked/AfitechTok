package com.afitech.tikdownloader.ui

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.afitech.tikdownloader.R
import com.afitech.tikdownloader.ui.adapters.ViewPagerAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        setContentView(R.layout.activity_main)

        val viewPager: ViewPager2 = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        val tabTitles = listOf("Offline", "Riwayat Offline")

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            val tabTextView = TextView(this).apply {
                text = tabTitles[position]
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.colorOnSurface))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            tab.customView = tabTextView
        }.attach()

        // Atur tab pertama (aktif) warnanya
        setTabActive(tabLayout.getTabAt(0))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                setTabActive(tab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                setTabInactive(tab)
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setTabActive(tab: TabLayout.Tab?) {
        val textView = tab?.customView as? TextView
        textView?.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary))
    }

    private fun setTabInactive(tab: TabLayout.Tab?) {
        val textView = tab?.customView as? TextView
        textView?.setTextColor(ContextCompat.getColor(this, R.color.colorOnSurface))
    }
}
