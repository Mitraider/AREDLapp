package com.example.aredlapp

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
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
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
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

    @RequiresApi(Build.VERSION_CODES.O)
    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val callbackUrl = result.data?.getStringExtra(AuthWebViewActivity.EXTRA_CALLBACK_URL)
        if (callbackUrl.isNullOrBlank()) {
            Toast.makeText(this, "Login callback missing", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        viewModel.completeDiscordLogin(callbackUrl)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedThemeMode()

        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        consumeAuthCallback(intent)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContentContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        navHeaderAuthStatus = binding.navHeaderAuthStatus

        binding.navView.setNavigationItemSelectedListener { item ->
            navigateToTopLevel(navController, item.itemId)
            true
        }

        val color = ThemeUtils.getSecondaryColor(this)
        val colorStateList = ColorStateList.valueOf(color)
        binding.navView.itemIconTintList = colorStateList
        binding.navView.itemTextColor = colorStateList
        binding.navView.itemBackground = createDrawerItemBackground(color)
        binding.btnMenu.imageTintList = colorStateList
        binding.btnLogin.backgroundTintList = colorStateList
        binding.btnAboutRepository.strokeColor = colorStateList
        binding.btnAboutRepository.setTextColor(color)
        binding.textAboutGithub.setTextColor(color)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showMainToolbar = when (destination.id) {
                R.id.nav_levels, R.id.nav_leaderboard, R.id.nav_todo, R.id.nav_my_submissions, R.id.nav_packs, R.id.nav_games, R.id.nav_settings, R.id.nav_player_detail -> true
                else -> false
            }
            binding.toolbar.visibility = if (showMainToolbar) View.VISIBLE else View.GONE

            val inDrawerMenu = when (destination.id) {
                R.id.nav_levels, R.id.nav_leaderboard, R.id.nav_todo, R.id.nav_my_submissions, R.id.nav_packs, R.id.nav_games, R.id.nav_settings -> true
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

        binding.btnAuth.setOnClickListener {
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

        val repoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Mitraider/AREDLapp"))
        binding.textAboutGithub.setOnClickListener { startActivity(repoIntent) }
        binding.btnAboutRepository.setOnClickListener { startActivity(repoIntent) }

        binding.imgAuthAvatar.setOnClickListener { anchor ->
            showAccountPopup(navController, isAuthenticated = true, anchor = anchor)
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

                    binding.btnAuth.visibility = View.VISIBLE
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
                    val displayName = user?.global_name ?: user?.username ?: "Mitraider08"
                    binding.textAboutCreator.text = "Created by $displayName"

                    val avatarUrl = when {
                        !user?.avatar.isNullOrBlank() -> user?.avatar
                        !user?.discord_id.isNullOrBlank() && !user?.discord_avatar.isNullOrBlank() ->
                            "https://cdn.discordapp.com/avatars/${user?.discord_id}/${user?.discord_avatar}.webp?size=128"
                        else -> null
                    }

                    binding.imgAboutAvatar.load(avatarUrl ?: R.drawable.aredl_logo) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeAuthCallback(intent)
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
            setLayerInset(1, 0, 0, 0, 0)
            setLayerSize(1, resources.displayMetrics.density.times(4).toInt(), -1)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), checkedBackground)
            addState(
                intArrayOf(),
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                }
            )
        }
    }

    private fun navigateToTopLevel(navController: NavController, destinationId: Int) {
        if (navController.currentDestination?.id == destinationId) return
        try {
            navController.navigate(destinationId, null, navOptions {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            })
        } catch (_: Exception) {
            Toast.makeText(this, "Navigation unavailable right now", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToPlayerDetail(navController: NavController) {
        if (navController.currentDestination?.id == R.id.nav_player_detail) return
        try {
            navController.navigate(R.id.nav_player_detail, null, navOptions {
                launchSingleTop = true
            })
        } catch (_: Exception) {
            Toast.makeText(this, "Navigation unavailable right now", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun consumeAuthCallback(intent: Intent?) {
        val callbackUrl = intent?.dataString
        if (callbackUrl.isNullOrBlank()) return
        val isCustomSchemeCallback = callbackUrl.startsWith(DISCORD_APP_CALLBACK_PREFIX)
        val isHttpsCallback = callbackUrl.startsWith(DISCORD_HTTPS_CALLBACK_PREFIX)
        if (!isCustomSchemeCallback && !isHttpsCallback) return
        viewModel.completeDiscordLogin(callbackUrl)
        setIntent(Intent(intent).setData(null))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showAccountPopup(navController: NavController, isAuthenticated: Boolean, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.account_popup_menu, popup.menu)
        popup.menu.findItem(R.id.action_account_profile).isVisible = isAuthenticated
        popup.menu.findItem(R.id.action_account_logout).isVisible = isAuthenticated
        popup.menu.findItem(R.id.action_account_login).isVisible = !isAuthenticated

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_account_profile -> {
                    if (!viewModel.selectAuthenticatedPlayer()) {
                        Toast.makeText(this, "Profile unavailable on leaderboard", Toast.LENGTH_SHORT).show()
                    } else {
                        navigateToPlayerDetail(navController)
                    }
                    true
                }
                R.id.action_account_login -> {
                    authLauncher.launch(
                        Intent(this, AuthWebViewActivity::class.java).putExtra(
                            AuthWebViewActivity.EXTRA_LOGIN_URL,
                            AuthWebViewActivity.DEFAULT_LOGIN_URL
                        )
                    )
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

    companion object {
        private const val DISCORD_APP_CALLBACK_PREFIX = "aredlapp://auth/discord/callback"
        private const val DISCORD_HTTPS_CALLBACK_PREFIX = "https://api.aredl.net/v2/api/auth/discord/callback"
    }
}
