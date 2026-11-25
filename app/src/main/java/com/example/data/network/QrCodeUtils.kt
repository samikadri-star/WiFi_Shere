package com.example.data.network

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeUtils {

    fun generateQrBitmap(
        content: String,
        sizePx: Int = 512,
    ): Bitmap? {
        if (content.isBlank()) return null
        return runCatching {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1,
            )
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints,
            )
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            val darkColor = Color.parseColor("#09100C")
            val lightColor = Color.parseColor("#DDE4DD")
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix[x, y]) darkColor else lightColor
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        }.getOrNull()
    }

    fun decodeQrFromImageProxy(image: ImageProxy): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride

        val normalizedData = if (rowStride == width) {
            data
        } else {
            ByteArray(width * height).also { out ->
                for (row in 0 until height) {
                    val srcOffset = row * rowStride
                    val length = minOf(width, data.size - srcOffset)
                    if (length > 0) {
                        System.arraycopy(data, srcOffset, out, row * width, length)
                    }
                }
            }
        }

        val source = PlanarYUVLuminanceSource(
            normalizedData,
            width,
            height,
            0,
            0,
            width,
            height,
            false,
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                ),
            )
        }
        return runCatching { reader.decode(binaryBitmap).text }.getOrNull()
    }
}
