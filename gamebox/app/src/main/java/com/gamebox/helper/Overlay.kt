package com.gamebox.helper

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread

class Chart(c: Context) : View(c) {
    private val d = ArrayDeque<Int>()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f; style = Paint.Style.STROKE; color = Color.rgb(125, 211, 252) }
    private val grid = Paint().apply { strokeWidth = 2f; color = Color.argb(70, 255, 255, 255) }
    fun add(v: Int) { d.addLast(v); if (d.size > 60) d.removeFirst(); invalidate() }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawLine(0f, h / 2, w, h / 2, grid) // mốc 60 FPS
        if (d.size < 2) return
        val p = Path()
        d.forEachIndexed { i, v ->
            val x = w * i / 59; val y = h - h * v.coerceIn(0, 120) / 120f
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        c.drawPath(p, line)
    }
}

// Nút lục giác kiểu bảng điều khiển trong game
class Hex(c: Context, val ic: String, var lb: String, val tap: (Hex) -> Unit) : View(c) {
    var on = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    init { setOnClickListener { tap(this) } }
    override fun onMeasure(w: Int, h: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension((70 * d).toInt(), (80 * d).toInt())
    }
    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f; val cy = 28 * d; val r = 25 * d
        val path = Path()
        for (i in 0 until 6) {
            val a = Math.toRadians(60.0 * i - 90)
            val x = (cx + r * Math.cos(a)).toFloat(); val y = (cy + r * Math.sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        p.style = Paint.Style.FILL; p.color = if (on) Color.argb(235, 110, 200, 250) else Color.argb(90, 20, 50, 80); c.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = Color.argb(210, 125, 211, 252); c.drawPath(path, p)
        t.textSize = 20 * d; t.color = Color.WHITE; c.drawText(ic, cx, cy + 7 * d, t)
        t.textSize = 10 * d; t.color = Color.WHITE; c.drawText(lb, cx, 74 * d, t)
    }
}

// Chấm tâm ngắm đặt giữa màn hình, không nhận chạm
class Cross(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 59, 48) }
    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        c.drawCircle(width / 2f, height / 2f, 3 * d, p)
    }
}

// Vòng tròn điểm chạm của Macro: kéo để đặt vị trí, lúc chạy thì không nhận chạm
class Tgt(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.rgb(250, 204, 21) }
    override fun onDraw(c: Canvas) {
        val r = minOf(width, height) / 2f - 4f
        c.drawCircle(width / 2f, height / 2f, r, p)
        c.drawCircle(width / 2f, height / 2f, 4f, p)
    }
}

