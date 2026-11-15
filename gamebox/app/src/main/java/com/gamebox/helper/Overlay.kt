package com.gamebox.helper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------- View cũ vẫn dùng cho tâm ngắm và điểm chạm Macro (cửa sổ riêng, không cần Compose) ----------

// Chấm tâm ngắm đặt giữa màn hình, không nhận chạm
class Cross(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.rgb(255, 59, 48) }
    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        c.drawCircle(width / 2f, height / 2f, 3 * d, p)
    }
}

// Vòng tròn điểm chạm của Macro: kéo để đặt vị trí, lúc chạy thì không nhận chạm
class Tgt(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = AColor.rgb(250, 204, 21) }
    override fun onDraw(c: Canvas) {
        val r = minOf(width, height) / 2f - 4f
        c.drawCircle(width / 2f, height / 2f, r, p)
        c.drawCircle(width / 2f, height / 2f, 4f, p)
    }
}

// ---------- Hạ tầng để chạy Compose trong cửa sổ của Service ----------

class OvOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val reg = LifecycleRegistry(this)
    private val ctl = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = reg
    override val savedStateRegistry: SavedStateRegistry get() = ctl.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store
    init { ctl.performAttach(); ctl.performRestore(null) }
    fun resume() { reg.currentState = Lifecycle.State.RESUMED }
    fun destroy() { reg.currentState = Lifecycle.State.DESTROYED; store.clear() }
}

// Khung bọc ComposeView của nút nổi: tự bắt thao tác kéo theo tọa độ màn hình (rawX/rawY) để cửa sổ di chuyển mượt,
// còn chạm nhanh vẫn đi xuống Compose
class DragFrame(
    c: Context,
    private val onStart: () -> Unit,
    private val onDrag: (Float, Float) -> Unit,
    private val onEnd: () -> Unit
) : FrameLayout(c) {
    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private var downX = 0f; private var downY = 0f; private var lastX = 0f; private var lastY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; lastX = downX; lastY = downY; dragging = false }
            MotionEvent.ACTION_MOVE -> if (!dragging && (abs(e.rawX - downX) > slop || abs(e.rawY - downY) > slop)) {
                dragging = true; lastX = e.rawX; lastY = e.rawY; onStart(); return true
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (dragging) { onDrag(e.rawX - lastX, e.rawY - lastY); lastX = e.rawX; lastY = e.rawY }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) { dragging = false; onEnd() }
        }
        return true
    }
}

// ---------- Trạng thái và danh sách tính năng ----------

class OvState {
    var dock by mutableIntStateOf(0)        // 0 nút nổi, 1 thanh bên trái, 2 thanh bên phải
    var side by mutableIntStateOf(1)        // bảng trượt ra từ bên nào (1 trái, 2 phải)
    var vis by mutableStateOf(false)        // bảng đang hiện (để chạy animation vào/ra)
    var open by mutableStateOf(false)
    var idleKey by mutableIntStateOf(0)     // tăng mỗi lần có thao tác, để thanh mép mờ đi khi không dùng
    var idle by mutableStateOf(false)
    var fps by mutableIntStateOf(0)
    var ram by mutableIntStateOf(0)
    var cpuP by mutableIntStateOf(0)
    var gpuP by mutableIntStateOf(0)
    var cpu by mutableIntStateOf(0)
    var gpu by mutableIntStateOf(0)
    var cg by mutableStateOf("")
    var gg by mutableStateOf("")
    var temp by mutableFloatStateOf(0f)
    var bat by mutableIntStateOf(0)
    var clock by mutableStateOf("")
    val hist = mutableStateListOf<Int>()
}

class Feat(val ic: Ic, val title: String, val act: (Feat) -> Unit) {
    var on by mutableStateOf(false)
    var label by mutableStateOf(title)
}

// ---------- Giao diện Compose của overlay ----------

