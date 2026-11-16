@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.gamebox.helper

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

// Kết quả các bài kiểm tra cảm ứng (Game Lab). -1 = chưa đo.
class LabState {
    var dead by mutableIntStateOf(-1)          // % ô màn hình đã nhận được chạm
    var deadNote by mutableStateOf("")
    var ghost by mutableIntStateOf(-1)         // số lần chạm xuất hiện khi không chạm
    var fingers by mutableIntStateOf(-1)       // số ngón tối đa cùng lúc
    var hz by mutableIntStateOf(-1)            // tần số lấy mẫu trung bình
    var hzMax by mutableIntStateOf(-1)
    var acc by mutableFloatStateOf(-1f)        // sai số chạm trung bình (mm)
    var jit by mutableFloatStateOf(-1f)        // độ rung khi giữ yên (mm)
    // Sửa cảm ứng: đo trước và sau
    var beforeMs by mutableIntStateOf(-1)
    var beforeMiss by mutableIntStateOf(-1)
    var afterMs by mutableIntStateOf(-1)
    var afterMiss by mutableIntStateOf(-1)
    var applied by mutableStateOf(false)
}

val LabTitles = listOf("Vùng chết", "Cảm ứng ma", "Đa điểm", "Tần số lấy mẫu", "Độ chính xác / rung")

private fun f1(v: Float) = f1(v.toDouble())

// ---------- Màn hình kiểm tra toàn màn hình ----------