class Overlay : Service() {
    private lateinit var wm: WindowManager
    private lateinit var box: LinearLayout
    private var cross: View? = null
    private var tgt: Tgt? = null
    private var tlp: WindowManager.LayoutParams? = null
    @Volatile private var macroOn = false
    @Volatile private var run = true

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("o", "Overlay", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, "o").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Game Box").build())
        wm = getSystemService(WindowManager::class.java)
        // Chưa có quyền "hiển thị trên ứng dụng khác" thì addView sẽ ném BadTokenException, nên dừng sớm
        if (!android.provider.Settings.canDrawOverlays(this)) { stopSelf(); return }
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        fun bg() = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.argb(205, 16, 44, 72), Color.argb(185, 8, 18, 32))).apply {
            cornerRadius = 22 * d; setStroke(dp(1), Color.argb(150, 125, 211, 252))
        }
        val info = TextView(this).apply { setTextColor(Color.WHITE); textSize = 12f; typeface = Typeface.MONOSPACE }
        val ch = Chart(this)
        val grid = android.widget.GridLayout(this).apply { columnCount = 6 }
        val hzs = intArrayOf(60, 90, 120); var hi = 0
        val sens = intArrayOf(0, 3, 5, 7); var si = 0
        fun hex(ic: String, lb: String, tap: (Hex) -> Unit) { grid.addView(Hex(this, ic, lb, tap)) }
        hex("🧹", "Dọn RAM") { thread { Rt.clean() } }
        hex("🔕", "Chặn noti") { it.on = !it.on; it.invalidate(); val v = it.on; thread { Rt.dnd(v) } }
        hex("🔄", "Quét 60Hz") {
            hi = (hi + 1) % 3; it.lb = "Quét ${hzs[hi]}Hz"; it.on = true; it.invalidate()
            val v = hzs[hi]; thread { Rt.sh("settings put system peak_refresh_rate $v", "settings put system min_refresh_rate $v") }
        }
        hex("🚀", "Turbo") { it.on = !it.on; it.invalidate(); val v = it.on; thread { Rt.mode(if (v) 1 else 0) } }
        var ms = 0 // Macro: 0 tắt, 1 đặt điểm chạm, 2 đang chạm liên tục
        hex("🔁", "Macro") {
            ms = (ms + 1) % 3
            when (ms) {
                1 -> {
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
                    wm.addView(t, l); tgt = t; tlp = l
                    it.lb = "Đặt điểm"; it.on = true
                }
                2 -> {
                    val t = tgt; val l = tlp
                    if (t != null && l != null) {
                        l.flags = l.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        runCatching { wm.updateViewLayout(t, l) }
                        val cx = l.x + dp(28); val cy = l.y + dp(28)
                        macroOn = true
                        thread { while (macroOn && run) { Rt.sh("input tap $cx $cy"); Thread.sleep(120) } }
                    }
                    it.lb = "Đang chạy"; it.on = true
                }
                else -> {
                    macroOn = false
                    tgt?.let { v -> runCatching { wm.removeView(v) } }
                    tgt = null; tlp = null
                    it.lb = "Macro"; it.on = false
                }
            }
            it.invalidate()
        }
        hex("🎯", "Tâm ngắm") {
            it.on = !it.on; it.invalidate()
            if (it.on) {
                val v = Cross(this)
                val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                wm.addView(v, WindowManager.LayoutParams(dp(24), dp(24), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER })
                cross = v
            } else {
                cross?.let { v -> runCatching { wm.removeView(v) } }
                cross = null
            }
        }
        hex("🎚", "Độ nhạy") {
            si = (si + 1) % sens.size; it.lb = if (si == 0) "Độ nhạy" else "Nhạy ${sens[si]}"; it.on = si != 0; it.invalidate()
            val v = sens[si]; thread { Rt.pointer(v) }
        }
        hex("📸", "Chụp MH") { thread { Rt.sh("screencap -p /sdcard/Pictures/GameBox_${System.currentTimeMillis()}.png") } }
        hex("🎥", "Quay MH") {
            it.on = !it.on; it.lb = if (it.on) "Đang quay" else "Quay MH"; it.invalidate()
            val v = it.on; thread { Rt.record(v) }
        }
        hex("👆", "Hiện chạm") { it.on = !it.on; it.invalidate(); val v = if (it.on) 1 else 0; thread { Rt.sh("settings put system show_touches $v") } }
        hex("🏠", "Về app") { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        hex("✕", "Thoát") { val p = Rt.pkg; Rt.pkg = ""; thread { Rt.restore(p); Rt.mode(0); Rt.dnd(false) }; stopSelf() }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)); background = bg(); visibility = View.GONE
            addView(info); addView(ch, LinearLayout.LayoutParams(dp(420), dp(40))); addView(grid)
        }
        val pill = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 12f; setPadding(dp(14), dp(6), dp(14), dp(6)); background = bg()
            setOnClickListener { panel.visibility = if (panel.visibility == View.GONE) View.VISIBLE else View.GONE }
        }
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; addView(pill); addView(panel) }
        val lp = WindowManager.LayoutParams(
            -2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(6) }
        try { wm.addView(box, lp) } catch (e: Exception) { stopSelf(); return }
        thread {
            val fmt = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            while (run) {
                val s = Rt.stats(); val (t, a) = Rt.mem(); val u = 100 - (a * 100 / t).toInt()
                box.post {
                    pill.text = "FPS ${s.fps}  ·  RAM $u%  ▾"
                    info.text = "${fmt.format(java.util.Date())}\nFPS ${s.fps}   RAM $u%   CPU ${s.cpuP}%\nCPU ${s.cpu}MHz ${s.cg}\nGPU ${s.gpu}MHz ${s.gg}"
                    ch.add(s.fps)
                }
                Thread.sleep(1000)
            }
        }
    }

    override fun onDestroy() {
        run = false; macroOn = false
        cross?.let { v -> runCatching { wm.removeView(v) } }
        tgt?.let { v -> runCatching { wm.removeView(v) } }
        runCatching { wm.removeView(box) }
    }
}
