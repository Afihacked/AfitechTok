package com.afitech.tikdownloader.ui

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
import com.afitech.tikdownloader.R
import com.afitech.tikdownloader.ui.fragments.*
import com.afitech.tikdownloader.ui.helpers.ThemeHelper
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FRAGMENT = "extra_fragment"
        const val EXTRA_VIDEO_URL = "video_url"
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var sharedPref: SharedPreferences
    private val REQ_NOTIF = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPref = getSharedPreferences("theme_pref", MODE_PRIVATE)
        ThemeHelper.applyTheme(this)  // Terapkan tema sebelum super.onCreate supaya langsung berpengaruh
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Izin notifikasi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIF
                )
            }
        }

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_tt_offline -> replaceFragment(DownloadFragmentTT(), getString(R.string.nav_tt_offline))
                R.id.nav_yt_offline -> replaceFragment(DownloadFragmentYT(), getString(R.string.nav_yt_offline))
                R.id.nav_wa_offline -> replaceFragment(WhatsappStoryFragment(), getString(R.string.nav_wa_offline))
                R.id.nav_history -> replaceFragment(HistoryFragment(), getString(R.string.nav_history))
                R.id.nav_about -> replaceFragment(TentangFragment(), getString(R.string.nav_about))
            }
            drawerLayout.closeDrawers()
            true
        }

        if (savedInstanceState == null) {
            val fragmentTarget = intent.getStringExtra(EXTRA_FRAGMENT)
            val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
            when (fragmentTarget) {
                "yt_downloader" -> replaceFragment(
                    DownloadFragmentYT.newInstance(videoUrl),
                    getString(R.string.nav_yt_offline)
                )
                "tt_downloader" -> replaceFragment(DownloadFragmentTT(), getString(R.string.nav_tt_offline))
                "wa_downloader" -> replaceFragment(WhatsappStoryFragment(), getString(R.string.nav_wa_offline))
                else -> replaceFragment(HomeFragment(), getString(R.string.nav_home))
            }
        }

        handleBackPressed()
    }

    private fun replaceFragment(fragment: Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        supportActionBar?.title = title
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_toolbar_menu, menu)
        menu?.findItem(R.id.action_night_mode)?.let { updateThemeIcon(it) }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_night_mode -> {
                val isDarkMode = ThemeHelper.getIsDarkMode(sharedPref)
                val newMode = !isDarkMode
                ThemeHelper.toggleTheme(sharedPref, newMode)
                updateThemeIcon(item)  // Update icon menu langsung
                recreate()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateThemeIcon(item: MenuItem) {
        val isDarkMode = ThemeHelper.getIsDarkMode(sharedPref)
        item.setIcon(if (isDarkMode) R.drawable.sun else R.drawable.moon)
        item.title = if (isDarkMode) "Mode Terang" else "Mode Malam"
    }

    private fun handleBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }

                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

                if (currentFragment is HomeFragment) {
                    finish()
                } else {
                    supportFragmentManager.popBackStack(
                        null,
                        androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                    replaceFragment(HomeFragment(), getString(R.string.nav_home))
                }
            }
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
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
