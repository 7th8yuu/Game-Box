package com.gamebox.helper

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Cách game render (OpenGL ES hay Vulkan). Game Unity đọc tham số khởi động từ extra "unity" của Intent mở game
// (tài liệu Unity: adb shell am start -n <gói>/<activity> -e unity <tham số>; ví dụ -force-vulkan, -force-gles20) nên Game Box
// chỉ việc thêm extra này lúc bấm Chơi. Không đưa mã vào game. Chỉ có tác dụng khi game khởi động mới và chỉ với game Unity
// mà activity của nó chuyển tiếp extra đó; vì không chắc nên sau khi game mở app đo lại API game thực sự dùng và ghi nhật ký.
object Render {
    val names = listOf("Tự động", "OpenGL ES", "Vulkan")

    fun arg(m: Int): String = when (m) { 1 -> "-force-gles30"; 2 -> "-force-vulkan"; else -> "" }

    // Đo API game đang dùng, nhớ kết quả cho từng game (hiện ở màn Kết cấu) và báo nếu khác với lựa chọn
    fun measure(sp: SharedPreferences, pkg: String, want: Int, say: (String) -> Unit) {
        if (Rt.backend == "none") return
        val d = GfxLab.detect(pkg)
        val t = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        if (!d.running) { Wm.log(sp, "Render: game không còn chạy lúc đo"); return }
        if (!d.readable) { Wm.log(sp, "Render: ${d.note}"); sp.edit().putString("rnd_seen_$pkg", "?|$t").apply(); return }
        sp.edit().putString("rnd_seen_$pkg", "${d.api}|$t").apply()
        val wantApi = when (want) { 1 -> "gles"; 2 -> "vulkan"; else -> "" }
        Wm.log(sp, "Render: game đang dùng ${d.note}${if (wantApi.isNotEmpty()) " · đã chọn ${names[want]} -> ${if (wantApi == d.api) "KHỚP" else "KHÔNG KHỚP"}" else ""}")
        if (wantApi.isNotEmpty() && wantApi != d.api) say("Game không nhận lệnh chọn ${names[want]} (đang dùng ${if (d.api == "vulkan") "Vulkan" else "OpenGL ES"})")
    }

    // Câu mô tả lần đo gần nhất cho màn Kết cấu
    fun seenText(sp: SharedPreferences, pkg: String): String {
        val v = (sp.getString("rnd_seen_$pkg", "") ?: "").split("|")
        val api = when (v.getOrNull(0)) { "vulkan" -> "Vulkan"; "gles" -> "OpenGL ES"; "?" -> "không đọc được"; else -> return "Chưa đo: bấm Chơi từ Game Box, app sẽ đo sau khi game mở." }
        return "Lần chơi gần nhất game dùng: $api (đo lúc ${v.getOrNull(1) ?: "?"})."
    }
}
