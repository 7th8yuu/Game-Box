package com.gamebox.helper

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ---------- Màu và dữ liệu dùng chung ----------

// Màu nhấn của giao diện, đổi được trong trang Đồ họa và lưu trong SharedPreferences
val Themes = listOf(Color(0xFF7DD3FC), Color(0xFF4ADE80), Color(0xFFFBBF24), Color(0xFFF472B6), Color(0xFFA78BFA), Color(0xFFFB7185))
var Cy by mutableStateOf(Themes[0])
val Bg = Color(0xFF0A1118)
val Ok = Color(0xFF4ADE80)
val Warn = Color(0xFFFBBF24)
val Bad = Color(0xFFFF6B6B)
val Dim = Color.White.copy(.62f)
val OnAccent = Color(0xFF06121C)

class Bat(val lv: Int, val t: Float, val mv: Int, val ma: Int, val health: Int, val plug: Int, val cnt: Long, val ua: Int)

fun readBat(ctx: Context): Bat {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val ua = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
    return Bat(
        i?.getIntExtra("level", 0) ?: 0, (i?.getIntExtra("temperature", 0) ?: 0) / 10f, i?.getIntExtra("voltage", 0) ?: 0,
        Math.abs(ua) / 1000, i?.getIntExtra("health", 0) ?: 0, i?.getIntExtra("plugged", 0) ?: 0,
        bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER), ua
    )
}

fun f1(x: Double): String = String.format(Locale.US, "%.1f", x)

// ---------- Hiệu ứng ----------

// Nhấn thì co lại rồi bật về (đặt trước clip/background để cả khối co theo)
fun Modifier.bounce(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) .94f else 1f, spring(dampingRatio = .5f, stiffness = 600f), label = "bounce")
    graphicsLayer { scaleX = sc; scaleY = sc }.clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

// Xuất hiện dần, trượt lên và phóng nhẹ, có thể trễ theo thứ tự
fun Modifier.enter(delayMs: Int = 0): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 28.dp.toPx()
        val s = .94f + .06f * a.value
        scaleX = s; scaleY = s
    }
}

@Composable
fun animInt(v: Int): Int { val a by animateIntAsState(v, tween(600), label = "n"); return a }

@Composable
fun animF(v: Float): Float { val a by animateFloatAsState(v, tween(600), label = "f"); return a }

// ---------- Nền ----------

// Nền có ba đốm sáng trôi chậm; đây cũng là nguồn blur cho các thẻ kính
@Composable
fun LiveBackdrop(hz: HazeState, live: Boolean) {
    val t = rememberInfiniteTransition(label = "bd").animateFloat(0f, 1f, infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "bdt")
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0A1118), Color(0xFF17140F)))).hazeSource(hz)) {
        Canvas(Modifier.fillMaxSize()) {
            val a = if (live) t.value * 6.2831855f else 0f
            fun blob(c: Color, cx: Float, cy: Float, r: Float) =
                drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
            blob(Color(0xFF8A5A1E).copy(.5f), size.width * (.85f + .06f * sin(a)), size.height * (.1f + .08f * cos(a)), size.height * .9f)
            blob(Cy.copy(.28f), size.width * (.12f + .06f * cos(a)), size.height * (.95f + .06f * sin(a)), size.height * .8f)
            blob(Color(0xFF6D4AFF).copy(.16f), size.width * (.5f + .1f * sin(a * 2f)), size.height * .5f, size.height * .7f)
        }
    }
}

// ---------- Thành phần ----------

// Kính lỏng: blur nền phía sau + tint tối + viền sáng chéo
@Composable
fun GCard(hz: HazeState, mod: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val s = RoundedCornerShape(24.dp)
    Column(
        mod.clip(s)
            .hazeEffect(hz) { backgroundColor = Bg; blurRadius = 24.dp; tints = listOf(HazeTint(Color(0xFF0C1620).copy(.55f))) }
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(.35f), Color.White.copy(.05f), Color.White.copy(.15f))), s)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun Pill(mod: Modifier = Modifier, tint: Color = Color.White, content: @Composable RowScope.() -> Unit) {
    val s = RoundedCornerShape(18.dp)
    Row(
        mod.clip(s).background(tint.copy(.08f)).border(1.dp, tint.copy(.25f), s).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = content
    )
}

