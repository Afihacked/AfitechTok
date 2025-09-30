package com.afitech.afitechtok.ui

import android.Manifest
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.afitech.afitechtok.R
import com.afitech.afitechtok.ui.fragments.*
import com.afitech.afitechtok.ui.helpers.RemoteConfigHelper
import com.afitech.afitechtok.ui.helpers.ThemeHelper
import com.afitech.afitechtok.ui.services.DownloadServiceTT
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

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

    private var tvGreeting: TextView? = null
    private var ivGreetingIcon: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        FirebaseApp.initializeApp(this)

        // ✅ Crashlytics
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        FirebaseCrashlytics.getInstance().log("MainActivity onCreate() called")

        // 🔧 Remote Config init
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

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_tt_offline -> replaceFragment(DownloadFragmentTT(), getString(R.string.nav_tt_offline))
                R.id.nav_wa_offline -> replaceFragment(WhatsappStoryFragment(), getString(R.string.nav_wa_offline))
                R.id.nav_history -> replaceFragment(HistoryFragment(), getString(R.string.nav_history))
                R.id.nav_about -> replaceFragment(TentangFragment(), getString(R.string.nav_about))
                R.id.nav_settings -> replaceFragment(SettingsFragment(), getString(R.string.nav_settings))
            }
            drawerLayout.closeDrawers()
            true
        }

        if (savedInstanceState == null) {
            val fragmentTarget = intent.getStringExtra(EXTRA_FRAGMENT)
            val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
            when (fragmentTarget) {
                "tt_downloader" -> replaceFragment(DownloadFragmentTT(), getString(R.string.nav_tt_offline))
                "wa_downloader" -> replaceFragment(WhatsappStoryFragment(), getString(R.string.nav_wa_offline))
                "history" -> replaceFragment(HistoryFragment(), getString(R.string.nav_history))
                "settings" -> replaceFragment(SettingsFragment(), getString(R.string.nav_settings))
                else -> replaceFragment(HomeFragment(), "") // greeting langsung
            }
        }

        // ✅ Update greeting realtime tiap menit
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    if (currentFragment is HomeFragment) {
                        updateGreeting()
                    }
                    delay(60_000)
                }
            }
        }

        handleBackPressed()
    }

    private fun getGreetingWithIcon(): Pair<String, String> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> "Selamat Pagi" to "https://img.icons8.com/emoji/48/sunrise-emoji.png"
            in 11..14 -> "Selamat Siang" to "https://img.icons8.com/emoji/48/sun-emoji.png"
            in 15..17 -> "Selamat Sore" to "https://img.icons8.com/emoji/48/sunset-emoji.png"
            else -> "Selamat Malam" to "https://img.icons8.com/emoji/48/crescent-moon-emoji.png"
        }
    }

    private fun updateGreeting() {
        val (greeting, iconUrl) = getGreetingWithIcon()
        tvGreeting?.text = greeting
        ivGreetingIcon?.let { imageView ->
            Glide.with(this).load(iconUrl).into(imageView)
        }
    }


    private fun enableGreetingToolbar() {
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayShowCustomEnabled(true)
        supportActionBar?.setCustomView(R.layout.toolbar_greeting)

        tvGreeting = supportActionBar?.customView?.findViewById(R.id.tvGreeting)
        ivGreetingIcon = supportActionBar?.customView?.findViewById(R.id.ivGreetingIcon)

        updateGreeting()
    }

    private fun disableGreetingToolbar(title: String) {
        supportActionBar?.setDisplayShowCustomEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = title

        // reset reference supaya tidak dipakai lagi
        tvGreeting = null
        ivGreetingIcon = null
    }


     fun replaceFragment(fragment: Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()

        if (fragment is HomeFragment) {
            enableGreetingToolbar()
        } else {
            disableGreetingToolbar(title)
        }

        // Hapus highlight jika kembali ke HomeFragment
        if (fragment is HomeFragment) {
            for (i in 0 until navView.menu.size()) {
                val item = navView.menu.getItem(i)
                item.isChecked = false
                if (item.hasSubMenu()) {
                    val subMenu = item.subMenu
                    for (j in 0 until (subMenu?.size() ?: 0)) {
                        subMenu?.getItem(j)?.isChecked = false
                    }
                }
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

                    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    if (currentFragment is HomeFragment) {
                        finish()
                    } else {
                        supportFragmentManager.popBackStack(
                            null,
                            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                        )
                        replaceFragment(HomeFragment(), "")
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
