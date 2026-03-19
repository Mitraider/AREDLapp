package com.example.aredlapp

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.setupWithNavController
import coil.load
import coil.transform.CircleCropTransformation
import com.example.aredlapp.databinding.ActivityMainBinding
import com.example.aredlapp.ui.AuthWebViewActivity
import com.example.aredlapp.utils.ThemeUtils
import com.example.aredlapp.viewmodel.AredlViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: AredlViewModel by viewModels()
    private lateinit var navHeaderAuthStatus: TextView
    private lateinit var aboutCreatorText: TextView
    private lateinit var aboutSpecialThanksText: TextView
    private lateinit var aboutGithubText: TextView
    private lateinit var aboutAvatar: ImageView
    private lateinit var navController: NavController

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val callbackUrl = result.data?.getStringExtra(AuthWebViewActivity.EXTRA_CALLBACK_URL)
        if (callbackUrl.isNullOrBlank()) {
            Toast.makeText(this, "Login callback missing", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        viewModel.completeDiscordLogin(callbackUrl)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedThemeMode()

        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContentContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.navView.setupWithNavController(navController)
        navHeaderAuthStatus = binding.navHeaderAuthStatus
        aboutCreatorText = binding.textAboutCreator
        aboutSpecialThanksText = binding.textAboutSpecialThanks
        aboutGithubText = binding.textAboutGithub
        aboutAvatar = binding.imgAboutAvatar

        val color = ThemeUtils.getSecondaryColor(this)
        val colorStateList = ColorStateList.valueOf(color)
        binding.navView.itemIconTintList = colorStateList
        binding.navView.itemTextColor = colorStateList
        binding.navView.itemBackground = createDrawerItemBackground(color)
        binding.btnMenu.imageTintList = colorStateList
        binding.btnDrawerSettings.strokeColor = colorStateList
        binding.btnDrawerSettings.setTextColor(color)
        aboutGithubText.setTextColor(color)
        binding.btnAboutRepository.strokeColor = colorStateList
        binding.btnAboutRepository.setTextColor(color)
        binding.btnLogin.backgroundTintList = colorStateList
        binding.btnDiscordInvite.backgroundTintList = colorStateList

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showMainToolbar = when (destination.id) {
                R.id.nav_levels, R.id.nav_leaderboard, R.id.nav_todo, R.id.nav_my_submissions, R.id.nav_packs, R.id.nav_games, R.id.nav_settings, R.id.nav_player_detail -> true
                else -> false
            }
            binding.toolbar.visibility = if (showMainToolbar) View.VISIBLE else View.GONE

            val inDrawerMenu = when (destination.id) {
                R.id.nav_levels, R.id.nav_leaderboard, R.id.nav_todo, R.id.nav_my_submissions, R.id.nav_packs, R.id.nav_games -> true
                else -> false
            }
            if (!inDrawerMenu) {
                val menu = binding.navView.menu
                for (i in 0 until menu.size()) {
                    menu.getItem(i).isChecked = false
                }
            } else {
                binding.navView.setCheckedItem(destination.id)
            }

            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.btnDiscordInvite.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/invite/aredl")))
        }

        binding.btnLogin.setOnClickListener {
            authLauncher.launch(
                Intent(this, AuthWebViewActivity::class.java).putExtra(
                    AuthWebViewActivity.EXTRA_LOGIN_URL,
                    AuthWebViewActivity.DEFAULT_LOGIN_URL
                )
            )
        }

        binding.imgAuthAvatar.setOnClickListener { anchor ->
            showAccountPopup(navController, anchor = anchor)
        }

        binding.btnDrawerSettings.setOnClickListener {
            navigateToTopLevel(navController, R.id.nav_settings)
        }
        aboutGithubText.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Mitraider/AREDLapp")))
        }
        binding.btnAboutRepository.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Mitraider/AREDLapp")))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { state ->
                    val displayName = state.globalName ?: state.username ?: "Unknown"
                    navHeaderAuthStatus.text = if (state.isAuthenticated) {
                        "Connected as $displayName"
                    } else {
                        "Not connected"
                    }

                    binding.btnLogin.visibility = if (state.isAuthenticated) View.GONE else View.VISIBLE
                    binding.imgAuthAvatar.visibility = if (state.isAuthenticated) View.VISIBLE else View.GONE

                    if (state.isAuthenticated) {
                        val avatarUrl =
                            if (!state.discordId.isNullOrBlank() && !state.discordAvatar.isNullOrBlank()) {
                                "https://cdn.discordapp.com/avatars/${state.discordId}/${state.discordAvatar}.webp?size=128"
                            } else null
                        binding.imgAuthAvatar.load(avatarUrl ?: R.drawable.aredl_logo) {
                            crossfade(true)
                            placeholder(R.drawable.aredl_logo)
                            error(R.drawable.aredl_logo)
                            transformations(CircleCropTransformation())
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.aboutCreatorProfile.collect { profile ->
                    val user = profile?.user
                    val creatorName = user?.global_name ?: user?.username ?: "Mitraider08"
                    aboutCreatorText.text = "Created by $creatorName"
                    val avatarUrl =
                        if (user != null && !user.discord_id.isNullOrBlank() && !user.discord_avatar.isNullOrBlank()) {
                            "https://cdn.discordapp.com/avatars/${user.discord_id}/${user.discord_avatar}.webp?size=128"
                        } else {
                            user?.avatar
                        }
                    aboutAvatar.load(avatarUrl ?: R.drawable.aredl_logo) {
                        crossfade(true)
                        placeholder(R.drawable.aredl_logo)
                        error(R.drawable.aredl_logo)
                        transformations(CircleCropTransformation())
                    }
                }
            }
        }

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {
                binding.btnMenu.imageTintList = ColorStateList.valueOf(android.graphics.Color.BLACK)
            }
            override fun onDrawerClosed(drawerView: View) {
                binding.btnMenu.imageTintList = colorStateList
            }
            override fun onDrawerStateChanged(newState: Int) {}
        })
    }

    override fun onResume() {
        super.onResume()
        applySavedThemeMode()
    }

    private fun applySavedThemeMode() {
        val prefs = getSharedPreferences("aredl_settings", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", true)
        val targetMode = if (isDarkMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }

    private fun createDrawerItemBackground(accentColor: Int): StateListDrawable {
        val checkedBackground = LayerDrawable(
            arrayOf(
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.argb(26, 255, 255, 255))
                },
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(accentColor)
                }
            )
        ).apply {
            setLayerInset(1, 0, 0, resources.displayMetrics.density.times(0).toInt(), 0)
            setLayerSize(1, resources.displayMetrics.density.times(4).toInt(), -1)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), checkedBackground)
            addState(intArrayOf(), GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
            })
        }
    }

    private fun navigateToTopLevel(navController: NavController, destinationId: Int) {
        if (navController.currentDestination?.id == destinationId) return
        try {
            navController.navigate(destinationId, null, navOptions {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            })
        } catch (_: Exception) {
            Toast.makeText(this, "Navigation unavailable right now", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAccountPopup(navController: NavController, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.account_popup_menu, popup.menu)
        popup.menu.findItem(R.id.action_account_profile).isVisible = true
        popup.menu.findItem(R.id.action_account_logout).isVisible = true
        popup.menu.findItem(R.id.action_account_login).isVisible = false

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_account_profile -> {
                    if (!viewModel.selectAuthenticatedPlayer()) {
                        Toast.makeText(this, "Profile unavailable on leaderboard", Toast.LENGTH_SHORT).show()
                    } else {
                        navigateToTopLevel(navController, R.id.nav_player_detail)
                    }
                    true
                }
                R.id.action_account_logout -> {
                    viewModel.logoutDiscord()
                    Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}
