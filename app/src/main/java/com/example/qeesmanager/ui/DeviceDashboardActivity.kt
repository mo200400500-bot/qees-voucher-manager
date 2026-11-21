package com.example.qeesmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.qeesmanager.MainActivity
import com.example.qeesmanager.databinding.ActivityDeviceDashboardBinding
import com.example.qeesmanager.network.RouterOsApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class DeviceDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val name = intent.getStringExtra("name").orEmpty()
        val host = intent.getStringExtra("host").orEmpty()
        val portStr = intent.getStringExtra("apiPort").orEmpty().ifEmpty { "8728" }
        val port = portStr.toIntOrNull() ?: 8728
        val username = intent.getStringExtra("username").orEmpty()
        val password = intent.getStringExtra("password").orEmpty()

        binding.tvDeviceTitle.text = if (name.isNotBlank()) name else "جهاز MikroTik"
        binding.tvDeviceMeta.text = if (host.isNotBlank()) "$host • API: $port" else "API: $port"

        val baseHost = if (host.isNotBlank()) host else "[رابط الروتر]"

        binding.btnPrintBarcode.setOnClickListener {
            val batch = (1..6).joinToString("\n") { idx ->
                val code = UUID.randomUUID().toString().take(8).uppercase()
                "كرت #$idx | كود: $code | QR: $baseHost?username=$code"
            }
            showResult("دفعة كروت مولّدة محلياً (غير مرسلة للراوتر بعد):\n\n$batch")
        }

        binding.btnMikrotikMaintenance.setOnClickListener {
            if (host.isBlank() || username.isBlank()) {
                showResult(
                    "يلزم إدخال (رابط أو IP الروتر) و(اسم مستخدم الروتر) في بيانات الجهاز لإجراء اتصال حقيقي عبر RouterOS API (المنفذ $port)."
                )
                return@setOnClickListener
            }

            showResult("جاري الاتصال الحقيقي بـ $host على المنفذ $port عبر RouterOS API...")

            lifecycleScope.launch {
                val client = RouterOsApiClient(host = host, port = port, timeoutMs = 7000)
                val res = withContext(Dispatchers.IO) {
                    client.executeCommand(
                        username = username,
                        password = password,
                        commandWords = listOf("/system/resource/print")
                    )
                }

                if (!res.success) {
                    showResult(res.message)
                    return@launch
                }

                val info = res.rawSentences.firstOrNull()?.entries
                    ?.joinToString("\n") { "• ${it.key}: ${it.value}" }
                    ?: "تم الدخول بنجاح ولم تُعد بيانات إضافية."

                showResult("${res.message}\n\n$info")
            }
        }

        binding.btnUserManager.setOnClickListener {
            startActivity(
                Intent(this, UserManagerActivity::class.java).apply {
                    putExtra("name", name)
                    putExtra("host", host)
                    putExtra("apiPort", port.toString())
                    putExtra("username", username)
                    putExtra("password", password)
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
