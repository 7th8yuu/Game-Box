package com.gamebox.helper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// Phòng thử đồ họa: thử lần lượt từng cách có thể giảm tải kết cấu/đồ họa của game (độ phân giải, ANGLE, layer OpenGL ES,
// layer Vulkan, Game Mode) ngay trên máy này và báo cái nào dùng được, cái nào bị Android khóa kèm lý do.
// Mọi bước chỉ ĐỌC trạng thái, trừ bước thử độ phân giải 5 giây (tự trả lại). Không đưa mã vào tiến trình game.
//
// Kiến thức nền (mã nguồn Android, GraphicsEnvironment/egl_layers): layer GLES/Vulkan và gói ANGLE gỡ lỗi chỉ được nạp vào một game khi
// (1) game là bản debug (tiến trình có cờ dumpable), hoặc (2) máy là bản userdebug/eng, hoặc (3) game tự khai
// com.android.graphics.injectLayers.enable trong manifest (chỉ có ở app mục tiêu API 30+).
object GfxLab {
    // tag: OK dùng được · KHÓA bị Android chặn · KHÔNG không có/thất bại · i thông tin
    class Row(val tag: String, val name: String, val detail: String)

    private val pkgRx = Regex("[A-Za-z0-9_.]+")
    private fun one(cmd: String): String = Rt.sh(cmd).joinToString(" ") { it.trim() }.trim()

    // Kết quả dò thư viện đồ họa game đã nạp. api: "vulkan" | "gles" | "?" (không đọc được / game chưa chạy)
    class Seen(val running: Boolean, val readable: Boolean, val api: String, val note: String, val angle: Boolean, val libs: List<String>)

    // Đọc /proc/<pid>/maps của game (cần root). Driver Vulkan của máy (vulkan.<chip>.so...) đã nạp = game đang vẽ bằng Vulkan;
    // chỉ có libvulkan.so chung thì có thể game chỉ dò Vulkan.
    fun detect(pkg: String): Seen {
        if (!pkgRx.matches(pkg)) return Seen(false, false, "?", "chưa chọn game", false, emptyList())
        val pid = one("pidof $pkg").split(" ").firstOrNull().orEmpty()
        if (pid.isEmpty()) return Seen(false, false, "?", "game chưa chạy", false, emptyList())
        val libs = Rt.sh("grep -o -E '[A-Za-z0-9_.-]*(vulkan|GLES|EGL|angle)[A-Za-z0-9_.-]*\\.so' /proc/$pid/maps | sort -u | head -n 16").map { it.trim() }.filter { it.isNotEmpty() }
        if (libs.isEmpty()) return Seen(true, false, "?", "không đọc được /proc/$pid/maps (cần quyền root; Shizuku chế độ adb không đọc được)", false, libs)
        val angle = libs.any { it.contains("angle", true) }
        val vkLoader = libs.any { it == "libvulkan.so" }
        val vkDriver = libs.any { it != "libvulkan.so" && it.contains("vulkan", true) }
        return when {
            vkDriver -> Seen(true, true, "vulkan", "Vulkan (đã nạp driver Vulkan của máy, nhiều khả năng game vẽ bằng Vulkan)", angle, libs)
            vkLoader -> Seen(true, true, "gles", "OpenGL ES (chỉ nạp thư viện Vulkan chung, có thể game chỉ dò Vulkan)", angle, libs)
            else -> Seen(true, true, "gles", "OpenGL ES (không thấy Vulkan)", angle, libs)
        }
    }

