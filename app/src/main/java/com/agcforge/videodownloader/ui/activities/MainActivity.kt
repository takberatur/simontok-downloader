package com.agcforge.videodownloader.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
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
import com.agcforge.videodownloader.utils.PreferenceManager
import com.agcforge.videodownloader.utils.loadImage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
                        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                            binding.drawerLayout.closeDrawer(GravityCompat.START)
                        } else {
                            binding.drawerLayout.openDrawer(GravityCompat.START)
                        }
                    }
                }
                else -> {
                    binding.toolbar.navigationIcon = ContextCompat.getDrawable(this, androidx.appcompat.R.drawable.abc_ic_ab_back_material)
                    binding.toolbar.setNavigationOnClickListener {
                        navController.navigateUp()
                    }
                }
            }
        }
    }

    private fun setupDrawer() {
        binding.navigationView.setNavigationItemSelectedListener(this)
    }

    private fun updateNavigationHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val ivAvatar = headerView.findViewById<ImageView>(R.id.ivAvatar)
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
            R.id.nav_home -> navController.navigate(R.id.homeFragment)
            R.id.nav_downloads -> navController.navigate(R.id.downloadsFragment)
            R.id.nav_settings -> navController.navigate(R.id.settingsFragment)
            R.id.nav_history -> navController.navigate(R.id.historyFragment)
            R.id.nav_about -> {
                showAboutDialog()
            }
            R.id.nav_logout -> {
                showLogoutDialog()
            }
            R.id.nav_login -> {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            R.id.nav_site -> {
                val siteUrl = getString(R.string.site_url)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(siteUrl))
                startActivity(intent)
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
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about)
            .setMessage(formattedMessage)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout)
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton(R.string.logout) { _, _ ->
                handleLogout()
            }
            .setNegativeButton("Cancel", null)
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

    override fun onSupportNavigateUp(): Boolean {
        return if (navController.currentDestination?.id in setOf(
                R.id.homeFragment,
                R.id.downloadsFragment,
                R.id.settingsFragment,
                R.id.historyFragment
            )) {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
            true
        } else {
            navController.navigateUp()
        }
    }

    private fun setupBackPressedCallback() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

}
