package com.afitech.afitechtok.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.afitech.afitechtok.R
import com.afitech.afitechtok.ui.adapters.MainPagerAdapter
import com.afitech.afitechtok.ui.fragments.*
import com.afitech.afitechtok.ui.helpers.RemoteConfigHelper
import com.afitech.afitechtok.ui.helpers.ThemeHelper
import com.afitech.afitechtok.ui.services.DownloadServiceTT
import com.afitech.afitechtok.ui.services.DownloadSession
import com.afitech.afitechtok.utils.setStatusBarColorInt
import com.afitech.afitechtok.utils.setStatusBarColorRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    // index tab TikTok (sesuaikan jika urutan adapter berubah)
    private val tiktokTabIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        FirebaseApp.initializeApp(this)

        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        FirebaseCrashlytics.getInstance().log("MainActivity onCreate() called")

        RemoteConfigHelper.init(this)

        sharedPref = getSharedPreferences("theme_pref", MODE_PRIVATE)
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        // 1) set layout
        setContentView(R.layout.activity_main)

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)

        // Izin notifikasi (Android 13+)
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

        // init views & toolbar
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        val scrim: View? = findViewById(R.id.status_bar_scrim) // <-- ensure this view exists in activity_main.xml (first child)
        setSupportActionBar(toolbar)

        // -------------------- SCRIM-BASED STATUSBAR (reliable on all devices) --------------------
        // Make status bar transparent and draw behind
        window.statusBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Ensure flags (safety)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        // desired color (from colors.xml)
        val desiredColor = ContextCompat.getColor(this, R.color.sttsbar)

        // ----- Calculate actionBar size (fallback to 56dp if not available) -----
        val tv = TypedValue()
        val actionBarHeight = if (theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
        } else {
            // fallback ~56dp
            (56 * resources.displayMetrics.density).toInt()
        }
        // toolbar styling and scrim color
        toolbar.setBackgroundColor(desiredColor)
        toolbar.elevation = 0f
        scrim?.setBackgroundColor(desiredColor)

        // set status bar icon contrast (true = dark icons)
// ===== FINAL STATUS BAR OWNER =====
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = ContextCompat.getColor(this, R.color.sttsbar)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true // karena sttsbar terang

        // Apply insets:
        //  - scrim height = statusBarHeight
        //  - toolbar height = actionBarHeight + statusBarHeight
        //  - toolbar padding top = statusBarHeight (so content not cut)
        scrim?.let { s ->
            ViewCompat.setOnApplyWindowInsetsListener(s) { v, insets ->
                val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                val statusBarHeight = statusBarInsets.top
                if (v.layoutParams.height != statusBarHeight) {
                    v.layoutParams = v.layoutParams.apply { height = statusBarHeight }
                    v.requestLayout()
                }
                insets
            }
            s.requestApplyInsets()
        }

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val statusBarHeight = statusBarInsets.top
            val totalToolbarHeight = actionBarHeight + statusBarHeight

            // update toolbar height if needed
            if (v.layoutParams.height != totalToolbarHeight) {
                v.layoutParams = v.layoutParams.apply { height = totalToolbarHeight }
                v.requestLayout()
            }

            // padding so toolbar content sits below status bar
            v.updatePadding(top = statusBarHeight)

            insets
        }
        toolbar.requestApplyInsets()

        // Optional fallback util (commented out because scrim is authoritative)
        // setStatusBarColorRes(R.color.sttsbar, isLightStatusBar = true, drawBehind = false)

        // ----------------------------------------------------------------------------------------

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

        viewPager.adapter = MainPagerAdapter(this)
        viewPager.offscreenPageLimit = 3

        // pastikan mode/gravity sudah benar
        tabLayout.tabMode = TabLayout.MODE_FIXED
        tabLayout.tabGravity = TabLayout.GRAVITY_FILL

// pakai drawable indikator yang baru
        tabLayout.setSelectedTabIndicator(R.drawable.tab_indicator_full)

