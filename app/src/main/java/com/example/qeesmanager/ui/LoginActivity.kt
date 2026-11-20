package com.example.qeesmanager.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.qeesmanager.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("qees_prefs", MODE_PRIVATE)
        val savedEmail = prefs.getString("account_email", null)
        if (!savedEmail.isNullOrBlank()) {
            openDevices(savedEmail)
            return
        }

        binding.authToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isRegisterMode = (checkedId == binding.btnTabRegister.id)
                binding.btnSubmitAuth.text = if (isRegisterMode) "إنشاء حساب" else "تسجيل الدخول"
            }
        }

        binding.tvForgotPassword.setOnClickListener {
            Toast.makeText(
                this,
                "أدخل البريد الإلكتروني وكلمة المرور للمتابعة إلى إدارة أجهزتك.",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnSubmitAuth.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            if (email.isEmpty()) {
                binding.etEmail.error = "أدخل البريد الإلكتروني"
                return@setOnClickListener
            }
            prefs.edit().putString("account_email", email).apply()
            openDevices(email)
        }
    }

    private fun openDevices(email: String) {
        startActivity(
            Intent(this, DevicesActivity::class.java).apply {
                putExtra("email", email)
            }
        )
        finish()
    }
}
