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
        android.util.Log.i("GB_CREATE", "GameSpace")
        if (Crash.pending(this)) { setContentView(Crash.view(this) { recreate() }); return }
        // Chỉ bản debug: cho smoke test CI bật overlay trực tiếp (Overlay không được export)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 && intent.getBooleanExtra("smoke_overlay", false)) {
            // ov_dock / ov_open cho phép chụp từng trạng thái của overlay (0 nổi, 1 trái, 2 phải)
            val oi = Intent(this, Overlay::class.java)
            if (intent.hasExtra("ov_dock")) oi.putExtra("dock", intent.getIntExtra("ov_dock", 0))
            if (intent.hasExtra("ov_open")) oi.putExtra("open", intent.getBooleanExtra("ov_open", false))
            startForegroundService(oi)
        }
        enableEdgeToEdge()
        cap60(this)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val sp = getSharedPreferences("gs", Context.MODE_PRIVATE)
        Cy = Themes[sp.getInt("theme", 0).coerceIn(0, Themes.lastIndex)]
        val g = Gs(this, sp)
        g.page = intent.getIntExtra("page", 0).coerceIn(0, 3)
        intent.getStringExtra("game")?.let { g.select(it) }
        // Chỉ bản debug: smoke test CI mở thẳng màn hình Kết cấu để chụp
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 && intent.getBooleanExtra("tex", false)) g.tex = true
        gs = g
        setContent { GameSpaceApp(g) }
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        gs?.let { it.page = i.getIntExtra("page", it.page).coerceIn(0, 3); i.getStringExtra("game")?.let { p -> it.select(p) } }
    }
}

// Hồ sơ Mapping: ảnh chụp cài đặt đồ họa của một game, lưu trong SharedPreferences, chia sẻ qua clipboard
class Prof(val name: String, val pkg: String, val cfg: Map<String, Int>)

// Trạng thái dùng chung của Game Space, các trang chỉ đọc/ghi qua đây
class Gs(val ctx: Context, val sp: SharedPreferences) {
    val pm: PackageManager = ctx.packageManager
    val hz = HazeState()
    var page by mutableIntStateOf(0)
    var tex by mutableStateOf(false)
    val lab = LabState()
    var labKind by mutableIntStateOf(-1) // bài kiểm tra cảm ứng đang mở toàn màn hình (-1 = không)
    var menu by mutableStateOf(false)
    var diagOpen by mutableStateOf(false) // hộp Chẩn đoán đang mở
    var gfxOpen by mutableStateOf(false)  // Phòng thử đồ họa đang mở

