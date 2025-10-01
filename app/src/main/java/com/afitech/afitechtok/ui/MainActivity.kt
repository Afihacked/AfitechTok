package com.afitech.afitechtok.ui

import android.Manifest
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.afitech.afitechtok.R
import com.afitech.afitechtok.ui.adapters.MainPagerAdapter
import com.afitech.afitechtok.ui.fragments.*
import com.afitech.afitechtok.ui.helpers.RemoteConfigHelper
import com.afitech.afitechtok.ui.helpers.ThemeHelper
import com.afitech.afitechtok.ui.services.DownloadServiceTT
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FRAGMENT = "extra_fragment"
        const val EXTRA_VIDEO_URL = "video_url"
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var sharedPref: SharedPreferences
    private val REQ_NOTIF = 1001

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        FirebaseApp.initializeApp(this)

        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        FirebaseCrashlytics.getInstance().log("MainActivity onCreate() called")

        RemoteConfigHelper.init(this)

        sharedPref = getSharedPreferences("theme_pref", MODE_PRIVATE)
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)

        // Izin notifikasi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIF
                )
            }
        }

        val notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifManager.cancel(DownloadServiceTT.NOTIF_ID)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Setup Tab + ViewPager
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        val adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> tab.setIcon(R.drawable.ic_tiktok2)     // sama dengan drawer
                1 -> tab.setIcon(R.drawable.ic_wa)
                2 -> tab.setIcon(R.drawable.ic_manager)
            }
        }.attach()

        // Judul toolbar saat ganti tab
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    0 -> {
                        navView.setCheckedItem(R.id.nav_tt_offline)
                        supportActionBar?.title = getString(R.string.btn_tiktok_downloader)
                    }
                    1 -> {
                        navView.setCheckedItem(R.id.nav_wa_offline)
                        supportActionBar?.title = getString(R.string.btn_whatsapp_story)
                    }
                    2 -> {
                        navView.setCheckedItem(R.id.nav_history)
                        supportActionBar?.title = getString(R.string.nav_history)
                    }
                }
            }
        })

        // Drawer item click
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_tt_offline -> {
                    showTabs()
                    viewPager.currentItem = 0
                }
                R.id.nav_wa_offline -> {
                    showTabs()
                    viewPager.currentItem = 1
                }
                R.id.nav_history -> {
                    showTabs()
                    viewPager.currentItem = 2
                }
                R.id.nav_about -> {
                    replaceFragment(TentangFragment(), getString(R.string.nav_about))
                }
                R.id.nav_settings -> {
                    replaceFragment(SettingsFragment(), getString(R.string.nav_settings))
                }
            }
            drawerLayout.closeDrawers()
            true
        }

        handleBackPressed()
    }

    // Ganti fragment ke container ekstra
    fun replaceFragment(fragment: Fragment, title: String) {
        findViewById<View>(R.id.extra_fragment_container).visibility = View.VISIBLE
        findViewById<View>(R.id.viewPager).visibility = View.GONE
        findViewById<View>(R.id.tabLayout).visibility = View.GONE

        supportFragmentManager.beginTransaction()
            .replace(R.id.extra_fragment_container, fragment)
            .addToBackStack(null)
            .commit()

        supportActionBar?.title = title

        // 🔥 Sinkron highlight drawer
        when (fragment) {
            is TentangFragment -> navView.setCheckedItem(R.id.nav_about)
            is SettingsFragment -> navView.setCheckedItem(R.id.nav_settings)
        }
    }


    private fun showTabs() {
        findViewById<View>(R.id.extra_fragment_container).visibility = View.GONE
        findViewById<View>(R.id.viewPager).visibility = View.VISIBLE
        findViewById<View>(R.id.tabLayout).visibility = View.VISIBLE

        when (viewPager.currentItem) {
            0 -> {
                supportActionBar?.title = getString(R.string.btn_tiktok_downloader)
                navView.setCheckedItem(R.id.nav_tt_offline)
            }
            1 -> {
                supportActionBar?.title = getString(R.string.btn_whatsapp_story)
                navView.setCheckedItem(R.id.nav_wa_offline)
            }
            2 -> {
                supportActionBar?.title = getString(R.string.nav_history)
                navView.setCheckedItem(R.id.nav_history)
            }
        }
    }


    private fun handleBackPressed() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START)
                        return
                    }

                    val extraContainer = findViewById<View>(R.id.extra_fragment_container)
                    if (extraContainer.visibility == View.VISIBLE) {
                        supportFragmentManager.popBackStack()
                        showTabs() // ini otomatis update highlight sesuai tab aktif
                        return
                    }

                    if (supportFragmentManager.backStackEntryCount > 0) {
                        supportFragmentManager.popBackStack()
                    } else {
                        finish()
                    }
                }
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) {
            if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    this,
                    "Izin notifikasi diperlukan agar download berjalan di background",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
