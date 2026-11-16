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
    // Chỉ dùng cho kiểm thử dòng lệnh trong CI (debug/FpsCli.kt): chạy lệnh thẳng bằng quyền shell của app_process
    @Volatile var exec: ((String) -> List<String>)? = null
    val meter = FpsMeter { Rt.sh(it) }
    @Volatile var fpsNow = -1 // FPS mới nhất do vòng lặp của dịch vụ overlay đo, -1 = không đo được
    private var lt = 0L
    private var li = 0L

    // perfmtk: path, giá trị BẬT, giá trị TẮT. Khác nhau theo kernel MediaTek, sửa cho khớp máy.
    val PERFMTK = listOf(
        Triple("/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost", "100", "0"),
        Triple("/proc/perfmgr/boost_ctrl/cpu_ctrl/cfp_enable", "0", "1")
    )

    fun init(): String {
        // Cầu nối shell của CI (debug/Bridge.kt) coi như đã có quyền shell, giống Shizuku
        if (exec != null) { backend = "shizuku"; if (since == 0L) since = android.os.SystemClock.elapsedRealtime(); return backend }
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

    fun sh(vararg c: String): List<String> {
        exec?.let { return it("{\n" + c.joinToString("\n") + "\n} 2>/dev/null") }
        return when (backend) {
            "root" -> Shell.cmd(*c).exec().out
            "shizuku" -> exec1("{\n" + c.joinToString("\n") + "\n} 2>/dev/null")
            else -> emptyList()
        }
    }

    // Lỗi gần nhất khi chạy lệnh qua Shizuku (hiện trong hộp Chẩn đoán), rỗng nếu lần gần nhất chạy được
    @Volatile var lastErr = ""

    private fun exec1(cmd: String): List<String> = try {
        val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val r = p.inputStream.bufferedReader().readLines()
        p.waitFor()
        lastErr = ""
        r
    } catch (e: Throwable) {
        val c = e.cause ?: e
        lastErr = "${c.javaClass.simpleName}: ${c.message}"
        emptyList()
    }

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

    // Chỉnh cảm ứng an toàn, hoàn tác được: tần số quét cao nhất, hiệu ứng nhanh 0.5x, tắt tiết kiệm pin, nhấn giữ 300 ms
    fun touchTweaks(on: Boolean, maxHz: Int) {
        if (on) {
            sh("settings put system peak_refresh_rate $maxHz", "settings put system min_refresh_rate $maxHz", "settings put global low_power 0", "settings put secure long_press_timeout 300")
            anim(1)
        } else {
            sh("settings delete system peak_refresh_rate", "settings delete system min_refresh_rate", "settings delete secure long_press_timeout")
            anim(0)
        }
    }

    // Tốc độ con trỏ -7..7. Android không có chỉnh độ nhạy cảm ứng chung, chỉ có tác dụng với chuột/tay cầm
    fun pointer(v: Int) { sh("settings put system pointer_speed $v") }

    // Quay màn hình tối đa 3 phút, lưu vào /sdcard/Movies. Chạy nền để lệnh dừng không bị chờ
    fun record(on: Boolean) {
        if (on) sh("mkdir -p /sdcard/Movies", "nohup screenrecord --time-limit 180 /sdcard/Movies/GameBox_${System.currentTimeMillis()}.mp4 >/dev/null 2>&1 &")
        else sh("pkill -2 screenrecord")
    }

    // gm 0 chuẩn, 1 hiệu năng, 2 tiết kiệm. res % (chỉ dùng cho cách "Chỉ game"), fps 0 = tối đa, hz 0 = mặc định, sat 1.0 = gốc.
    // Kiểm thử trên Android 14: "--fps disable" bị từ chối (NumberFormatException) nên chỉ truyền tham số khi thật sự cần;
    // "set --mode" đòi số; game phải ở chế độ đó mới có hiệu lực và game không hỗ trợ thì lệnh "mode" báo "not supported".
    fun applyGame(p: String, gm: Int, res: Int, fps: Int, hz: Int, sat: Float) {
        var args = ""
        if (res < 95) args += String.format(Locale.US, " --downscale %.2f", (res.coerceIn(30, 90) / 5 * 5) / 100.0)
        if (fps in listOf(30, 45, 60, 90, 120)) args += " --fps $fps"
        // Cấu hình ghi đè (độ phân giải, FPS) chỉ dùng được ở chế độ khác Chuẩn, nên Chuẩn mà có yêu cầu thì dùng Hiệu năng
        val m = if (gm == 0 && args.isNotEmpty()) 1 else gm
        if (m != 0) {
            val name = arrayOf("standard", "performance", "battery")[m]
            if (args.isNotEmpty()) sh("cmd game set --mode ${m + 1}$args $p")
            val r = sh("cmd game mode $name $p")
            // Game chưa khai báo chế độ này: thử chế độ tùy chỉnh (custom) của Android 14+
            if (args.isNotEmpty() && r.any { it.contains("not supported", true) }) sh("cmd game set$args $p", "cmd game mode custom $p")
        }
        if (hz > 0) sh("settings put system peak_refresh_rate $hz", "settings put system min_refresh_rate $hz")
        if (sat != 1f) sh("service call SurfaceFlinger 1022 f $sat")
    }

    fun restore(p: String) {
        sh("cmd game reset $p", "cmd game mode standard $p", "settings delete system peak_refresh_rate", "settings delete system min_refresh_rate", "service call SurfaceFlinger 1022 f 1.0")
        anim(0); msaa(false); driver(0); pointer(0); record(false)
        sh("dumpsys SurfaceFlinger --timestats -disable >/dev/null 2>&1")
        meter.reset(); fpsNow = -1
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
            ((v("f").toLongOrNull() ?: 0L) / 1000000).toInt(), v("h"), fpsNow, cp.coerceIn(0, 100), (v("b").toIntOrNull() ?: 0).coerceIn(0, 100)
        )
    }

    // Đo FPS của game (chỉ vòng lặp của dịch vụ overlay gọi, mỗi giây một lần). Mọi nơi khác đọc Rt.fpsNow
    // để không có hai nơi cùng gọi dumpsys liên tục làm nặng máy đang chơi game.
    fun measureFps(): Int {
        val v = meter.measure(pkg, android.os.SystemClock.elapsedRealtime())
        fpsNow = v
        return v
    }
}