@Composable
fun LabScreen(gs: Gs) {
    val k = gs.labKind
    if (k < 0) return
    BackHandler { gs.labKind = -1 }
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val close = { gs.labKind = -1 }
    Box(Modifier.fillMaxSize().background(Color(0xFF05090E))) {
        when (k) {
            0 -> DeadTest(gs.lab, close)
            1 -> GhostTest(gs.lab, close)
            2 -> MultiTest(gs.lab, close)
            3 -> HzTest(gs.lab, close)
            else -> AccTest(gs.lab, close)
        }
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircleBtn(Ic.Close, 40.dp) { close() }
            Text(LabTitles[k.coerceIn(0, 4)], fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BoxScope.Bottom(title: String, sub: String, btn: String? = null, onBtn: () -> Unit = {}) {
    Column(
        Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(Color(0xE60B1620)).border(1.dp, Color.White.copy(.12f), RoundedCornerShape(20.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = Dim, textAlign = TextAlign.Center)
        if (btn != null) { Spacer(Modifier.height(10.dp)); PrimaryBtn(btn, Modifier.fillMaxWidth()) { onBtn() } }
    }
}

private fun mm(px: Float, dpi: Float) = px / dpi * 25.4f

// 1. Vùng chết: chia màn hình thành lưới, ô nào có chạm thật sự được tô màu. Chỉ dùng mẫu thật (không nội suy)
// vì vùng chết không sinh mẫu; nội suy giữa hai điểm sẽ che mất vùng chết.
@Composable
private fun BoxScope.DeadTest(lab: LabState, close: () -> Unit) {
    val cols = 9
    var size by remember { mutableStateOf(IntSize.Zero) }
    val rows = if (size.width == 0) 18 else (size.height.toFloat() / size.width * cols).roundToInt().coerceIn(10, 30)
    val hdr = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
    val hit = remember(rows, size) {
        mutableStateListOf<Boolean>().apply {
            val ch = if (size.height > 0) size.height.toFloat() / rows else 1f
            // Ô nằm dưới thanh tiêu đề không chạm tới được, coi như đã đo
            for (i in 0 until cols * rows) add(((i / cols) + .5f) * ch < hdr)
        }
    }
    var done by remember { mutableStateOf(false) }
    val pct = if (hit.isEmpty()) 0 else hit.count { it } * 100 / hit.size
    Canvas(
        Modifier.fillMaxSize().onSizeChanged { size = it }.pointerInput(rows, size) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    if (size.width == 0) continue
                    for (c in e.changes) {
                        if (!c.pressed) continue
                        val pts = c.historical.map { it.position } + c.position
                        for (o in pts) {
                            val cx = (o.x / size.width * cols).toInt().coerceIn(0, cols - 1)
                            val cy = (o.y / size.height * rows).toInt().coerceIn(0, rows - 1)
                            hit[cy * cols + cx] = true
                        }
                    }
                }
            }
        }
    ) {
        val cw = this.size.width / cols; val chh = this.size.height / rows
        for (r in 0 until rows) for (c in 0 until cols) {
            val on = hit.getOrNull(r * cols + c) == true
            drawRect(if (on) Cy.copy(.38f) else Color.White.copy(.04f), Offset(c * cw + 1f, r * chh + 1f), Size(cw - 2f, chh - 2f))
        }
    }
    if (!done) Bottom("Đã chạm $pct%", "Vuốt ngón tay khắp màn hình cho mọi ô sáng lên. Ô nào mãi không sáng là vùng không phản hồi.", "Xong") {
        val miss = hit.indices.filter { !hit[it] }
        lab.dead = pct
        lab.deadNote = if (miss.isEmpty()) "Mọi ô đều phản hồi"
        else "Còn ${miss.size} ô chưa phản hồi: " + miss.take(4).joinToString(", ") { "hàng ${it / cols + 1}/cột ${it % cols + 1}" } + if (miss.size > 4) "…" else ""
        done = true
    } else Bottom("Phản hồi $pct%", lab.deadNote, "Đóng") { close() }
}

// 2. Cảm ứng ma: không chạm trong 15 giây, mọi lần chạm xuất hiện là chạm ma
@Composable
private fun BoxScope.GhostTest(lab: LabState, close: () -> Unit) {
    var left by remember { mutableIntStateOf(15) }
    var count by remember { mutableIntStateOf(0) }
    val dots = remember { mutableStateListOf<Offset>() }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- }; lab.ghost = count }
    Canvas(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    if (left <= 0) continue
                    for (c in e.changes) if (c.changedToDown()) { count++; dots.add(c.position) }
                }
            }
        }
    ) { dots.forEach { drawCircle(Bad.copy(.8f), 18.dp.toPx(), it); drawCircle(Bad, 18.dp.toPx(), it, style = Stroke(2.dp.toPx())) } }
    if (left > 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$left", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = Cy)
                Text("Đừng chạm vào màn hình", fontSize = 16.sp, color = Dim)
            }
        }
        Bottom("$count lần chạm lạ", "Mỗi lần chạm xuất hiện trong lúc bạn không chạm là một chạm ma.")
    } else {
        Bottom(
            if (count == 0) "Không có chạm ma" else "$count lần chạm ma",
            if (count == 0) "Màn hình ổn định trong 15 giây." else "Thử lau màn hình, tháo ốp/miếng dán hoặc rút sạc rồi đo lại.", "Đóng"
        ) { close() }
    }
}

// 3. Đa điểm: số ngón đặt cùng lúc
@Composable
private fun BoxScope.MultiTest(lab: LabState, close: () -> Unit) {
    val pts = remember { mutableStateMapOf<Long, Offset>() }
    var maxN by remember { mutableIntStateOf(0) }
    Canvas(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    for (c in e.changes) if (c.pressed) pts[c.id.value] = c.position else pts.remove(c.id.value)
                    if (pts.size > maxN) { maxN = pts.size; lab.fingers = maxN }
                }
            }
        }
    ) { pts.values.forEach { drawCircle(Cy.copy(.35f), 44.dp.toPx(), it); drawCircle(Cy, 44.dp.toPx(), it, style = Stroke(2.dp.toPx())) } }
    Bottom("Tối đa $maxN ngón", "Đang chạm ${pts.size}. Đặt thêm ngón tay cho tới khi số này không tăng nữa.", "Xong") { close() }
}