    // Mọi ứng dụng mở được, và danh sách game tự nhận diện (dùng khi người dùng chưa tự chọn thư viện)
    val allApps: List<ApplicationInfo> = pm.getInstalledApplications(0)
        .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != ctx.packageName }
        .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
    private val auto: List<ApplicationInfo> = allApps
        .filter { it.category == ApplicationInfo.CATEGORY_GAME || (it.flags and ApplicationInfo.FLAG_IS_GAME) != 0 }
        .ifEmpty { allApps }
    // Thư viện game do người dùng chọn (Trang chủ > Thêm). Trống thì dùng danh sách tự nhận diện
    val lib = mutableStateListOf<String>().apply {
        addAll((sp.getString("lib", "") ?: "").split(",").filter { it.isNotEmpty() && runCatching { pm.getApplicationInfo(it, 0) }.isSuccess })
    }
    val games: List<ApplicationInfo>
        get() = if (lib.isEmpty()) auto else lib.mapNotNull { runCatching { pm.getApplicationInfo(it, 0) }.getOrNull() }
    fun inLib(p: String) = if (lib.isEmpty()) auto.any { it.packageName == p } else p in lib
    fun toggleLib(p: String) {
        if (lib.isEmpty()) lib.addAll(auto.map { it.packageName })
        if (p in lib) lib.remove(p) else lib.add(p)
        sp.edit().putString("lib", lib.joinToString(",")).apply()
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
    val lastMs = mutableStateMapOf<String, Long>()
    var disk by mutableIntStateOf(0)
    var rootOk by mutableStateOf(false)
    var shz by mutableIntStateOf(0)
    var upSec by mutableLongStateOf(0L)

    // Mã người chơi hiển thị ở Trang chủ/Hồ sơ: băm từ ANDROID_ID, không gửi đi đâu
    val pid: String = runCatching {
        val id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "gamebox"
        java.util.UUID.nameUUIDFromBytes(id.toByteArray()).toString().replace("-", "").take(8).uppercase()
    }.getOrDefault("00000000")
    val version: String = runCatching { pm.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0" }.getOrDefault("1.0")
    val model: String = android.os.Build.MODEL

    private val disp = ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    val hzs: Set<Int> = runCatching { disp.supportedModes.map { it.refreshRate.roundToInt() }.toSet() }.getOrDefault(setOf(60))
    val refresh: Int get() = runCatching { disp.refreshRate.roundToInt() }.getOrDefault(60)

    private val icons = HashMap<String, ImageBitmap>()
    private val softs = HashMap<String, ImageBitmap>()

    init {
        GlassOn = sp.getBoolean("glass", defaultGlass())
        // Phiên trước còn dang dở (tiến trình app bị tắt giữa chừng): trả lại cài đặt để game mở ngoài app chạy bình thường.
        // Chỉ kiểm tra một lần cho mỗi tiến trình. Gs được tạo lại mỗi khi Activity được tạo lại; nếu kiểm tra ở mỗi lần thì
        // phiên vừa áp (ví dụ vừa hạ độ phân giải, Activity bị tạo lại) sẽ bị coi là dang dở và hoàn tác ngay.
        if (!checked) {
            checked = true
            val pend = sp.getString("sess", "") ?: ""
            if ((pend.isNotEmpty() || sp.getBoolean("wm_on", false) || sp.getBoolean("ang_on", false)) && !Overlay.alive) {
                sp.edit().remove("sess").apply()
                thread { Rt.init(); Rt.restore(pend); Wm.restore(sp, "app mở lại, còn phiên dang dở"); Rt.mode(0); Rt.dnd(false); Rt.fixed(false) }
            }
        }
    }

    companion object { @Volatile private var checked = false }

    // Luôn đọc từ SharedPreferences (dùng chung cho mọi Activity). Trước đây ưu tiên bộ nhớ đệm cfg của riêng từng Gs, nên giá trị
    // lưu ở màn hình này không tới được Gs của màn hình kia (ví dụ lưu mức giảm ở Game Space, bấm Chơi ở Trang chủ vẫn đọc giá trị cũ).
    // cfg chỉ còn để Compose biết khi nào cần vẽ lại.
    fun g(k: String, d: Int): Int { cfg["${k}_$sel"]; return sp.getInt("${k}_$sel", d) }
    fun s(k: String, v: Int) { cfg["${k}_$sel"] = v; sp.edit().putInt("${k}_$sel", v).apply() }
    fun unset(k: String) { sp.edit().remove("${k}_$sel").apply(); cfg.remove("${k}_$sel") }
    fun reset() { listOf("hz", "sat", "res", "fps", "gm", "msaa", "drv", "anim", "texl", "texd", "texb", "texm", "rnd").forEach { unset(it) } }
    fun custom(p: String): Boolean =
        listOf("hz", "sat", "res", "fps", "gm", "msaa", "drv", "anim", "texl", "texd", "texb", "texm", "rnd").any { sp.contains("${it}_$p") }

    val profiles = mutableStateListOf<Prof>().apply {
        runCatching {
            val a = org.json.JSONArray(sp.getString("profiles", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i); val c = o.getJSONObject("c")
                add(Prof(o.getString("n"), o.getString("p"), c.keys().asSequence().associateWith { c.getInt(it) }))
            }
        }
    }
    private fun saveProfiles() {
        val a = org.json.JSONArray()
        profiles.forEach { a.put(org.json.JSONObject().put("n", it.name).put("p", it.pkg).put("c", org.json.JSONObject(it.cfg))) }
        sp.edit().putString("profiles", a.toString()).apply()
    }
    fun addProfile() {
        val keys = mapOf("hz" to 0, "sat" to 100, "res" to 100, "fps" to 5, "gm" to 0, "msaa" to 0, "drv" to 0, "anim" to 0, "texl" to 0, "texd" to 0, "texb" to 0, "texm" to 0, "rnd" to 0)
        val stamp = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        profiles.add(0, Prof("$selName · $stamp", sel, keys.mapValues { (k, d) -> g(k, d) }))
        saveProfiles()
    }
    fun applyProfile(p: Prof) { select(p.pkg); p.cfg.forEach { (k, v) -> s(k, v) } }
    fun removeProfile(p: Prof) { profiles.remove(p); saveProfiles() }
    fun profileJson(p: Prof): String =
        org.json.JSONObject().put("app", "GameBox").put("n", p.name).put("p", p.pkg).put("c", org.json.JSONObject(p.cfg)).toString()
    fun importProfile(t: String): Boolean = runCatching {
        val o = org.json.JSONObject(t)
        if (o.optString("app") != "GameBox") return@runCatching false
        val c = o.getJSONObject("c")
        profiles.add(0, Prof(o.getString("n"), o.getString("p"), c.keys().asSequence().associateWith { c.getInt(it) }))
        saveProfiles(); true
    }.getOrDefault(false)

    fun select(p: String) { sel = p; sp.edit().putString("sel", p).apply() }
    fun putDnd(v: Boolean) { dnd = v; sp.edit().putBoolean("dnd", v).apply() }
    fun putCln(v: Boolean) { cln = v; sp.edit().putBoolean("cln", v).apply() }
    fun putOv(v: Boolean) { ov = v; sp.edit().putBoolean("ov", v).apply() }
    fun putLive(v: Boolean) { live = v; sp.edit().putBoolean("live", v).apply() }
    fun putGlass(v: Boolean) { GlassOn = v; sp.edit().putBoolean("glass", v).apply() }
    fun setTheme(i: Int) { Cy = Themes[i]; sp.edit().putInt("theme", i).apply() }

    fun label(p: String) = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p)
    val selName: String get() = if (sel.isEmpty()) "Chơi" else label(sel)
    // Nền mờ dựng sẵn của icon (dùng cho thẻ game), xem softBitmap
    fun soft(p: String): ImageBitmap? = softs[p]
        ?: runCatching { softBitmap(pm.getApplicationIcon(p).toBitmap(96, 96)).asImageBitmap() }.getOrNull()?.also { softs[p] = it }
    fun icon(p: String): ImageBitmap? = icons[p] ?: runCatching { pm.getApplicationIcon(p).toBitmap(192, 192).asImageBitmap() }.getOrNull()?.also { icons[p] = it }

    // Điểm hiệu năng 0..100 từ RAM, CPU và nhiệt độ
    val score: Int get() =
        (100 - (ram * 0.35 + (st?.cpuP ?: 0) * 0.35 + ((bat?.t ?: 30f) - 30f).coerceAtLeast(0f) * 1.5)).toInt().coerceIn(0, 100)

    // true khi app đang giữ độ phân giải màn hình ở mức đã hạ (phiên chơi chưa được khôi phục)
    var wmOn by mutableStateOf(false)
    var wmT by mutableStateOf("") // kích thước đang áp, ví dụ 432×960

    fun sample() {
        wmOn = sp.getBoolean("wm_on", false) || sp.getBoolean("ang_on", false)
        wmT = sp.getString("wm_t", "") ?: ""
        val x = Rt.stats(); st = x
        val (t, a) = Rt.mem(); total = t; used = t - a; ram = (100 - a * 100 / t).toInt()
        val b = readBat(ctx); bat = b
        clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        listOf(x.cpuP, x.gpuP, ram, x.fps.coerceAtLeast(0)).forEachIndexed { i, v -> hist[i].add(v); if (hist[i].size > 60) hist[i].removeAt(0) }
        temps.add(b.t); if (temps.size > 600) temps.removeAt(0)
        runCatching {
            val sf = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            disk = (100 - sf.availableBytes * 100 / sf.totalBytes.coerceAtLeast(1L)).toInt()
        }
        upSec = if (Rt.since > 0L) (android.os.SystemClock.elapsedRealtime() - Rt.since) / 1000L else 0L
    }

    // Kiểm tra từng quyền riêng (Root, Shizuku) cho Trung tâm kích hoạt
    fun refreshAccess() {
        rootOk = Rt.rootOk(); shz = Rt.shizukuState(); be = Rt.backend
    }

    fun fmtUp(): String = String.format(Locale.US, "%02d:%02d:%02d", upSec / 3600, (upSec / 60) % 60, upSec % 60)

    // Thời gian chơi cần quyền truy cập dữ liệu sử dụng, Rt.grant cấp bằng root/Shizuku
    fun loadPlaytime() {
        runCatching {
            val um = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            um.queryAndAggregateUsageStats(now - 365L * 86400000L, now).forEach { (k, v) -> playMs[k] = v.totalTimeInForeground; lastMs[k] = v.lastTimeUsed }
        }
    }

    fun fmtPlay(p: String): String {
        val ms = playMs[p] ?: 0L
        return if (ms >= 3600000L) "${ms / 3600000L}h ${(ms / 60000L) % 60}p" else "${ms / 60000L}p"
    }

    fun play() {
        val p = sel; if (p.isEmpty()) return
        // Chưa có quyền vẽ đè thì xin trước, chưa áp dụng gì cả để không phải hoàn tác
        if (ov && !Settings.canDrawOverlays(ctx)) {
            thread {
                // Thử cấp quyền vẽ đè bằng root/Shizuku; nếu vẫn thiếu thì mở trang cấp quyền, không mở game
                Rt.grant(ctx.packageName)
                Handler(Looper.getMainLooper()).post {
                    if (Settings.canDrawOverlays(ctx)) start(p, true)
                    else {
                        Toast.makeText(ctx, "Hãy bật \"Hiển thị trên ứng dụng khác\" cho Game Box rồi bấm Chơi lại", Toast.LENGTH_LONG).show()
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                    }
                }
            }
        } else start(p, ov)
    }

    // Áp dụng cài đặt cho game rồi mới mở game. Mọi thay đổi chỉ tồn tại trong phiên này: cờ "sess" cho phép
    // dịch vụ, nút Thoát hoặc lần mở app sau trả lại như cũ, nên game mở ngoài app vẫn chạy bình thường.
    private fun start(p: String, withOverlay: Boolean) {
        Rt.pkg = p
        Rt.meter.reset(); Rt.fpsNow = -1
        sp.edit().putString("sess", p).apply()
        val gm = g("gm", 0); val res = g("res", 100); val fps = listOf(30, 45, 60, 90, 120, 0)[g("fps", 5)]
        val hzv = g("hz", 0).let { if (it < 30) 0 else it }; val sat = g("sat", 100) / 100f
        val an = g("anim", 0); val ms = g("msaa", 0) == 1; val dv = g("drv", 0)
        thread {
            // Nhật ký quyết định (hiện trong Chẩn đoán) để biết vì sao có hoặc không hạ độ phân giải
            Wm.log(sp, "Chơi $p: mức $res%${if (res >= 95) " · không giảm" else ""}")
            fun toast(msg: String) { if (msg.isNotEmpty()) Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() } }
            // Hạ cả màn hình (wm size) trước khi mở game để game khởi động đã ở độ phân giải mới.
            // Trước đây có thêm lựa chọn "Chỉ game" qua Game Mode: máy thật báo nhận lệnh nhưng Free Fire không hề nhỏ đi
            // (không có cách kiểm chứng), nên bỏ; wm size là cách duy nhất đã kiểm chứng được bằng cách đọc lại.
            // Nếu wm không đổi được thì vẫn thử Game Mode như phương án cuối.
            var gmRes = res
            if (res < 95) {
                if (Wm.apply(sp, res)) { gmRes = 100; Thread.sleep(700) }
                // Báo ngay kết quả (hiện được cả khi game đã mở) để biết độ phân giải có thật sự bị hạ không
                toast(Wm.lastMsg)
            }
            Rt.applyGame(p, gm, gmRes, fps, hzv, sat); Rt.mode(gm); Rt.anim(an); Rt.msaa(ms); Rt.driver(dv)
            // Kết cấu thật (ANGLE): chỉ có hiệu lực khi game khởi động lại, nên báo nếu game đang chạy sẵn
            val running = Rt.sh("pidof $p").any { it.trim().isNotEmpty() }
            val tl = g("texl", 0); val th = g("texd", 0) > 0; val ts = g("texb", 0) == 1
            if (tl > 0 || th || ts) {
                toast(Angle.apply(sp, p, tl, th, ts))
                if (Angle.lastOk && running) toast("Game đang chạy sẵn: hãy đóng hẳn game rồi bấm Chơi lại để kết cấu có hiệu lực")
                if (Angle.lastOk) thread { Thread.sleep(20000); Angle.check(sp, p) }
            }
            sp.edit().putLong("sess_t", System.currentTimeMillis()).apply()
            // Cách game render: game Unity nhận tham số khởi động qua extra "unity". Chỉ có tác dụng khi game khởi động mới.
            val rnd = g("rnd", 0)
            if (rnd > 0) {
                Wm.log(sp, "Chọn cách render: ${if (rnd == 1) "OpenGL ES" else "Vulkan"} (tham số ${Render.arg(rnd)})${if (running) " · game đang chạy sẵn nên chưa có hiệu lực" else ""}")
                if (running) toast("Game đang chạy sẵn: hãy đóng hẳn game rồi bấm Chơi lại để đổi cách render")
            }
            if (!running || rnd > 0) thread { Thread.sleep(25000); Render.measure(sp, p, rnd) { toast(it) } }
            if (fixed) Rt.fixed(true); if (dnd) Rt.dnd(true); if (cln) Rt.clean()
            Handler(Looper.getMainLooper()).post {
                // Dịch vụ luôn chạy khi bấm Chơi để theo dõi game và khôi phục khi game thoát; ui=false thì không vẽ nút nổi
                ctx.startForegroundService(Intent(ctx, Overlay::class.java).putExtra("ui", withOverlay))
                pm.getLaunchIntentForPackage(p)?.let { if (rnd > 0 && !running) it.putExtra("unity", Render.arg(rnd)); ctx.startActivity(it) }
            }
        }
    }

    fun power() {
        val p = Rt.pkg.ifEmpty { sel }; Rt.pkg = ""
        sp.edit().remove("sess").apply()
        ctx.stopService(Intent(ctx, Overlay::class.java))
        thread { Rt.restore(p); Wm.restore(sp, "nút Thoát trong app"); Rt.mode(0); Rt.fixed(false); Rt.dnd(false) }
    }
}

// Vòng lặp nền dùng chung cho cả Game Space và Hub: cấp quyền, đọc số liệu, ping
@Composable
fun GsEffects(gs: Gs) {
    val ctx = gs.ctx
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Rt.init(); Rt.grant(ctx.packageName); gs.refreshAccess() }
        withContext(Dispatchers.IO) { gs.loadPlaytime() }
    }
    // Chỉ đo khi màn hình app đang hiện: khi đang chơi game app nằm nền, mỗi lần đo là vài tiến trình shell làm nặng máy
    val life = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) { while (true) { if (life.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) withContext(Dispatchers.IO) { gs.sample() }; delay(1000) } }
    LaunchedEffect(Unit) { while (true) { if (life.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) gs.ping = withContext(Dispatchers.IO) { Rt.ping() }; delay(10000) } }
}

private val Titles = listOf("Chọn game", "Hiệu năng", "Pin", "Đồ họa")

@Composable
fun GameSpaceApp(gs: Gs) {
    BackHandler(enabled = gs.tex) { gs.tex = false }

    GsEffects(gs)
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
            Text("GAME SPACE", fontSize = 11.sp, letterSpacing = 3.sp, color = Cy, maxLines = 1)
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
