package com.example.aredlapp.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputLayout

object ThemeUtils {
    fun getSecondaryColor(context: Context): Int {
        val prefs = context.getSharedPreferences("aredl_settings", Context.MODE_PRIVATE)
        val colorHex = prefs.getString("secondary_color", "#FF8C00") ?: "#FF8C00"
        return try {
            colorHex.toColorInt()
        } catch (e: Exception) {
            Color.parseColor("#FF8C00")
        }
    }

    fun applyThemeColor(view: View) {
        val color = getSecondaryColor(view.context)
        val colorStateList = ColorStateList.valueOf(color)

        when (view) {
            is MaterialButton -> {
                view.backgroundTintList = colorStateList
            }
            is Button -> {
                view.backgroundTintList = colorStateList
            }
            is TextView -> {
                // only change aredl_orange colored text
                view.setTextColor(color)
            }
            is ImageView -> {
                view.imageTintList = colorStateList
            }
            is TextInputLayout -> {
                view.boxStrokeColor = color
                view.hintTextColor = colorStateList
            }
            is Chip -> {
                view.setTextColor(colorStateList)
                view.chipStrokeColor = colorStateList
            }
        }
    }
}
