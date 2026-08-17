package com.example.logic

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
}
