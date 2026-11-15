package com.gamebox.helper

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

// Game Space (giao diện ngang): chọn game, hiệu năng, pin, đồ họa
class MainActivity : ComponentActivity() {
    private var gs: Gs? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Crash.pending(this)) { setContentView(Crash.view(this) { recreate() }); return }
        // Chỉ bản debug: cho smoke test CI bật overlay trực tiếp (Overlay không được export)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 && intent.getBooleanExtra("smoke_overlay", false)) {
            startForegroundService(Intent(this, Overlay::class.java))
        }
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val sp = getSharedPreferences("gs", Context.MODE_PRIVATE)
        Cy = Themes[sp.getInt("theme", 0).coerceIn(0, Themes.lastIndex)]
        val g = Gs(this, sp)
        g.page = intent.getIntExtra("page", 0).coerceIn(0, 3)
        gs = g
        setContent { GameSpaceApp(g) }
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        gs?.let { it.page = i.getIntExtra("page", it.page).coerceIn(0, 3) }
    }
}

// Trạng thái dùng chung của Game Space, các trang chỉ đọc/ghi qua đây
class Gs(val ctx: Context, val sp: SharedPreferences) {
    val pm: PackageManager = ctx.packageManager
    val hz = HazeState()
    var page by mutableIntStateOf(0)
    var tex by mutableStateOf(false)
    var menu by mutableStateOf(false)

