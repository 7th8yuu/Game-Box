package com.gamebox.helper

import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.concurrent.thread

// Ứng dụng chính (màn hình dọc): Hồ sơ, Trang chủ, Tối ưu game. Game Space ngang mở từ Trang chủ.
class HubActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Crash.pending(this)) { setContentView(Crash.view(this) { recreate() }); return }
        enableEdgeToEdge(SystemBarStyle.dark(AColor.TRANSPARENT), SystemBarStyle.dark(AColor.TRANSPARENT))
        val sp = getSharedPreferences("gs", Context.MODE_PRIVATE)
        Cy = Themes[sp.getInt("theme", 0).coerceIn(0, Themes.lastIndex)]
        val g = Gs(this, sp)
        setContent { HubApp(g) }
    }
}

// Mức quyền 0..3: bit 2 = Root, bit 1 = Shizuku
fun accessLevel(gs: Gs): Int = (if (gs.rootOk) 2 else 0) + (if (gs.shz == 2) 1 else 0)

fun accessName(gs: Gs): String = when {
    gs.rootOk && gs.shz == 2 -> "Đầy đủ"
    gs.rootOk || gs.shz == 2 -> "Một phần"
    else -> "Chưa có"
}

fun accessDetail(gs: Gs): String = when {
    gs.rootOk && gs.shz == 2 -> "Đang bật · Root · Shizuku"
    gs.rootOk -> "Đang bật · Root"
    gs.shz == 2 -> "Đang bật · Shizuku"
    else -> "Chưa cấp quyền"
}

// Khối kính tối giản dùng cho danh sách và ô thông tin
@Composable
fun Panel(mod: Modifier = Modifier, r: Dp = 22.dp, pad: Dp = 16.dp, tint: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    val s = RoundedCornerShape(r)
    Column(mod.clip(s).background(tint.copy(.04f)).border(1.dp, tint.copy(.1f), s).padding(pad), content = content)
}

@Composable
fun HLine(mod: Modifier = Modifier) { Box(mod.fillMaxWidth().height(1.dp).background(Color.White.copy(.1f))) }

@Composable
fun CircleBtn(ic: Ic, sz: Dp = 44.dp, mod: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        mod.size(sz).bounce(onClick = onClick).clip(CircleShape).background(Color.White.copy(.06f)).border(1.dp, Color.White.copy(.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) { Ico(ic, sz = sz * .5f) }
}

@Composable
fun ArrowBtn(mod: Modifier = Modifier) {
    Box(mod.size(46.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Ico(Ic.Back, Modifier.rotate(180f), Color.Black, 22.dp)
    }
}

@Composable
fun Bar(frac: Float, mod: Modifier = Modifier, color: Color = Cy) {
    val f = animF(frac.coerceIn(0f, 1f))
    Box(mod.height(5.dp).clip(CircleShape).background(Color.White.copy(.1f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(color))
    }
}

// Ảnh đại diện: vòng gradient, nét đứt quay chậm và icon người dùng
@Composable
fun Avatar(sz: Dp) {
    val r = rememberInfiniteTransition(label = "av").animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "avr")
    Box(Modifier.size(sz), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Cy.copy(.35f), Color.Transparent), center, size.minDimension / 2), size.minDimension / 2)
            drawCircle(
                Brush.sweepGradient(listOf(Cy, Color.Transparent, Cy.copy(.5f), Color.Transparent, Cy)),
                size.minDimension / 2 - 4.dp.toPx(), style = Stroke(3.dp.toPx())
            )
        }
        Box(Modifier.fillMaxSize(.8f).clip(CircleShape).background(Color(0xFF070D12)))
        Canvas(Modifier.fillMaxSize(.66f).graphicsLayer { rotationZ = r.value }) {
            drawCircle(Cy.copy(.55f), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))))
        }
        Ico(Ic.User, tint = Cy, sz = sz * .3f)
    }
}

