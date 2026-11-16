package com.gamebox.helper

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Hạ độ phân giải toàn màn hình (wm size + wm density) chỉ trong phiên chơi bắt đầu từ app, rồi trả về đúng như cũ.
// Mật độ điểm ảnh giảm theo cùng tỉ lệ nên kích thước dp không đổi, giao diện game/overlay không bị phóng to.
// Trạng thái gốc được lưu trong SharedPreferences (wm_on, wm_o) để app bị tắt giữa chừng vẫn khôi phục được ở lần mở sau.
// Mọi lần áp/khôi phục đều ghi nhật ký (wm_log) kèm lý do để biết ai đã trả độ phân giải về (xem hộp Chẩn đoán).
object Wm {
    class Info(val pw: Int, val ph: Int, val pd: Int, val ow: Int, val oh: Int, val od: Int)

    private val rx = Regex("(Physical|Override) (size|density): ?(\\d+)(?:x(\\d+))?")

    // Đọc kích thước/mật độ vật lý và (nếu có) giá trị đang bị ghi đè
    fun read(): Info? {
        var pw = 0; var ph = 0; var pd = 0; var ow = 0; var oh = 0; var od = 0
        for (l in Rt.sh("wm size", "wm density")) {
            val m = rx.find(l) ?: continue
            val phys = m.groupValues[1] == "Physical"
            val a = m.groupValues[3].toInt(); val b = m.groupValues[4].toIntOrNull() ?: 0
            if (m.groupValues[2] == "size") { if (phys) { pw = a; ph = b } else { ow = a; oh = b } }
            else if (phys) pd = a else od = a
        }
        return if (pw > 0 && ph > 0 && pd > 0) Info(pw, ph, pd, ow, oh, od) else null
    }

    // Kích thước và mật độ mới cho tỉ lệ pct (%), luôn tính từ kích thước vật lý để áp lại không bị giảm chồng
    fun target(i: Info, pct: Int): Triple<Int, Int, Int> {
        val s = pct.coerceIn(30, 95) / 100.0
        fun even(v: Double) = (Math.round(v / 2.0) * 2).toInt()
        return Triple(even(i.pw * s).coerceAtLeast(240), even(i.ph * s).coerceAtLeast(320), Math.round(i.pd * s).toInt().coerceAtLeast(72))
    }

    fun log(sp: SharedPreferences, msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val old = (sp.getString("wm_log", "") ?: "").split("\n").filter { it.isNotEmpty() }
        sp.edit().putString("wm_log", (old + "$t $msg").takeLast(16).joinToString("\n")).apply()
    }

    fun logText(sp: SharedPreferences): String = sp.getString("wm_log", "") ?: ""

    // Trả về true nếu đã đổi độ phân giải và đọc lại thấy đúng. pct >= 95 thì không đổi gì
    fun apply(sp: SharedPreferences, pct: Int): Boolean {
        if (pct >= 95) return false
        if (Rt.backend == "none") { log(sp, "bỏ qua: chưa có quyền Root/Shizuku"); return false }
        val i = read()
        if (i == null) { log(sp, "bỏ qua: không đọc được wm size/density"); return false }
        if (!sp.getBoolean("wm_on", false)) {
            // Lưu giá trị gốc (0x0 / 0 = mặc định của máy) trước khi đổi lần đầu
            sp.edit().putBoolean("wm_on", true).putString("wm_o", "${i.ow}x${i.oh}|${i.od}").apply()
        }
        val (w, h, d) = target(i, pct)
        Rt.sh("wm size ${w}x$h", "wm density $d")
        Thread.sleep(300)
        // Đọc lại để biết lệnh có thật sự có hiệu lực (một số máy từ chối âm thầm)
        val j = read()
        val ok = j != null && j.ow == w && j.oh == h
        log(sp, "áp $pct%: ${i.pw}x${i.ph}/${i.pd} -> ${w}x$h/$d, đọc lại ${j?.let { "${it.ow}x${it.oh}/${it.od}" } ?: "lỗi"} ${if (ok) "ĐÚNG" else "KHÔNG KHỚP"}")
        if (ok) sp.edit().putString("wm_t", "${w}×$h").apply() else restore(sp, "áp không có hiệu lực")
        return ok
    }

    // Trả lại độ phân giải gốc nếu app đã đổi nó. Không có quyền thì giữ cờ để lần sau thử lại
    fun restore(sp: SharedPreferences, why: String = "") {
        if (!sp.getBoolean("wm_on", false) || Rt.backend == "none") return
        val o = (sp.getString("wm_o", "0x0|0") ?: "0x0|0").split("|")
        val sz = o.getOrElse(0) { "0x0" }; val dn = o.getOrElse(1) { "0" }
        Rt.sh(if (sz == "0x0") "wm size reset" else "wm size $sz", if (dn == "0") "wm density reset" else "wm density $dn")
        log(sp, "khôi phục${if (why.isEmpty()) "" else ": $why"}")
        sp.edit().remove("wm_on").remove("wm_o").remove("wm_t").apply()
    }

    // Nút khẩn cấp trong cài đặt: về mặc định của máy bất kể đang ở trạng thái nào
    fun forceReset(sp: SharedPreferences) {
        if (Rt.backend == "none") return
        Rt.sh("wm size reset", "wm density reset")
        log(sp, "khôi phục: nút khẩn cấp")
        sp.edit().remove("wm_on").remove("wm_o").remove("wm_t").apply()
    }
}
