package com.gamebox.helper

import android.content.SharedPreferences
import android.os.Build

// "Kết cấu thật": giảm chi tiết texture của game OpenGL ES bằng cách cho game chạy qua ANGLE (lớp dịch OpenGL ES sang Vulkan
// của Android) và bật các tính năng ghi đè của ANGLE (tài liệu ANGLE: thuộc tính hệ thống debug.angle.feature_overrides_enabled,
// các tên cách nhau bằng dấu hai chấm; danh sách tính năng Vulkan có forceTextureLodOffset1..4, forceNearestMipFiltering,
// forceNearestFiltering):
//   mức Nhẹ..Cực đại  -> forceTextureLodOffset1..4 (bỏ bớt 1..4 mức mip, texture mờ/vỡ khối dần)
//   "Hard Distance Steps" -> forceNearestMipFiltering (chuyển mip theo bậc thay vì pha trộn mượt)
//   "Sharp Pixels"        -> forceNearestFiltering (lấy mẫu gần nhất, thấy rõ ô vuông)
// Android chọn ANGLE cho từng game qua Settings.Global angle_gl_driver_selection_pkgs/values, cần có một gói hệ thống ANGLE
// (khai báo action android.app.action.ANGLE_FOR_ANDROID). Máy không có gói đó thì tính năng này không dùng được và app nói rõ.
//
// Là thay đổi toàn hệ thống trong lúc chơi nên: lưu giá trị gốc, chỉ áp khi bấm Chơi trong app, tự trả lại khi game thoát
// (cùng đường khôi phục với độ phân giải, xem Wm.restore). Game phải khởi động lại mới có hiệu lực.
// Lưới an toàn: game thoát trong vài chục giây đầu sau khi bật thì tạm tắt tính năng cho đúng bản phần mềm của máy.
object Angle {
    val names = listOf("Gốc", "Nhẹ", "Retro", "Khối", "Cực đại")
    private val lod = arrayOf("", "forceTextureLodOffset1", "forceTextureLodOffset2", "forceTextureLodOffset3", "forceTextureLodOffset4")
    private const val K_PKGS = "angle_gl_driver_selection_pkgs"
    private const val K_VALS = "angle_gl_driver_selection_values"
    private const val PROP = "debug.angle.feature_overrides_enabled"
    private val pkgRx = Regex("[A-Za-z0-9_.]+")

    class Probe(val pkg: String, val system: Boolean, val vulkan: Int, val note: String, val bad: Boolean) {
        val vkOk get() = vulkan >= 0x401000   // Vulkan 1.1
        val ok get() = pkg.isNotEmpty() && system && vkOk && !bad
        fun why(): String = when {
            pkg.isEmpty() -> "máy không có gói ANGLE của hệ thống (Android cần gói này để chạy game OpenGL ES qua ANGLE)"
            !system -> "gói ANGLE ($pkg) không phải gói hệ thống nên Android không nạp cho game"
            !vkOk -> "máy chỉ hỗ trợ Vulkan thấp hơn 1.1"
            bad -> "game đã tự thoát ngay khi bật kết cấu thật trên bản phần mềm này nên đã tạm tắt"
            else -> "dùng được"
        }
    }

    @Volatile var lastOk = false

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    fun probe(sp: SharedPreferences?): Probe {
        if (Rt.backend == "none") return Probe("", false, 0, "chưa có quyền Root/Shizuku", false)
        val out = Rt.sh("pm query-activities --brief -a android.app.action.ANGLE_FOR_ANDROID 2>&1 | head -n 8")
        val rx = Regex("([A-Za-z0-9_.]+)/[A-Za-z0-9_.$]+")
        val pkg = out.firstNotNullOfOrNull { rx.find(it)?.groupValues?.get(1) } ?: ""
        val path = if (pkg.isEmpty()) "" else Rt.sh("pm path $pkg").firstOrNull().orEmpty()
        val system = Regex("^package:/(system|product|system_ext|vendor|apex)/").containsMatchIn(path)
        val vk = Rt.sh("pm list features | grep -F android.hardware.vulkan.version").firstOrNull()?.substringAfter("=", "")?.trim()?.toIntOrNull() ?: 0
        val ex = Rt.sh(
            "echo \"ro.gfx.angle.supported=\$(getprop ro.gfx.angle.supported)\"",
            "echo \"persist.graphics.egl=\$(getprop persist.graphics.egl)\"",
            "echo \"thư viện angle=\$(ls /system/lib64 /vendor/lib64/egl /system/lib64/egl 2>/dev/null | grep -i angle | head -n 3 | tr '\\n' ' ')\""
        ).joinToString(" · ") { it.trim() }
        val bad = sp != null && (sp.getString("ang_bad", "") ?: "") == Build.FINGERPRINT
        return Probe(pkg, system, vk, ex, bad)
    }

    fun report(sp: SharedPreferences): String {
        val p = probe(sp)
        val b = StringBuilder()
        b.append("gói ANGLE: ").append(p.pkg.ifEmpty { "(không có)" }).append(if (p.pkg.isNotEmpty()) if (p.system) " (hệ thống)" else " (không phải gói hệ thống)" else "").append('\n')
        b.append("Vulkan: ").append(if (p.vulkan == 0) "không thấy" else "0x${Integer.toHexString(p.vulkan)} ${if (p.vkOk) "(>= 1.1)" else "(< 1.1)"}").append('\n')
        b.append("thêm: ").append(p.note).append('\n')
        b.append("kết luận: ").append(p.why()).append('\n')
        b.append("đang giữ thay đổi: ").append(sp.getBoolean("ang_on", false))
        return b.toString()
    }

