package com.gamebox.helper

import android.content.pm.ApplicationInfo
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

// ---------- Thành phần dùng riêng cho các trang ----------

@Composable
fun ColumnScope.ScrollCol(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), content = content)

@Composable
fun Ring(frac: Float, color: Color, sz: Dp, text: String) {
    val f = animF(frac.coerceIn(0f, 1f))
    Box(Modifier.size(sz), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val sw = 6.dp.toPx()
            drawArc(Color.White.copy(.1f), 135f, 270f, false, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * f, false, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun MetricTile(name: String, v: String, on: Boolean, mod: Modifier, onClick: () -> Unit) {
    val s = RoundedCornerShape(14.dp)
    val bc by animateColorAsState(if (on) Cy else Color.Transparent, tween(220), label = "mt")
    val bg by animateColorAsState(if (on) Cy.copy(.12f) else Color.White.copy(.06f), tween(220), label = "mtb")
    Column(mod.bounce(onClick = onClick).clip(s).background(bg).border(1.dp, bc, s).padding(12.dp)) {
        Lbl(name)
        Text(v, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun InfoTile(name: String, v: String, mod: Modifier) {
    Column(mod.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.06f)).padding(12.dp)) {
        Lbl(name)
        Text(v, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun SmallBtn(text: String, tint: Color = Cy, onClick: () -> Unit) {
    val s = RoundedCornerShape(12.dp)
    Text(
        text, Modifier.bounce(onClick = onClick).clip(s).border(1.dp, tint.copy(.5f), s).padding(horizontal = 12.dp, vertical = 6.dp),
        color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium
    )
}

// Tiêu đề một nhóm cài đặt, có icon và giá trị hiện tại bên phải
@Composable
fun Sec(ic: Ic, title: String, value: String = "") {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(ic, tint = Cy, sz = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 15.sp)
        if (value.isNotEmpty()) Text(value, color = Cy, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun SwRow(title: String, sub: String?, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = if (enabled) Color.White else Color.White.copy(.35f))
            if (sub != null) Text(sub, fontSize = 12.sp, color = Color.White.copy(if (enabled) .6f else .3f))
        }
        GSwitch(on) { if (enabled) onChange(it) }
    }
}

@Composable
fun BatteryGlyph(lv: Int, plug: Boolean) {
    val f = animF(lv / 100f)
    val pulse = rememberInfiniteTransition(label = "bp").animateFloat(.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bpv")
    val c = if (lv <= 15 && !plug) Bad else Ok
    Box(Modifier.width(64.dp).height(32.dp).border(2.dp, Cy, RoundedCornerShape(8.dp)).padding(3.dp)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(.02f, 1f)).graphicsLayer { alpha = if (plug) pulse.value else 1f }
                .background(c, RoundedCornerShape(5.dp))
        )
        if (plug) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Ico(Ic.Bolt, tint = OnAccent, sz = 18.dp) }
    }
}

// Ước tính thời gian còn lại: đang sạc thì tới đầy, đang xả thì tới hết pin (từ dung lượng suy ra qua bộ đếm điện tích)
fun batEta(b: Bat): String {
    val plug = b.plug != 0
    if (plug && b.lv >= 100) return "Đã đầy"
    if (b.lv <= 0 || b.cnt <= 0L || b.ma < 20) return "Đang tính…"
    val cap = b.cnt * 100.0 / b.lv
    val ua = b.ma * 1000.0
    val h = (if (plug) cap * (100 - b.lv) / 100.0 / ua else b.cnt / ua).coerceIn(0.0, 99.0)
    val hh = h.toInt(); val mm = ((h - hh) * 60).toInt()
    val t = if (hh > 0) "${hh}h ${mm}p" else "${mm}p"
    return if (plug) "Đầy sau ~$t" else "Dùng được ~$t"
}

// ---------- Trang 1: Chọn game ----------

@Composable
fun GamesPage(gs: Gs) {
    val b = gs.bat; val st = gs.st
    Column(Modifier.fillMaxSize()) {
        if (gs.games.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Chưa tìm thấy ứng dụng nào để thêm vào Game Space.", color = Dim) }
        } else {
            LazyRow(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(gs.games, key = { _, a -> a.packageName }) { i, a -> GameCard(gs, a, i) }
            }
        }
        Spacer(Modifier.height(10.dp))
        GCard(gs.hz, Modifier.fillMaxWidth().enter(150)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    Stat("NHIỆT ĐỘ", "${b?.t ?: 0f}°C")
                    Stat("CPU", "${f1((st?.cpu ?: 0) / 1000.0)} GHz")
                    Stat("PING DNS", if (gs.ping < 0) "-" else "${gs.ping} ms")
                    Stat("PIN", "${b?.lv ?: 0}%")
                    Stat("ĐÃ CHƠI", gs.fmtPlay(gs.sel))
                }
                Spacer(Modifier.width(10.dp))
                GhostBtn("Kết cấu", icon = Ic.Layers) { gs.tex = true }
                Spacer(Modifier.width(10.dp))
                PrimaryBtn("Chơi", icon = Ic.Play) { gs.play() }
            }
        }
    }
}

@Composable
private fun GameCard(gs: Gs, a: ApplicationInfo, i: Int) {
    val p = a.packageName
    val on = p == gs.sel
    val ic = gs.icon(p)
    val sh = RoundedCornerShape(26.dp)
    val bc by animateColorAsState(if (on) Cy else Color.White.copy(.15f), tween(250), label = "gcb")
    val bw by animateDpAsState(if (on) 2.dp else 1.dp, tween(250), label = "gcw")
    val sc by animateFloatAsState(if (on) 1f else .95f, spring(dampingRatio = .6f, stiffness = 300f), label = "gcs")
    val tick by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = .45f, stiffness = 500f), label = "gct")
    Box(
        Modifier.enter(i * 60).fillMaxHeight().width(220.dp).bounce { gs.select(p) }
            .graphicsLayer { scaleX = sc; scaleY = sc }.clip(sh).border(bw, bc, sh)
    ) {
        if (ic != null) {
            Image(ic, null, Modifier.fillMaxSize().blur(22.dp), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.75f)))))
            Image(ic, null, Modifier.align(Alignment.Center).size(84.dp).clip(RoundedCornerShape(20.dp)))
        } else Box(Modifier.fillMaxSize().background(Color.White.copy(.08f)))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp).fillMaxWidth(.8f)) {
            Text(gs.pm.getApplicationLabel(a).toString(), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            val t = gs.fmtPlay(p)
            Text(if (gs.custom(p)) "$t  ·  Tuỳ chỉnh" else t, fontSize = 11.sp, color = Color.White.copy(.65f), maxLines = 1)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(14.dp).size(28.dp).graphicsLayer { scaleX = tick; scaleY = tick; alpha = tick.coerceIn(0f, 1f) }
                .clip(CircleShape).background(Cy),
            contentAlignment = Alignment.Center
        ) { Ico(Ic.Check, tint = OnAccent, sz = 18.dp) }
    }
}

