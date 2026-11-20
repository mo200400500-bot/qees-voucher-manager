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

        val name = intent.getStringExtra("name") ?: "star net"
        val host = intent.getStringExtra("host") ?: "https://l.net/login"
        val port = intent.getStringExtra("apiPort") ?: "8728"

        binding.tvDeviceTitle.text = name
        binding.tvDeviceMeta.text = "$host • API: $port"

        binding.btnPrintBarcode.setOnClickListener {
            val batch = (1..6).joinToString("\n") { idx ->
                val code = UUID.randomUUID().toString().take(8).uppercase()
                "كرت #$idx | كود: $code | QR: $host?username=$code"
            }
            showResult("دفعة كروت جاهزة للطباعة والباركود:\n\n$batch")
        }

        binding.btnMikrotikMaintenance.setOnClickListener {
            showResult(
                "أدوات صيانة المايكروتك ($name):\n" +
                    "• العنوان: $host\n" +
                    "• منفذ API: $port\n" +
                    "• فحص الخدمات: /ip service print\n" +
                    "• فحص الذاكرة والمعالج: /system resource print"
            )
        }

        binding.btnUserManager.setOnClickListener {
            showResult(
                "صيانة User Manager والهوت سبوت ($name):\n" +
                    "• مراجعة الجلسات النشطة: /ip hotspot active print\n" +
                    "• مراجعة مستخدمي User Manager: /user-manager user print\n" +
                    "• رابط بوابة الدخول: $host"
            )
        }

        binding.btnTemplates.setOnClickListener {
            val templateHtml = """
                <!-- قالب دخول Qees / MikroTik -->
                <form action="$host" method="post">
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
