package com.example.aredlapp.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.aredlapp.databinding.ActivityAuthWebviewBinding

class AuthWebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthWebviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val loginUrl = intent.getStringExtra(EXTRA_LOGIN_URL) ?: DEFAULT_LOGIN_URL

        binding.btnCloseAuth.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        with(binding.webAuth.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        binding.webAuth.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return interceptCallbackIfNeeded(url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressAuth.visibility = android.view.View.VISIBLE
                if (!url.isNullOrBlank()) interceptCallbackIfNeeded(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressAuth.visibility = android.view.View.GONE
            }
        }

        binding.webAuth.loadUrl(loginUrl)
    }

    private fun interceptCallbackIfNeeded(url: String): Boolean {
        if (!url.startsWith(CALLBACK_PREFIX)) return false
        val result = Intent().putExtra(EXTRA_CALLBACK_URL, url)
        setResult(Activity.RESULT_OK, result)
        finish()
        return true
    }

    override fun onDestroy() {
        binding.webAuth.stopLoading()
        binding.webAuth.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LOGIN_URL = "extra_login_url"
        const val EXTRA_CALLBACK_URL = "extra_callback_url"
        const val DEFAULT_LOGIN_URL = "https://api.aredl.net/v2/api/auth/discord"
        const val CALLBACK_PREFIX = "https://api.aredl.net/v2/api/auth/discord/callback"
    }
}
