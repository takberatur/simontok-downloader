package com.agcforge.videodownloader.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.databinding.ActivityMainBinding
import com.agcforge.videodownloader.databinding.NavHeaderBinding
import com.agcforge.videodownloader.service.WebSocketService
import com.agcforge.videodownloader.ui.activities.auth.LoginActivity
import com.agcforge.videodownloader.ui.activities.SubscriptionActivity
import com.agcforge.videodownloader.utils.PreferenceManager
import com.agcforge.videodownloader.utils.loadImage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.navigation.NavOptions
import com.agcforge.videodownloader.ui.activities.web.WebViewActivity
import com.google.android.material.imageview.ShapeableImageView
import com.startapp.sdk.internal.th

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var preferenceManager: PreferenceManager

    private var isDarkMode: Boolean = false

    private var isFirstLaunch = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBackPressedCallback()

        preferenceManager = PreferenceManager(this)

        setupToolbar()
        setupNavigation()
        setupDrawer()
        updateNavigationHeader()
        startWebSocketService()
    }

    private fun observeThemeChanges() {
        lifecycleScope.launch {
            preferenceManager.theme.collect { themeValue ->
                isDarkMode = themeValue?.toIntOrNull() == AppCompatDelegate.MODE_NIGHT_YES

                val backgroundRes = if (isDarkMode) {
                    R.color.bg_menu_primary
                } else {
                    R.color.bg_menu_primary
                }

                binding.toolbar.setBackgroundColor(ContextCompat.getColor(this@MainActivity, backgroundRes))
                binding.bottomNavigation.setBackgroundColor(ContextCompat.getColor(this@MainActivity, backgroundRes))

                val headerView = binding.navigationView.getHeaderView(0)
                val headerBinding = NavHeaderBinding.bind(headerView)

                headerBinding.headerNavigationView.setBackgroundColor(ContextCompat.getColor(this@MainActivity, backgroundRes))
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)

        binding.toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.ic_menu)
        binding.toolbar.setNavigationIconTint(ContextCompat.getColor(this, R.color.white))

        binding.toolbar.setNavigationOnClickListener {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        val topLevelDestinations = setOf(
            R.id.homeFragment,
            R.id.downloadsFragment,
            R.id.settingsFragment,
            R.id.historyFragment
        )

        appBarConfiguration = AppBarConfiguration(
            topLevelDestinations,
            binding.drawerLayout
        )

        binding.toolbar.setupWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                in topLevelDestinations -> {
                    binding.toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.ic_menu)
                    binding.toolbar.setNavigationOnClickListener {
                        toggleDrawer()
                    }
                }
                R.id.videoConverterFragment -> {
                    binding.toolbar.navigationIcon = ContextCompat.getDrawable(this, androidx.appcompat.R.drawable.abc_ic_ab_back_material)
                    binding.toolbar.setNavigationOnClickListener {
                        navigateBackFromVideoConverter()
                    }
                }
                else -> {
                    binding.toolbar.navigationIcon = ContextCompat.getDrawable(this, androidx.appcompat.R.drawable.abc_ic_ab_back_material)
                    binding.toolbar.setNavigationOnClickListener {
                        navController.navigateUp()
                    }
                }
            }
            when (destination.id) {
                R.id.videoConverterFragment,
                R.id.nav_premium -> {
                    binding.bottomNavigation.visibility = View.GONE
                }
                else -> {
                    binding.bottomNavigation.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupDrawer() {
        binding.navigationView.setNavigationItemSelectedListener(this)
    }

    private fun toggleDrawer() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun updateNavigationHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val ivAvatar = headerView.findViewById<ShapeableImageView>(R.id.ivAvatar)
        val tvUserName = headerView.findViewById<TextView>(R.id.tvUserName)
        val tvUserEmail = headerView.findViewById<TextView>(R.id.tvUserEmail)

        lifecycleScope.launch {
            val userName = preferenceManager.userName.first()
            val userEmail = preferenceManager.userName.first()
            val avatarUrl = preferenceManager.userAvatar.first()
            val token = preferenceManager.authToken.first()

            val isLoggedIn = !token.isNullOrEmpty()
            val menu = binding.navigationView.menu

            menu.findItem(R.id.nav_logout).isVisible = isLoggedIn
            menu.findItem(R.id.nav_login).isVisible = !isLoggedIn

            tvUserName.text = userName ?: "Guest User"
            tvUserEmail.text = userEmail ?: "guest@example.com"

            if (avatarUrl != null && avatarUrl.isNotEmpty()) {
                ivAvatar.loadImage(avatarUrl)
            }
        }
    }

    private fun startWebSocketService() {
        lifecycleScope.launch {
            val userId = preferenceManager.userId.first()
            val token = preferenceManager.authToken.first()

            if (!userId.isNullOrEmpty() && !token.isNullOrEmpty()) {
                WebSocketService.start(this@MainActivity, userId, token)
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                navController.navigate(R.id.homeFragment)
                binding.bottomNavigation.selectedItemId = R.id.nav_home
            }
            R.id.nav_downloads -> {
                navController.navigate(R.id.downloadsFragment)
                binding.bottomNavigation.selectedItemId = R.id.nav_downloads
            }
            R.id.nav_history -> {
                navController.navigate(R.id.historyFragment)
                binding.bottomNavigation.selectedItemId = R.id.nav_history
            }
            R.id.nav_settings -> {
                navController.navigate(R.id.settingsFragment)
                binding.bottomNavigation.selectedItemId = R.id.nav_settings
            }
            R.id.nav_video_converter -> {
                val navOptions = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setEnterAnim(androidx.navigation.ui.R.anim.nav_default_enter_anim)
                    .setExitAnim(androidx.navigation.ui.R.anim.nav_default_exit_anim)
                    .setPopEnterAnim(androidx.navigation.ui.R.anim.nav_default_pop_enter_anim)
                    .setPopExitAnim(androidx.navigation.ui.R.anim.nav_default_pop_exit_anim)
                    .build()

                navController.navigate(R.id.videoConverterFragment, null, navOptions)
            }

			R.id.nav_premium -> startActivity(Intent(this, SubscriptionActivity::class.java))
            R.id.nav_about -> {
                WebViewActivity.start(
                    context = this,
                    url = getString(R.string.about_url),
                    desktopMode = false
                )
            }
            R.id.nav_report -> {
                startActivity(Intent(this, ReportActivity::class.java))
            }
            R.id.nav_logout -> {
                showLogoutDialog()
            }
            R.id.nav_login -> {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            R.id.nav_site -> {
                val siteUrl = getString(R.string.site_url)
//                val intent = Intent(Intent.ACTION_VIEW, siteUrl.toUri())
//                startActivity(intent)
                WebViewActivity.start(
                    context = this,
                    url = siteUrl,
                    desktopMode = false
                )
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    @SuppressLint("StringFormatInvalid")
    private fun showAboutDialog() {
        val appCreator = "AgcForge Team"
        val librariesUsed = "Jetpack, Retrofit, and many other open source libraries"
        val formattedMessage = getString(R.string.about_dialog_message, appCreator, librariesUsed)
        MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
            .setTitle(R.string.about)
            .setMessage(formattedMessage)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)
            .setTitle(R.string.logout)
            .setMessage(getString(R.string.logout_confirm))
            .setPositiveButton(R.string.logout) { _, _ ->
                handleLogout()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleLogout() {
        lifecycleScope.launch {
            // Stop WebSocket service
            WebSocketService.stop(this@MainActivity)

            // Clear user data
            preferenceManager.clearUserData()

            // Navigate to login
            startActivity(Intent(this@MainActivity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun navigateBackFromVideoConverter() {
		if (!navController.popBackStack()) {
			navController.navigate(R.id.homeFragment)
			binding.bottomNavigation.selectedItemId = R.id.nav_home
		}
    }

    override fun onSupportNavigateUp(): Boolean {
        return when (navController.currentDestination?.id) {
            R.id.videoConverterFragment -> {
                navigateBackFromVideoConverter()
                true
            }
            in setOf(
                R.id.homeFragment,
                R.id.downloadsFragment,
                R.id.settingsFragment,
                R.id.historyFragment
            ) -> {
                toggleDrawer()
                true
            }
            else -> {
                navController.navigateUp()
            }
        }
    }

    private fun resetBottomNavigation() {
        binding.bottomNavigation.visibility = View.VISIBLE

        when (navController.currentDestination?.id) {
            R.id.homeFragment -> binding.bottomNavigation.selectedItemId = R.id.nav_home
            R.id.downloadsFragment -> binding.bottomNavigation.selectedItemId = R.id.nav_downloads
            R.id.historyFragment -> binding.bottomNavigation.selectedItemId = R.id.nav_history
            R.id.settingsFragment -> binding.bottomNavigation.selectedItemId = R.id.nav_settings
        }
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            @SuppressLint("SuspiciousIndentation")
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }

				if (!::navController.isInitialized) {
					finish()
					return
				}

				when (navController.currentDestination?.id) {
					R.id.videoConverterFragment -> {
						navigateBackFromVideoConverter()
						return
					}
					in setOf(
						R.id.homeFragment,
						R.id.downloadsFragment,
						R.id.settingsFragment,
						R.id.historyFragment
					) -> {
						finish()
						return
					}
				}

				if (!navController.popBackStack()) {
					finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

}
