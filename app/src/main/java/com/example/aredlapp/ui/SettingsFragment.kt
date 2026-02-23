package com.example.aredlapp.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.example.aredlapp.databinding.FragmentSettingsBinding
import com.example.aredlapp.utils.ThemeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var selectedTheme: String = "Dark"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("aredl_settings", Context.MODE_PRIVATE)
        
        // Load current settings
        val isDarkMode = prefs.getBoolean("dark_mode", true)
        selectedTheme = if (isDarkMode) "Dark" else "Light"
        binding.btnSelectTheme.text = selectedTheme

        val secondaryColor = prefs.getString("secondary_color", "#FF8C00") ?: "#FF8C00"
        binding.editHexColor.setText(secondaryColor)
        updatePreview(secondaryColor)
        
        applyColors()

        binding.btnSelectTheme.setOnClickListener {
            val themes = arrayOf("Dark", "Light")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Theme")
                .setItems(themes) { _, which ->
                    selectedTheme = themes[which]
                    binding.btnSelectTheme.text = selectedTheme
                    
                    // Auto-apply theme switch
                    val newDarkMode = selectedTheme == "Dark"
                    prefs.edit().putBoolean("dark_mode", newDarkMode).apply()
                    
                    if (newDarkMode) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                    requireActivity().recreate()
                }
                .show()
        }

        binding.btnResetColor.setOnClickListener {
            binding.editHexColor.setText("#FF8C00")
            updatePreview("#FF8C00")
        }

        binding.btnApplySettings.setOnClickListener {
            val newColor = binding.editHexColor.text.toString()

            if (isValidHex(newColor)) {
                prefs.edit().apply {
                    putString("secondary_color", newColor)
                    apply()
                }

                // Recreate activity to apply color changes
                requireActivity().recreate()

            } else {
                Toast.makeText(context, "Invalid Hex Color (ex: #FF8C00)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePreview(color: String) {
        try {
            binding.viewColorPreview.setBackgroundColor(color.toColorInt())
        } catch (e: Exception) {}
    }

    private fun applyColors() {
        val color = ThemeUtils.getSecondaryColor(requireContext())
        val colorStateList = ColorStateList.valueOf(color)

        binding.settingsTitle.setTextColor(color)
        binding.btnApplySettings.backgroundTintList = colorStateList
        binding.btnResetColor.strokeColor = colorStateList
        binding.btnResetColor.setTextColor(color)
        
        binding.inputLayoutHex.setBoxStrokeColor(color)
        binding.inputLayoutHex.setHintTextColor(colorStateList)
        binding.btnSelectTheme.strokeColor = colorStateList
        binding.btnSelectTheme.setTextColor(color)
    }

    private fun isValidHex(color: String): Boolean {
        return try {
            color.toColorInt()
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