// Thanh chọn có viên thuốc trượt theo lựa chọn; mục bị tắt bị làm mờ
@Composable
fun Seg(items: List<String>, sel: Int, mod: Modifier = Modifier, enabled: List<Boolean>? = null, on: (Int) -> Unit) {
    val r = RoundedCornerShape(10.dp)
    BoxWithConstraints(mod.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.06f)).padding(4.dp)) {
        val w = maxWidth / items.size
        val x by animateDpAsState(w * sel.coerceAtLeast(0), spring(dampingRatio = .8f, stiffness = 400f), label = "seg")
        if (sel in items.indices) Box(Modifier.offset(x = x).width(w).height(40.dp).clip(r).background(Cy.copy(.22f)).border(1.dp, Cy.copy(.5f), r))
        Row {
            items.forEachIndexed { i, s ->
                val ok = enabled?.getOrNull(i) ?: true
                val c by animateColorAsState(if (!ok) Color.White.copy(.25f) else if (i == sel) Cy else Color.White.copy(.85f), tween(200), label = "segc")
                Box(Modifier.weight(1f).height(40.dp).clip(r).clickable(enabled = ok) { on(i) }, contentAlignment = Alignment.Center) {
                    Text(s, color = c, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

// Công tắc có núm nảy; bật thì nền theo màu nhấn
@Composable
fun GSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (on) 24.dp else 3.dp, spring(dampingRatio = .55f, stiffness = 500f), label = "swx")
    val c by animateColorAsState(if (on) Cy else Color.White.copy(.14f), tween(220), label = "swc")
    val k by animateColorAsState(if (on) OnAccent else Color.White.copy(.7f), tween(220), label = "swk")
    Box(
        Modifier.size(54.dp, 30.dp).clip(CircleShape).background(c)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!on) }
    ) { Box(Modifier.offset(x = x, y = 3.dp).size(24.dp).clip(CircleShape).background(k)) }
}

// Thanh trượt có núm vuông bo tròn; rainbow = nền cầu vồng như ảnh mẫu
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, rainbow: Boolean = false, mod: Modifier = Modifier, onChange: (Float) -> Unit) {
    val brush = Brush.horizontalGradient(listOf(Color(0xFFFF5A5F), Color(0xFFFFC145), Color(0xFF8EDB5E), Color(0xFF3FB6FF), Color(0xFFA78BFA)))
    Slider(
        value = value, onValueChange = onChange, modifier = mod, valueRange = range, steps = steps,
        thumb = { Box(Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFEAF6FF)).border(2.dp, Cy.copy(.6f), RoundedCornerShape(9.dp))) },
        track = { st ->
            val frac = ((st.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(.12f))) {
                if (rainbow) Box(Modifier.fillMaxSize().background(brush))
                else Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Cy))
            }
        }
    )
}

