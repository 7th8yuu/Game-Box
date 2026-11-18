package com.gamebox.helper

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

// Màn hình Kết cấu: so sánh trước/sau trên cảnh mẫu (chỉ là mô phỏng), chọn mức kết cấu, Hard Distance Steps, Sharp Pixels và
// độ phân giải màn hình. Kết cấu áp thật qua ANGLE khi máy hỗ trợ (xem Angle.kt), độ phân giải áp thật bằng wm size (xem Wm.kt).
@Composable
fun TexScreen(gs: Gs) {
    val icon = gs.icon(gs.sel)
    val name = gs.selName
    val names = Angle.names
    // Hệ số thu nhỏ của từng mức trong bản xem trước (cảnh mẫu chỉ 320 px nên hiệu ứng được phóng đại cho dễ thấy)
    val fracs = listOf(1f, 1f / 3, 1f / 6, 1f / 10, 1f / 16)
    var lvl by remember { mutableIntStateOf(gs.g("texl", 0).coerceIn(0, 4)) }
    var resv by remember { mutableIntStateOf(gs.g("res", 100).coerceIn(30, 100)) }
    var on by remember { mutableStateOf(true) }
    var hard by remember { mutableStateOf(gs.g("texd", 0) > 0) }
    val dist = if (hard) 60 else 0
    var edge by remember { mutableStateOf(gs.g("texb", 0) == 1) }
    var pr by remember { mutableStateOf<Angle.Probe?>(null) }
    var prTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(prTick) { pr = withContext(Dispatchers.IO) { Angle.probe(gs.sp) } }
    var info by remember { mutableStateOf<Triple<String, Int, Gpu.Game>?>(null) }
    LaunchedEffect(gs.sel) {
        info = withContext(Dispatchers.IO) { Gpu.detect(); Triple(Gpu.renderer, Gpu.tier(), Gpu.game(gs.ctx, gs.sel)) }
    }
    var pos by remember { mutableFloatStateOf(.5f) }
    val scene = remember { sceneBitmap() }
    val after = remember(lvl) { pixelate(scene, fracs[lvl]) }
    // Bản làm mờ của ảnh "sau" cho mô phỏng mờ theo độ xa (phần trên = xa hơn)
    val blur = remember(lvl) {
        val a = after.asAndroidBitmap()
        android.graphics.Bitmap.createScaledBitmap(android.graphics.Bitmap.createScaledBitmap(a, 40, 23, true), SCENE_W, SCENE_H, true).asImageBitmap()
    }
    var hold by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(if (hold) 1f else pos, if (hold) tween<Float>(150) else snap<Float>(), label = "hold")
    BackHandler { gs.tex = false }
    val tag = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(.55f)).padding(horizontal = 10.dp, vertical = 4.dp)
    Box(Modifier.fillMaxSize().background(Color(0xFF080E14)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }) {
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().enter(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(44.dp).bounce { gs.tex = false }.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.08f)), contentAlignment = Alignment.Center) {
                        Ico(Ic.Back, sz = 22.dp)
                    }
                    Text("Kết cấu", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, Color.White.copy(.2f), RoundedCornerShape(20.dp))) {
                    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ -> pos = (change.position.x / size.width).coerceIn(0f, 1f) }
                    }) {
                        val w = size.width.toInt(); val h = size.height.toInt()
                        drawImage(scene, dstSize = IntSize(w, h), filterQuality = FilterQuality.None)
                        clipRect(left = size.width * shown) {
                            // Viền bật: giữ nguyên các khối vuông (không làm mịn); tắt: phóng mịn nên khó nhận ra khối
                            drawImage(after, dstSize = IntSize(w, h), filterQuality = if (edge) FilterQuality.None else FilterQuality.Medium)
                            if (dist > 0) drawIntoCanvas { c ->
                                c.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
                                drawImage(blur, dstSize = IntSize(w, h), filterQuality = FilterQuality.Medium)
                                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(dist / 100f), Color.Transparent), 0f, size.height * .75f), blendMode = BlendMode.DstIn)
                                c.restore()
                            }
                        }
                        if (!hold) {
                            drawLine(Color.White, Offset(size.width * pos, 0f), Offset(size.width * pos, size.height), strokeWidth = 2.dp.toPx())
                            drawCircle(Color.White, 16.dp.toPx(), Offset(size.width * pos, size.height / 2))
                        }
                    }
                    Text("TRƯỚC", tag.align(Alignment.TopStart).padding(10.dp), fontSize = 10.sp, letterSpacing = 2.sp)
                    Text("SAU", tag.align(Alignment.TopEnd).padding(10.dp), fontSize = 10.sp, letterSpacing = 2.sp)
                    Pill(Modifier.align(Alignment.BottomCenter).padding(10.dp).pointerInput(Unit) { detectTapGestures(onPress = { hold = true; tryAwaitRelease(); hold = false }) }) {
                        Ico(Ic.Hand, sz = 18.dp)
                        Text("Giữ: Trước", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.06f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Image(icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Bật cho $name", fontSize = 12.sp, color = Color.White.copy(.65f))
                    }
                    GSwitch(on) { on = it }
                }
            }
            Column(Modifier.weight(1.3f).fillMaxHeight().enter(80).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                info?.let { (r, t, g) ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.06f)).padding(12.dp)) {
                        Lbl("TỰ NHẬN DIỆN")
                        Text("GPU: ${r.ifEmpty { "Không rõ" }} · ${Gpu.tierName(t)}", fontSize = 12.sp, maxLines = 2)
                        Text("Game: ${g.engine} · ${g.api}", fontSize = 12.sp, color = Color.White.copy(.75f), maxLines = 2)
                        Spacer(Modifier.height(6.dp))
                        SmallBtn("Tự động: gợi ý ${names[Gpu.suggest(t)]}") { lvl = Gpu.suggest(t) }
                    }
                }
                Lbl("KẾT CẤU")
                Seg(names, lvl, Modifier.fillMaxWidth()) { lvl = it }
                val p = pr
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.06f)).padding(12.dp)) {
                    when {
                        p == null -> Text("Đang kiểm tra máy có hỗ trợ kết cấu thật không...", fontSize = 12.sp, color = Color.White.copy(.75f))
                        p.ok -> Text("Máy này dùng được kết cấu thật (ANGLE). Áp khi bấm Chơi: game cần được mở lại từ Game Box. Chỉ có tác dụng với game OpenGL ES, game chạy Vulkan thuần sẽ không đổi.", fontSize = 12.sp, color = Cy)
                        else -> {
                            Text("Máy này chưa dùng được kết cấu thật: ${p.why()}.", fontSize = 12.sp, color = Warn)
                            Text("Ảnh Trước/Sau chỉ là mô phỏng. Muốn nhẹ máy ngay thì dùng độ phân giải ở dưới, phần đó chạy được trên mọi máy có Shizuku hoặc root.", fontSize = 12.sp, color = Color.White.copy(.65f))
                            if (p.bad) { Spacer(Modifier.height(6.dp)); SmallBtn("Cho phép thử lại") { Angle.clearBad(gs.sp); prTick++ } }
                        }
                    }
                }
                SwRow("Hard Distance Steps", "Chuyển chi tiết theo bậc theo khoảng cách, vật xa vỡ khối rõ hơn", hard) { hard = it }
                SwRow("Sharp Pixels", "Lấy mẫu gần nhất: thấy rõ các ô vuông thay vì làm mờ mượt", edge) { edge = it }
                Lbl("ĐỘ PHÂN GIẢI MÀN HÌNH")
                val dm = gs.ctx.resources.displayMetrics
                val big = maxOf(dm.widthPixels, dm.heightPixels); val small = minOf(dm.widthPixels, dm.heightPixels)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Độ phân giải trong lúc chơi", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(if (resv >= 100) "Gốc" else "$resv%", color = Cy, fontWeight = FontWeight.Bold)
                }
                GSlider(resv.toFloat(), 30f..100f, steps = 13, mod = Modifier.fillMaxWidth()) { resv = (it / 5).roundToInt() * 5 }
                Text(if (resv >= 95) "Giữ nguyên độ phân giải của máy" else "Game sẽ chạy ở khoảng ${big * resv / 100}×${small * resv / 100} thay vì $big×$small", fontSize = 13.sp, color = Cy, fontWeight = FontWeight.Medium)
                Text("Hạ độ phân giải màn hình (wm size) trong lúc chơi nên game nào cũng nhẹ GPU hơn; kích thước chữ và nút trong game không đổi. Tự trả lại khi thoát game; nếu bị kẹt, vào Cài đặt > Khôi phục độ phân giải.", fontSize = 12.sp, color = Color.White.copy(.65f))
                Text("Áp dụng thật khi bạn bấm Chơi trong app này. Game mở ngoài app vẫn chạy bình thường. Đổi khi đang chơi thì đóng game rồi mở lại.", fontSize = 12.sp, color = Color.White.copy(.65f))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GhostBtn("Đặt lại gốc", icon = Ic.Reset) { lvl = 0; hard = false; edge = false; resv = 100 }
                    PrimaryBtn("Lưu", Modifier.weight(1f), icon = Ic.Save) {
                        gs.s("res", if (on) resv else 100); gs.s("texl", if (on) lvl else 0); gs.s("texm", 0); gs.s("texd", if (on && hard) 60 else 0); gs.s("texb", if (on && edge) 1 else 0); gs.tex = false
                    }
                }
            }
        }
    }
}