// ---------- Trang 2: Hiệu năng ----------

@Composable
fun PerfPage(gs: Gs) {
    var m by remember { mutableIntStateOf(0) }
    val st = gs.st
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GCard(gs.hz, Modifier.weight(1.7f).fillMaxHeight().enter()) {
            val fpsV = st?.fps ?: 0
            val vals = listOf("${st?.cpuP ?: 0}%", "${st?.gpuP ?: 0}%", "${gs.ram}%", if (fpsV > 0) "$fpsV" else "${gs.refresh} Hz")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("CPU", "GPU", "RAM", "FPS").forEachIndexed { i, n -> MetricTile(n, vals[i], m == i, Modifier.weight(1f)) { m = i } }
            }
            Spacer(Modifier.height(12.dp))
            Spark(gs.hist[m], if (m == 3) 144 else 100, Modifier.fillMaxWidth().weight(1f))
            Row(Modifier.fillMaxWidth()) {
                Text("60 GIÂY", Modifier.weight(1f), fontSize = 10.sp, color = Color.White.copy(.5f))
                Text("HIỆN TẠI", fontSize = 10.sp, color = Color.White.copy(.5f))
            }
        }
        GCard(gs.hz, Modifier.weight(1f).fillMaxHeight().enter(80)) {
            val sc = gs.score
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(sc / 100f, if (sc >= 70) Ok else if (sc >= 40) Warn else Bad, 64.dp, "$sc")
                Spacer(Modifier.width(10.dp))
                Column {
                    Lbl("ĐIỂM HIỆU NĂNG")
                    Text("RAM ${f1(gs.used / 1048576.0)} / ${f1(gs.total / 1048576.0)} GB", fontSize = 12.sp, color = Color.White.copy(.7f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GhostBtn("Dọn RAM", icon = Ic.Rocket) { thread { gs.msg = Rt.clean() } }
                Spacer(Modifier.width(10.dp))
                if (gs.msg.isNotEmpty()) Text(gs.msg, fontSize = 12.sp, color = Cy)
            }
            Spacer(Modifier.height(10.dp))
            Lbl("ỨNG DỤNG NỀN NẶNG")
            LazyColumn(Modifier.weight(1f)) {
                items(gs.procs, key = { it.pkg }) { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(gs.label(p.pkg), Modifier.weight(1f), maxLines = 1, fontSize = 14.sp)
                        Text("${p.mb} MB", fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        SmallBtn("Dừng", Bad) { thread { Rt.sh("am force-stop ${p.pkg}") } }
                    }
                }
            }
        }
    }
}

