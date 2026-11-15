package com.gamebox.helper

import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku
import java.io.File
import java.util.Locale

class St(val cpu: Int, val cg: String, val gpu: Int, val gg: String, val fps: Int, val cpuP: Int, val gpuP: Int)
class Proc(val pkg: String, val mb: Int)

object Rt {
    @Volatile var pkg = ""
    @Volatile var backend = "none" // root | shizuku | none
    @Volatile var since = 0L // thời điểm có quyền (elapsedRealtime), dùng cho "Phiên truy cập"
    private var lt = 0L
    private var li = 0L

    // perfmtk: path, giá trị BẬT, giá trị TẮT. Khác nhau theo kernel MediaTek, sửa cho khớp máy.
    val PERFMTK = listOf(
        Triple("/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost", "100", "0"),
        Triple("/proc/perfmgr/boost_ctrl/cpu_ctrl/cfp_enable", "0", "1")
    )

    fun init(): String {
        backend = when {
            Shell.getShell().isRoot -> "root"
            runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> {
                if (Shizuku.checkSelfPermission() != 0) runCatching { Shizuku.requestPermission(1) }
                if (Shizuku.checkSelfPermission() == 0) "shizuku" else "none"
            }
            else -> "none"
        }
        if (backend == "none") since = 0L else if (since == 0L) since = android.os.SystemClock.elapsedRealtime()
        return backend
    }

    fun rootOk(): Boolean = runCatching { Shell.getShell().isRoot }.getOrDefault(false)

    // 0 Shizuku không chạy, 1 chạy nhưng chưa cấp quyền, 2 đã cấp quyền
    fun shizukuState(): Int = runCatching {
        if (!Shizuku.pingBinder()) 0 else if (Shizuku.checkSelfPermission() == 0) 2 else 1
    }.getOrDefault(0)

    fun sh(vararg c: String): List<String> = when (backend) {
        "root" -> Shell.cmd(*c).exec().out
        "shizuku" -> exec1("{\n" + c.joinToString("\n") + "\n} 2>/dev/null")
        else -> emptyList()
    }

    private fun exec1(cmd: String): List<String> = runCatching {
        val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val r = p.inputStream.bufferedReader().readLines()
        p.waitFor()
        r
    }.getOrDefault(emptyList())

    // Cấp quyền vẽ đè, thông báo và đọc thời gian sử dụng (để hiện giờ đã chơi) bằng root/Shizuku
    fun grant(p: String) { sh("appops set $p SYSTEM_ALERT_WINDOW allow", "appops set $p GET_USAGE_STATS allow", "pm grant $p android.permission.POST_NOTIFICATIONS") }

    // m: 0 chuẩn/cân bằng, 1 hiệu năng, 2 tiết kiệm (ghi governor cần root)
    fun mode(m: Int) {
        val cg = arrayOf("schedutil walt interactive", "performance", "powersave")[m]
        val gg = arrayOf("simple_ondemand msm-adreno-tz", "performance", "powersave")[m]
        val on = m == 1
        sh(
            "for p in /sys/devices/system/cpu/cpu*/cpufreq; do for g in $cg; do echo \$g > \$p/scaling_governor 2>/dev/null && break; done; done",
            "for d in /sys/class/devfreq/*mali* /sys/class/devfreq/*gpu* /sys/class/kgsl/kgsl-3d0/devfreq; do for g in $gg; do echo \$g > \$d/governor 2>/dev/null && break; done; done",
            *PERFMTK.map { "echo ${if (on) it.second else it.third} > ${it.first} 2>/dev/null" }.toTypedArray()
        )
    }

    fun fixed(on: Boolean) { sh("cmd power set-fixed-performance-mode-enabled $on") }
    fun saver(on: Boolean) { sh("settings put global low_power ${if (on) 1 else 0}") }
    fun dnd(on: Boolean) { sh("cmd notification set_dnd ${if (on) "on" else "off"}") }

