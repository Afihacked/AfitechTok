package com.afitech.afitechtok.ui

import android.Manifest
import android.app.NotificationManager
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
import com.afitech.afitechtok.ui.services.DownloadServiceTT
import com.afitech.afitechtok.ui.services.DownloadSession
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.afitech.afitechtok.ui.interfaces.SelectionMenuHost

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FRAGMENT = "extra_fragment"
        const val EXTRA_VIDEO_URL = "video_url"
    }

    private lateinit var pagerAdapter: MainPagerAdapter
    private var historyFragment: HistoryListFragment? = null
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var sharedPref: SharedPreferences
    private val REQ_NOTIF = 1001
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    // index tab TikTok (sesuaikan jika urutan adapter berubah)
    private val tiktokTabIndex = 0

    private var showSelectionMenu = false

    override fun onCreate(savedInstanceState: Bundle?) {

        sharedPref = getSharedPreferences("theme_pref", MODE_PRIVATE)
//        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        // 1) set layout
        setContentView(R.layout.activity_main)

        // Iain notification (Android 13+)
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
        setSupportActionBar(toolbar)
        toolbar.setTitleTextColor(Color.WHITE)
        toolbar.navigationIcon?.setTint(Color.WHITE)

// EDGE TO EDGE (clean & stable)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

// warna surface dari theme
        val primaryColor = MaterialColors.getColor(
            toolbar,
            com.google.android.material.R.attr.colorPrimary
        )

        toolbar.setBackgroundColor(primaryColor)

// dark/light icon otomatis
        val isLightTheme =
            (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) !=
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false   // icon PUTIH
            isAppearanceLightNavigationBars = false
        }

// tinggi scrim mengikuti status bar

        val content = findViewById<View>(R.id.viewPager)

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(bottom = bottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->

            val statusBarHeight =
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

            val actionBarHeight = resources.getDimensionPixelSize(
                androidx.appcompat.R.dimen.abc_action_bar_default_height_material
            )

            val params = view.layoutParams
            params.height = statusBarHeight + actionBarHeight
            view.layoutParams = params

            view.setPadding(
                view.paddingLeft,
                statusBarHeight,
                view.paddingRight,
                view.paddingBottom
            )

            insets
        }

        toolbar.requestApplyInsets()

        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        toggle.drawerArrowDrawable.color = Color.WHITE
        // Setup Tab + ViewPager
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
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

        pagerAdapter = MainPagerAdapter(this)
        viewPager.adapter = pagerAdapter

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
                if (position == 2) {
                    historyFragment = supportFragmentManager.fragments
                        .filterIsInstance<HistoryListFragment>()
                        .firstOrNull()
                }
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
                R.id.nav_wa_web -> {
                    replaceFragment(WaWebFragment(), getString(R.string.whatsapp_web))
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
    fun setSelectionMenuVisible(visible: Boolean) {
        showSelectionMenu = visible
        invalidateOptionsMenu()
    }
    // Inflate menu (ikon help)
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val refreshItem = menu?.findItem(R.id.action_refresh)
        val actionView = refreshItem?.actionView
        val lottie = actionView?.findViewById<LottieAnimationView>(R.id.lottie_refresh)
        lottie?.apply {

            // Recolor all layers
            addValueCallback(
                KeyPath("**"),
                LottieProperty.COLOR_FILTER,
                LottieValueCallback(
                    SimpleColorFilter(
                        ContextCompat.getColor(context, R.color.white)
                    )
                )
            )}
        lottie?.setOnClickListener {
            lottie.playAnimation()

            val fragment = supportFragmentManager.findFragmentById(R.id.extra_fragment_container)
            if (fragment is WaWebFragment) {
                fragment.reloadPage()
            }
        }

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {

        val helpItem = menu?.findItem(R.id.action_help)
        val refreshItem = menu?.findItem(R.id.action_refresh)
        val selectionItem = menu?.findItem(R.id.action_selection_menu)

        val extraVisible =
            findViewById<View>(R.id.extra_fragment_container).visibility == View.VISIBLE

        val currentFragment =
            supportFragmentManager.findFragmentById(R.id.extra_fragment_container)

        // ✅ Help hanya di tab TikTok
        val showHelp =
            !extraVisible &&
                    ::viewPager.isInitialized &&
                    viewPager.currentItem == tiktokTabIndex

        helpItem?.isVisible = showHelp

        // ✅ Refresh hanya saat WA Web aktif
        refreshItem?.isVisible =
            extraVisible && currentFragment is WaWebFragment

        // ✅ Selection menu hanya di tab HISTORY & saat selection aktif
        val isHistoryTab =
            !extraVisible &&
                    ::viewPager.isInitialized &&
                    viewPager.currentItem == 2   // tab history index

        selectionItem?.isVisible = isHistoryTab && showSelectionMenu

        // tint help icon
        helpItem?.icon?.mutate()?.setTint(
            ContextCompat.getColor(this, R.color.white)
        )
        Log.d("MENU_DEBUG", "showSelectionMenu = $showSelectionMenu")
        Log.d("MENU_DEBUG", "isHistoryTab = $isHistoryTab")
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
                    Toast.makeText(
                        this,
                        getString(R.string.help_only_tiktok),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                true
            }

            R.id.action_selection_menu -> {

                Log.d("MENU_DEBUG","Selection menu clicked")

                val fragment = pagerAdapter.getFragment(viewPager.currentItem)
                        as? SelectionMenuHost

                if (fragment != null) {
                    Log.d("MENU_DEBUG","History fragment found")
                    fragment.onSelectionMenuClicked()
                } else {
                    Log.d("MENU_DEBUG","Fragment not ready")
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

        supportFragmentManager.executePendingTransactions() // ⬅ pastikan fragment sudah aktif

        supportActionBar?.title = title

        when (fragment) {
            is WaWebFragment -> navView.setCheckedItem(R.id.nav_wa_web)
            is TentangFragment -> navView.setCheckedItem(R.id.nav_about)
            is SettingsFragment -> navView.setCheckedItem(R.id.nav_settings)
        }

// refresh menu setelah fragment benar-benar aktif
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
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorPrimary,
                    ContextCompat.getColor(this, R.color.colorPrimary)
                )
            } catch (e: Exception) {
                ContextCompat.getColor(this, R.color.colorPrimary)
            }
            catch (e: Exception) {
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
        invalidateOptionsMenu()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
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