// 4. Tần số lấy mẫu: đếm các mẫu chạm thật (kể cả mẫu lịch sử gộp trong một khung hình) trong 1 giây gần nhất
@Composable
private fun BoxScope.HzTest(lab: LabState, close: () -> Unit) {
    val ts = remember { ArrayList<Long>() }
    var hz by remember { mutableIntStateOf(0) }
    var maxHz by remember { mutableIntStateOf(0) }
    var sum by remember { mutableLongStateOf(0L) }
    var n by remember { mutableIntStateOf(0) }
    val trail = remember { mutableStateListOf<Offset>() }
    Canvas(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull { it.pressed }
                    if (c == null) { ts.clear(); trail.clear(); continue }
                    c.historical.forEach { ts.add(it.uptimeMillis) }
                    ts.add(c.uptimeMillis)
                    trail.add(c.position); if (trail.size > 40) trail.removeAt(0)
                    while (ts.size > 1 && ts.first() < ts.last() - 1000L) ts.removeAt(0)
                    val span = ts.last() - ts.first()
                    if (ts.size >= 8 && span >= 300L) {
                        hz = ((ts.size - 1) * 1000f / span).roundToInt()
                        if (hz > maxHz) maxHz = hz
                        sum += hz; n++
                        lab.hz = (sum / n).toInt(); lab.hzMax = maxHz
                    }
                }
            }
        }
    ) { trail.forEachIndexed { i, o -> drawCircle(Cy.copy(.1f + .5f * i / 40f), 10.dp.toPx(), o) } }
    Bottom(if (n == 0) "Chưa có số liệu" else "$hz Hz", "Trung bình ${if (n == 0) 0 else (sum / n)} Hz · cao nhất $maxHz Hz. Giữ một ngón và trượt liên tục khắp màn hình.", "Xong") { close() }
}

// 5. Độ chính xác: chạm 7 mục tiêu (sai số), rồi giữ yên ngón tay 3 giây (độ rung)
@Composable
private fun BoxScope.AccTest(lab: LabState, close: () -> Unit) {
    val dpi = LocalContext.current.resources.displayMetrics.xdpi.coerceAtLeast(120f)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val spots = remember { listOf(.2f to .25f, .8f to .3f, .5f to .45f, .2f to .62f, .8f to .65f, .4f to .8f, .65f to .85f) }
    var idx by remember { mutableIntStateOf(0) }
    val errs = remember { ArrayList<Float>() }
    var holdStart by remember { mutableLongStateOf(0L) }
    val pos = remember { ArrayList<Offset>() }
    var phase by remember { mutableIntStateOf(0) } // 0 chạm mục tiêu, 1 giữ yên, 2 xong
    var accV by remember { mutableFloatStateOf(0f) }
    var jitV by remember { mutableFloatStateOf(0f) }
    fun tgt(): Offset = if (idx < spots.size && phase == 0) Offset(size.width * spots[idx].first, size.height * spots[idx].second) else Offset(size.width * .5f, size.height * .5f)
    Canvas(
        Modifier.fillMaxSize().onSizeChanged { size = it }.pointerInput(size) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull() ?: continue
                    if (size.width == 0 || phase == 2) continue
                    if (phase == 0) {
                        if (c.changedToDown()) {
                            errs.add(mm(hypot(c.position.x - tgt().x, c.position.y - tgt().y), dpi))
                            idx++
                            if (idx >= spots.size) { accV = errs.average().toFloat(); phase = 1 }
                        }
                    } else {
                        if (c.changedToDown()) { holdStart = SystemClock.uptimeMillis(); pos.clear() }
                        if (c.pressed && holdStart > 0L) {
                            pos.add(c.position)
                            if (SystemClock.uptimeMillis() - holdStart >= 3000L && pos.size > 3) {
                                val mx = pos.map { it.x }.average().toFloat(); val my = pos.map { it.y }.average().toFloat()
                                jitV = mm(pos.maxOf { hypot(it.x - mx, it.y - my) }, dpi)
                                lab.acc = accV; lab.jit = jitV
                                phase = 2
                            }
                        }
                    }
                }
            }
        }
    ) {
        if (size.width > 0 && phase < 2) {
            val t = tgt()
            drawCircle(Cy.copy(.25f), 30.dp.toPx(), t)
            drawCircle(Cy, 30.dp.toPx(), t, style = Stroke(2.dp.toPx()))
            drawCircle(Cy, 4.dp.toPx(), t)
        }
    }
    when (phase) {
        0 -> Bottom("Chạm vào tâm mục tiêu", "Mục tiêu ${(idx + 1).coerceAtMost(spots.size)}/${spots.size}. Chạm đúng giữa chấm.")
        1 -> Bottom("Giữ yên ngón tay", "Đặt ngón tay lên chấm giữa màn hình và giữ yên 3 giây, đừng nhấc lên.")
        else -> Bottom(
            "Sai số ${f1(accV)} mm · rung ${f1(jitV)} mm",
            (if (accV < 2f) "Chạm rất chính xác. " else if (accV < 4f) "Chạm khá chính xác. " else "Chạm hơi lệch. ") +
                (if (jitV < .6f) "Ngón giữ yên không bị rung." else if (jitV < 1.3f) "Có hơi rung nhẹ." else "Rung nhiều, có thể do nhiễu cảm ứng (thử lau màn hình, rút sạc)."),
            "Đóng"
        ) { close() }
    }
}

