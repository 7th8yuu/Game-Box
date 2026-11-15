package com.gamebox.helper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.sin

// Cảnh mẫu vẽ bằng code (hoàng hôn, núi, mặt đất có đường kẻ mảnh, HUD) để xem trước độ vỡ hình khi giảm độ phân giải.
// Nhiều chi tiết mảnh nên giảm độ phân giải nhìn thấy rõ hơn so với một icon phẳng.
const val SCENE_W = 320
const val SCENE_H = 180

fun sceneBitmap(): ImageBitmap {
    val bmp = ImageBitmap(SCENE_W, SCENE_H)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(SCENE_W.toFloat(), SCENE_H.toFloat())) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF16224A), Color(0xFFB4506E), Color(0xFFFFB46B)), 0f, h * .66f), size = Size(w, h * .72f))
        val sun = Offset(w * .72f, h * .46f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF2C2), Color(0x00FFC46B)), sun, h * .3f), h * .3f, sun)
        drawCircle(Color(0xFFFFF5D6), h * .075f, sun)

        fun ridge(c: Color, base: Float, amp: Float, freq: Float, ph: Float) {
            val p = Path()
            p.moveTo(0f, h)
            var x = 0f
            while (x <= w) {
                p.lineTo(x, base - amp * (.5f + .5f * sin(x * freq + ph)) - amp * .4f * sin(x * freq * 2.3f + ph * 1.7f))
                x += 3f
            }
            p.lineTo(w, h); p.close()
            drawPath(p, c)
        }
        ridge(Color(0xFF3A2A5C), h * .62f, h * .16f, .018f, 1f)
        ridge(Color(0xFF241A3E), h * .70f, h * .12f, .03f, 3f)

        val gy = h * .72f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1F3B2A), Color(0xFF0F1D16)), gy, h), Offset(0f, gy), Size(w, h - gy))
        // Đường kẻ phối cảnh trên mặt đất (chi tiết mảnh)
        for (k in 1..9) drawLine(Color.White.copy(.14f), Offset(0f, gy + k * k * 1.2f), Offset(w, gy + k * k * 1.2f), 1f)
        for (j in -9..9) drawLine(Color.White.copy(.12f), Offset(w / 2 + j * 12f, gy), Offset(w / 2 + j * 58f, h), 1f)
        // Cỏ
        for (i in 0 until 110) {
            val x = i * w / 110f; val y = gy + 4f + (i % 6) * 6f
            drawLine(Color(0xFF7BD66B).copy(.75f), Offset(x, y), Offset(x + (i % 3) - 1f, y + 9f), 1f)
        }
        // Cây
        for (i in 0 until 7) {
            val x = 20f + i * 46f; val t = h * .72f - 4f * (i % 3)
            val p = Path().apply { moveTo(x, t - 34f); lineTo(x - 11f, t); lineTo(x + 11f, t); close() }
            drawPath(p, Color(0xFF14261C))
            drawLine(Color(0xFF14261C), Offset(x, t), Offset(x, t + 8f), 2f)
        }
        // Nhân vật
        val cx = w * .36f; val cy = h * .78f
        drawCircle(Color(0xFFEAF6FF), 7f, Offset(cx, cy - 26f), style = Stroke(1.5f))
        drawLine(Color(0xFFEAF6FF), Offset(cx, cy - 19f), Offset(cx, cy - 2f), 1.5f)
        drawLine(Color(0xFFEAF6FF), Offset(cx - 9f, cy - 14f), Offset(cx + 9f, cy - 14f), 1.5f)
        drawLine(Color(0xFFEAF6FF), Offset(cx, cy - 2f), Offset(cx - 6f, cy + 12f), 1.5f)
        drawLine(Color(0xFFEAF6FF), Offset(cx, cy - 2f), Offset(cx + 6f, cy + 12f), 1.5f)
        // HUD: tâm ngắm, thanh máu, minimap
        val c = Offset(w / 2, h * .5f)
        drawCircle(Color.White.copy(.85f), 9f, c, style = Stroke(1f))
        drawLine(Color.White.copy(.85f), Offset(c.x - 16f, c.y), Offset(c.x - 5f, c.y), 1f)
        drawLine(Color.White.copy(.85f), Offset(c.x + 5f, c.y), Offset(c.x + 16f, c.y), 1f)
        drawLine(Color.White.copy(.85f), Offset(c.x, c.y - 16f), Offset(c.x, c.y - 5f), 1f)
        drawLine(Color.White.copy(.85f), Offset(c.x, c.y + 5f), Offset(c.x, c.y + 16f), 1f)
        drawRect(Color.White.copy(.25f), Offset(12f, 12f), Size(92f, 7f), style = Stroke(1f))
        drawRect(Color(0xFF4ADE80), Offset(13f, 13f), Size(60f, 5f))
        drawRect(Color.White.copy(.3f), Offset(w - 64f, 12f), Size(52f, 52f), style = Stroke(1f))
        for (i in 1..3) {
            drawLine(Color.White.copy(.15f), Offset(w - 64f + i * 13f, 12f), Offset(w - 64f + i * 13f, 64f), 1f)
            drawLine(Color.White.copy(.15f), Offset(w - 64f, 12f + i * 13f), Offset(w - 12f, 12f + i * 13f), 1f)
        }
        drawCircle(Color(0xFFFF6B6B), 2.5f, Offset(w - 40f, 30f))
    }
    return bmp
}

// Giảm độ phân giải rồi phóng lại không làm mịn: mô phỏng hình bị vỡ khi game render ở độ phân giải thấp
fun pixelate(src: ImageBitmap, frac: Float): ImageBitmap {
    if (frac >= .999f) return src
    val sw = (SCENE_W * frac).toInt().coerceAtLeast(8); val sh = (SCENE_H * frac).toInt().coerceAtLeast(5)
    val small = android.graphics.Bitmap.createScaledBitmap(src.asAndroidBitmap(), sw, sh, true)
    return android.graphics.Bitmap.createScaledBitmap(small, SCENE_W, SCENE_H, false).asImageBitmap()
}