// Thanh điều hướng nổi: mục đang chọn mở rộng ra kèm nhãn
@Composable
fun HubNav(sel: Int, set: (Int) -> Unit, mod: Modifier = Modifier) {
    val items = listOf(Ic.Grid to "Hồ sơ", Ic.Home to "Trang chủ", Ic.Touch to "Game")
    val s = RoundedCornerShape(32.dp)
    Row(
        mod.clip(s).background(Color(0xFF0B141C).copy(.94f)).border(1.dp, Color.White.copy(.12f), s).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (ic, lb) ->
            val on = i == sel
            val bg by animateColorAsState(if (on) Cy.copy(.16f) else Color.Transparent, tween(250), label = "nvb")
            val c by animateColorAsState(if (on) Cy else Color.White.copy(.7f), tween(250), label = "nvc")
            Row(
                Modifier.bounce { set(i) }.clip(RoundedCornerShape(26.dp)).background(bg).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Ico(ic, tint = c, sz = 24.dp)
                AnimatedVisibility(on, enter = fadeIn(tween(200)) + expandHorizontally(tween(250)), exit = fadeOut(tween(120)) + shrinkHorizontally(tween(200))) {
                    Text(lb, Modifier.padding(start = 8.dp), color = c, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun HubApp(gs: Gs) {
    GsEffects(gs)
    var tab by remember { mutableIntStateOf(1) }
    var act by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    BackHandler(enabled = act) { act = false }
    MaterialTheme(colorScheme = darkColorScheme(primary = Cy, background = Bg, surface = Bg, onBackground = Color.White, onSurface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
            Box(Modifier.fillMaxSize()) {
                LiveBackdrop(gs.hz, gs.live)
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    AnimatedContent(
                        tab, Modifier.fillMaxSize(), label = "tab",
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { dir * it / 8 }) togetherWith fadeOut(tween(120))
                        }
                    ) { t ->
                        when (t) {
                            0 -> ProfilePage(gs, { act = true }, { settings = true })
                            1 -> HomePage(gs, { tab = 0 })
                            else -> OptPage(gs)
                        }
                    }
                }
                HubNav(tab, { tab = it }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp))
                AnimatedVisibility(
                    act, enter = slideInVertically(tween(300)) { it / 3 } + fadeIn(tween(260)),
                    exit = slideOutVertically(tween(240)) { it / 3 } + fadeOut(tween(200))
                ) { ActivationCenter(gs) { act = false } }
                if (settings) HubSettings(gs) { settings = false }
            }
        }
    }
}

@Composable
fun ModeCard(title: String, sub: String, ic: Ic, on: Boolean, mod: Modifier, onClick: () -> Unit) {
    val s = RoundedCornerShape(22.dp)
    val bc by animateColorAsState(if (on) Cy.copy(.7f) else Color.White.copy(.1f), tween(220), label = "mcb")
    val bg by animateColorAsState(if (on) Cy.copy(.1f) else Color.White.copy(.03f), tween(220), label = "mcg")
    Column(mod.bounce(onClick = onClick).clip(s).background(bg).border(1.dp, bc, s).padding(16.dp)) {
        Ico(ic, tint = Cy, sz = 24.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(sub, fontSize = 12.sp, color = Dim, maxLines = 1)
    }
}

@Composable
fun PermCard(title: String, ic: Ic, ok: Boolean, status: String, btn: String, mod: Modifier, onClick: () -> Unit) {
    Panel(mod, pad = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.06f)).border(1.dp, Cy.copy(.3f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Ico(ic, tint = Cy, sz = 24.dp)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (ok) Cy else Color.White.copy(.3f)))
            Spacer(Modifier.width(6.dp))
            Text(if (ok) "Đã cấp" else "Chưa cấp", fontSize = 12.sp, color = if (ok) Cy else Dim, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(status, fontSize = 12.sp, color = Dim, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        if (ok) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cy.copy(.3f)).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(btn, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White.copy(.85f))
            }
        } else {
            PrimaryBtn(btn, Modifier.fillMaxWidth(), onClick = onClick)
        }
    }
}