    fun run(sp: SharedPreferences, pkg: String, screenTest: Boolean, progress: (String) -> Unit): List<Row> {
        val rows = ArrayList<Row>()
        fun add(tag: String, name: String, d: String) { rows.add(Row(tag, name, d)) }
        if (Rt.backend == "none") return listOf(Row("KHÔNG", "Quyền", "Chưa có Root/Shizuku nên không thử được gì. Mở Shizuku và cấp quyền cho Game Box."))

        progress("Đọc thông tin máy...")
        val dev = one("echo \"chip=\$(getprop ro.board.platform) · egl=\$(getprop ro.hardware.egl) · vulkan=\$(getprop ro.hardware.vulkan) · bản=\$(getprop ro.build.type) · ro.debuggable=\$(getprop ro.debuggable) · selinux=\$(getenforce)\"")
        add("i", "Máy", dev)
        val type = one("getprop ro.build.type")
        val sysDebug = type == "userdebug" || type == "eng" || one("getprop ro.debuggable") == "1"

        // Thông tin game và thư viện đồ họa nó đã nạp
        var appDebug = false
        var angleLoaded = false
        var vkUsed = false
        if (!pkgRx.matches(pkg)) add("i", "Game", "Chưa chọn game. Chọn một game ở Game Space rồi chạy lại.")
        else {
            progress("Đọc thông tin game...")
            val flags = one("dumpsys package $pkg | grep -m3 -E 'flags=|reqGlEsVersion|targetSdk='")
            appDebug = flags.contains("DEBUGGABLE")
            add("i", "Game $pkg", (flags.ifEmpty { "không đọc được thông tin gói" }) + " -> " + if (appDebug) "game là bản debug" else "game KHÔNG phải bản debug")
            progress("Xem thư viện đồ họa game đã nạp...")
            val d = detect(pkg)
            angleLoaded = d.angle
            vkUsed = d.api == "vulkan"
            when {
                !d.running -> add("i", "Game dùng đồ họa gì", "Game chưa chạy. Mở game từ Game Box, ở lại trong game vài giây rồi mở Phòng thử và chạy lại để biết game nạp OpenGL ES hay Vulkan.")
                !d.readable -> add("KHÔNG", "Game dùng đồ họa gì", d.note)
                else -> add("i", "Game dùng đồ họa gì", "${d.note} · ANGLE: ${if (d.angle) "đang nạp" else "không"} · thư viện: ${d.libs.joinToString(" ")}")
            }
        }

        progress("Thử layer OpenGL ES / Vulkan...")
        val cfg = one("echo \"enable_gpu_debug_layers=\$(settings get global enable_gpu_debug_layers) gpu_debug_app=\$(settings get global gpu_debug_app) layers_gles=\$(settings get global gpu_debug_layers_gles) debug.gles.layers=[\$(getprop debug.gles.layers)] debug.vulkan.layers=[\$(getprop debug.vulkan.layers)]\"")
        val canLoad = appDebug || sysDebug
        val why = "Android chỉ nạp layer ngoài vào game khi game là bản debug, hoặc máy là bản userdebug, hoặc game tự bật trong manifest của nó. Máy này là bản '$type' và game ${if (appDebug) "là bản debug" else "không phải bản debug"}"
        if (canLoad) {
            add("i", "Layer OpenGL ES", "Điều kiện nạp đã đủ ($cfg). Nhưng cần một thư viện layer chặn glTexImage2D/glTexParameteri để thật sự giảm kết cấu, và Game Box chưa có thư viện đó.")
            add("i", "Layer Vulkan", "Điều kiện nạp đã đủ. Cần một layer chặn lệnh tạo ảnh/lấy mẫu của Vulkan, Game Box chưa có.")
        } else {
            add("KHÓA", "Layer OpenGL ES", "$why, nên layer OpenGL ES không được nạp vào game này. ($cfg)")
            add("KHÓA", "Layer Vulkan", "Cùng điều kiện với OpenGL ES nên layer Vulkan cũng bị khóa.")
        }

        progress("Thử ANGLE...")
        val ap = Angle.probe(sp)
        when {
            angleLoaded -> add("OK", "ANGLE", "Game đang nạp thư viện ANGLE, kết cấu thật qua ANGLE dùng được.")
            ap.ok -> add("OK", "ANGLE", "Máy có gói ANGLE hệ thống (${ap.pkg}), kết cấu thật qua ANGLE dùng được. Cần đóng hẳn rồi mở lại game từ Game Box.")
            else -> add("KHÓA", "ANGLE", ap.why().replaceFirstChar { it.uppercase() } + if (canLoad) "." else ". Gói ANGLE tự cài cũng không nạp được vì dùng chung điều kiện debug ở trên.")
        }

        progress("Xem Game Mode...")
        val gm = if (pkgRx.matches(pkg)) one("dumpsys game 2>&1 | grep -i -A5 -F $pkg | head -n 8") else ""
        add("i", "Game Mode (cmd game, hạ độ phân giải theo từng game)", (if (gm.isEmpty()) "không có cấu hình riêng cho game này" else gm.take(300)) + " · Android nhận lệnh downscale nhưng chỉ đổi với game khai báo hỗ trợ; Free Fire không đổi trên máy đã thử.")

        var resOk = false
        if (screenTest) {
            progress("Thử hạ độ phân giải 5 giây (màn hình sẽ thu nhỏ rồi tự trả lại)...")
            val t = Wm.selfTest(sp)
            resOk = t.contains("ĐÃ ĐỔI")
            add(if (resOk) "OK" else "KHÔNG", "Độ phân giải màn hình (wm size)", t.lines().filter { it.isNotEmpty() }.joinToString(" | "))
        } else add("i", "Độ phân giải màn hình (wm size)", "Bỏ qua bước thử 5 giây (thử riêng bằng nút ở Chẩn đoán).")

        val ok = ArrayList<String>()
        if (resOk) ok.add("độ phân giải màn hình")
        if (angleLoaded || ap.ok) ok.add("kết cấu qua ANGLE")
        add("i", "KẾT LUẬN", if (ok.isEmpty())
            "Chưa cách nào dùng được trên máy này${if (screenTest) "" else " (chưa thử độ phân giải)"}."
        else "Dùng được: ${ok.joinToString(", ")}." + if (!(angleLoaded || ap.ok)) (if (canLoad) " Kết cấu thật cần một thư viện layer riêng (Game Box chưa có)." else " Kết cấu thật (OpenGL ES, Vulkan, ANGLE) bị Android khóa.") + " Cách còn lại giảm tải đáng kể là độ phân giải, vì mip kết cấu được chọn theo số điểm ảnh trên màn nên hạ độ phân giải cũng làm game lấy mẫu kết cấu nhỏ hơn." else "")
        add("i", "Đường vòng có root (Game Box KHÔNG làm)", "Bật ro.debuggable=1 rồi khởi động lại hệ thống sẽ mở khóa layer, nhưng nhiều game phát hiện máy ở chế độ debug (rủi ro bị khóa tài khoản), có thể treo máy khi khởi động, và bạn bè không root không dùng được. Nếu muốn thử thì tự chịu rủi ro.")
        return rows
    }

