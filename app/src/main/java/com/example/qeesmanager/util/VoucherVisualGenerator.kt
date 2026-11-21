package com.example.qeesmanager.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.widget.TextView
import kotlin.math.abs

object VoucherVisualGenerator {

    fun generateBarcodeBitmap(content: String, width: Int = 640, height: Int = 120): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val clean = if (content.isBlank()) "000000" else content.trim()
        var x = 16f
        val maxX = width - 16f
        val top = 10f
        val bottom = height - 10f

        // Guard start bars
        canvas.drawRect(x, top, x + 5f, bottom, paint)
        x += 9f
        canvas.drawRect(x, top, x + 3f, bottom, paint)
        x += 8f

        var hash = 2166136261L
        for (ch in clean) {
            hash = ((hash xor ch.code.toLong()) * 16777619L) and 0x7FFFFFFFL
        }

        var bitIndex = 0
        while (x < maxX - 24f) {
            val seed = abs((hash xor ((bitIndex * 2654435761L) and 0x7FFFFFFFL)))
            val barWidth = when ((seed % 4).toInt()) {
                0 -> 2f
                1 -> 4f
                2 -> 7f
                else -> 10f
            }
            val gapWidth = when (((seed ushr 3) % 3).toInt()) {
                0 -> 2f
                1 -> 4f
                else -> 6f
            }

            if (x + barWidth <= maxX - 20f) {
                canvas.drawRect(x, top, x + barWidth, bottom, paint)
            }
            x += barWidth + gapWidth
            bitIndex++
        }

        // Guard end bars
        x = maxX - 14f
        canvas.drawRect(x, top, x + 3f, bottom, paint)
        canvas.drawRect(x + 7f, top, x + 12f, bottom, paint)

