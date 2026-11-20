package com.example.qeesmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.qeesmanager.databinding.ActivityUserManagerBinding
import kotlin.random.Random

class UserManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserManagerBinding
    private var lastGeneratedBatch: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackUserManager.setOnClickListener { finish() }

        binding.umTabsGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    binding.btnTabSingleCard.id -> {
                        binding.etCardsCount.setText("1")
                        updatePreview()
                    }
                    binding.btnTabPrintCards.id -> {
                        if (binding.etCardsCount.text.toString() == "1") {
                            binding.etCardsCount.setText("20")
                        }
                        updatePreview()
                    }
                    binding.btnTabUsers.id -> {
                        binding.tvUmOutput.text = "عرض وإدارة مستخدمي يوزر منجر (/tool user-manager user print)."
                    }
                    binding.btnTabProfiles.id -> {
                        binding.tvUmOutput.text = "عرض وإدارة باقات يوزر منجر (/tool user-manager profile print)."
                    }
                }
            }
        }

        binding.btnResetTemplate.setOnClickListener {
            binding.etPackageName.setText("100")
            binding.etCodeLength.setText("8")
            binding.etCodeType.setText("أرقام")
            binding.etPasswordMode.setText("بدون")
            binding.etCardsCount.setText("20")
            binding.etPointOfSale.setText("")
            binding.swQuota.isChecked = false
            binding.swPrice.isChecked = false
            binding.swComment1.isChecked = true
            binding.swComment2.isChecked = false
            binding.swSerial.isChecked = false
            binding.swBindFirstUse.isChecked = false
            updatePreview()
            Toast.makeText(this, "تمت إعادة ضبط إعدادات الكرت", Toast.LENGTH_SHORT).show()
        }

        binding.btnReadyTemplates.setOnClickListener {
            binding.tvCardNetworkTitle.text = "بطاقة دخول الإنترنت"
            updatePreview()
            Toast.makeText(this, "تم تطبيق نموذج الكرت الجاهز", Toast.LENGTH_SHORT).show()
        }

        binding.btnPhoneTemplate.setOnClickListener {
            Toast.makeText(this, "تم تفعيل وضع تخصيص القالب من الهاتف", Toast.LENGTH_SHORT).show()
            updatePreview()
        }

        binding.btnSavePrint.setOnClickListener {
            updatePreview()
            val count = binding.etCardsCount.text.toString().toIntOrNull()?.coerceIn(1, 200) ?: 20
            val len = binding.etCodeLength.text.toString().toIntOrNull()?.coerceIn(4, 16) ?: 8
            val pkg = binding.etPackageName.text.toString().trim().ifEmpty { "100" }
            val pos = binding.etPointOfSale.text.toString().trim().ifEmpty { "لا يوجد" }
            val numericOnly = binding.etCodeType.text.toString().contains("أرقام")

            val codes = (1..count).map { generateCode(len, numericOnly) }
            lastGeneratedBatch = codes.joinToString("\n") { code ->
                "مستخدم: $code | باقة: $pkg | نقطة بيع: $pos"
            }
            binding.tvUmOutput.text = "تم توليد $count كرت وحفظ الدفعة:\n\n$lastGeneratedBatch"
            copyToClipboard("دفعة الكروت", lastGeneratedBatch)
        }

        binding.btnPreviousFiles.setOnClickListener {
            if (lastGeneratedBatch.isBlank()) {
                binding.tvUmOutput.text = "لا توجد ملفات سابقة محفوظة في هذه الجلسة بعد."
            } else {
                binding.tvUmOutput.text = "آخر دفعة محفوظة:\n\n$lastGeneratedBatch"
            }
        }

        binding.btnExportRsc.setOnClickListener {
            val pkg = binding.etPackageName.text.toString().trim().ifEmpty { "100" }
            val comment1 = if (binding.swComment1.isChecked) "MAG-BATCH" else ""
            val script = """
                /tool user-manager user
                add name="12345678" password="" customer="admin" comment="$comment1"
                /tool user-manager user-profile
                add user="12345678" profile="$pkg"
            """.trimIndent()
            copyToClipboard("سكربت RSC", script)
            binding.tvUmOutput.text = "تم توليد ونسخ سكربت (.rsc):\n\n$script"
        }

        updatePreview()
    }

    private fun generateCode(length: Int, numericOnly: Boolean): String {
        val chars = if (numericOnly) "0123456789" else "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private fun updatePreview() {
        val len = binding.etCodeLength.text.toString().toIntOrNull()?.coerceIn(4, 16) ?: 8
        val numericOnly = binding.etCodeType.text.toString().contains("أرقام")
        val sample = generateCode(len, numericOnly)
        val pkg = binding.etPackageName.text.toString().trim().ifEmpty { "100" }
        val count = binding.etCardsCount.text.toString().toIntOrNull()?.coerceIn(1, 130) ?: 20

        val flags = buildList {
            if (binding.swQuota.isChecked) add("الرصيد")
            if (binding.swPrice.isChecked) add("السعر")
            if (binding.swComment1.isChecked) add("ملاحظات 1")
            if (binding.swComment2.isChecked) add("ملاحظات 2")
            if (binding.swSerial.isChecked) add("تسلسلي")
            if (binding.swBindFirstUse.isChecked) add("ربط أول استخدام")
        }

        binding.tvCardCodePreview.text = sample
        binding.tvCardDetailsPreview.text = "الباقة: $pkg" + if (flags.isNotEmpty()) " • ${flags.joinToString("، ")}" else ""
        binding.tvGridCapacity.text = "يمكن للصفحة أن تتسع لـ 130 بطاقة (10 أعمدة × 13 صف) • العدد المحدد: $count"
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "تم نسخ $label", Toast.LENGTH_SHORT).show()
    }
}