    private fun get(k: String): String = Rt.sh("settings get global $k").firstOrNull()?.trim().orEmpty().ifEmpty { "null" }

    // Danh sách tính năng ANGLE cho cấu hình này, rỗng nếu không có gì cần bật
    fun features(level: Int, hard: Boolean, sharp: Boolean): String =
        listOfNotNull(lod.getOrNull(level.coerceIn(0, 4))?.ifEmpty { null }, if (hard) "forceNearestMipFiltering" else null, if (sharp) "forceNearestFiltering" else null).joinToString(":")

    // Trả về thông báo cho người dùng (rỗng nếu không có gì cần làm). lastOk = true khi đã đặt xong và đọc lại thấy đúng.
    // force chỉ dùng trong CI để thử cơ chế đặt/đọc/trả lại mà không cần máy có gói ANGLE thật.
    fun apply(sp: SharedPreferences, pkg: String, level: Int, hard: Boolean, sharp: Boolean, force: Boolean = false): String {
        lastOk = false
        val feat = features(level, hard, sharp)
        if (feat.isEmpty()) return ""
        if (!pkgRx.matches(pkg)) return ""
        if (Rt.backend == "none") { Wm.log(sp, "Kết cấu thật: chưa có quyền Root/Shizuku"); return "Kết cấu thật: chưa có quyền Root/Shizuku" }
        if (!force) {
            val p = probe(sp)
            if (!p.ok) {
                Wm.log(sp, "Kết cấu thật: không dùng được (${p.why()})")
                return "Kết cấu thật chưa dùng được: ${p.why()}"
            }
        }
        val pk = get(K_PKGS); val vl = get(K_VALS); val pr = Rt.sh("getprop $PROP").firstOrNull()?.trim().orEmpty()
        if (!sp.getBoolean("ang_on", false)) sp.edit().putBoolean("ang_on", true).putString("ang_o", "$pk\n$vl\n$pr").apply()
        // Giữ nguyên lựa chọn ANGLE của các game khác, chỉ thêm/ghi đè game này (hai danh sách phải cùng độ dài)
        val ep = if (pk == "null" || pk.isBlank()) emptyList() else pk.split(",")
        val ev = if (vl == "null" || vl.isBlank()) emptyList() else vl.split(",")
        val keep = if (ep.size == ev.size) ep.zip(ev).filter { it.first != pkg } else emptyList()
        val np = (keep.map { it.first } + pkg).joinToString(","); val nv = (keep.map { it.second } + "angle").joinToString(",")
        Rt.sh("settings put global $K_PKGS ${q(np)}", "settings put global $K_VALS ${q(nv)}", "setprop $PROP ${q(feat)}")
        val okSet = get(K_PKGS) == np && get(K_VALS) == nv && Rt.sh("getprop $PROP").firstOrNull()?.trim() == feat
        Wm.log(sp, "Kết cấu thật: $pkg -> $feat, đọc lại ${if (okSet) "ĐÚNG" else "KHÔNG KHỚP"}")
        lastOk = okSet
        return if (okSet) "Kết cấu ${names[level.coerceIn(0, 4)]}: đã bật. Cần đóng hẳn rồi mở lại game để có hiệu lực" else "Kết cấu thật: máy không nhận thiết lập"
    }

    // Trả lại đúng giá trị gốc. Không có quyền thì giữ cờ để lần sau thử lại
    fun restore(sp: SharedPreferences, why: String = "") {
        if (!sp.getBoolean("ang_on", false) || Rt.backend == "none") return
        val o = (sp.getString("ang_o", "") ?: "").split("\n")
        val pk = o.getOrElse(0) { "null" }; val vl = o.getOrElse(1) { "null" }; val pr = o.getOrElse(2) { "" }
        Rt.sh(
            if (pk == "null" || pk.isBlank()) "settings delete global $K_PKGS" else "settings put global $K_PKGS ${q(pk)}",
            if (vl == "null" || vl.isBlank()) "settings delete global $K_VALS" else "settings put global $K_VALS ${q(vl)}",
            "setprop $PROP ${q(pr)}"
        )
        Wm.log(sp, "kết cấu thật: khôi phục${if (why.isEmpty()) "" else ": $why"}")
        sp.edit().remove("ang_on").remove("ang_o").apply()
    }

    // Sau khi game mở một lúc: xem log của Android có báo đã nạp ANGLE cho game không, ghi vào nhật ký để biết có tác dụng thật
    fun check(sp: SharedPreferences, pkg: String) {
        if (!sp.getBoolean("ang_on", false) || Rt.backend == "none" || !pkgRx.matches(pkg)) return
        val l = Rt.sh("logcat -d -t 4000 GraphicsEnvironment:V '*:S' 2>/dev/null | grep -i angle | tail -n 3")
        Wm.log(sp, "ANGLE trong log hệ thống: ${if (l.isEmpty()) "không thấy dòng nào (game có thể không nạp ANGLE)" else l.joinToString(" | ") { it.trim().takeLast(110) }}")
    }

    // Game thoát ngay sau khi bật: tạm tắt cho đúng bản phần mềm này (người dùng có thể cho thử lại)
    fun markBad(sp: SharedPreferences) {
        sp.edit().putString("ang_bad", Build.FINGERPRINT).apply()
        Wm.log(sp, "Kết cấu thật: game thoát sớm, tạm tắt trên bản phần mềm này")
    }
    fun clearBad(sp: SharedPreferences) { sp.edit().remove("ang_bad").apply() }
}