// pastikan indicator full width (API MaterialComponent)
        try {
            tabLayout.isTabIndicatorFullWidth = true
        } catch (e: Throwable) {
            // property tidak tersedia di versi lama -> lewati
        }

        val adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> tab.setIcon(R.drawable.ic_tiktok2)
                1 -> tab.setIcon(R.drawable.ic_wa)
                2 -> tab.setIcon(R.drawable.ic_manager)
            }
        }.attach()

        // Judul toolbar saat ganti tab dan (opsional) ubah warna per-tab
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
                invalidateOptionsMenu()
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
    private fun openDownloadTTFragment() {

        // pindah ke TAB TikTok
        showTabs()
        viewPager.currentItem = tiktokTabIndex

        // ambil fragment TikTok yang sedang aktif
        val fragment =
            supportFragmentManager.fragments
                .firstOrNull { it is DownloadFragmentTT }
                    as? DownloadFragmentTT

        // kirim ulang URL terakhir dari DownloadSession
        fragment?.onNotificationOpened(
            DownloadSession.lastVideoUrl
        )
    }

    // Inflate menu (ikon help)
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        // Pastikan menu ter-inflate
        val helpItem = menu?.findItem(R.id.action_help)
        // visible hanya jika sedang di tab TikTok (index tiktokTabIndex) dan extra container TIDAK visible
        val extraVisible = findViewById<View>(R.id.extra_fragment_container).visibility == View.VISIBLE
        val showHelp = !extraVisible && ::viewPager.isInitialized && viewPager.currentItem == tiktokTabIndex
        helpItem?.isVisible = showHelp
        return super.onPrepareOptionsMenu(menu)
    }

    // Handle klik menu
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_help -> {
                val current = viewPager.currentItem
                if (current == tiktokTabIndex) {
                    showTutorialDialog()
                } else {
                    Toast.makeText(this, getString(R.string.help_only_tiktok), Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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

        when (fragment) {
            is TentangFragment -> navView.setCheckedItem(R.id.nav_about)
            is SettingsFragment -> navView.setCheckedItem(R.id.nav_settings)
        }
        invalidateOptionsMenu()
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
        invalidateOptionsMenu()
    }

    private fun showTutorialDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tutorial, null)
        val dialog = MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setView(dialogView)
            .create()

        dialog.show()

        // Ambil tombol OK yang ada di layout dialog (custom MaterialButton)
        val btnOk = dialog.findViewById<MaterialButton>(R.id.btnOk)
        if (btnOk != null) {
            // Ambil colorPrimary dari theme, fallback ke R.color.colorPrimary
            val primaryColor = try {
                com.google.android.material.color.MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorPrimary,
                    ContextCompat.getColor(this, R.color.colorPrimary)
                )
            } catch (e: Exception) {
                ContextCompat.getColor(this, R.color.colorPrimary)
            }

            // Set teks tombol sesuai primary color
            btnOk.setTextColor(primaryColor)
            btnOk.isAllCaps = false

            // Buat ripple yang sedikit transparan dari primaryColor sehingga masih terlihat pada latar apa pun
            val rippleAlpha = 0x30 // 48 decimal -> semi-transparent ripple
            val rippleColor = androidx.core.graphics.ColorUtils.setAlphaComponent(primaryColor, rippleAlpha)
            btnOk.rippleColor = android.content.res.ColorStateList.valueOf(rippleColor)

            // Tampilkan sebagai "text-only" namun jangan menghapus ripple — set background transparan safely
            btnOk.backgroundTintList = null
            btnOk.background = null
            btnOk.minHeight = 0
            btnOk.setPadding(btnOk.paddingLeft, btnOk.paddingTop, btnOk.paddingRight, btnOk.paddingBottom)

            // Klik tutup dialog
            btnOk.setOnClickListener { dialog.dismiss() }
        }

        // atur ukuran dialog (95% width, 85% height)
        dialog.window?.apply {
            val dm = resources.displayMetrics
            val width = (dm.widthPixels * 0.95).toInt()
            val height = (dm.heightPixels * 0.85).toInt()
            setLayout(width, height)
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
                        showTabs()
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

    override fun onResume() {
        super.onResume()
        Log.d(
            "STATUSBAR",
            "color=#${Integer.toHexString(window.statusBarColor)}"
        )

        // optional debug
        android.util.Log.d("DBG_STATUSBAR", "onResume (scrim) window.statusBarColor=#${Integer.toHexString(window.statusBarColor)}")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // optional debug
        android.util.Log.d("DBG_STATUSBAR", "onWindowFocusChanged hasFocus=$hasFocus statusBarColor=#${Integer.toHexString(window.statusBarColor)}")
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