    val games: List<ApplicationInfo> = run {
        val all = pm.getInstalledApplications(0).filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != ctx.packageName }
        val g = all.filter { it.category == ApplicationInfo.CATEGORY_GAME || (it.flags and ApplicationInfo.FLAG_IS_GAME) != 0 }
        g.ifEmpty { all }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
    }
    var sel by mutableStateOf(sp.getString("sel", null) ?: games.firstOrNull()?.packageName ?: "")
    val cfg = mutableStateMapOf<String, Int>()

    var dnd by mutableStateOf(sp.getBoolean("dnd", true))
    var cln by mutableStateOf(sp.getBoolean("cln", true))
    var ov by mutableStateOf(sp.getBoolean("ov", true))
    var live by mutableStateOf(sp.getBoolean("live", true))
    var fixed by mutableStateOf(false)
    var saver by mutableStateOf(false)

    var be by mutableStateOf(Rt.backend)
    var st by mutableStateOf<St?>(null)
    var ram by mutableIntStateOf(0)
    var used by mutableLongStateOf(0L)
    var total by mutableLongStateOf(1L)
    var bat by mutableStateOf<Bat?>(null)
    var ping by mutableIntStateOf(-1)
    var clock by mutableStateOf("")
    var msg by mutableStateOf("")
    var procs by mutableStateOf(listOf<Proc>())
    val hist = List(4) { mutableStateListOf<Int>() }
    val temps = mutableStateListOf<Float>()
    val playMs = mutableStateMapOf<String, Long>()

    private val disp = ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    val hzs: Set<Int> = runCatching { disp.supportedModes.map { it.refreshRate.roundToInt() }.toSet() }.getOrDefault(setOf(60))
    val refresh: Int get() = runCatching { disp.refreshRate.roundToInt() }.getOrDefault(60)

    private val icons = HashMap<String, ImageBitmap>()

    fun g(k: String, d: Int) = cfg["${k}_$sel"] ?: sp.getInt("${k}_$sel", d)
    fun s(k: String, v: Int) { cfg["${k}_$sel"] = v; sp.edit().putInt("${k}_$sel", v).apply() }
    fun reset() {
        listOf("hz", "sat", "res", "fps", "gm", "msaa", "drv", "anim").forEach { sp.edit().remove("${it}_$sel").apply(); cfg.remove("${it}_$sel") }
    }
    fun custom(p: String): Boolean =
        cfg.keys.any { it.endsWith("_$p") } || listOf("hz", "sat", "res", "fps", "gm", "msaa", "drv", "anim").any { sp.contains("${it}_$p") }

    fun select(p: String) { sel = p; sp.edit().putString("sel", p).apply() }
    fun putDnd(v: Boolean) { dnd = v; sp.edit().putBoolean("dnd", v).apply() }
    fun putCln(v: Boolean) { cln = v; sp.edit().putBoolean("cln", v).apply() }
    fun putOv(v: Boolean) { ov = v; sp.edit().putBoolean("ov", v).apply() }
    fun putLive(v: Boolean) { live = v; sp.edit().putBoolean("live", v).apply() }
    fun setTheme(i: Int) { Cy = Themes[i]; sp.edit().putInt("theme", i).apply() }

    fun label(p: String) = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p)
    val selName: String get() = games.firstOrNull { it.packageName == sel }?.let { pm.getApplicationLabel(it).toString() } ?: "Chơi"
    fun icon(p: String): ImageBitmap? = icons[p] ?: runCatching { pm.getApplicationIcon(p).toBitmap(192, 192).asImageBitmap() }.getOrNull()?.also { icons[p] = it }

    // Điểm hiệu năng 0..100 từ RAM, CPU và nhiệt độ
    val score: Int get() =
        (100 - (ram * 0.35 + (st?.cpuP ?: 0) * 0.35 + ((bat?.t ?: 30f) - 30f).coerceAtLeast(0f) * 1.5)).toInt().coerceIn(0, 100)

    fun sample() {
        val x = Rt.stats(); st = x
        val (t, a) = Rt.mem(); total = t; used = t - a; ram = (100 - a * 100 / t).toInt()
        val b = readBat(ctx); bat = b
        clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        listOf(x.cpuP, x.gpuP, ram, x.fps).forEachIndexed { i, v -> hist[i].add(v); if (hist[i].size > 60) hist[i].removeAt(0) }
        temps.add(b.t); if (temps.size > 600) temps.removeAt(0)
    }

    // Thời gian chơi cần quyền truy cập dữ liệu sử dụng, Rt.grant cấp bằng root/Shizuku
    fun loadPlaytime() {
        runCatching {
            val um = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            um.queryAndAggregateUsageStats(now - 365L * 86400000L, now).forEach { (k, v) -> playMs[k] = v.totalTimeInForeground }
        }
    }

    fun fmtPlay(p: String): String {
        val ms = playMs[p] ?: 0L
        return if (ms >= 3600000L) "${ms / 3600000L}h ${(ms / 60000L) % 60}p" else "${ms / 60000L}p"
    }

    fun play() {
        val p = sel; if (p.isEmpty()) return
        Rt.pkg = p
        val gm = g("gm", 0); val res = g("res", 100); val fps = listOf(30, 45, 60, 90, 120, 0)[g("fps", 5)]
        val hzv = g("hz", 0).let { if (it < 30) 0 else it }; val sat = g("sat", 100) / 100f
        val an = g("anim", 0); val ms = g("msaa", 0) == 1; val dv = g("drv", 0)
        thread { Rt.applyGame(p, gm, res, fps, hzv, sat); Rt.mode(gm); Rt.anim(an); Rt.msaa(ms); Rt.driver(dv); if (fixed) Rt.fixed(true); if (dnd) Rt.dnd(true); if (cln) Rt.clean() }
        fun launch(withOverlay: Boolean) {
            if (withOverlay) ctx.startForegroundService(Intent(ctx, Overlay::class.java))
            pm.getLaunchIntentForPackage(p)?.let { ctx.startActivity(it) }
        }
        if (!ov || Settings.canDrawOverlays(ctx)) launch(ov)
        else thread {
            // Thử cấp quyền vẽ đè bằng root/Shizuku; nếu vẫn thiếu thì mở trang cấp quyền, không mở game
            Rt.grant(ctx.packageName)
            Handler(Looper.getMainLooper()).post {
                if (Settings.canDrawOverlays(ctx)) launch(true)
                else {
                    Toast.makeText(ctx, "Hãy bật \"Hiển thị trên ứng dụng khác\" cho Game Box rồi bấm Chơi lại", Toast.LENGTH_LONG).show()
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                }
            }
        }
    }

    fun power() {
        val p = Rt.pkg.ifEmpty { sel }; Rt.pkg = ""
        ctx.stopService(Intent(ctx, Overlay::class.java))
        thread { Rt.restore(p); Rt.mode(0); Rt.fixed(false); Rt.dnd(false) }
    }
}

private val Titles = listOf("Chọn game", "Hiệu năng", "Pin", "Đồ họa")

