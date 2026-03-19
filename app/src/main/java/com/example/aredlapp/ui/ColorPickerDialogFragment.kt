package com.example.aredlapp.ui

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.core.graphics.toColorInt
import androidx.fragment.app.DialogFragment
import com.example.aredlapp.databinding.DialogColorPickerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ColorPickerDialogFragment : DialogFragment() {

    private var _binding: DialogColorPickerBinding? = null
    private val binding get() = _binding!!

    private var hue: Float = 32f
    private var saturation: Float = 1f
    private var value: Float = 1f
    private var updatingHex = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogColorPickerBinding.inflate(layoutInflater)

        val initialHex = arguments?.getString(ARG_INITIAL_HEX).orEmpty().ifBlank { DEFAULT_HEX }
        setColorFromHex(initialHex)

        binding.hueSliderPicker.setOnHueChangedListener { newHue ->
            hue = newHue
            binding.colorSquarePicker.setHue(newHue)
            updateHexField(fromUser = false)
        }
        binding.colorSquarePicker.setOnSelectionChangedListener { newSaturation, newValue ->
            saturation = newSaturation
            value = newValue
            updateHexField(fromUser = false)
        }
        binding.btnPickerReset.setOnClickListener {
            setColorFromHex(DEFAULT_HEX)
        }
        binding.editPickerHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updatingHex) return
                val candidate = s?.toString().orEmpty()
                if (candidate.length == 7 && runCatching { candidate.toColorInt() }.isSuccess) {
                    setColorFromHex(candidate)
                }
            }
        })

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pick a secondary color")
            .setView(binding.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Use color") { _, _ ->
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply { putString(RESULT_HEX, currentHex()) }
                )
            }
            .create()
    }

    private fun setColorFromHex(hex: String) {
        val hsv = FloatArray(3)
        Color.colorToHSV(hex.toColorInt(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        binding.hueSliderPicker.setHue(hue)
        binding.colorSquarePicker.setHue(hue)
        binding.colorSquarePicker.setSelection(saturation, value)
        updateHexField(fromUser = false)
    }

    private fun updateHexField(fromUser: Boolean) {
        val hex = currentHex()
        updatingHex = true
        binding.editPickerHex.setText(hex)
        binding.editPickerHex.setSelection(hex.length)
        updatingHex = false
    }

    private fun currentHex(): String {
        return String.format("#%06X", 0xFFFFFF and Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_INITIAL_HEX = "initial_hex"
        private const val DEFAULT_HEX = "#FF8C00"
        const val REQUEST_KEY = "color_picker_request"
        const val RESULT_HEX = "color_picker_result_hex"

        fun newInstance(initialHex: String): ColorPickerDialogFragment {
            return ColorPickerDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_INITIAL_HEX, initialHex)
                }
            }
        }
    }
}
