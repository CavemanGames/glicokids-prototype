package com.glicokids.prototype.presentation.parents

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.glicokids.prototype.R
import com.glicokids.prototype.databinding.ActivityParentSecurityBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ParentSecurityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParentSecurityBinding
    private val viewModel: SecurityViewModel by viewModels()
    private var currentPin = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParentSecurityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        val keys = listOf(
            binding.btnKey1, binding.btnKey2, binding.btnKey3,
            binding.btnKey4, binding.btnKey5, binding.btnKey6,
            binding.btnKey7, binding.btnKey8, binding.btnKey9,
            binding.btnKey0
        )

        keys.forEach { btn ->
            btn.setOnClickListener { addDigit(btn.text.toString()) }
        }

        binding.btnKeyDel.setOnClickListener { removeDigit() }
    }

    private fun addDigit(digit: String) {
        if (currentPin.length < 4) {
            currentPin += digit
            updateDots()
            if (currentPin.length == 4) {
                viewModel.validatePin(currentPin)
            }
        }
    }

    private fun removeDigit() {
        if (currentPin.isNotEmpty()) {
            currentPin = currentPin.substring(0, currentPin.length - 1)
            updateDots()
        }
    }

    private fun updateDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
        dots.forEachIndexed { index, view ->
            if (index < currentPin.length) {
                view.setBackgroundResource(R.drawable.bg_pin_dot_filled)
            } else {
                view.setBackgroundResource(R.drawable.bg_pin_dot_empty)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.accessGranted.observe(this) { granted ->
            if (granted) {
                Toast.makeText(this, "Acesso Concedido!", Toast.LENGTH_SHORT).show()
                // Whoever opened the lock decides where to go: we return a result
                // instead of just closing (before this the Parent Area was unreachable).
                setResult(RESULT_OK)
                finish()
            } else if (currentPin.length == 4) {
                // Auto-clear on wrong PIN
                currentPin = ""
                updateDots()
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
        
        viewModel.isLocked.observe(this) { locked ->
            if (locked) {
                binding.glKeypad.visibility = View.GONE
                binding.tvSubtitle.text = "Acesso bloqueado por segurança."
            }
        }
    }
}