@Composable
fun GameSpaceApp(gs: Gs) {
    val ctx = gs.ctx
    BackHandler(enabled = gs.tex) { gs.tex = false }

    LaunchedEffect(Unit) {
        gs.be = withContext(Dispatchers.IO) { val b = Rt.init(); Rt.grant(ctx.packageName); b }
        withContext(Dispatchers.IO) { gs.loadPlaytime() }
    }
    LaunchedEffect(Unit) { while (true) { withContext(Dispatchers.IO) { gs.sample() }; delay(1000) } }
    LaunchedEffect(Unit) { while (true) { gs.ping = withContext(Dispatchers.IO) { Rt.ping() }; delay(10000) } }
    LaunchedEffect(gs.page) { while (gs.page == 1) { gs.procs = withContext(Dispatchers.IO) { Rt.heavy() }; delay(8000) } }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cy, background = Bg, surface = Bg, onBackground = Color.White, onSurface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
            Box(Modifier.fillMaxSize()) {
                LiveBackdrop(gs.hz, gs.live)
                Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Rail(gs.page, { gs.page = it }, { gs.power() })
                    Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 18.dp, vertical = 10.dp)) {
                        GsHeader(gs)
                        Spacer(Modifier.height(10.dp))
                        AnimatedContent(
                            gs.page, Modifier.weight(1f).fillMaxWidth(), label = "page",
                            transitionSpec = {
                                val dir = if (targetState > initialState) 1 else -1
                                (fadeIn(tween(280)) + slideInHorizontally(tween(280)) { dir * it / 10 }) togetherWith fadeOut(tween(140))
                            }
                        ) { p ->
                            when (p) {
                                0 -> GamesPage(gs)
                                1 -> PerfPage(gs)
                                2 -> BatPage(gs)
                                else -> GfxPage(gs)
                            }
                        }
                    }
                }
                AnimatedVisibility(gs.tex, enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = .96f), exit = fadeOut(tween(160)) + scaleOut(tween(200), targetScale = .96f)) {
                    TexScreen(gs)
                }
            }
        }
    }
}

@Composable
fun GsHeader(gs: Gs) {
    val b = gs.bat
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("GAME BOX  ·  GAME SPACE", fontSize = 11.sp, letterSpacing = 3.sp, color = Cy)
            AnimatedContent(gs.page, label = "title", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { p ->
                Text(Titles[p], fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        when (gs.page) {
            1 -> {
                val t = b?.t ?: 0f
                val c = if (t >= 45f) Bad else if (t >= 40f) Warn else Ok
                Pill(tint = c) { Ico(Ic.Thermo, tint = c, sz = 18.dp); Text("${t.roundToInt()}°C  ${if (t >= 45f) "Nóng" else if (t >= 40f) "Ấm" else "Bình thường"}", color = c, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
            }
            2 -> if ((b?.plug ?: 0) != 0) Pill(tint = Ok) { Ico(Ic.Bolt, tint = Ok, sz = 18.dp); Text("Đang sạc", color = Ok, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
            3 -> {
                Box {
                    Pill(Modifier.bounce { gs.menu = true }) {
                        gs.icon(gs.sel)?.let { androidx.compose.foundation.Image(it, null, Modifier.size(22.dp)) }
                        Column { Text(gs.selName, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1); Text("Cài đặt theo từng game", fontSize = 10.sp, color = Dim, maxLines = 1) }
                        Ico(Ic.Down, sz = 16.dp)
                    }
                    DropdownMenu(gs.menu, { gs.menu = false }) {
                        gs.games.forEach { a -> DropdownMenuItem({ Text(gs.pm.getApplicationLabel(a).toString()) }, { gs.select(a.packageName); gs.menu = false }) }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Pill(Modifier.bounce { gs.reset() }) { Ico(Ic.Reset, sz = 18.dp); Text("Đặt lại mặc định", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
        }
        Spacer(Modifier.width(8.dp))
        val bc = if (gs.be == "none") Bad else Ok
        Pill(Modifier.bounce { thread { gs.be = Rt.init(); Rt.grant(gs.ctx.packageName) } }, tint = bc) {
            Text(when (gs.be) { "root" -> "Root"; "shizuku" -> "Shizuku"; else -> "Chưa có quyền" }, color = bc, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(12.dp))
        Text(gs.clock, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