@Composable
fun OvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Cy, background = Color.Transparent, surface = Color.Transparent, onBackground = Color.White, onSurface = Color.White)) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}

// Nút nổi tròn (hiện FPS) hoặc thanh nhỏ dính mép màn hình
@Composable
fun OvBubbleUi(s: OvState, tap: () -> Unit, long: () -> Unit) {
    LaunchedEffect(s.idleKey, s.dock) { s.idle = false; kotlinx.coroutines.delay(3000); s.idle = true }
    val a by animateFloatAsState(if (s.dock != 0 && s.idle) .4f else 1f, tween(400), label = "bub")
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = a }.pointerInput(s.dock) { detectTapGestures(onTap = { tap() }, onLongPress = { long() }) },
        contentAlignment = Alignment.Center
    ) {
        if (s.dock == 0) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xE61A3550), Color(0xE60A1420)))).border(1.5.dp, Cy.copy(.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${s.fps}", fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp)
                    Text("FPS", fontSize = 8.sp, color = Cy, lineHeight = 9.sp)
                }
            }
        } else {
            val shape = if (s.dock == 1) RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp) else RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
            Box(Modifier.fillMaxSize().clip(shape).background(Color(0xE60E1C2C)).border(1.dp, Cy.copy(.7f), shape), contentAlignment = Alignment.Center) {
                Ico(Ic.Back, Modifier.rotate(if (s.dock == 1) 180f else 0f), Cy, 16.dp)
            }
        }
    }
}

@Composable
fun OvIconBtn(ic: Ic, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).bounce(onClick = onClick).clip(CircleShape).background(Color.White.copy(.08f)).border(1.dp, Color.White.copy(.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) { Ico(ic, sz = 16.dp) }
}

@Composable
fun OvChip(l: String, v: String, mod: Modifier) {
    Column(mod.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(.07f)).padding(horizontal = 8.dp, vertical = 5.dp)) {
        Text(l, fontSize = 9.sp, letterSpacing = 1.sp, color = Color.White.copy(.6f))
        Text(v, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun OvStats(s: OvState) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${s.fps}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Cy, lineHeight = 42.sp)
        Text(" FPS", Modifier.padding(bottom = 6.dp), fontSize = 12.sp, color = Color.White.copy(.7f))
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(s.clock, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("${s.bat}%  ${s.temp}°C", fontSize = 11.sp, color = Color.White.copy(.7f))
        }
    }
    Spark(s.hist, 120, Modifier.fillMaxWidth().height(44.dp))
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OvChip("CPU", "${s.cpuP}%", Modifier.weight(1f))
        OvChip("GPU", "${s.gpuP}%", Modifier.weight(1f))
        OvChip("RAM", "${s.ram}%", Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    Text("CPU ${s.cpu} MHz ${s.cg}", fontSize = 10.sp, color = Color.White.copy(.55f), maxLines = 1)
    Text("GPU ${s.gpu} MHz ${s.gg}", fontSize = 10.sp, color = Color.White.copy(.55f), maxLines = 1)
}

@Composable
fun OvTile(f: Feat, mod: Modifier) {
    val sh = RoundedCornerShape(16.dp)
    val bg by animateColorAsState(if (f.on) Cy.copy(.92f) else Color.White.copy(.08f), tween(200), label = "ovt")
    Column(mod.bounce { f.act(f) }.padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(sh).background(bg).border(1.dp, Cy.copy(.4f), sh), contentAlignment = Alignment.Center) {
            Ico(f.ic, tint = if (f.on) OnAccent else Color.White, sz = 24.dp)
        }
        Spacer(Modifier.height(4.dp))
        Text(f.label, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center, color = Color.White.copy(.9f))
    }
}

