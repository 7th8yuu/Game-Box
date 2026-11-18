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

    // Cơ sở để tính mức giảm: giá trị đang dùng của máy (kể cả khi người dùng tự chỉnh mật độ, ví dụ 230) để kích thước dp
    // trong game không đổi. Nếu app đã hạ rồi mà chưa trả lại (wm_on) thì dùng giá trị gốc đã lưu, tránh giảm chồng.
    private fun base(sp: SharedPreferences?, i: Info): Triple<Int, Int, Int> {
        var ow = i.ow; var oh = i.oh; var od = i.od
        if (sp != null && sp.getBoolean("wm_on", false)) {
            val o = (sp.getString("wm_o", "0x0|0") ?: "0x0|0").split("|")
            val z = o.getOrElse(0) { "0x0" }.split("x")
            ow = z.getOrNull(0)?.toIntOrNull() ?: 0; oh = z.getOrNull(1)?.toIntOrNull() ?: 0
            od = o.getOrElse(1) { "0" }.toIntOrNull() ?: 0
        }
        val sized = ow > 0 && oh > 0
        return Triple(if (sized) ow else i.pw, if (sized) oh else i.ph, if (od > 0) od else i.pd)
    }

    // Kích thước và mật độ mới cho tỉ lệ pct (%), tính từ cơ sở ở trên
    fun target(i: Info, pct: Int, sp: SharedPreferences? = null): Triple<Int, Int, Int> {
        val s = pct.coerceIn(30, 95) / 100.0
        val (bw, bh, bd) = base(sp, i)
        fun even(v: Double) = (Math.round(v / 2.0) * 2).toInt()
        return Triple(even(bw * s).coerceAtLeast(240), even(bh * s).coerceAtLeast(320), Math.round(bd * s).toInt().coerceAtLeast(72))
    }

    fun log(sp: SharedPreferences, msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val old = (sp.getString("wm_log", "") ?: "").split("\n").filter { it.isNotEmpty() }
        sp.edit().putString("wm_log", (old + "$t $msg").takeLast(16).joinToString("\n")).apply()
    }

    fun logText(sp: SharedPreferences): String = sp.getString("wm_log", "") ?: ""

    // Kết quả lần áp gần nhất bằng lời, hiện thành thông báo ngay khi bấm Chơi để biết đã hạ được hay chưa
    @Volatile var lastMsg = ""

    private fun fail(sp: SharedPreferences, why: String): Boolean {
        lastMsg = "Không hạ được độ phân giải: $why"
        log(sp, lastMsg)
        return false
    }

    // Trả về true nếu đã đổi độ phân giải và đọc lại thấy đúng. pct >= 95 thì không đổi gì
    fun apply(sp: SharedPreferences, pct: Int): Boolean {
        lastMsg = ""
        if (pct >= 95) return false
        if (Rt.backend == "none") return fail(sp, "chưa có quyền Root/Shizuku")
        val i = read() ?: return fail(sp, "không đọc được kích thước màn hình (wm size)${Rt.lastErr.let { if (it.isEmpty()) "" else " · $it" }}")
        val (w, h, d) = target(i, pct, sp)
        if (!sp.getBoolean("wm_on", false)) {
            // Lưu giá trị gốc (0x0 / 0 = mặc định của máy) trước khi đổi lần đầu
            sp.edit().putBoolean("wm_on", true).putString("wm_o", "${i.ow}x${i.oh}|${i.od}").apply()
        }
        // 2>&1 để thấy lời báo lỗi của chính lệnh wm (bình thường bị nuốt), ghi vào nhật ký khi không có hiệu lực
        val said = Rt.sh("wm size ${w}x$h 2>&1", "wm density $d 2>&1").joinToString(" | ").trim()
        // Đọc lại để biết lệnh có thật sự có hiệu lực (một số máy từ chối âm thầm hoặc áp chậm nên thử hai lần)
        var j: Info? = null
        var ok = false
        for (n in 0 until 2) {
            Thread.sleep(if (n == 0) 300L else 700L)
            j = read()
            ok = j != null && j.ow == w && j.oh == h
            if (ok) break
        }
        log(sp, "áp $pct%: máy ${i.pw}x${i.ph}/${i.pd}${if (i.ow > 0 || i.od > 0) " (đang chỉnh ${if (i.ow > 0) "${i.ow}x${i.oh}" else "-"}/${if (i.od > 0) i.od else "-"})" else ""} -> ${w}x$h/$d, đọc lại ${j?.let { "${it.ow}x${it.oh}/${it.od}" } ?: "lỗi"} ${if (ok) "ĐÚNG" else "KHÔNG KHỚP"}${if (said.isNotEmpty()) " · máy trả lời: ${said.take(160)}" else ""}")
        if (ok) {
            sp.edit().putString("wm_t", "${w}×$h").apply()
            lastMsg = "Đã hạ độ phân giải xuống ${w}×$h ($pct%)"
            return true
        }
        restore(sp, "áp không có hiệu lực")
        lastMsg = "Không hạ được độ phân giải: máy không nhận lệnh (đọc lại ${j?.let { if (it.ow > 0) "${it.ow}x${it.oh}" else "vẫn mặc định" } ?: "lỗi"})${if (said.isNotEmpty()) " · " + said.take(80) else ""}"
        return false
    }

    // Trả lại độ phân giải gốc nếu app đã đổi nó. Không có quyền thì giữ cờ để lần sau thử lại
    fun restore(sp: SharedPreferences, why: String = "") {
        Angle.restore(sp, why) // kết cấu thật (ANGLE) cùng vòng đời với phiên chơi nên trả lại cùng lúc
        if (!sp.getBoolean("wm_on", false) || Rt.backend == "none") return
        val o = (sp.getString("wm_o", "0x0|0") ?: "0x0|0").split("|")
        val sz = o.getOrElse(0) { "0x0" }; val dn = o.getOrElse(1) { "0" }
        Rt.sh(if (sz == "0x0") "wm size reset" else "wm size $sz", if (dn == "0") "wm density reset" else "wm density $dn")
        log(sp, "khôi phục${if (why.isEmpty()) "" else ": $why"}")
        sp.edit().remove("wm_on").remove("wm_o").remove("wm_t").apply()
    }

    // Thử độc lập với game: hạ xuống 70% khoảng 5 giây rồi trả lại, trả về báo cáo từng bước kèm lời máy trả lời.
    // Dùng trong hộp Chẩn đoán để biết riêng lệnh wm có chạy được trên máy này không, tách khỏi luồng bấm Chơi.
    fun selfTest(sp: SharedPreferences): String {
        val b = StringBuilder()
        fun l(s: String) { b.append(s).append('\n') }
        fun st(i: Info?) = i?.let { "ghi đè ${if (it.ow > 0) "${it.ow}x${it.oh}" else "không"} · mật độ ${if (it.od > 0) it.od else "mặc định"}" } ?: "không đọc được"
        if (Rt.backend == "none") return "Chưa có quyền Root/Shizuku nên không thử được."
        if (sp.getBoolean("wm_on", false)) return "App đang giữ độ phân giải đã hạ (phiên chơi chưa kết thúc). Bấm Khôi phục độ phân giải rồi thử lại."
        val i = read() ?: return "Không đọc được wm size/density${Rt.lastErr.let { if (it.isEmpty()) "" else " · $it" }}."
        l("1. Trước: máy ${i.pw}x${i.ph}/${i.pd} · ${st(i)}")
        val (w, h, d) = target(i, 70, null)
        l("2. Thử: wm size ${w}x$h + wm density $d")
        sp.edit().putBoolean("wm_on", true).putString("wm_o", "${i.ow}x${i.oh}|${i.od}").apply()
        val said = Rt.sh("wm size ${w}x$h 2>&1", "wm density $d 2>&1")
        l("3. Máy trả lời: ${if (said.isEmpty()) "(không in gì, bình thường)" else said.joinToString(" | ").take(200)}")
        Thread.sleep(1500)
        val j = read()
        val ok = j != null && j.ow == w && j.oh == h
        l("4. Sau 1,5 giây: ${st(j)} -> ${if (ok) "ĐÃ ĐỔI" else "KHÔNG ĐỔI"}")
        Thread.sleep(3500)
        val o2 = Rt.sh(if (i.ow > 0) "wm size ${i.ow}x${i.oh} 2>&1" else "wm size reset 2>&1", if (i.od > 0) "wm density ${i.od} 2>&1" else "wm density reset 2>&1")
        sp.edit().remove("wm_on").remove("wm_o").remove("wm_t").apply()
        Thread.sleep(700)
        val k = read()
        l("5. Đã trả lại: ${st(k)}${if (o2.isNotEmpty()) " · máy trả lời: ${o2.joinToString(" | ").take(120)}" else ""}")
        l(if (ok) "KẾT LUẬN: máy nhận lệnh wm, lỗi nằm ở luồng bấm Chơi, xem nhật ký bên dưới." else "KẾT LUẬN: máy không nhận lệnh wm bằng quyền hiện tại.")
        log(sp, "thử 5 giây: ${if (ok) "máy nhận lệnh" else "máy KHÔNG nhận lệnh"}${if (said.isNotEmpty()) " · ${said.joinToString(" | ").take(120)}" else ""}")
        return b.toString()
    }

    // Nút khẩn cấp trong cài đặt: về mặc định của máy bất kể đang ở trạng thái nào
    fun forceReset(sp: SharedPreferences) {
        if (Rt.backend == "none") return
        Angle.restore(sp, "nút khẩn cấp")
        Rt.sh("wm size reset", "wm density reset")
        log(sp, "khôi phục: nút khẩn cấp")
        sp.edit().remove("wm_on").remove("wm_o").remove("wm_t").apply()
    }
}
