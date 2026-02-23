package com.example.aredlapp

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.aredlapp.databinding.ActivityMainBinding
import com.example.aredlapp.utils.ThemeUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("aredl_settings", Context.MODE_PRIVATE)
        
        val isDarkMode = prefs.getBoolean("dark_mode", true)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

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
        val navController = navHostFragment.navController
        
        binding.navView.setupWithNavController(navController)

        val color = ThemeUtils.getSecondaryColor(this)
        val colorStateList = ColorStateList.valueOf(color)
        binding.navView.itemIconTintList = colorStateList
        binding.navView.itemTextColor = colorStateList
        binding.btnMenu.imageTintList = colorStateList

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showMainToolbar = when (destination.id) {
                R.id.nav_levels, R.id.nav_leaderboard, R.id.nav_todo, R.id.nav_games, R.id.nav_settings -> true
                else -> false
            }
            binding.toolbar.visibility = if (showMainToolbar) View.VISIBLE else View.GONE
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
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
}