@Composable
fun OvGrid(feats: List<Feat>, cols: Int) {
    feats.chunked(cols).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { OvTile(it, Modifier.weight(1f)) }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun OvSide(s: OvState, feats: List<Feat>, w: Dp, wide: Boolean, h: Dp, close: () -> Unit, toggleDock: () -> Unit) {
    val sh = RoundedCornerShape(26.dp)
    Column(
        Modifier.width(w).heightIn(max = h).clip(sh).background(Brush.verticalGradient(listOf(Color(0xF2102236), Color(0xF20A141F)))).border(1.dp, Cy.copy(.45f), sh)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("GAME SPACE", Modifier.weight(1f), fontSize = 11.sp, letterSpacing = 3.sp, color = Cy)
            OvIconBtn(Ic.Drag, toggleDock)
            Spacer(Modifier.width(6.dp))
            OvIconBtn(Ic.Close, close)
        }
        Spacer(Modifier.height(8.dp))
        val scroll = rememberScrollState()
        if (wide) {
            Row(Modifier.verticalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.width(170.dp)) { OvStats(s) }
                Column(Modifier.weight(1f)) { OvGrid(feats, 4) }
            }
        } else {
            Column(Modifier.verticalScroll(scroll)) { OvStats(s); Spacer(Modifier.height(10.dp)); OvGrid(feats, 4) }
        }
    }
}

// Cửa sổ toàn màn hình: nền mờ, chạm ra ngoài để đóng, bảng trượt ra từ mép
@Composable
fun OvPanelRoot(s: OvState, feats: List<Feat>, close: () -> Unit, toggleDock: () -> Unit) {
    val left = s.side == 1
    val scrim by animateFloatAsState(if (s.vis) .45f else 0f, tween(250), label = "scrim")
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black.copy(scrim)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() }
    ) {
        val pw = minOf(maxWidth - 24.dp, 480.dp)
        AnimatedVisibility(
            s.vis, Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd).padding(10.dp),
            enter = slideInHorizontally(tween(280)) { if (left) -it else it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(240)) { if (left) -it else it } + fadeOut(tween(160))
        ) { OvSide(s, feats, pw, pw >= 460.dp, maxHeight - 20.dp, close, toggleDock) }
    }
}

// ---------- Service ----------

class Overlay : Service() {
    companion object { @Volatile var alive = false }

