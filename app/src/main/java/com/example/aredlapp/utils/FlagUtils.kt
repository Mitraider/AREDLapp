package com.example.aredlapp.utils

import android.view.View
import android.widget.ImageView
import coil.decode.SvgDecoder
import coil.load
import coil.request.CachePolicy
import com.example.aredlapp.R

object FlagUtils {

    private fun flagUrl(alpha2: String): String {
        return "https://cdn.jsdelivr.net/gh/lipis/flag-icons/flags/4x3/${alpha2.lowercase()}.svg"
    }

    fun loadFlag(imageView: ImageView, countryCodeNumeric: String?) {
        val alpha2 = CountryUtils.getCountryAlpha2(countryCodeNumeric)
        if (alpha2.isNullOrBlank()) {
            imageView.visibility = View.GONE
            imageView.setImageDrawable(null)
            return
        }

        imageView.visibility = View.VISIBLE
        imageView.load(flagUrl(alpha2)) {
            decoderFactory(SvgDecoder.Factory())
            crossfade(true)
            placeholder(R.drawable.aredl_logo)
            error(R.drawable.aredl_logo)
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
        }
    }
}
