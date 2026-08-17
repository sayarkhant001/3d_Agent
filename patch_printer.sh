#!/bin/bash
cat << 'INNEREOF' > temp_printer.txt

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
INNEREOF

sed -i '/^}$/d' app/src/main/java/com/example/logic/BluetoothPrinter.kt
cat temp_printer.txt >> app/src/main/java/com/example/logic/BluetoothPrinter.kt
