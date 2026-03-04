package com.afitech.afitechtok.ui

import android.Manifest
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.afitech.afitechtok.R
import com.afitech.afitechtok.ui.adapters.MainPagerAdapter
import com.afitech.afitechtok.ui.fragments.*
import com.afitech.afitechtok.ui.services.DownloadServiceTT
import com.google.android.material.color.MaterialColors

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FRAGMENT = "extra_fragment"
        const val EXTRA_VIDEO_URL = "video_url"
    }

    private lateinit var pagerAdapter: MainPagerAdapter
    private lateinit var sharedPref: SharedPreferences
    private lateinit var viewPager: ViewPager2

    private val REQ_NOTIF = 1001

    private var showSelectionMenu = false

    override fun onCreate(savedInstanceState: Bundle?) {

        sharedPref = getSharedPreferences("theme_pref", MODE_PRIVATE)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }
        setupStatusBar()
        setupToolbar()
        setupViewPager()
        initCustomBottomNav()
        handleBackPressed()

        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        )


        // Notification permission (Android 13+)
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

        // Blur effect Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val isDark = resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

            window.setBackgroundBlurRadius(
                if (isDark) 45 else 60
            )
        }
        val notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifManager.cancel(DownloadServiceTT.NOTIF_ID)

    }

    private fun setupToolbar() {

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->

            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

            if (view.paddingTop != topInset) {
                view.setPadding(
                    view.paddingLeft,
                    topInset,
                    view.paddingRight,
                    view.paddingBottom
                )
            }

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupStatusBar() {

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorPrimary,
            Color.RED
        )

        val controller = WindowInsetsControllerCompat(window, window.decorView)

        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {

        val helpItem = menu?.findItem(R.id.action_help)
        val refreshItem = menu?.findItem(R.id.action_refresh)
        val selectionItem = menu?.findItem(R.id.action_selection_menu)

        val extraContainer = findViewById<View>(R.id.extra_fragment_container)
        val extraFragment =
            supportFragmentManager.findFragmentById(R.id.extra_fragment_container)

        val isExtraActive =
            extraContainer.visibility == View.VISIBLE &&
                    extraFragment != null

        // HELP → hanya TikTok
        helpItem?.isVisible =
            !isExtraActive && viewPager.currentItem == 0

        // REFRESH → hanya WA Web
        refreshItem?.isVisible =
            isExtraActive && extraFragment is WaWebFragment

        // SELECTION → hanya History + selection aktif
        selectionItem?.isVisible =
            !isExtraActive &&
                    viewPager.currentItem == 2 &&
                    showSelectionMenu

        return super.onPrepareOptionsMenu(menu)
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val refreshItem = menu?.findItem(R.id.action_refresh)
        val actionView = refreshItem?.actionView

        actionView?.setOnClickListener {

            val fragment =
                supportFragmentManager.findFragmentById(R.id.extra_fragment_container)

            if (fragment is WaWebFragment) {
                fragment.reloadPage()
            }
        }

        return true
    }
    private fun showTutorialDialog() {

        val dialogView = layoutInflater.inflate(
            R.layout.dialog_tutorial,
            null
        )

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            R.style.ThemeOverlay_TikDownloader_MaterialAlertDialog
        )
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.show()

        dialogView.findViewById<View>(R.id.btnOk).setOnClickListener {
            dialog.dismiss()
        }
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.action_help -> {
                showTutorialDialog()
                return true
            }

            R.id.action_selection_menu -> {
                // kalau ada logic selection nanti
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun setupViewPager() {
        viewPager = findViewById(R.id.viewPager)
        viewPager.offscreenPageLimit = 3
        viewPager.isUserInputEnabled = true

        pagerAdapter = MainPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        viewPager.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {

            override fun onPageSelected(position: Int) {
                setActiveNav(position)

                when (position) {
                    0 -> supportActionBar?.title =
                        getString(R.string.btn_tiktok_downloader)
                    1 -> supportActionBar?.title =
                        getString(R.string.btn_whatsapp_story)
                    2 -> supportActionBar?.title =
                        getString(R.string.nav_history)
                }

                invalidateOptionsMenu()
            }
        })
    }

    // ===========================
    // Bottom Nav
    // ===========================

    private fun haptic(view: View) {
        view.performHapticFeedback(
            android.view.HapticFeedbackConstants.KEYBOARD_TAP
        )
    }

    private fun animateNav(view: View) {
        view.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(120)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .start()
            }
            .start()
    }

    private fun setActiveNav(index: Int) {


        val containers = listOf(
            findViewById<View>(R.id.navTikTokInner),
            findViewById<View>(R.id.navWhatsAppInner),
            findViewById<View>(R.id.navHistoryInner),
            findViewById<View>(R.id.navSettingsInner)
        )

        val icons = listOf(
            findViewById<ImageView>(R.id.iconTikTok),
            findViewById<ImageView>(R.id.iconWhatsApp),
            findViewById<ImageView>(R.id.iconHistory),
            findViewById<ImageView>(R.id.iconSettings)
        )

        val texts = listOf(
            findViewById<TextView>(R.id.textTikTok),
            findViewById<TextView>(R.id.textWhatsApp),
            findViewById<TextView>(R.id.textHistory),
            findViewById<TextView>(R.id.textSettings)
        )

        containers.forEachIndexed { i, view ->
            if (i == index) {
                view.animate()
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(150)
                    .start()

                view.background = ContextCompat.getDrawable(
                    this,
                    R.drawable.bg_nav_active
                )
            } else {
                view.background = null
                view.scaleX = 1f
                view.scaleY = 1f
            }
        }

        icons.forEachIndexed { i, icon ->

            val isActive = i == index

            val color = if (isActive) {
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnPrimary,
                    Color.WHITE
                )
            } else {
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    Color.GRAY
                )
            }

            icon.imageTintList = ColorStateList.valueOf(color)
        }

        texts.forEachIndexed { i, text ->

            val isActive = i == index

            val color = if (isActive) {
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnPrimary,
                    Color.WHITE
                )
            } else {
                MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    Color.GRAY
                )
            }

            text.setTextColor(color)

            // Jangan pakai alpha lagi → bikin glass look
            text.alpha = 1f
        }
    }

    fun setSelectionMenuVisible(visible: Boolean) {
        showSelectionMenu = visible
        invalidateOptionsMenu()
    }
    private fun initCustomBottomNav() {

        findViewById<View>(R.id.navTikTok).setOnClickListener {
            haptic(it)
            animateNav(findViewById(R.id.navTikTokInner))
            showTabs()
            viewPager.currentItem = 0
        }

        findViewById<View>(R.id.navWhatsApp).setOnClickListener {
            haptic(it)
            animateNav(findViewById(R.id.navWhatsAppInner))
            showTabs()
            viewPager.currentItem = 1
        }

        findViewById<View>(R.id.navHistory).setOnClickListener {
            haptic(it)
            animateNav(findViewById(R.id.navHistoryInner))
            showTabs()
            viewPager.currentItem = 2
        }

        findViewById<View>(R.id.navSettings).setOnClickListener {
            haptic(it)
            animateNav(findViewById(R.id.navSettingsInner))
            replaceFragment(
                SettingsFragment(),
                getString(R.string.nav_settings)
            )
            setActiveNav(3)
        }

        setActiveNav(0)
    }

    // ===========================
    // Fragment Switch
    // ===========================

    fun replaceFragment(fragment: Fragment, title: String) {

        findViewById<View>(R.id.extra_fragment_container).visibility = View.VISIBLE
        findViewById<View>(R.id.viewPager).visibility = View.GONE

        supportFragmentManager.beginTransaction()
            .replace(R.id.extra_fragment_container, fragment)
            .addToBackStack(null)
            .commit()

        supportActionBar?.title = title

        supportFragmentManager.executePendingTransactions()
        invalidateOptionsMenu()
    }

    private fun showTabs() {

        findViewById<View>(R.id.extra_fragment_container).visibility = View.GONE
        findViewById<View>(R.id.viewPager).visibility = View.VISIBLE

        supportFragmentManager.executePendingTransactions()

        // 🔥 RESET NAV SESUAI PAGE SEKARANG
        setActiveNav(viewPager.currentItem)

        invalidateOptionsMenu()
    }

    // ===========================
    // Back Press
    // ===========================

    private fun handleBackPressed() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {

                    val extraContainer =
                        findViewById<View>(
                            R.id.extra_fragment_container
                        )

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

        if (findViewById<View>(R.id.viewPager).visibility == View.VISIBLE) {
            setActiveNav(viewPager.currentItem)
        }
    }
}