// Trung tâm kích hoạt: tình trạng Root / Shizuku và cách cấp quyền
@Composable
fun ActivationCenter(gs: Gs, close: () -> Unit) {
    val ctx = gs.ctx
    var mode by remember { mutableIntStateOf(0) }
    var guide by remember { mutableStateOf(false) }
    val lv = accessLevel(gs)
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { gs.refreshAccess() }; kotlinx.coroutines.delay(2000) } }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0C1824), Bg))).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Trung tâm kích hoạt", Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                CircleBtn(Ic.Close, 48.dp, onClick = close)
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Brush.radialGradient(listOf(Cy.copy(.3f), Color.Transparent), center, size.minDimension / 2), size.minDimension / 2)
                        drawCircle(Brush.sweepGradient(listOf(Cy, Color.Transparent, Cy.copy(.4f), Color.Transparent, Cy)), size.minDimension / 2 - 4.dp.toPx(), style = Stroke(2.5.dp.toPx()))
                    }
                    Box(Modifier.fillMaxSize(.82f).clip(CircleShape).background(Color(0xFF070D12)), contentAlignment = Alignment.Center) { Ico(Ic.Phone, tint = Cy, sz = 34.dp) }
                }
                Spacer(Modifier.width(18.dp))
                Column {
                    Text("TRẠNG THÁI QUYỀN", fontSize = 11.sp, letterSpacing = 3.sp, color = Dim)
                    Text(accessName(gs), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(20.dp))
            Panel(Modifier.fillMaxWidth().enter(), r = 22.dp, tint = Cy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = when { lv == 0 -> "CHƯA BẬT"; gs.rootOk -> "ĐANG BẬT · ROOT"; else -> "ĐANG BẬT · SHIZUKU" }
                    Row(Modifier.clip(CircleShape).background(Cy.copy(.12f)).border(1.dp, Cy.copy(.5f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (lv == 0) Bad else Cy))
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = Cy, maxLines = 1)
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Phiên truy cập", fontSize = 12.sp, color = Dim)
                        Text(gs.fmtUp(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(if (lv == 0) "Chưa kết nối · hãy cấp Root hoặc Shizuku" else "Đã kết nối · mọi tính năng sẵn sàng", fontSize = 13.sp, color = Dim)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().enter(60), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModeCard("Nhanh", "Root · Shizuku", Ic.Bolt, mode == 0, Modifier.weight(1f)) {
                    mode = 0; thread { Rt.init(); Rt.grant(ctx.packageName); gs.refreshAccess() }
                }
                ModeCard("Ghép nối thủ công", "TCP/IP · Không dây · OTG", Ic.Tune, mode == 1, Modifier.weight(1f)) { mode = 1; guide = true }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().enter(120), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PermCard("Quyền Root", Ic.Hash, gs.rootOk, if (gs.rootOk) "Đang bật" else "Chưa bật", if (gs.rootOk) "Đang bật" else "Cấp quyền", Modifier.weight(1f)) {
                    thread { runCatching { com.topjohnwu.superuser.Shell.getCachedShell()?.close() }; Rt.init(); Rt.grant(ctx.packageName); gs.refreshAccess() }
                }
                PermCard(
                    "Quyền Shizuku", Ic.Link, gs.shz == 2,
                    when (gs.shz) { 2 -> "Đang bật"; 1 -> "Chờ cấp quyền"; else -> "Chưa chạy" },
                    when (gs.shz) { 2 -> "Đang bật"; 1 -> "Cấp quyền"; else -> "Mở Shizuku" }, Modifier.weight(1f)
                ) {
                    if (gs.shz == 0) {
                        val i = gs.pm.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        if (i != null) ctx.startActivity(i) else Toast.makeText(ctx, "Chưa cài Shizuku. Hãy cài Shizuku rồi mở lại.", Toast.LENGTH_LONG).show()
                    } else thread { Rt.init(); gs.refreshAccess() }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
        if (guide) PairGuide(gs) { guide = false }
    }
}

// Hướng dẫn ghép nối thủ công: Android không cho app tự bật gỡ lỗi không dây, nên chỉ hướng dẫn và mở đúng màn hình cài đặt
@Composable
fun PairGuide(gs: Gs, close: () -> Unit) {
    val ctx = gs.ctx
    Dialog(close, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(.92f).clip(RoundedCornerShape(26.dp)).background(Color(0xFF0E1822)).border(1.dp, Color.White.copy(.12f), RoundedCornerShape(26.dp)).padding(20.dp)) {
            Text("Ghép nối thủ công", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            listOf(
                "Bật Tùy chọn nhà phát triển, rồi bật Gỡ lỗi không dây.",
                "Mở Shizuku, chọn Ghép nối và nhập mã ghép nối hiện trên màn hình.",
                "Bấm Khởi động trong Shizuku, rồi quay lại Game Box bấm Quyền Shizuku > Cấp quyền."
            ).forEachIndexed { i, t ->
                Row(Modifier.padding(vertical = 5.dp)) {
                    Text("${i + 1}", Modifier.width(24.dp), color = Cy, fontWeight = FontWeight.Bold)
                    Text(t, fontSize = 14.sp, color = Color.White.copy(.85f))
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostBtn("Cài đặt nhà phát triển", Modifier.weight(1f)) {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                PrimaryBtn("Xong", onClick = close)
            }
        }
    }
}

// Cài đặt giao diện của Hub (nút bánh răng ở Hồ sơ)
@Composable
fun HubSettings(gs: Gs, close: () -> Unit) {
    Dialog(close, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(.92f).clip(RoundedCornerShape(26.dp)).background(Color(0xFF0E1822)).border(1.dp, Color.White.copy(.12f), RoundedCornerShape(26.dp)).padding(20.dp)) {
            Text("Cài đặt", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Sec(Ic.Bulb, "Màu giao diện")
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Themes.forEachIndexed { i, c ->
                    val ring by animateColorAsState(if (Cy == c) Color.White else Color.Transparent, tween(200), label = "hth")
                    Box(Modifier.size(34.dp).bounce { gs.setTheme(i) }.clip(CircleShape).background(c).border(2.dp, ring, CircleShape))
                }
            }
            SwRow("Nền động", "Đốm sáng trôi chậm phía sau", gs.live) { gs.putLive(it) }
            SwRow("Bảng điều khiển trong game", "Hiện nút nổi khi chơi từ Game Box", gs.ov) { gs.putOv(it) }
            Spacer(Modifier.height(8.dp))
            PrimaryBtn("Xong", Modifier.fillMaxWidth(), onClick = close)
        }
    }
}

// Chọn game đưa vào thư viện
@Composable
fun AppPicker(gs: Gs, close: () -> Unit) {
    Dialog(close, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(.92f).fillMaxHeight(.8f).clip(RoundedCornerShape(26.dp)).background(Color(0xFF0E1822)).border(1.dp, Color.White.copy(.12f), RoundedCornerShape(26.dp)).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Thư viện game", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${gs.games.size} đã chọn", fontSize = 12.sp, color = Dim)
            }
            Spacer(Modifier.height(4.dp))
            Text("Bật những ứng dụng bạn muốn hiện trong Game Box.", fontSize = 12.sp, color = Dim)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(gs.allApps, key = { it.packageName }) { a ->
                    val p = a.packageName
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val ic = gs.icon(p)
                        if (ic != null) androidx.compose.foundation.Image(ic, null, Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)))
                        else Box(Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(gs.pm.getApplicationLabel(a).toString(), Modifier.weight(1f), maxLines = 1, fontSize = 15.sp)
                        GSwitch(gs.inLib(p)) { gs.toggleLib(p) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PrimaryBtn("Xong", Modifier.fillMaxWidth(), onClick = close)
        }
    }
}