// ---------- Trang 3: Pin ----------

@Composable
fun BatPage(gs: Gs) {
    val b = gs.bat
    val lv = b?.lv ?: 0
    val plug = (b?.plug ?: 0) != 0
    val temps = gs.temps
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GCard(gs.hz, Modifier.weight(1.8f).fillMaxHeight().enter()) {
            ScrollCol {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BatteryGlyph(lv, plug)
                    Spacer(Modifier.width(12.dp))
                    Text("${animInt(lv)}", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Text("%", fontSize = 18.sp, color = Cy)
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (plug) "Đang sạc" else "Không sạc", color = Cy, fontWeight = FontWeight.Medium)
                        if (b != null) Text(batEta(b), fontSize = 12.sp, color = Color.White.copy(.65f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoTile("NHIỆT ĐỘ", "${b?.t ?: 0f}°C", Modifier.weight(1f))
                    InfoTile("ĐIỆN ÁP", "${String.format(Locale.US, "%.2f", (b?.mv ?: 0) / 1000.0)} V", Modifier.weight(1f))
                    InfoTile("DÒNG", "${b?.ma ?: 0} mA", Modifier.weight(1f))
                    InfoTile("TÌNH TRẠNG", if (b?.health == 2) "Tốt" else "Cần chú ý", Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Lbl("NHIỆT ĐỘ PIN")
                Spacer(Modifier.height(8.dp))
                val frac = animF((((b?.t ?: 30f) - 30f) / 20f).coerceIn(.02f, .98f))
                BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
                    Box(
                        Modifier.align(Alignment.Center).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF2E7D32), Color(0xFFFFB300), Color(0xFFE53935))))
                    )
                    Box(Modifier.offset(x = maxWidth * frac - 3.dp).width(6.dp).height(18.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    listOf("30°", "40°", "45°", "50°").forEach { Text(it, Modifier.weight(1f), fontSize = 11.sp, color = Color.White.copy(.55f)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("BAN ĐẦU" to temps.firstOrNull(), "TRUNG BÌNH" to temps.takeIf { it.isNotEmpty() }?.average()?.toFloat(), "CAO NHẤT" to temps.maxOrNull()).forEach { (n, v) ->
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(.06f)).padding(10.dp)) {
                            Text(n, Modifier.weight(1f), fontSize = 11.sp, color = Color.White.copy(.6f))
                            Text("${f1((v ?: 0f).toDouble())}°", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GCard(gs.hz, Modifier.fillMaxWidth().enter(80)) {
                SwRow("Tiết kiệm pin", "Bật chế độ tiết kiệm pin của hệ thống", gs.saver) { gs.saver = it; thread { Rt.saver(it) } }
            }
            GCard(gs.hz, Modifier.fillMaxWidth().enter(160)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ico(Ic.Bolt, tint = if (plug) Ok else Dim, sz = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Sạc", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    if (plug && (b?.ma ?: 0) >= 1800) Text("Sạc nhanh (ước tính)", color = Ok, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    when (b?.plug) { 1 -> "Củ sạc AC, ${b?.ma ?: 0} mA"; 2 -> "Cổng USB, ${b?.ma ?: 0} mA"; 4 -> "Sạc không dây, ${b?.ma ?: 0} mA"; else -> "Không cắm sạc" },
                    fontSize = 13.sp, color = Color.White.copy(.7f)
                )
            }
        }
    }
}

// ---------- Trang 4: Đồ họa ----------

@Composable
fun GfxPage(gs: Gs) {
    val ctx = gs.ctx
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GCard(gs.hz, Modifier.weight(1.8f).fillMaxHeight().enter()) {
            ScrollCol {
                // Tần số quét: chỉ liệt kê các mức mà màn hình máy này hỗ trợ
                val hzOpts = remember(gs.hzs) { listOf(0) + gs.hzs.sorted() }
                val hzSel = hzOpts.indexOf(gs.g("hz", 0)).coerceAtLeast(0)
                Sec(Ic.Refresh, "Tần số quét", if (hzSel == 0) "Mặc định" else "${hzOpts[hzSel]} Hz")
                Seg(hzOpts.map { if (it == 0) "Mặc định" else "$it" }, hzSel, Modifier.fillMaxWidth()) { gs.s("hz", hzOpts[it]) }
                Spacer(Modifier.height(8.dp))
                SwRow("Chế độ màu", "Không khả dụng trên thiết bị này", false, enabled = false) {}
                Sec(Ic.Sun, "Độ bão hòa màu", "${f1(gs.g("sat", 100) / 100.0)}x")
                GSlider(gs.g("sat", 100) / 100f, 0.8f..2f, rainbow = true, mod = Modifier.fillMaxWidth()) { gs.s("sat", (it * 20).roundToInt() * 5) }
                Sec(Ic.Res, "Độ phân giải game", "${gs.g("res", 100)}%")
                GSlider(gs.g("res", 100).toFloat(), 50f..100f, steps = 9, mod = Modifier.fillMaxWidth()) { gs.s("res", (it / 5).roundToInt() * 5) }
                Text("Cần Android 13 trở lên để áp dụng.", fontSize = 11.sp, color = Color.White.copy(.5f))
                Spacer(Modifier.height(6.dp))
                Sec(Ic.Pulse, "Giới hạn FPS")
                Seg(listOf("30", "45", "60", "90", "120", "Tối đa"), gs.g("fps", 5), Modifier.fillMaxWidth()) { gs.s("fps", it) }
                Spacer(Modifier.height(8.dp))
                SwRow("Khử răng cưa 4x MSAA", "Áp dụng sau khi khởi động lại game", gs.g("msaa", 0) == 1) { gs.s("msaa", if (it) 1 else 0) }
                Sec(Ic.Chip, "Driver đồ họa")
                Seg(listOf("Mặc định", "Game Driver", "Vulkan"), gs.g("drv", 0), Modifier.fillMaxWidth()) { gs.s("drv", it) }
                Spacer(Modifier.height(6.dp))
            }
        }
        GCard(gs.hz, Modifier.weight(1f).fillMaxHeight().enter(80)) {
            ScrollCol {
                // Chế độ nhẹ: một công tắc gom các tùy chọn giảm tải cho máy yếu (theo từng game)
                val light = gs.g("res", 100) <= 70 && gs.g("fps", 5) <= 2 && gs.g("msaa", 0) == 0 && gs.g("anim", 0) == 2
                SwRow("Chế độ nhẹ", "Độ phân giải 70%, FPS 60, tắt MSAA và hiệu ứng hệ thống", light) {
                    if (it) { gs.s("res", 70); gs.s("fps", 2); gs.s("msaa", 0); gs.s("anim", 2) }
                    else listOf("res", "fps", "msaa", "anim").forEach { k -> gs.unset(k) }
                }
                Sec(Ic.Game, "Chế độ game")
                Seg(listOf("Chuẩn", "Hiệu năng", "Tiết kiệm"), gs.g("gm", 0), Modifier.fillMaxWidth()) { gs.s("gm", it) }
                Spacer(Modifier.height(6.dp))
                SwRow("Hiệu năng cố định", "Khóa xung nhịp cho ổn định", gs.fixed) { gs.fixed = it; thread { Rt.fixed(it) } }
                SwRow("Chặn thông báo", null, gs.dnd) { gs.putDnd(it) }
                SwRow("Dọn RAM khi mở game", null, gs.cln) { gs.putCln(it) }
                SwRow("Bảng điều khiển trong game", null, gs.ov) { gs.putOv(it); if (!it && Overlay.alive) ctx.startService(android.content.Intent(ctx, Overlay::class.java).putExtra("ui", false)) }
                Sec(Ic.Timer, "Hiệu ứng hệ thống")
                Seg(listOf("Thường", "Nhanh", "Tắt"), gs.g("anim", 0), Modifier.fillMaxWidth()) { gs.s("anim", it) }
                Spacer(Modifier.height(10.dp))
                Sec(Ic.Bulb, "Giao diện")
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Themes.forEachIndexed { i, c ->
                        val ring by animateColorAsState(if (Cy == c) Color.White else Color.Transparent, tween(200), label = "th")
                        Box(Modifier.size(32.dp).bounce { gs.setTheme(i) }.clip(CircleShape).background(c).border(2.dp, ring, CircleShape))
                    }
                }
                SwRow("Nền động", "Đốm sáng trôi chậm phía sau", gs.live) { gs.putLive(it) }
                Text("Mọi cài đặt được khôi phục khi bạn thoát game (nút nguồn bên trái).", fontSize = 11.sp, color = Color.White.copy(.5f))
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
