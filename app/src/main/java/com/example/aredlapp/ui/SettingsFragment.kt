package com.example.aredlapp.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
    private var updatingHexFromPicker = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("aredl_settings", Context.MODE_PRIVATE)
        
        // settings loading
        val isDarkMode = prefs.getBoolean("dark_mode", true)
        selectedTheme = if (isDarkMode) "Dark" else "Light"
        binding.btnSelectTheme.text = selectedTheme

        val secondaryColor = prefs.getString("secondary_color", "#FF8C00") ?: "#FF8C00"
        binding.editHexColor.setText(secondaryColor)
        setPickerColor(secondaryColor)

        applyColors()

        binding.editHexColor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updatingHexFromPicker) return
                val candidate = s?.toString().orEmpty()
                if (isValidHex(candidate)) {
                    setPickerColor(candidate)
                }
            }
        })

        binding.settingsHueSliderPicker.setOnHueChangedListener { hue ->
            binding.settingsColorSquarePicker.setHue(hue)
            syncHexFromPicker()
        }
        binding.settingsColorSquarePicker.setOnSelectionChangedListener { _, _ ->
            syncHexFromPicker()
        }

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
            setPickerColor("#FF8C00")
        }

        binding.btnApplySettings.setOnClickListener {
            val newColor = binding.editHexColor.text.toString()

            if (isValidHex(newColor)) {
                prefs.edit().apply {
                    putString("secondary_color", newColor)
                    apply()
                }

                // ugly way to apply settings without restarting the app
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

    private fun setPickerColor(color: String) {
        try {
            val hsv = FloatArray(3)
            Color.colorToHSV(color.toColorInt(), hsv)
            binding.settingsHueSliderPicker.setHue(hsv[0])
            binding.settingsColorSquarePicker.setHue(hsv[0])
            binding.settingsColorSquarePicker.setSelection(hsv[1], hsv[2])
            updatingHexFromPicker = true
            binding.editHexColor.setText(color.uppercase())
            binding.editHexColor.setSelection(binding.editHexColor.text?.length ?: 0)
            updatingHexFromPicker = false
            updatePreview(color)
        } catch (_: Exception) {
            updatingHexFromPicker = false
        }
    }

    private fun syncHexFromPicker() {
        val hue = binding.settingsHueSliderPicker.getHue()
        val saturation = binding.settingsColorSquarePicker.getSaturation()
        val value = binding.settingsColorSquarePicker.getValue()
        val hex = String.format("#%06X", 0xFFFFFF and Color.HSVToColor(floatArrayOf(hue, saturation, value)))
        updatingHexFromPicker = true
        binding.editHexColor.setText(hex)
        binding.editHexColor.setSelection(hex.length)
        updatingHexFromPicker = false
        updatePreview(hex)
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
