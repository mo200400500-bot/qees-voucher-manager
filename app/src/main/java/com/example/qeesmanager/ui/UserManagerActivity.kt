package com.example.qeesmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.qeesmanager.databinding.ActivityUserManagerBinding
import kotlin.random.Random

class UserManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserManagerBinding
    private var lastGeneratedBatch: String = ""
    private var selectedTemplateIndex: Int = 0

    private data class CardTemplateStyle(
        val name: String,
        val cardBgColor: Int,
        val strokeColor: Int,
        val titleColor: Int,
        val codeBgColor: Int,
        val badgeBgColor: Int
    )

    private val templates = listOf(
        CardTemplateStyle(
            name = "القالب الذهبي الملكي",
            cardBgColor = Color.parseColor("#0F172A"),
            strokeColor = Color.parseColor("#FACC15"),
            titleColor = Color.parseColor("#FACC15"),
            codeBgColor = Color.parseColor("#FEF08A"),
            badgeBgColor = Color.parseColor("#F59E0B")
        ),
        CardTemplateStyle(
            name = "القالب الأزرق الاحترافي",
            cardBgColor = Color.parseColor("#1E3A8A"),
            strokeColor = Color.parseColor("#38BDF8"),
            titleColor = Color.parseColor("#7DD3FC"),
            codeBgColor = Color.parseColor("#E0F2FE"),
            badgeBgColor = Color.parseColor("#0EA5E9")
        ),
        CardTemplateStyle(
            name = "القالب الزمردي الشبكي",
            cardBgColor = Color.parseColor("#064E3B"),
            strokeColor = Color.parseColor("#34D399"),
            titleColor = Color.parseColor("#6EE7B7"),
            codeBgColor = Color.parseColor("#D1FAE5"),
            badgeBgColor = Color.parseColor("#10B981")
        ),
        CardTemplateStyle(
            name = "القالب البنفسجي المميز",
            cardBgColor = Color.parseColor("#3B0764"),
            strokeColor = Color.parseColor("#E879F9"),
            titleColor = Color.parseColor("#F5D0FE"),
            codeBgColor = Color.parseColor("#FAE8FF"),
            badgeBgColor = Color.parseColor("#C026D3")
        )
    )

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
            selectedTemplateIndex = 0
            binding.etCardPrice.setText("100")
            binding.etPackageName.setText("100")
            binding.etCodeLength.setText("8")
            binding.etCodeType.setText("أرقام")
            binding.etPasswordMode.setText("بدون")
            binding.etCardsCount.setText("20")
            binding.etPointOfSale.setText("")
            binding.swBarcodeEnabled.isChecked = true
            binding.swPrice.isChecked = true
            binding.swQuota.isChecked = false
            binding.swComment1.isChecked = true
            binding.swComment2.isChecked = false
            binding.swSerial.isChecked = false
            binding.swBindFirstUse.isChecked = false
            updatePreview()
            Toast.makeText(this, "تمت إعادة ضبط إعدادات الكرت والقالب", Toast.LENGTH_SHORT).show()
        }

        binding.btnReadyTemplates.setOnClickListener {
            val names = templates.map { it.name }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("اختر قالباً جاهزاً للكروت")
                .setItems(names) { _, which ->
                    selectedTemplateIndex = which
                    updatePreview()
                    Toast.makeText(this, "تم تطبيق ${names[which]}", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        binding.btnPhoneTemplate.setOnClickListener {
            selectedTemplateIndex = (selectedTemplateIndex + 1) % templates.size
            updatePreview()
            Toast.makeText(
                this,
                "تم التبديل إلى ${templates[selectedTemplateIndex].name}",
                Toast.LENGTH_SHORT
            ).show()
        }

        val previewUpdater = { _: Any, _: Any -> updatePreview() }
        binding.swBarcodeEnabled.setOnCheckedChangeListener { _, _ -> updatePreview() }
        binding.swPrice.setOnCheckedChangeListener { _, _ -> updatePreview() }
        binding.swQuota.setOnCheckedChangeListener { _, _ -> updatePreview() }
        binding.swComment1.setOnCheckedChangeListener { _, _ -> updatePreview() }
        binding.swComment2.setOnCheckedChangeListener { _, _ -> updatePreview() }
        binding.swSerial.setOnCheckedChangeListener { _, _ -> updatePreview() }
        binding.swBindFirstUse.setOnCheckedChangeListener { _, _ -> updatePreview() }
        previewUpdater.let { }

        binding.btnSavePrint.setOnClickListener {
            updatePreview()
            val count = binding.etCardsCount.text.toString().toIntOrNull()?.coerceIn(1, 200) ?: 20
            val len = binding.etCodeLength.text.toString().toIntOrNull()?.coerceIn(4, 16) ?: 8
            val pkg = binding.etPackageName.text.toString().trim().ifEmpty { "100" }
            val price = binding.etCardPrice.text.toString().trim().ifEmpty { "100" }
            val pos = binding.etPointOfSale.text.toString().trim().ifEmpty { "لا يوجد" }
            val numericOnly = binding.etCodeType.text.toString().contains("أرقام")
            val barcodeTag = if (binding.swBarcodeEnabled.isChecked) " | باركود/QR" else ""
            val priceTag = if (binding.swPrice.isChecked) " | السعر: $price" else ""

            val codes = (1..count).map { generateCode(len, numericOnly) }
            lastGeneratedBatch = codes.joinToString("\n") { code ->
                "مستخدم: $code | باقة: $pkg$priceTag$barcodeTag | نقطة بيع: $pos"
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
                add name="53846126" password="" customer="admin" comment="$comment1"
                /tool user-manager user-profile
                add user="53846126" profile="$pkg"
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
        val style = templates[selectedTemplateIndex]
        binding.cardPreviewContainer.setCardBackgroundColor(style.cardBgColor)
        binding.cardPreviewContainer.strokeColor = style.strokeColor
        binding.tvCardNetworkTitle.setTextColor(style.titleColor)
        binding.tvCardCodePreview.setBackgroundColor(style.codeBgColor)
        binding.tvCardPriceBadge.setBackgroundColor(style.badgeBgColor)

        val len = binding.etCodeLength.text.toString().toIntOrNull()?.coerceIn(4, 16) ?: 8
        val numericOnly = binding.etCodeType.text.toString().contains("أرقام")
        val sample = generateCode(len, numericOnly)
        val pkg = binding.etPackageName.text.toString().trim().ifEmpty { "100" }
        val price = binding.etCardPrice.text.toString().trim().ifEmpty { "100" }
        val count = binding.etCardsCount.text.toString().toIntOrNull()?.coerceIn(1, 130) ?: 20

        if (binding.swPrice.isChecked) {
            binding.tvCardPriceBadge.visibility = View.VISIBLE
            binding.tvCardPriceBadge.text = price
        } else {
            binding.tvCardPriceBadge.visibility = View.GONE
        }

        if (binding.swBarcodeEnabled.isChecked) {
            binding.tvBarcodeVisual.visibility = View.VISIBLE
            binding.tvQrBox.visibility = View.VISIBLE
            binding.tvQrBox.text = "QR\nCODE\n$sample"
        } else {
            binding.tvBarcodeVisual.visibility = View.GONE
            binding.tvQrBox.visibility = View.GONE
        }

        val extras = buildList {
            if (binding.swBarcodeEnabled.isChecked) add("باركود مفعّل")
            if (binding.swPrice.isChecked) add("السعر: $price")
            if (binding.swQuota.isChecked) add("الرصيد")
            if (binding.swComment1.isChecked) add("ملاحظات 1")
            if (binding.swComment2.isChecked) add("ملاحظات 2")
            if (binding.swSerial.isChecked) add("تسلسلي")
            if (binding.swBindFirstUse.isChecked) add("ربط أول استخدام")
        }

        binding.tvCardCodePreview.text = sample
        binding.tvCardDetailsPreview.text = "الباقة: $pkg" + if (extras.isNotEmpty()) " • ${extras.joinToString(" • ")}" else ""
        binding.tvGridCapacity.text = "يمكن للصفحة أن تتسع لـ 130 بطاقة (10 أعمدة × 13 صف) • العدد المحدد: $count"
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "تم نسخ $label", Toast.LENGTH_SHORT).show()
    }
}
