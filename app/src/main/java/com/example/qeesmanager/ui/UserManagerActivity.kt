package com.example.qeesmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.qeesmanager.R
import com.example.qeesmanager.databinding.ActivityUserManagerBinding
import com.example.qeesmanager.network.RouterOsApiClient
import com.example.qeesmanager.util.VoucherVisualGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.Locale
import kotlin.random.Random

class UserManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserManagerBinding
    private val savedBatches = mutableListOf<String>()
    private var lastGeneratedCodes: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val deviceName = intent.getStringExtra("name").orEmpty()
        val host = intent.getStringExtra("host").orEmpty()
        val apiPortStr = intent.getStringExtra("apiPort").orEmpty().ifEmpty { "8728" }
        val apiPort = apiPortStr.toIntOrNull() ?: 8728
        val username = intent.getStringExtra("username").orEmpty()
        val password = intent.getStringExtra("password").orEmpty()

        binding.tvConnectionStatus.text = if (host.isNotBlank()) {
            "• متصل بالمايكروتك ($host:$apiPort)"
        } else {
            "• وضع معاينة الكروت المحلية"
        }

        binding.btnBackUserManager.setOnClickListener { finish() }

        binding.umTabsGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnTabSingleCard -> {
                    binding.etCardsCount.setText("1")
                    updatePreview()
                    binding.tvUmOutput.text = "وضع البطاقة الواحدة مفعّل: تم ضبط عدد الكروت على 1."
                }
                R.id.btnTabPrintCards -> {
                    updatePreview()
                    binding.tvUmOutput.text = "وضع طباعة الكروت مفعّل داخل التطبيق مع باركود و QR حقيقي."
                }
                R.id.btnTabUsers -> {
                    binding.tvUmOutput.text = buildString {
                        appendLine("إدارة مستخدمي يوزر منجر (${deviceName.ifBlank { "الجهاز" }}):")
                        appendLine("• المستخدم الإداري: ${username.ifBlank { "غير محدد" }}")
                        appendLine("• عدد الدفعات المحفوظة في هذه الجلسة: ${savedBatches.size}")
                        if (lastGeneratedCodes.isNotEmpty()) {
                            appendLine("• آخر الأكواد المولّدة: ${lastGeneratedCodes.take(5).joinToString(", ")}")
                        }
                    }.trim()
                }
                R.id.btnTabProfiles -> {
                    val pkg = binding.etPackageName.text?.toString()?.trim().orEmpty().ifBlank { "100" }
                    val price = binding.etCardPrice.text?.toString()?.trim().orEmpty().ifBlank { "1" }
                    binding.tvUmOutput.text = "الباقة الحالية: $pkg | السعر: $price | منفذ API: $apiPort"
                }
            }
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        listOf(
            binding.etCardPrice,
            binding.etPackageName,
            binding.etCodeLength,
            binding.etCodeType,
            binding.etPasswordMode,
            binding.etCardsCount,
            binding.etPointOfSale
        ).forEach { it.addTextChangedListener(watcher) }

        listOf(
            binding.swBarcodeEnabled,
            binding.swPrice,
            binding.swQuota,
            binding.swComment1,
            binding.swComment2,
            binding.swSerial,
            binding.swBindFirstUse
        ).forEach { sw ->
            sw.setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        binding.btnResetTemplate.setOnClickListener {
            binding.etCardPrice.setText("1")
            binding.etPackageName.setText("100")
            binding.etCodeLength.setText("12")
            binding.etCodeType.setText("أرقام")
            binding.etPasswordMode.setText("بدون")
            binding.etCardsCount.setText("1")
            binding.etPointOfSale.setText("")
            binding.swBarcodeEnabled.isChecked = true
            binding.swPrice.isChecked = true
            binding.swComment1.isChecked = true
            binding.swQuota.isChecked = false
            binding.swComment2.isChecked = false
            binding.swSerial.isChecked = false
            binding.swBindFirstUse.isChecked = false
            updatePreview()
            binding.tvUmOutput.text = "تمت إعادة ضبط إعدادات الكرت والباركود إلى الوضع الافتراضي."
        }

        binding.btnReadyTemplates.setOnClickListener {
            binding.etCardPrice.setText("5")
            binding.etPackageName.setText("يومي مفتوح")
            binding.etCodeLength.setText("10")
            binding.etCodeType.setText("أرقام")
            binding.etCardsCount.setText("10")
            binding.swBarcodeEnabled.isChecked = true
            binding.swPrice.isChecked = true
            updatePreview()
            binding.tvUmOutput.text = "تم تطبيق نموذج جاهز (10 كروت • باقة يومي مفتوح • باركود و QR مفعل)."
        }

        binding.btnPhoneTemplate.setOnClickListener {
            val code = binding.tvCardCodePreview.text?.toString().orEmpty()
            val snippet = if (host.isNotBlank()) {
                val clean = host.trim().removeSuffix("/")
                val prefix = if (clean.startsWith("http")) clean else "http://$clean"
                "$prefix/login?username=$code"
            } else {
                "username=$code"
            }
            copyText("رابط الدخول السريع", snippet)
            binding.tvUmOutput.text = "تم نسخ قالب/رابط الدخول للهاتف:\n$snippet"
        }

        binding.btnSavePrint.setOnClickListener {
            val count = binding.etCardsCount.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(1, 130) ?: 1
            val length = binding.etCodeLength.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(6, 20) ?: 12
            val codeType = binding.etCodeType.text?.toString()?.trim().orEmpty()
            val passwordMode = binding.etPasswordMode.text?.toString()?.trim().orEmpty()
            val pkg = binding.etPackageName.text?.toString()?.trim().orEmpty().ifBlank { "100" }
            val price = binding.etCardPrice.text?.toString()?.trim().orEmpty().ifBlank { "1" }
            val pos = binding.etPointOfSale.text?.toString()?.trim().orEmpty()

            val codes = (1..count).map { generateVoucherCode(length, codeType) }
            lastGeneratedCodes = codes
            binding.tvCardCodePreview.text = codes.first()
            updateVisualsForCode(codes.first(), host)

            val summary = "مستخدم: ${codes.first()} | باقة: $pkg | السعر: $price | باركود/QR: ${
                if (binding.swBarcodeEnabled.isChecked) "مفعل" else "معطل"
            } | نقطة بيع: ${pos.ifBlank { "لا يوجد" }}"
            savedBatches.add(0, summary)

            binding.tvUmOutput.text = "تم توليد $count كرت وحفظ الدفعة وبدء الطباعة داخل التطبيق:\n$summary"

            val sheetBitmap = VoucherVisualGenerator.createPrintableVoucherSheetBitmap(
                networkTitle = deviceName.ifBlank { "بطاقة دخول الإنترنت" },
                host = host,
                packageName = pkg,
                price = price,
                pointOfSale = pos,
                vouchers = codes,
                includeBarcodeAndQr = binding.swBarcodeEnabled.isChecked,
                includePrice = binding.swPrice.isChecked,
                includeComment1 = binding.swComment1.isChecked
            )
            printBitmapDirectlyInApp("MAG_Vouchers_${System.currentTimeMillis()}", sheetBitmap)

            if (host.isNotBlank() && username.isNotBlank()) {
                uploadCardsToUserManager(
                    host = host,
                    port = apiPort,
                    username = username,
                    password = password,
                    codes = codes,
                    passwordMode = passwordMode,
                    comment = pos,
                    baseSummary = summary
                )
            } else {
                binding.tvUmOutput.text = "$summary\n\nملاحظة: لم يتم الإرسال لليوزر منجر لعدم توفر عنوان/مستخدم الراوتر."
            }
        }

        binding.btnPreviousFiles.setOnClickListener {
            if (savedBatches.isEmpty()) {
                binding.tvUmOutput.text = "لا توجد دفعات محفوظة بعد في هذه الجلسة."
            } else {
                binding.tvUmOutput.text = "الملفات والدفعات المحفوظة:\n" +
                    savedBatches.take(10).mapIndexed { i, item -> "${i + 1}. $item" }.joinToString("\n")
            }
        }

        binding.btnExportRsc.setOnClickListener {
            val pkg = binding.etPackageName.text?.toString()?.trim().orEmpty().ifBlank { "100" }
            val codes = if (lastGeneratedCodes.isNotEmpty()) {
                lastGeneratedCodes
            } else {
                val len = binding.etCodeLength.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(6, 20) ?: 12
                val cType = binding.etCodeType.text?.toString()?.trim().orEmpty()
                listOf(generateVoucherCode(len, cType))
            }

            val rscScript = buildString {
                appendLine("# MAG RouterOS / User-Manager Script")
                codes.forEach { code ->
                    appendLine("/tool user-manager user add name=\"$code\" password=\"\" comment=\"$pkg\"")
                }
            }.trim()

            copyText("سكربت RouterOS (.rsc)", rscScript)
            binding.tvUmOutput.text = "تم تجهيز سكربت (.rsc) ونسخه إلى الحافظة:\n\n$rscScript"
        }

        updatePreview()
    }

    private fun uploadCardsToUserManager(
        host: String,
        port: Int,
        username: String,
        password: String,
        codes: List<String>,
        passwordMode: String,
        comment: String,
        baseSummary: String
    ) {
        binding.tvUmOutput.text = "$baseSummary\n\nجاري رفع ${codes.size} كرت تلقائياً إلى User Manager ($host:$port)..."

        lifecycleScope.launch {
            val client = RouterOsApiClient(host = host, port = port)
            val result = withContext(Dispatchers.IO) {
                var addedCount = 0
                var lastError: String? = null
                val useSamePassword = !passwordMode.contains("بدون", ignoreCase = true) &&
                    !passwordMode.contains("none", ignoreCase = true) &&
                    passwordMode.isNotBlank()

                for (code in codes) {
                    val cmd = buildList {
                        add("/tool/user-manager/user/add")
                        add("=name=$code")
                        if (useSamePassword) {
                            add("=password=$code")
                        }
                        if (comment.isNotBlank()) {
                            add("=comment=$comment")
                        }
                    }
                    val res = client.executeCommand(
                        username = username,
                        password = password,
                        commandWords = cmd
                    )
                    if (res.success) {
                        addedCount++
                    } else {
                        lastError = res.message
                        break
                    }
                }
                Pair(addedCount, lastError)
            }

            val addedCount = result.first
            val lastError = result.second

            binding.tvUmOutput.text = if (lastError == null) {
                "$baseSummary\n\nتم رفع $addedCount من ${codes.size} كرت تلقائياً إلى User Manager بنجاح."
            } else {
                "$baseSummary\n\nتم رفع $addedCount من ${codes.size} كرت قبل توقف الإرسال:\nالسبب: $lastError"
            }
        }
    }

    private fun updatePreview() {
        val pkg = binding.etPackageName.text?.toString()?.trim().orEmpty().ifBlank { "100" }
        val price = binding.etCardPrice.text?.toString()?.trim().orEmpty().ifBlank { "1" }
        val pos = binding.etPointOfSale.text?.toString()?.trim().orEmpty()
        val count = binding.etCardsCount.text?.toString()?.trim()?.toIntOrNull()?.coerceIn(1, 130) ?: 1

        binding.tvCardPriceBadge.isVisible = binding.swPrice.isChecked
        binding.tvCardPriceBadge.text = price

        val currentCode = binding.tvCardCodePreview.text?.toString()?.trim().orEmpty().ifBlank { "591481453137" }
        binding.tvCardCodePreview.text = currentCode

        val host = intent.getStringExtra("host").orEmpty()
        updateVisualsForCode(currentCode, host)

        val detailsParts = mutableListOf<String>()
        detailsParts.add("الباقة: $pkg")
        detailsParts.add("باركود: ${if (binding.swBarcodeEnabled.isChecked) "مفعل" else "معطل"}")
        if (binding.swPrice.isChecked) detailsParts.add("السعر: $price")
        if (binding.swQuota.isChecked) detailsParts.add("حجم الرصيد: مفعل")
        if (binding.swComment1.isChecked && pos.isNotBlank()) detailsParts.add("نقطة بيع: $pos")
        if (binding.swComment2.isChecked) detailsParts.add("ملاحظات 2: مفعل")
        if (binding.swSerial.isChecked) detailsParts.add("تسلسلي: مفعل")
        if (binding.swBindFirstUse.isChecked) detailsParts.add("ربط أول استخدام: مفعل")

        binding.tvCardDetailsPreview.text = detailsParts.joinToString(" • ")
        binding.tvGridCapacity.text = "يمكن للصفحة أن تتسع لـ 130 بطاقة (10 أعمدة × 13 صف) • العدد المحدد: $count"
    }

    private fun updateVisualsForCode(code: String, host: String) {
        val enabled = binding.swBarcodeEnabled.isChecked
        binding.tvBarcodeVisual.isVisible = enabled
        binding.tvQrBox.isVisible = enabled

        val qrPayload = if (host.isNotBlank()) {
            val clean = host.trim().removeSuffix("/")
            val prefix = if (clean.startsWith("http")) clean else "http://$clean"
            "$prefix/login?username=$code"
        } else {
            code
        }

        VoucherVisualGenerator.renderBarcodeIntoTextView(binding.tvBarcodeVisual, code, enabled)
        VoucherVisualGenerator.renderQrIntoTextView(binding.tvQrBox, qrPayload, enabled)
    }

    private fun generateVoucherCode(length: Int, codeType: String): String {
        val useAlphaNum = codeType.contains("حروف", ignoreCase = true) ||
            codeType.contains("أحرف", ignoreCase = true) ||
            codeType.contains("alpha", ignoreCase = true) ||
            codeType.contains("مختلط", ignoreCase = true)

        return if (useAlphaNum) {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        } else {
            (1..length).map { Random.nextInt(0, 10).toString() }.joinToString("")
        }.uppercase(Locale.US)
    }

    private fun printBitmapDirectlyInApp(jobName: String, bitmap: Bitmap) {
        val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(this, "خدمة الطباعة غير متوفرة على هذا الجهاز", Toast.LENGTH_SHORT).show()
            return
        }

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, newAttributes != oldAttributes)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                val pdfDocument = PdfDocument()
                try {
                    val pageWidth = 595
                    val pageHeight = 842
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                    val page = pdfDocument.startPage(pageInfo)

                    val scale = minOf(
                        (pageWidth - 40f) / bitmap.width.toFloat(),
                        (pageHeight - 40f) / bitmap.height.toFloat()
                    ).coerceAtMost(1f)

                    val drawW = (bitmap.width * scale).toInt().coerceAtLeast(1)
                    val drawH = (bitmap.height * scale).toInt().coerceAtLeast(1)
                    val left = ((pageWidth - drawW) / 2f)
                    val top = 20f

                    val scaled = Bitmap.createScaledBitmap(bitmap, drawW, drawH, true)
                    page.canvas.drawBitmap(scaled, left, top, null)
                    if (scaled !== bitmap) {
                        scaled.recycle()
                    }
                    pdfDocument.finishPage(page)

                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDocument.writeTo(out)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.localizedMessage)
                } finally {
                    pdfDocument.close()
                }
            }
        }

        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }

    private fun copyText(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "تم نسخ $label", Toast.LENGTH_SHORT).show()
    }
}