        return bitmap
    }

    fun generateQrBitmap(content: String, size: Int = 320): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val clean = if (content.isBlank()) "MAG-VOUCHER" else content.trim()
        val modules = 25
        val margin = 2
        val totalCells = modules + margin * 2
        val cellSize = size.toFloat() / totalCells.toFloat()

        val grid = Array(modules) { BooleanArray(modules) }

        fun drawFinderPattern(startRow: Int, startCol: Int) {
            for (r in -1..7) {
                for (c in -1..7) {
                    val rr = startRow + r
                    val cc = startCol + c
                    if (rr in 0 until modules && cc in 0 until modules) {
                        val isBorder = r in 0..6 && (c == 0 || c == 6) || c in 0..6 && (r == 0 || r == 6)
                        val isCenter = r in 2..4 && c in 2..4
                        grid[rr][cc] = isBorder || isCenter
                    }
                }
            }
        }

        drawFinderPattern(0, 0)
        drawFinderPattern(0, modules - 7)
        drawFinderPattern(modules - 7, 0)

        // Timing patterns
        for (i in 8 until modules - 8) {
            grid[6][i] = (i % 2 == 0)
            grid[i][6] = (i % 2 == 0)
        }

        // Fill data modules deterministically from content
        var state = 0x9E3779B9L
        for (ch in clean) {
            state = ((state * 1103515245L) + ch.code.toLong() + 12345L) and 0x7FFFFFFFL
        }

        var step = 0
        for (r in 0 until modules) {
            for (c in 0 until modules) {
                val inTopLeft = r < 8 && c < 8
                val inTopRight = r < 8 && c >= modules - 8
                val inBottomLeft = r >= modules - 8 && c < 8
                val isTiming = r == 6 || c == 6
                if (!inTopLeft && !inTopRight && !inBottomLeft && !isTiming) {
                    state = ((state xor (state shl 13)) and 0x7FFFFFFFL)
                    state = ((state xor (state ushr 7)) and 0x7FFFFFFFL)
                    state = ((state xor (state shl 17)) and 0x7FFFFFFFL)
                    val mix = (state + step * 31L) and 0x7FFFFFFFL
                    grid[r][c] = ((mix % 2L) == 0L)
                    step++
                }
            }
        }

        for (r in 0 until modules) {
            for (c in 0 until modules) {
                if (grid[r][c]) {
                    val left = (margin + c) * cellSize
                    val top = (margin + r) * cellSize
                    canvas.drawRect(left, top, left + cellSize + 0.4f, top + cellSize + 0.4f, paint)
                }
            }
        }

        return bitmap
    }

    fun renderBarcodeIntoTextView(textView: TextView, content: String, enabled: Boolean) {
        if (!enabled) {
            textView.text = ""
            return
        }
        val bmp = generateBarcodeBitmap(content, width = 620, height = 105)
        val drawable = BitmapDrawable(textView.resources, bmp).apply {
            setBounds(0, 0, bmp.width, bmp.height)
        }
        val ssb = SpannableStringBuilder(" ")
        ssb.setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BASELINE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.text = ssb
    }

    fun renderQrIntoTextView(textView: TextView, content: String, enabled: Boolean) {
        if (!enabled) {
            textView.text = ""
            return
        }
        val bmp = generateQrBitmap(content, size = 240)
        val drawable = BitmapDrawable(textView.resources, bmp).apply {
            setBounds(0, 0, bmp.width, bmp.height)
        }
        val ssb = SpannableStringBuilder(" ")
        ssb.setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BASELINE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.text = ssb
    }

    fun createPrintableVoucherSheetBitmap(
        networkTitle: String,
        host: String,
        packageName: String,
        price: String,
        pointOfSale: String,
        vouchers: List<String>,
        includeBarcodeAndQr: Boolean,
        includePrice: Boolean,
        includeComment1: Boolean
    ): Bitmap {
        val cols = 2
        val cardWidth = 520
        val cardHeight = 290
        val gap = 24
        val rows = ((vouchers.size.coerceAtLeast(1) + cols - 1) / cols)
        val sheetWidth = cols * cardWidth + (cols + 1) * gap
        val sheetHeight = rows * cardHeight + (rows + 1) * gap

        val bitmap = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E3A8A")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E3A8A")
            style = Paint.Style.FILL
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            isFakeBoldText = true
        }
        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f
            isFakeBoldText = true
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 20f
        }

        vouchers.forEachIndexed { idx, code ->
            val col = idx % cols
            val row = idx / cols
            val left = gap + col * (cardWidth + gap)
            val top = gap + row * (cardHeight + gap)
            val right = left + cardWidth
            val bottom = top + cardHeight

            val cardRect = Rect(left, top, right, bottom)
            canvas.drawRect(cardRect, borderPaint)
            canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), (top + 52).toFloat(), headerPaint)

            val titleText = if (networkTitle.isNotBlank()) networkTitle else "بطاقة دخول الإنترنت"
            canvas.drawText(titleText, left + 18f, top + 35f, titlePaint)

            canvas.drawText("الكود: $code", left + 18f, top + 98f, codePaint)

            if (includeBarcodeAndQr) {
                val qrValue = if (host.isNotBlank()) {
                    val clean = host.trim().removeSuffix("/")
                    val prefix = if (clean.startsWith("http")) clean else "http://$clean"
                    "$prefix/login?username=$code"
                } else {
                    code
                }
                val qrBmp = generateQrBitmap(qrValue, size = 120)
                canvas.drawBitmap(qrBmp, (right - 138).toFloat(), (top + 66).toFloat(), null)

                val barBmp = generateBarcodeBitmap(code, width = 310, height = 56)
                canvas.drawBitmap(barBmp, (left + 18).toFloat(), (top + 120).toFloat(), null)
            }

            val details = buildList {
                if (packageName.isNotBlank()) add("الباقة: $packageName")
                if (includePrice && price.isNotBlank()) add("السعر: $price")
                if (includeComment1 && pointOfSale.isNotBlank()) add("نقطة البيع: $pointOfSale")
            }.joinToString(" | ")

            if (details.isNotBlank()) {
                canvas.drawText(details, left + 18f, (bottom - 20).toFloat(), metaPaint)
            }
        }

        return bitmap
    }
}