@Composable
fun PrimaryBtn(text: String, mod: Modifier = Modifier, icon: Ic? = null, onClick: () -> Unit) {
    val s = RoundedCornerShape(18.dp)
    Row(
        mod.bounce(onClick = onClick).clip(s).background(Brush.horizontalGradient(listOf(Cy, Cy.copy(.8f)))).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Ico(icon, tint = OnAccent, sz = 20.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun GhostBtn(text: String, mod: Modifier = Modifier, icon: Ic? = null, tint: Color = Cy, onClick: () -> Unit) {
    val s = RoundedCornerShape(18.dp)
    Row(
        mod.bounce(onClick = onClick).clip(s).background(Color.White.copy(.04f)).border(1.dp, tint.copy(.5f), s).padding(horizontal = 18.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Ico(icon, tint = tint, sz = 20.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, color = tint, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun Stat(l: String, v: String, mod: Modifier = Modifier, color: Color = Color.White) {
    Column(mod.padding(end = 22.dp)) {
        Text(l, fontSize = 10.sp, letterSpacing = 1.5.sp, color = Color.White.copy(.6f), maxLines = 1)
        Text(v, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

@Composable
fun VDiv() { Box(Modifier.padding(end = 22.dp).width(1.dp).height(30.dp).background(Color.White.copy(.12f))) }

@Composable
fun Lbl(t: String) = Text(t, fontSize = 11.sp, letterSpacing = 2.sp, color = Cy.copy(.85f))

// Một dòng gợi ý có icon nhỏ
@Composable
fun Hint(ic: Ic, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ico(ic, tint = Cy, sz = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(t, fontSize = 12.sp, color = Color.White.copy(.65f))
    }
}

// Biểu đồ đường mượt, tô nền và chấm sáng ở điểm mới nhất (phải = hiện tại)
@Composable
fun Spark(v: List<Int>, max: Int, mod: Modifier) = Canvas(mod) {
    repeat(3) { drawLine(Color.White.copy(.07f), Offset(0f, size.height * (it + 1) / 4), Offset(size.width, size.height * (it + 1) / 4), strokeWidth = 1.dp.toPx()) }
    if (v.size < 2) return@Canvas
    val n = 60f
    fun px(i: Int) = size.width * (n - v.size + i) / (n - 1f)
    fun py(y: Int) = size.height - size.height * y.coerceIn(0, max) / max.toFloat()
    val p = Path()
    p.moveTo(px(0), py(v[0]))
    for (i in 1 until v.size) {
        val x0 = px(i - 1); val y0 = py(v[i - 1]); val x1 = px(i); val y1 = py(v[i]); val cx = (x0 + x1) / 2
        p.cubicTo(cx, y0, cx, y1, x1, y1)
    }
    val fill = Path().apply { addPath(p); lineTo(px(v.size - 1), size.height); lineTo(px(0), size.height); close() }
    drawPath(fill, Brush.verticalGradient(listOf(Cy.copy(.35f), Color.Transparent)))
    drawPath(p, Cy, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(Cy.copy(.3f), 9.dp.toPx(), Offset(px(v.size - 1), py(v.last())))
    drawCircle(Cy, 4.5.dp.toPx(), Offset(px(v.size - 1), py(v.last())))
}

// Logo Game Box: vòng gradient quay chậm quanh tia sét
@Composable
fun Logo(sz: Dp) {
    val r = rememberInfiniteTransition(label = "logo").animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "logor")
    Box(Modifier.size(sz), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = r.value }) {
            drawCircle(Brush.sweepGradient(listOf(Cy, Color.Transparent, Color(0xFF3B82F6), Color.Transparent, Cy)), style = Stroke(width = 2.5.dp.toPx()))
        }
        Box(Modifier.fillMaxSize().padding(5.dp).clip(CircleShape).background(Color(0xFF0B1620)), contentAlignment = Alignment.Center) {
            Ico(Ic.Bolt, tint = Cy, sz = sz * .5f)
        }
    }
}

// Thanh bên của Game Space: viên chọn trượt xuống mục đang chọn
@Composable
fun Rail(page: Int, set: (Int) -> Unit, power: () -> Unit) {
    val icons = listOf(Ic.Game, Ic.Gauge, Ic.Battery, Ic.Tune)
    val itemH = 66.dp
    val y by animateDpAsState(itemH * page, spring(dampingRatio = .7f, stiffness = 350f), label = "rail")
    Column(Modifier.fillMaxHeight().width(84.dp).background(Color.Black.copy(.35f)).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Logo(50.dp)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(itemH * icons.size)) {
            Box(Modifier.offset(y = y + 7.dp).align(Alignment.TopCenter).size(52.dp).clip(RoundedCornerShape(16.dp)).background(Cy.copy(.2f)))
            Box(Modifier.offset(x = 4.dp, y = y + 17.dp).align(Alignment.TopStart).width(3.dp).height(32.dp).clip(CircleShape).background(Cy))
            Column {
                icons.forEachIndexed { i, ic ->
                    val c by animateColorAsState(if (page == i) Cy else Color.White.copy(.7f), tween(200), label = "railc")
                    Box(Modifier.fillMaxWidth().height(itemH).bounce { set(i) }, contentAlignment = Alignment.Center) { Ico(ic, tint = c, sz = 26.dp) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(48.dp).bounce { power() }.clip(CircleShape).background(Color.White.copy(.1f)), contentAlignment = Alignment.Center) { Ico(Ic.Power, sz = 22.dp) }
    }
}