    // Hiệu ứng hệ thống: 0 bình thường (1x), 1 nhanh (0.5x), 2 tắt
    fun anim(m: Int) {
        val v = arrayOf("1.0", "0.5", "0")[m.coerceIn(0, 2)]
        sh("settings put global window_animation_scale $v", "settings put global transition_animation_scale $v", "settings put global animator_duration_scale $v")
    }

    // Khử răng cưa 4x MSAA (như tùy chọn nhà phát triển; áp dụng sau khi mở lại game)
    fun msaa(on: Boolean) { sh("setprop debug.egl.force_msaa $on") }

    // Driver đồ họa: 0 mặc định, 1 game driver, 2 Vulkan cho giao diện hệ thống
    fun driver(m: Int) {
        sh("settings put global game_driver_all_apps ${if (m == 1) 1 else 0}", "setprop debug.hwui.renderer ${if (m == 2) "skiavk" else "''"}")
    }

    // Tốc độ con trỏ -7..7. Android không có chỉnh độ nhạy cảm ứng chung, chỉ có tác dụng với chuột/tay cầm
    fun pointer(v: Int) { sh("settings put system pointer_speed $v") }

    // Quay màn hình tối đa 3 phút, lưu vào /sdcard/Movies. Chạy nền để lệnh dừng không bị chờ
    fun record(on: Boolean) {
        if (on) sh("mkdir -p /sdcard/Movies", "nohup screenrecord --time-limit 180 /sdcard/Movies/GameBox_${System.currentTimeMillis()}.mp4 >/dev/null 2>&1 &")
        else sh("pkill -2 screenrecord")
    }

    // gm 0 chuẩn, 1 hiệu năng, 2 tiết kiệm. res %, fps 0 = tối đa, hz 0 = mặc định, sat 1.0 = gốc
    fun applyGame(p: String, gm: Int, res: Int, fps: Int, hz: Int, sat: Float) {
        val mode = arrayOf("standard", "performance", "battery")[gm]
        val ds = if (res >= 95) "disable" else String.format(Locale.US, "%.2f", (res.coerceAtLeast(30) / 5 * 5) / 100.0)
        val fp = if (fps in listOf(30, 45, 60, 90, 120)) fps.toString() else "disable"
        sh("cmd game set --mode $mode --downscale $ds --fps $fp $p")
        if (hz > 0) sh("settings put system peak_refresh_rate $hz", "settings put system min_refresh_rate $hz")
        if (sat != 1f) sh("service call SurfaceFlinger 1022 f $sat")
    }

    fun restore(p: String) {
        sh("cmd game reset $p", "settings delete system peak_refresh_rate", "settings delete system min_refresh_rate", "service call SurfaceFlinger 1022 f 1.0")
        anim(0); msaa(false); driver(0); pointer(0); record(false)
    }

    fun clean(): String {
        val b = mem().second
        sh("am kill-all", "sync", "echo 3 > /proc/sys/vm/drop_caches")
        return "Đã giải phóng khoảng ${(mem().second - b) / 1024} MB"
    }

    fun mem(): Pair<Long, Long> {
        val m = File("/proc/meminfo").readLines().associate {
            it.substringBefore(":") to (it.substringAfter(":").trim().substringBefore(" ").toLongOrNull() ?: 0L)
        }
        return (m["MemTotal"] ?: 1L).coerceAtLeast(1L) to (m["MemAvailable"] ?: 0L)
    }

    fun ping(): Int = sh("ping -c 1 -W 1 8.8.8.8 2>/dev/null | grep -o 'time=[0-9.]*' | cut -d= -f2").firstOrNull()?.toDoubleOrNull()?.toInt() ?: -1

    fun heavy(): List<Proc> {
        val re = Regex("^\\s*([\\d,]+)K: ([\\w.]+)")
        return sh("dumpsys meminfo | head -n 90").mapNotNull { re.find(it) }
            .map { Proc(it.groupValues[2], ((it.groupValues[1].replace(",", "").toLongOrNull() ?: 0L) / 1024).toInt()) }
            .filter { '.' in it.pkg && !it.pkg.startsWith("com.android.") && it.pkg != "com.gamebox.helper" && it.pkg != pkg }
            .distinctBy { it.pkg }.take(6)
    }

