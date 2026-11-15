package com.gamebox.helper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Bộ icon nét mảnh tự vẽ trên lưới 24x24, dùng chung cho app và overlay
enum class Ic {
    Game, Gauge, Battery, Tune, Power, Home, User, Touch, Back, Check, Plus, Minus, Info, Bolt, Thermo, Chip,
    Cam, Rec, Target, Cursor, Refresh, Res, Shield, Bell, Timer, Mic, Vol, Net, Save, Reset, Drag, Down, Close,
    Layers, Play, Rocket, Pulse, Gear, Bandage, Sun, Bulb, Link, Hand
}

@Composable
fun Ico(k: Ic, modifier: Modifier = Modifier, tint: Color = Color.White, sz: Dp = 24.dp) {
    Canvas(modifier.size(sz)) { drawIc(k, tint) }
}

fun DrawScope.drawIc(k: Ic, tint: Color) {
    val u = size.minDimension / 24f
    val st = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(a: Float, b: Float, c: Float, d: Float) =
        drawLine(tint, Offset(a * u, b * u), Offset(c * u, d * u), strokeWidth = st.width, cap = StrokeCap.Round)
    fun ring(cx: Float, cy: Float, r: Float) = drawCircle(tint, r * u, Offset(cx * u, cy * u), style = st)
    fun dot(cx: Float, cy: Float, r: Float) = drawCircle(tint, r * u, Offset(cx * u, cy * u), style = Fill)
    fun box(x: Float, y: Float, w: Float, h: Float, r: Float) =
        drawRoundRect(tint, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u), style = st)
    fun arc(x: Float, y: Float, w: Float, h: Float, s: Float, sw: Float) =
        drawArc(tint, s, sw, false, Offset(x * u, y * u), Size(w * u, h * u), style = st)
    fun poly(vararg f: Float, close: Boolean = false) {
        val p = Path()
        p.moveTo(f[0] * u, f[1] * u)
        var i = 2
        while (i + 1 < f.size) { p.lineTo(f[i] * u, f[i + 1] * u); i += 2 }
        if (close) p.close()
        drawPath(p, tint, style = st)
    }
    when (k) {
        Ic.Game -> { box(2f, 7f, 20f, 11f, 5.5f); line(7f, 10.5f, 7f, 14.5f); line(5f, 12.5f, 9f, 12.5f); dot(15.5f, 11.5f, 1f); dot(18.2f, 13.8f, 1f) }
        Ic.Gauge -> { arc(3f, 4f, 18f, 18f, 150f, 240f); line(12f, 13f, 16f, 8.5f); dot(12f, 13f, 1.3f) }
        Ic.Battery -> { box(7f, 4f, 10f, 17f, 2.5f); line(10f, 2.2f, 14f, 2.2f); poly(12.8f, 8.5f, 10f, 13f, 14f, 13f, 11.2f, 17.5f) }
        Ic.Tune -> {
            line(4f, 7f, 7f, 7f); line(11f, 7f, 20f, 7f); ring(9f, 7f, 2f)
            line(4f, 12f, 12f, 12f); line(16f, 12f, 20f, 12f); ring(14f, 12f, 2f)
            line(4f, 17f, 6f, 17f); line(10f, 17f, 20f, 17f); ring(8f, 17f, 2f)
        }
        Ic.Power -> { arc(5f, 5f, 14f, 14f, -60f, 300f); line(12f, 3f, 12f, 11f) }
        Ic.Home -> { poly(3f, 11f, 12f, 3f, 21f, 11f); poly(5.5f, 9.5f, 5.5f, 20f, 18.5f, 20f, 18.5f, 9.5f); poly(10f, 20f, 10f, 14f, 14f, 14f, 14f, 20f) }
        Ic.User -> { ring(12f, 8f, 4f); arc(4f, 14f, 16f, 12f, 180f, 180f) }
        Ic.Touch -> { arc(5f, 2f, 14f, 14f, 200f, 140f); arc(8f, 5f, 8f, 8f, 200f, 140f); poly(10f, 13f, 10f, 20f, 13.5f, 21.5f, 17.5f, 20f, 17.5f, 14.5f, 14f, 13.5f, 14f, 9f) }
        Ic.Back -> { line(20f, 12f, 5f, 12f); poly(11f, 6f, 5f, 12f, 11f, 18f) }
        Ic.Check -> poly(4.5f, 12.5f, 10f, 18f, 19.5f, 6.5f)
        Ic.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        Ic.Minus -> line(5f, 12f, 19f, 12f)
        Ic.Info -> { ring(12f, 12f, 9f); line(12f, 11f, 12f, 17f); dot(12f, 7.6f, 1f) }
        Ic.Bolt -> poly(13.5f, 2.5f, 5.5f, 13.5f, 11.5f, 13.5f, 10.5f, 21.5f, 18.5f, 10.5f, 12.5f, 10.5f, close = true)
        Ic.Thermo -> { box(9.5f, 3f, 5f, 12f, 2.5f); ring(12f, 17.5f, 3.5f) }
        Ic.Chip -> {
            box(6f, 6f, 12f, 12f, 2f); box(10f, 10f, 4f, 4f, 0.8f)
            line(9f, 3f, 9f, 6f); line(15f, 3f, 15f, 6f); line(9f, 18f, 9f, 21f); line(15f, 18f, 15f, 21f)
            line(3f, 9f, 6f, 9f); line(3f, 15f, 6f, 15f); line(18f, 9f, 21f, 9f); line(18f, 15f, 21f, 15f)
        }
        Ic.Cam -> { box(3f, 7f, 18f, 13f, 3f); ring(12f, 13.5f, 3.5f); line(8f, 7f, 9.5f, 4f); line(9.5f, 4f, 14.5f, 4f); line(14.5f, 4f, 16f, 7f) }
        Ic.Rec -> { box(3f, 6f, 13f, 12f, 3f); poly(16f, 10.5f, 21f, 7.5f, 21f, 16.5f, 16f, 13.5f, close = true) }
        Ic.Target -> { ring(12f, 12f, 7.5f); ring(12f, 12f, 2.5f); line(12f, 2f, 12f, 5f); line(12f, 19f, 12f, 22f); line(2f, 12f, 5f, 12f); line(19f, 12f, 22f, 12f) }
        Ic.Cursor -> poly(6f, 3f, 6f, 17f, 10f, 13.5f, 13f, 20f, 15.5f, 19f, 12.5f, 12.5f, 17.5f, 12f, close = true)
        Ic.Refresh -> { arc(4f, 4f, 16f, 16f, -40f, 280f); poly(14f, 3.5f, 19f, 4.5f, 17f, 9.5f) }
        Ic.Res -> { box(3f, 5f, 18f, 12f, 2f); line(8f, 20f, 16f, 20f); line(12f, 17f, 12f, 20f) }
        Ic.Shield -> { poly(12f, 3f, 19.5f, 6f, 19.5f, 12f, 12f, 21f, 4.5f, 12f, 4.5f, 6f, close = true); poly(8.5f, 12f, 11f, 14.5f, 15.5f, 9.5f) }
        Ic.Bell -> { poly(5f, 17.5f, 7f, 15.5f, 7f, 10.5f, 9f, 6.5f, 12f, 5.5f, 15f, 6.5f, 17f, 10.5f, 17f, 15.5f, 19f, 17.5f, close = true); line(10f, 20.5f, 14f, 20.5f) }
        Ic.Timer -> { ring(12f, 13.5f, 7.5f); line(12f, 13.5f, 12f, 9.5f); line(9.5f, 3f, 14.5f, 3f); line(18f, 6f, 19.5f, 4.5f) }
        Ic.Mic -> { box(9f, 3f, 6f, 11f, 3f); arc(5.5f, 7.5f, 13f, 11f, 0f, 180f); line(12f, 18.5f, 12f, 21.5f); line(9f, 21.5f, 15f, 21.5f) }
        Ic.Vol -> { poly(4f, 9.5f, 8f, 9.5f, 12.5f, 6f, 12.5f, 18f, 8f, 14.5f, 4f, 14.5f, close = true); arc(11.5f, 8f, 6f, 8f, -50f, 100f); arc(10f, 5f, 10f, 14f, -50f, 100f) }
        Ic.Net -> { arc(2f, 9f, 20f, 20f, 225f, 90f); arc(5.5f, 12.5f, 13f, 13f, 225f, 90f); dot(12f, 19f, 1.2f) }
        Ic.Save -> { box(4f, 4f, 16f, 16f, 3f); box(8f, 4f, 8f, 5f, 1f); box(7.5f, 13f, 9f, 7f, 1f) }
        Ic.Reset -> { arc(4f, 4f, 16f, 16f, -60f, 300f); poly(8f, 3.5f, 4.5f, 4.5f, 5.5f, 8.5f) }
        Ic.Drag -> {
            line(12f, 3f, 12f, 21f); line(3f, 12f, 21f, 12f)
            poly(9f, 6f, 12f, 3f, 15f, 6f); poly(9f, 18f, 12f, 21f, 15f, 18f); poly(6f, 9f, 3f, 12f, 6f, 15f); poly(18f, 9f, 21f, 12f, 18f, 15f)
        }
        Ic.Down -> { line(12f, 4f, 12f, 19f); poly(6f, 13f, 12f, 19f, 18f, 13f) }
        Ic.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        Ic.Layers -> { poly(12f, 3f, 21f, 8f, 12f, 13f, 3f, 8f, close = true); poly(3f, 12f, 12f, 17f, 21f, 12f); poly(3f, 16f, 12f, 21f, 21f, 16f) }
        Ic.Play -> poly(8f, 5f, 19f, 12f, 8f, 19f, close = true)
        Ic.Rocket -> { poly(12f, 2.5f, 16f, 8f, 16f, 15f, 8f, 15f, 8f, 8f, close = true); ring(12f, 9.5f, 1.6f); poly(8f, 15f, 5f, 19f, 9f, 18f); poly(16f, 15f, 19f, 19f, 15f, 18f); line(12f, 17f, 12f, 21f) }
        Ic.Pulse -> poly(2f, 12f, 7f, 12f, 9.5f, 5f, 14f, 19f, 16.5f, 12f, 22f, 12f)
        Ic.Gear -> {
            ring(12f, 12f, 3.2f); ring(12f, 12f, 7f)
            for (i in 0 until 8) {
                val a = Math.PI / 4 * i
                line(12f + 7f * cos(a).toFloat(), 12f + 7f * sin(a).toFloat(), 12f + 10f * cos(a).toFloat(), 12f + 10f * sin(a).toFloat())
            }
        }
        Ic.Bandage -> { poly(6f, 12.5f, 12.5f, 6f, 18f, 11.5f, 11.5f, 18f, close = true); dot(11f, 11f, 0.9f); dot(13.2f, 13.2f, 0.9f); dot(10.5f, 14f, 0.9f); dot(14f, 10.2f, 0.9f) }
        Ic.Sun -> { ring(12f, 12f, 4f); for (i in 0 until 8) { val a = Math.PI / 4 * i; line(12f + 7f * cos(a).toFloat(), 12f + 7f * sin(a).toFloat(), 12f + 9.5f * cos(a).toFloat(), 12f + 9.5f * sin(a).toFloat()) } }
        Ic.Bulb -> { arc(6f, 3f, 12f, 12f, 150f, 240f); line(9.5f, 17f, 14.5f, 17f); line(10.5f, 20f, 13.5f, 20f); line(9f, 13.5f, 9.5f, 17f); line(15f, 13.5f, 14.5f, 17f) }
        Ic.Link -> { box(3f, 9f, 11f, 6f, 3f); box(10f, 9f, 11f, 6f, 3f) }
        Ic.Hand -> { poly(8f, 13f, 8f, 20f, 12f, 21.5f, 17f, 20f, 17.5f, 12f); line(8f, 13f, 8f, 5f); line(11f, 12f, 11f, 3.5f); line(14f, 12f, 14f, 5f); line(17f, 12f, 17f, 8f) }
        else -> Unit
    }
}