    fun format(rows: List<Row>): String = rows.joinToString("\n\n") { "[${it.tag}] ${it.name}\n${it.detail}" }
}

@Composable
fun GfxLabDialog(gs: Gs, close: () -> Unit) {
    var text by remember { mutableStateOf("Bấm \"Chạy thử\". App sẽ thử từng cách trên máy này, mất khoảng 10 giây.") }
    var running by remember { mutableStateOf(false) }
    var screenTest by remember { mutableStateOf(true) }
    Dialog(close, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(.94f).fillMaxHeight(.86f).clip(RoundedCornerShape(26.dp)).background(Color(0xFF0E1822)).border(1.dp, Color.White.copy(.12f), RoundedCornerShape(26.dp)).padding(18.dp)) {
            Text("Phòng thử đồ họa", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Thử OpenGL ES, Vulkan, ANGLE, Game Mode và độ phân giải trên máy này. Mở game trước để biết game dùng API nào.", fontSize = 12.sp, color = Dim)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(.35f)).padding(10.dp)) {
                Text(text, Modifier.verticalScroll(rememberScrollState()), fontSize = 11.sp, lineHeight = 15.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(8.dp))
            SwRow("Gồm bước thử hạ độ phân giải 5 giây", "Màn hình sẽ thu nhỏ rồi tự trả lại", screenTest, !running) { screenTest = it }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostBtn(if (running) "Đang thử..." else "Chạy thử", Modifier.weight(1f)) {
                    if (!running) {
                        running = true
                        val pkg = gs.sel
                        kotlin.concurrent.thread {
                            val post = { s: String -> android.os.Handler(android.os.Looper.getMainLooper()).post { text = s } }
                            val rows = runCatching { GfxLab.run(gs.sp, pkg, screenTest) { post("Đang thử: $it") } }.getOrElse { listOf(GfxLab.Row("KHÔNG", "Lỗi khi thử", it.toString())) }
                            val head = "Game Box ${gs.version} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} · quyền=${Rt.backend}\n\n"
                            android.os.Handler(android.os.Looper.getMainLooper()).post { text = head + GfxLab.format(rows); running = false }
                        }
                    }
                }
                GhostBtn("Sao chép", Modifier.weight(1f)) {
                    val cm = gs.ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Game Box phòng thử đồ họa", text))
                    android.widget.Toast.makeText(gs.ctx, "Đã sao chép", android.widget.Toast.LENGTH_SHORT).show()
                }
                PrimaryBtn("Đóng", Modifier.weight(.8f), onClick = close)
            }
        }
    }
}
