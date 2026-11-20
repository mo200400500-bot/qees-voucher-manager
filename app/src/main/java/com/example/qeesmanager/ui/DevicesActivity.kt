package com.example.qeesmanager.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.qeesmanager.databinding.ActivityDevicesBinding
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject

class DevicesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDevicesBinding
    private val devices = mutableListOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDevicesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("qees_prefs", MODE_PRIVATE)
        val email = intent.getStringExtra("email")
            ?: prefs.getString("account_email", "")
            ?: ""
        binding.tvAccountEmail.text = if (email.isNotBlank()) email else "حساب المستخدم"

        loadDevices(prefs)
        renderDevices(prefs)

        binding.btnLogout.setOnClickListener {
            prefs.edit().remove("account_email").apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnAddDevice.setOnClickListener {
            showDeviceDialog(prefs, existingIndex = null)
        }
    }

    private fun loadDevices(prefs: android.content.SharedPreferences) {
        devices.clear()
        val raw = prefs.getString("devices_json", "[]").orEmpty()
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            devices.add(arr.getJSONObject(i))
        }
    }

    private fun saveDevices(prefs: android.content.SharedPreferences) {
        val arr = JSONArray()
        devices.forEach { arr.put(it) }
        prefs.edit().putString("devices_json", arr.toString()).apply()
    }

    private fun renderDevices(prefs: android.content.SharedPreferences) {
        binding.devicesContainer.removeAllViews()

        if (devices.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "لا توجد أجهزة مضافة بعد. اضغط على (+ إضافة جهاز) لإدخال بيانات الروتر."
                textSize = 14f
                setPadding(12, 24, 12, 12)
            }
            binding.devicesContainer.addView(emptyView)
            return
        }

        devices.forEachIndexed { index, device ->
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 24 }
                radius = 32f
                cardElevation = 4f
            }

            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(36, 32, 36, 32)
            }

            val deviceName = device.optString("name", "")
            val host = device.optString("host", "")
            val port = device.optString("apiPort", "8728")
            val username = device.optString("username", "")

            val title = TextView(this).apply {
                text = if (deviceName.isNotBlank()) deviceName else host
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val subtitle = TextView(this).apply {
                val userPart = if (username.isNotBlank()) " • المستخدم: $username" else ""
                text = "$host • API: $port$userPart"
                textSize = 13f
            }

            box.addView(title)
            box.addView(subtitle)
            card.addView(box)

            card.setOnClickListener {
                startActivity(
                    Intent(this, DeviceDashboardActivity::class.java).apply {
                        putExtra("name", deviceName)
                        putExtra("host", host)
                        putExtra("apiPort", port)
                        putExtra("username", username)
                        putExtra("password", device.optString("password", ""))
                    }
                )
            }

            card.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle(if (deviceName.isNotBlank()) deviceName else host)
                    .setItems(arrayOf("تعديل البيانات", "حذف الجهاز")) { _, which ->
                        when (which) {
                            0 -> showDeviceDialog(prefs, existingIndex = index)
                            1 -> {
                                devices.removeAt(index)
                                saveDevices(prefs)
                                renderDevices(prefs)
                            }
                        }
                    }
                    .show()
                true
            }

            binding.devicesContainer.addView(card)
        }
    }

    private fun showDeviceDialog(prefs: android.content.SharedPreferences, existingIndex: Int?) {
        val current = existingIndex?.let { devices.getOrNull(it) }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        val etName = EditText(this).apply {
            hint = "اسم الجهاز أو الشبكة"
            setText(current?.optString("name", "").orEmpty())
        }
        val etHost = EditText(this).apply {
            hint = "رابط أو IP الروتر"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(current?.optString("host", "").orEmpty())
        }
        val etUsername = EditText(this).apply {
            hint = "اسم مستخدم الروتر"
            setText(current?.optString("username", "").orEmpty())
        }
        val etPassword = EditText(this).apply {
            hint = "كلمة مرور الروتر"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(current?.optString("password", "").orEmpty())
        }
        val etPort = EditText(this).apply {
            hint = "منفذ API (مثال: 8728)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current?.optString("apiPort", "8728").orEmpty())
        }

        layout.addView(etName)
        layout.addView(etHost)
        layout.addView(etUsername)
        layout.addView(etPassword)
        layout.addView(etPort)

        val dialogTitle = if (existingIndex == null) "إضافة جهاز جديد" else "تعديل بيانات الجهاز"

        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val name = etName.text.toString().trim()
                val host = etHost.text.toString().trim()
                val username = etUsername.text.toString().trim()
                val password = etPassword.text.toString()
                val port = etPort.text.toString().trim().ifEmpty { "8728" }

                val obj = JSONObject().apply {
                    put("name", name)
                    put("host", host)
                    put("username", username)
                    put("password", password)
                    put("apiPort", port)
                }

                if (existingIndex != null && existingIndex in devices.indices) {
                    devices[existingIndex] = obj
                } else {
                    devices.add(obj)
                }
                saveDevices(prefs)
                renderDevices(prefs)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