    fun stats(): St {
        val o = sh(
            "echo c:\$(cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq 2>/dev/null | sort -n | tail -1)",
            "echo g:\$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null)",
            "echo f:\$(cat /sys/class/devfreq/*mali*/cur_freq /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null | head -1)",
            "echo h:\$(cat /sys/class/devfreq/*mali*/governor /sys/class/kgsl/kgsl-3d0/devfreq/governor 2>/dev/null | head -1)",
            "echo p:\$(head -1 /proc/stat)",
            "echo b:\$(cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null | tr -d ' %')"
        )
        fun v(k: String) = o.firstOrNull { it.startsWith("$k:") }?.substring(2)?.trim() ?: ""
        val n = v("p").split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        var cp = 0
        if (n.size >= 5) {
            val tot = n.sum(); val idle = n[3] + n[4]
            if (lt > 0 && tot > lt) cp = (100 * ((tot - lt) - (idle - li)) / (tot - lt)).toInt()
            lt = tot; li = idle
        }
        return St(
            ((v("c").toLongOrNull() ?: 0L) / 1000).toInt(), v("g"),
            ((v("f").toLongOrNull() ?: 0L) / 1000000).toInt(), v("h"), fps(), cp.coerceIn(0, 100), (v("b").toIntOrNull() ?: 0).coerceIn(0, 100)
        )
    }

    // FPS từ SurfaceFlinger --latency. Một game có vài layer (cửa sổ, SurfaceView, "Background for ..."),
    // nên đo mọi layer thuộc gói rồi lấy layer có nhịp khung hình cao nhất; bỏ layer nền/viền không có khung.
    private val lastTs = HashMap<String, Pair<Long, Long>>() // layer -> (mốc khung cuối, lúc mốc đó đổi lần cuối)

    fun fps(): Int {
        val p = pkg
        if (p.isBlank()) return 0
        val o = sh(
            "dumpsys SurfaceFlinger --list | grep -F \"$p\" | grep -v -e '^Background for' -e '^Bounds for' | head -n 8 | " +
                "while IFS= read -r L; do echo \"@@\$L\"; dumpsys SurfaceFlinger --latency \"\$L\"; done"
        )
        return parseFps(o)
    }

    // Đầu vào: các khối "@@<tên layer>", dòng đầu là chu kỳ làm mới, các dòng sau gồm 3 mốc thời gian (ns); cột 2 = lúc hiển thị thật.
    // Layer không có khung mới suốt hơn 2 giây (game tạm dừng) tính là 0. Có thể gọi từ nhiều luồng nên đồng bộ.
    @Synchronized
    fun parseFps(o: List<String>): Int {
        val now = android.os.SystemClock.elapsedRealtime()
        var best = 0
        var name = ""
        var ts = ArrayList<Long>()
        fun flush() {
            if (name.isEmpty() || ts.size < 2) { ts = ArrayList(); return }
            val last = ts.max()
            val prev = lastTs[name]
            if (prev == null || prev.first != last) lastTs[name] = last to now
            else if (now - prev.second > 2000L) { ts = ArrayList(); return }
            val win = ts.filter { it > last - 1_000_000_000L }.sorted()
            val span = win.last() - win.first()
            val f = if (win.size >= 3 && span > 200_000_000L) ((win.size - 1) * 1e9 / span).toInt() else win.size
            if (f > best) best = f
            ts = ArrayList()
        }
        for (raw in o) {
            val l = raw.trim()
            if (l.startsWith("@@")) { flush(); name = l.substring(2); continue }
            val c = l.split(Regex("\\s+"))
            if (c.size >= 3) {
                val t = c[1].toLongOrNull() ?: continue
                if (t > 0L && t < Long.MAX_VALUE / 2) ts.add(t)
            }
        }
        flush()
        return best.coerceIn(0, 240)
    }
}
