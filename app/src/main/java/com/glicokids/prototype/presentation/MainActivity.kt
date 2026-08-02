package com.glicokids.prototype.presentation

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.glicokids.prototype.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val TAG = "GlicoKids_Lifecycle"
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        /* Security Hardening: Prevent screenshots and screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        ) */

        Log.d(TAG, "onCreate: Activity Criada")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        showSafetyDisclaimer()
    }

    /**
     * Module 2 requirement: AlertDialog implementation.
     */
    private fun showSafetyDisclaimer() {
        AlertDialog.Builder(this)
            .setTitle("Aviso de Segurança")
            .setMessage("Este aplicativo é um protótipo acadêmico. Nunca tome decisões médicas baseadas exclusivamente nele sem consultar um profissional.")
            .setPositiveButton("Compreendido", null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Activity visível mas sem foco")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Activity pronta para interação")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Activity perdendo o foco")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: Activity não mais visível")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Activity sendo destruída")
    }
}
