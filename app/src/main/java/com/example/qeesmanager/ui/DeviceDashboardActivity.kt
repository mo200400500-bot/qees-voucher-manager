package com.example.qeesmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.qeesmanager.MainActivity
import com.example.qeesmanager.databinding.ActivityDeviceDashboardBinding
import java.util.UUID

class DeviceDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val name = intent.getStringExtra("name").orEmpty()
        val host = intent.getStringExtra("host").orEmpty()
        val port = intent.getStringExtra("apiPort").orEmpty().ifEmpty { "8728" }
        val username = intent.getStringExtra("username").orEmpty()

        binding.tvDeviceTitle.text = if (name.isNotBlank()) name else "جهاز MikroTik"
        binding.tvDeviceMeta.text = if (host.isNotBlank()) "$host • API: $port" else "API: $port"

        val baseHost = if (host.isNotBlank()) host else "[رابط الروتر]"

        binding.btnPrintBarcode.setOnClickListener {
            val batch = (1..6).joinToString("\n") { idx ->
                val code = UUID.randomUUID().toString().take(8).uppercase()
                "كرت #$idx | كود: $code | QR: $baseHost?username=$code"
            }
            showResult("دفعة كروت جاهزة للطباعة والباركود:\n\n$batch")
        }

        binding.btnMikrotikMaintenance.setOnClickListener {
            val userLine = if (username.isNotBlank()) "\n• اسم المستخدم: $username" else ""
            showResult(
                "أدوات صيانة المايكروتك:\n" +
                    "• العنوان: $baseHost\n" +
                    "• منفذ API: $port$userLine\n" +
                    "• فحص الخدمات: /ip service print\n" +
                    "• فحص الذاكرة والمعالج: /system resource print"
            )
        }

        binding.btnUserManager.setOnClickListener {
            startActivity(
                Intent(this, UserManagerActivity::class.java).apply {
                    putExtra("name", name)
                    putExtra("host", host)
                    putExtra("apiPort", port)
                    putExtra("username", username)
                }
            )
        }

        binding.btnTemplates.setOnClickListener {
            val templateHtml = """
                <!-- قالب دخول MAG / MikroTik -->
                <form action="$baseHost" method="post">
                  <input type="text" name="username" placeholder="أدخل رمز الكرت" required />
                  <button type="submit">دخول الإنترنت</button>
                </form>
            """.trimIndent()
            copyText("قالب الدخول", templateHtml)
            showResult("تم تجهيز قالب الدخول ونسخه إلى الحافظة:\n\n$templateHtml")
        }

        binding.btnVoucherManager.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun showResult(text: String) {
        binding.tvOutputLog.text = text
    }

    private fun copyText(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "تم نسخ $label", Toast.LENGTH_SHORT).show()
    }
}