// ---------- Đo thời gian phản hồi (dùng cho tab Sửa cảm ứng) ----------

// 3 vòng x 4 lần chạm. Một ô sáng lên sau thời gian ngẫu nhiên; đo thời gian từ lúc sáng tới lúc chạm đúng,
// chạm nhầm ô khác tính là "trượt".
@Composable
fun TapMeasure(running: Boolean, onDone: (avgMs: Int, miss: Int) -> Unit) {
    var lit by remember { mutableIntStateOf(-1) }
    var litAt by remember { mutableLongStateOf(0L) }
    var tap by remember { mutableIntStateOf(0) }
    var miss by remember { mutableIntStateOf(0) }
    val times = remember { ArrayList<Long>() }
    val rounds = 3; val per = 4
    LaunchedEffect(running) {
        if (running) { tap = 0; miss = 0; times.clear(); lit = -1 }
    }
    LaunchedEffect(running, tap) {
        if (!running || tap >= rounds * per) return@LaunchedEffect
        delay(500L + Random.nextLong(0, 700))
        lit = Random.nextInt(6); litAt = SystemClock.uptimeMillis()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Vòng chạm", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Vòng ${(tap / per + 1).coerceAtMost(rounds)}/$rounds", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Cy)
    }
    Spacer(Modifier.height(8.dp))
    Text("3 vòng × 4 lần chạm. Chạm ô sáng nhanh nhất có thể.", fontSize = 13.sp, color = Dim)
    Spacer(Modifier.height(14.dp))
    repeat(2) { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = if (row == 0) 10.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { col ->
                val i = row * 3 + col
                val on = lit == i && running
                Box(
                    Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (on) Cy.copy(.5f) else Color.White.copy(.04f))
                        .border(if (on) 2.dp else 1.dp, if (on) Cy else Color.White.copy(.08f), RoundedCornerShape(16.dp))
                        .pointerInput(running) {
                            awaitPointerEventScope {
                                while (true) {
                                    val e = awaitPointerEvent()
                                    if (!running || lit < 0 || tap >= rounds * per) continue
                                    val d = e.changes.firstOrNull { it.changedToDown() } ?: continue
                                    if (i == lit) {
                                        times.add(max(0L, d.uptimeMillis - litAt)); lit = -1; tap++
                                        if (tap >= rounds * per) onDone(times.average().roundToInt(), miss)
                                    } else miss++
                                }
                            }
                        }
                )
            }
        }
    }
}
