package com.glicokids.prototype.presentation.kids

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.glicokids.prototype.R
import com.glicokids.prototype.databinding.ActivityHelpBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

@AndroidEntryPoint
class HelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.wvHelp.apply {
            webViewClient = WebViewClient() // never hand off to an external browser
            settings.javaScriptEnabled = true
        }

        if (isOnline()) {
            binding.tvOfflineBanner.visibility = View.GONE
            binding.wvHelp.loadUrl(ONLINE_URL)
        } else {
            showOfflineGuide()
        }
    }

    /**
     * Module 5 — requirement 6: with no connection the content comes from
     * `res/raw/ajuda_offline.html` through `openRawResource` + `InputStreamReader`.
     */
    private fun showOfflineGuide() {
        binding.tvOfflineBanner.visibility = View.VISIBLE

        lifecycleScope.launch {
            val html = withContext(Dispatchers.IO) {
                resources.openRawResource(R.raw.ajuda_offline).use { input ->
                    InputStreamReader(input, Charsets.UTF_8).use { it.readText() }
                }
            }
            binding.wvHelp.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        private const val ONLINE_URL = "https://diabetes.org.br"
    }
}
