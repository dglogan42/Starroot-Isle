package com.starrootisle.app.net

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Deep-link + QR helpers for online rooms.
 *
 * Format: starroot://join?room=ABCD&ws=ws%3A%2F%2Fhost%3A8790
 */
object RoomQr {
    const val SCHEME = "starroot"
    const val HOST_JOIN = "join"

    fun buildLink(room: String, wsUrl: String): String {
        return Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST_JOIN)
            .appendQueryParameter("room", room.uppercase())
            .appendQueryParameter("ws", wsUrl)
            .build()
            .toString()
    }

    fun parseLink(uri: Uri): Pair<String, String?>? {
        // starroot://join?room=X&ws=Y
        // also starroot://join/X
        val room = when {
            uri.getQueryParameter("room") != null -> uri.getQueryParameter("room")
            uri.pathSegments.isNotEmpty() -> uri.pathSegments[0]
            uri.host != null && uri.host != HOST_JOIN -> uri.host
            else -> null
        }?.uppercase()?.replace(Regex("[^A-Z0-9]"), "")
        if (room.isNullOrBlank()) return null
        val ws = uri.getQueryParameter("ws")
        return room to ws
    }

    fun encode(content: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.parseColor("#1A1A2E") else Color.parseColor("#FFF8E7"))
            }
        }
        return bmp
    }

    fun decode(bitmap: Bitmap): String? {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val source = RGBLuminanceSource(w, h, pixels)
            val binary = BinaryBitmap(HybridBinarizer(source))
            MultiFormatReader().decode(binary).text
        } catch (_: Exception) {
            null
        }
    }

    fun parseAny(text: String): Pair<String, String?>? {
        val t = text.trim()
        // raw room code
        if (t.matches(Regex("[A-Za-z0-9]{4,6}"))) {
            return t.uppercase() to null
        }
        return try {
            parseLink(Uri.parse(t))
        } catch (_: Exception) {
            null
        }
    }
}
