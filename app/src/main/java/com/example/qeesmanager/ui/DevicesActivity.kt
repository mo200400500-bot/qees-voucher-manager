package com.example.qeesmanager.ui

import android.content.Intent
import android.os.Bundle
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
            ?: prefs.getString("account_email", "mo200400500@gmail.com")
            ?: "mo200400500@gmail.com"
        binding.tvAccountEmail.text = email

        loadDevices(prefs)
        renderDevices()

        binding.btnLogout.setOnClickListener {
            prefs.edit().remove("account_email").apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnAddDevice.setOnClickListener {
            showAddDeviceDialog(prefs)
        }
    }

    private fun loadDevices(prefs: android.content.SharedPreferences) {
        devices.clear()
        val raw = prefs.getString("devices_json", null)
        if (raw.isNullOrBlank()) {
            devices.add(
                JSONObject().apply {
                    put("name", "star net")
                    put("host", "https://l.net/login")
                    put("apiPort", "8728")
                    put("username", "admin")
                }
            )
            saveDevices(prefs)
            return
        }
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

    private fun renderDevices() {
        binding.devicesContainer.removeAllViews()
        for (device in devices) {
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

            val title = TextView(this).apply {
                text = device.optString("name", "جهاز MikroTik")
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val subtitle = TextView(this).apply {
                text = "${device.optString("host")} • API: ${device.optString("apiPort", "8728")}"
                textSize = 13f
            }

            box.addView(title)
            box.addView(subtitle)
            card.addView(box)

            card.setOnClickListener {
                startActivity(
                    Intent(this, DeviceDashboardActivity::class.java).apply {
                        putExtra("name", device.optString("name"))
                        putExtra("host", device.optString("host"))
                        putExtra("apiPort", device.optString("apiPort", "8728"))
                        putExtra("username", device.optString("username", "admin"))
                    }
                )
            }

            binding.devicesContainer.addView(card)
        }
    }

    private fun showAddDeviceDialog(prefs: android.content.SharedPreferences) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val etName = EditText(this).apply { hint = "اسم الجهاز (مثال: star net)" }
        val etHost = EditText(this).apply {
            hint = "عنوان الراوتر أو الهوت سبوت (مثال: https://l.net/login)"
            setText("https://l.net/login")
        }
        val etPort = EditText(this).apply {
            hint = "منفذ RouterOS API (مثال: 8728)"
            setText("8728")
        }

        layout.addView(etName)
        layout.addView(etHost)
        layout.addView(etPort)

        AlertDialog.Builder(this)
            .setTitle("إضافة جهاز جديد")
            .setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val name = etName.text.toString().trim().ifEmpty { "MikroTik Device" }
                val host = etHost.text.toString().trim().ifEmpty { "192.168.88.1" }
                val port = etPort.text.toString().trim().ifEmpty { "8728" }
                devices.add(
                    JSONObject().apply {
                        put("name", name)
                        put("host", host)
                        put("apiPort", port)
                        put("username", "admin")
                    }
                )
                saveDevices(prefs)
                renderDevices()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
