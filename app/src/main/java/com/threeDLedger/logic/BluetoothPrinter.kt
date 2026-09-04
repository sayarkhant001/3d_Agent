package com.threeDLedger.logic

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

object BluetoothPrinter {
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(deviceMac: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false
            val device = adapter.getRemoteDevice(deviceMac)
            bluetoothSocket?.close()
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: Exception) {}
    }

    suspend fun printBitmap(bitmap: Bitmap, paperSize: String): Boolean = withContext(Dispatchers.IO) {
        val out = outputStream ?: return@withContext false
        try {
            // Init printer
            out.write(byteArrayOf(0x1B, 0x40))
            
            // Align center
            out.write(byteArrayOf(0x1B, 0x61, 0x01))

            val width = bitmap.width
            val height = bitmap.height
            
            // We use GS v 0 (raster print)
            // m=0 (normal), xL, xH, yL, yH
            val widthBytes = (width + 7) / 8
            val bytes = ByteArray(widthBytes * height)
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val a = Color.alpha(pixel)
                    // If dark, set bit to 1
                    if (a > 128 && (r < 128 || g < 128 || b < 128)) {
                        val byteIndex = y * widthBytes + (x / 8)
                        val bitOffset = 7 - (x % 8)
                        bytes[byteIndex] = (bytes[byteIndex].toInt() or (1 shl bitOffset)).toByte()
                    }
                }
            }

            val command = byteArrayOf(
                0x1D, 0x76, 0x30, 0x00,
                (widthBytes and 0xFF).toByte(),
                ((widthBytes shr 8) and 0xFF).toByte(),
                (height and 0xFF).toByte(),
                ((height shr 8) and 0xFF).toByte()
            )
            
            out.write(command)
            out.write(bytes)
            
            // Feed paper and cut
            out.write(byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A))
            out.write(byteArrayOf(0x1D, 0x56, 0x41, 0x10))
            out.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun createBitmapFromText(text: String, width: Int = 384): Bitmap {
        val textPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        textPaint.color = Color.BLACK
        textPaint.textSize = 24f
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD

        val staticLayout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1f, 1f)
            .setIncludePad(false)
            .build()

        val bitmap = Bitmap.createBitmap(width, staticLayout.height + 20, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.translate(10f, 10f)
        staticLayout.draw(canvas)
        return bitmap
    }

    // Creates a structured voucher bitmap with borders, circle header, bet lines, zigzag cut.
    // Layout: circle+title | date+name | bets | total | footer | zigzag
    data class VoucherData(
        val batchNumber: Int,
        val voucherId: Int,
        val date: String,
        val customerName: String,
        val remark: String = "",
        val bets: List<Pair<String, Int>>,  // number to amount
        val totalAmount: Int,
        val footerText: String
    )

    fun createVoucherBitmap(data: VoucherData, paperWidthMm: String = "58mm"): Bitmap {
        val width = if (paperWidthMm == "80mm") 576 else 384
        val pad = 14f

        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK

        val lineH    = 36f
        val bigLineH = 54f
        val circleR  = 42f
        val headerH  = circleR * 2 + 20f
        val betLineH = 32f
        val footerPad = 28f
        val zigzagH  = 18f

        val totalHeight = (
            footerPad +
            headerH + 18f +          // header + gap
            10f +                    // separator
            lineH * 2 + 12f +        // date + name
            (if (data.remark.isNotBlank()) lineH else 0f) +  // remark line
            betLineH * data.bets.size + 12f +
            10f +                    // separator
            bigLineH + 8f +          // total
            10f +                    // separator
            lineH + 12f +            // footer
            zigzagH + footerPad      // zigzag + bottom pad
        ).toInt()

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // ==
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.BLACK
        val bR = 8f
        canvas.drawRoundRect(4f, 4f, width - 4f, totalHeight - 4f, bR, bR, paint)
        paint.strokeWidth = 1f
        canvas.drawRoundRect(8f, 8f, width - 8f, totalHeight - 8f, bR * 0.6f, bR * 0.6f, paint)
        paint.style = android.graphics.Paint.Style.FILL

        var y = footerPad

        // ==
        // Black filled circle with batch number
        paint.color = Color.BLACK
        val circleX = pad + 8f + circleR
        val circleY = y + circleR
        canvas.drawCircle(circleX, circleY, circleR, paint)

        // Concentric ring effect
        paint.style = android.graphics.Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 2f
        canvas.drawCircle(circleX, circleY, circleR - 6f, paint)
        paint.style = android.graphics.Paint.Style.FILL

        // Batch number
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign = android.graphics.Paint.Align.CENTER
        canvas.drawText("${data.batchNumber}", circleX, circleY + 14f, paint)

        // Title: "3D Voucher"
        paint.color = Color.BLACK
        paint.textSize = 42f
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign = android.graphics.Paint.Align.LEFT
        val titleX = circleX + circleR + 14f
        canvas.drawText("3D Voucher", titleX, circleY - 2f, paint)

        // Subtitle with voucher number
        paint.textSize = 21f
        paint.typeface = android.graphics.Typeface.DEFAULT
        canvas.drawText("Voucher No. ${data.voucherId}", titleX, circleY + 28f, paint)

        y += headerH + 14f

        // Helper: draw a diamond-decorated separator
        fun drawSeparator(yPos: Float) {
            paint.color = Color.BLACK
            paint.strokeWidth = 1.2f
            paint.style = android.graphics.Paint.Style.STROKE
            val mid = width / 2f
            val dSize = 5f
            // left line
            canvas.drawLine(pad + 8f, yPos, mid - dSize - 4f, yPos, paint)
            // right line
            canvas.drawLine(mid + dSize + 4f, yPos, width - pad - 8f, yPos, paint)
            // diamond at centre
            paint.style = android.graphics.Paint.Style.FILL
            val path = android.graphics.Path().apply {
                moveTo(mid, yPos - dSize)
                lineTo(mid + dSize, yPos)
                lineTo(mid, yPos + dSize)
                lineTo(mid - dSize, yPos)
                close()
            }
            canvas.drawPath(path, paint)
            paint.style = android.graphics.Paint.Style.FILL
        }

        drawSeparator(y)
        y += 14f

        // ==
        paint.textSize = 26f
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textAlign = android.graphics.Paint.Align.LEFT
        paint.color = Color.BLACK
        canvas.drawText("Date: ${data.date}", pad + 8f, y + lineH * 0.72f, paint)
        y += lineH

        canvas.drawText("Name: ${data.customerName}", pad + 8f, y + lineH * 0.72f, paint)
        y += lineH
        if (data.remark.isNotBlank()) {
            paint.textSize = 22f
            canvas.drawText("မှတ်ချက်: ${data.remark}", pad + 8f, y + lineH * 0.72f, paint)
            paint.textSize = 26f
            y += lineH
        }
        y += 12f

        // ==
        paint.textSize = 28f
        paint.typeface = android.graphics.Typeface.MONOSPACE
        val betIndent = pad + 24f
        val rightEdge = width - pad - 24f

        // Pre-compute max amount string width so the column stays fixed
        val maxAmtStr = data.bets.maxOfOrNull { "%,d Ks".format(it.second) } ?: ""
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        val amtColumnWidth = paint.measureText(maxAmtStr) + 8f
        paint.textAlign = android.graphics.Paint.Align.LEFT

        data.bets.forEachIndexed { idx, (number, amount) ->
            val amtStr = "%,d Ks".format(amount)
            // Number — left aligned
            paint.textAlign = android.graphics.Paint.Align.LEFT
            canvas.drawText(number, betIndent, y + betLineH * 0.72f, paint)
            // Amount — right aligned at fixed right edge
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            canvas.drawText(amtStr, rightEdge, y + betLineH * 0.72f, paint)
            paint.textAlign = android.graphics.Paint.Align.LEFT
            y += betLineH
            // dotted line between bets (not after last)
            if (idx < data.bets.size - 1) {
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 4f), 0f)
                canvas.drawLine(betIndent, y - 2f, width - pad - 8f, y - 2f, paint)
                paint.pathEffect = null
                paint.style = android.graphics.Paint.Style.FILL
            }
        }
        y += 12f

        drawSeparator(y)
        y += 14f

        // ==
        // Shaded background band for total row
        paint.color = Color.BLACK
        val bandTop = y - 4f
        val bandBot = y + bigLineH + 4f
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(pad + 4f, bandTop, width - pad - 4f, bandBot, 4f, 4f, paint)
        paint.style = android.graphics.Paint.Style.FILL

        paint.textSize = 36f
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign = android.graphics.Paint.Align.LEFT
        paint.color = Color.BLACK
        canvas.drawText("Total", pad + 12f, y + bigLineH * 0.55f, paint)
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas.drawText("%,d Ks".format(data.totalAmount), width - pad - 12f, y + bigLineH * 0.55f, paint)
        y += bigLineH + 8f

        drawSeparator(y)
        y += 14f

        // ==
        paint.textSize = 26f
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.color = Color.BLACK
        canvas.drawText(data.footerText, width / 2f, y + lineH * 0.72f, paint)
        y += lineH + 12f

        // ==
        paint.strokeWidth = 1.5f
        paint.style = android.graphics.Paint.Style.STROKE
        paint.color = Color.BLACK
        val zigPath = android.graphics.Path()
        val segW = 8f
        var xz = pad + 8f
        val zy = y + zigzagH / 2f
        zigPath.moveTo(xz, zy)
        var toggle = true
        while (xz < width - pad - 8f) {
            xz += segW
            zigPath.lineTo(xz, if (toggle) zy - zigzagH / 2f else zy + zigzagH / 2f)
            toggle = !toggle
        }
        canvas.drawPath(zigPath, paint)

        // "--- cut ---" label above zigzag
        paint.style = android.graphics.Paint.Style.FILL
        paint.textSize = 18f
        paint.textAlign = android.graphics.Paint.Align.CENTER
        canvas.drawText("--- cut here ---", width / 2f, y + 4f, paint)

        return bitmap
    }
}