    private lateinit var wm: WindowManager
    private lateinit var sp: SharedPreferences
    private val main = Handler(Looper.getMainLooper())
    private val owner = OvOwner()
    private val st = OvState()
    private val feats = mutableListOf<Feat>()
    private var bubble: DragFrame? = null
    private var blp: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var closing = false
    private var acc = 0f
    private var cross: View? = null
    private var tgt: Tgt? = null
    private var tlp: WindowManager.LayoutParams? = null
    private var started = false
    @Volatile private var macroOn = false
    @Volatile private var run = true

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    @Suppress("DEPRECATION")
    private fun screen(): Point = Point().also { wm.defaultDisplay.getRealSize(it) }
    private fun toast(m: String) { main.post { Toast.makeText(this, m, Toast.LENGTH_SHORT).show() } }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        alive = true
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("o", "Overlay", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, "o").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Game Box").build())
        wm = getSystemService(WindowManager::class.java)
        sp = getSharedPreferences("gs", Context.MODE_PRIVATE)
        Cy = Themes[sp.getInt("theme", 0).coerceIn(0, Themes.lastIndex)]
        buildFeats()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (!started) { started = true; startLoop() }
        // ui=false: chỉ giữ phiên (tự khôi phục khi thoát game), không vẽ nút nổi
        if (i?.hasExtra("ui") == true && !i.getBooleanExtra("ui", true)) removeUi()
        else if (bubble == null && Settings.canDrawOverlays(this)) buildUi()
        if (i != null && bubble != null) {
            // Hai extra dưới đây chỉ để smoke test CI chụp từng trạng thái của overlay
            if (i.hasExtra("dock")) applyDock(i.getIntExtra("dock", 0))
            if (i.hasExtra("open")) if (i.getBooleanExtra("open", false)) openPanel() else closePanel()
        }
        return START_NOT_STICKY
    }

    // ----- Nút nổi / thanh mép -----

    private fun buildUi() {
        owner.resume()
        val frame = DragFrame(this, { acc = 0f }, { dx, dy -> onDrag(dx, dy) }, { onDragEnd() })
        val cv = ComposeView(this).apply { setContent { OvTheme { OvBubbleUi(st, { openPanel() }, { toggleDock() }) } } }
        frame.addView(cv, FrameLayout.LayoutParams(-1, -1))
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeViewModelStoreOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        val lp = WindowManager.LayoutParams(
            dp(52), dp(52), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            x = sp.getInt("ov_x", dp(12)); y = sp.getInt("ov_y", dp(80))
        }
        try { wm.addView(frame, lp) } catch (e: Exception) { return }
        bubble = frame; blp = lp
        val d = sp.getInt("ov_dock", 0)
        if (d != 0) applyDock(d) else { st.dock = 0; clampFloat(); push() }
    }

    private fun removeUi() {
        closePanel();
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null; blp = null
    }

    private fun push() {
        val b = bubble ?: return; val lp = blp ?: return
        runCatching { wm.updateViewLayout(b, lp) }
    }

    private fun clampFloat() {
        val lp = blp ?: return; val sc = screen()
        lp.x = lp.x.coerceIn(0, (sc.x - lp.width).coerceAtLeast(0)); lp.y = lp.y.coerceIn(0, (sc.y - lp.height).coerceAtLeast(0))
    }

    private fun saveBubble() {
        val lp = blp ?: return
        sp.edit().putInt("ov_dock", st.dock).putInt("ov_x", lp.x).putInt("ov_y", lp.y).apply()
    }

    // 0 = nút nổi, 1 = dính mép trái, 2 = dính mép phải
    private fun applyDock(d: Int) {
        val lp = blp ?: return; val sc = screen(); val prev = st.dock
        st.dock = d; st.idleKey++
        if (d == 0) {
            lp.width = dp(52); lp.height = dp(52)
            lp.x = when (prev) { 1 -> dp(10); 2 -> sc.x - lp.width - dp(10); else -> lp.x }
        } else {
            lp.width = dp(22); lp.height = dp(80); lp.x = if (d == 1) 0 else sc.x - lp.width
        }
        clampFloat(); push(); saveBubble()
    }

    private fun toggleDock() {
        val lp = blp ?: return
        if (st.dock == 0) applyDock(if (lp.x + lp.width / 2 < screen().x / 2) 1 else 2) else applyDock(0)
    }

    private fun onDrag(dx: Float, dy: Float) {
        val lp = blp ?: return; val sc = screen()
        st.idleKey++
        if (st.dock == 0) {
            lp.x = (lp.x + dx).toInt(); lp.y = (lp.y + dy).toInt(); clampFloat()
        } else {
            lp.y = (lp.y + dy).toInt(); clampFloat()
            acc += dx
            // Kéo thanh từ mép vào trong thì mở bảng
            if ((st.dock == 1 && acc > dp(40)) || (st.dock == 2 && acc < -dp(40))) { acc = 0f; openPanel() }
        }
        push()
    }

    private fun onDragEnd() {
        val lp = blp ?: return; val sc = screen()
        if (st.dock == 0) {
            // Kéo nút sát mép màn hình thì thu thành thanh bên (kiểu Game Space của Oppo)
            if (lp.x <= dp(20)) applyDock(1) else if (lp.x + lp.width >= sc.x - dp(20)) applyDock(2) else saveBubble()
        } else saveBubble()
    }

    // ----- Bảng Game Space -----

    private fun openPanel() {
        if (panel != null || bubble == null) return
        val lp0 = blp ?: return
        st.side = if (st.dock != 0) st.dock else if (lp0.x + lp0.width / 2 < screen().x / 2) 1 else 2
        closing = false
        val frame = FrameLayout(this)
        val cv = ComposeView(this).apply { setContent { OvTheme { OvPanelRoot(st, feats, { closePanel() }, { toggleDock() }) } } }
        frame.addView(cv, FrameLayout.LayoutParams(-1, -1))
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeViewModelStoreOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        val lp = WindowManager.LayoutParams(
            -1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        try { wm.addView(frame, lp) } catch (e: Exception) { return }
        panel = frame
        st.vis = false; st.open = true
        bubble?.visibility = View.INVISIBLE
        main.post { st.vis = true }
    }

    private fun closePanel() {
        val p = panel ?: return
        if (closing) return
        closing = true; st.vis = false
        main.postDelayed({
            runCatching { wm.removeView(p) }
            if (panel === p) panel = null
            st.open = false; closing = false
            bubble?.visibility = View.VISIBLE
        }, 300)
    }

    // ----- Tính năng -----

    private fun buildFeats() {
        val hzOpts = runCatching {
            getSystemService(DisplayManager::class.java).getDisplay(0).supportedModes.map { it.refreshRate.roundToInt() }.distinct().sorted()
        }.getOrDefault(listOf(60)).ifEmpty { listOf(60) }
        var hi = -1
        val sens = intArrayOf(0, 3, 5, 7); var si = 0
        var ms = 0 // Macro: 0 tắt, 1 đặt điểm chạm, 2 đang chạm liên tục
        feats += Feat(Ic.Chip, "Dọn RAM") { thread { toast(Rt.clean()) } }
        feats += Feat(Ic.Bell, "Chặn noti") { it.on = !it.on; val v = it.on; thread { Rt.dnd(v) } }
        feats += Feat(Ic.Refresh, "Quét ${hzOpts.last()}Hz") {
            hi = (hi + 1) % hzOpts.size; val v = hzOpts[hi]
            it.label = "Quét ${v}Hz"; it.on = true
            thread { Rt.sh("settings put system peak_refresh_rate $v", "settings put system min_refresh_rate $v") }
        }
        feats += Feat(Ic.Bolt, "Turbo") { it.on = !it.on; val v = it.on; thread { Rt.mode(if (v) 1 else 0) } }
        feats += Feat(Ic.Cursor, "Macro") {
            ms = (ms + 1) % 3
            when (ms) {
                1 -> { addTarget(); it.label = "Đặt điểm"; it.on = true }
                2 -> { startMacro(); it.label = "Đang chạy"; it.on = true }
                else -> { stopMacro(); it.label = "Macro"; it.on = false }
            }
        }
        feats += Feat(Ic.Target, "Tâm ngắm") {
            it.on = !it.on
            if (it.on) addCross() else { cross?.let { v -> runCatching { wm.removeView(v) } }; cross = null }
        }
        feats += Feat(Ic.Gauge, "Độ nhạy") {
            si = (si + 1) % sens.size; it.label = if (si == 0) "Độ nhạy" else "Nhạy ${sens[si]}"; it.on = si != 0
            val v = sens[si]; thread { Rt.pointer(v) }
        }
        feats += Feat(Ic.Cam, "Chụp MH") {
            // Đóng bảng trước để ảnh chụp không dính bảng
            closePanel()
            thread { Thread.sleep(450); Rt.sh("screencap -p /sdcard/Pictures/GameBox_${System.currentTimeMillis()}.png"); toast("Đã lưu ảnh vào Pictures") }
        }
        feats += Feat(Ic.Rec, "Quay MH") {
            it.on = !it.on; it.label = if (it.on) "Đang quay" else "Quay MH"
            val v = it.on; thread { Rt.record(v) }
        }
        feats += Feat(Ic.Touch, "Hiện chạm") { it.on = !it.on; val v = if (it.on) 1 else 0; thread { Rt.sh("settings put system show_touches $v") } }
        feats += Feat(Ic.Home, "Về app") { startActivity(Intent(this, HubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); closePanel() }
        feats += Feat(Ic.Power, "Thoát") { endSession() }
    }

    private fun addCross() {
        val v = Cross(this)
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        runCatching {
            wm.addView(v, WindowManager.LayoutParams(dp(24), dp(24), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER })
            cross = v
        }
    }

    private fun addTarget() {
        val t = Tgt(this)
        val l = WindowManager.LayoutParams(
            dp(56), dp(56), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(300); y = dp(120) }
        t.setOnTouchListener { _, e ->
            l.x = (e.rawX - dp(28)).toInt(); l.y = (e.rawY - dp(28)).toInt()
            runCatching { wm.updateViewLayout(t, l) }
            true
        }
        runCatching { wm.addView(t, l); tgt = t; tlp = l }
        closePanel()
    }

    private fun startMacro() {
        val t = tgt; val l = tlp
        if (t != null && l != null) {
            l.flags = l.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { wm.updateViewLayout(t, l) }
            val cx = l.x + dp(28); val cy = l.y + dp(28)
            macroOn = true
            thread { while (macroOn && run) { Rt.sh("input tap $cx $cy"); Thread.sleep(120) } }
        }
    }

    private fun stopMacro() {
        macroOn = false
        tgt?.let { v -> runCatching { wm.removeView(v) } }
        tgt = null; tlp = null
    }

    // Kết thúc phiên chơi: trả lại cài đặt hệ thống/game rồi dừng dịch vụ
    private fun endSession() {
        val p = Rt.pkg.ifEmpty { sp.getString("sess", "") ?: "" }
        Rt.pkg = ""
        sp.edit().remove("sess").apply()
        thread { Rt.restore(p); Rt.mode(0); Rt.dnd(false); Rt.fixed(false) }
        stopSelf()
    }

    // ----- Vòng lặp số liệu và theo dõi game -----

    private fun startLoop() {
        thread {
            val fmt = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            var tick = 0; var seen = false; var miss = 0
            while (run) {
                val s = Rt.stats(); val (t, a) = Rt.mem()
                val b = readBat(this)
                st.fps = s.fps; st.cpuP = s.cpuP; st.gpuP = s.gpuP; st.cpu = s.cpu; st.gpu = s.gpu; st.cg = s.cg; st.gg = s.gg
                st.ram = 100 - (a * 100 / t).toInt(); st.temp = b.t; st.bat = b.lv; st.clock = fmt.format(java.util.Date())
                st.hist.add(s.fps); if (st.hist.size > 60) st.hist.removeAt(0)
                // Theo dõi game: khi tiến trình game thoát (đã từng thấy) thì tự khôi phục để lần mở game ngoài app là bình thường.
                // Chỉ kết luận khi đã từng thấy tiến trình, tránh tự dừng nhầm nếu máy không cho đọc pidof.
                val p = Rt.pkg
                if (tick % 2 == 0 && p.isNotEmpty() && Rt.backend != "none") {
                    val alive = Rt.sh("pidof $p").any { it.trim().isNotEmpty() }
                    if (alive) { seen = true; miss = 0 } else if (seen && ++miss >= 3) {
                        main.post { endSession() }
                        break
                    }
                }
                if (tick % 5 == 0) main.post { relayout() }
                tick++
                Thread.sleep(1000)
            }
        }
    }

    // Xoay màn hình khi đang dính mép phải thì cập nhật lại vị trí
    private fun relayout() {
        val lp = blp ?: return; val sc = screen()
        if (st.dock == 2) lp.x = sc.x - lp.width
        clampFloat(); push()
    }

    override fun onDestroy() {
        alive = false; run = false; macroOn = false
        cross?.let { v -> runCatching { wm.removeView(v) } }
        tgt?.let { v -> runCatching { wm.removeView(v) } }
        panel?.let { v -> runCatching { wm.removeView(v) } }
        bubble?.let { v -> runCatching { wm.removeView(v) } }
        owner.destroy()
    }
}
