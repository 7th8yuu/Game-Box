package com.gamebox.helper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Hộp Chẩn đoán: gom trạng thái quyền, kết quả vài lệnh thử, nhật ký đổi độ phân giải và số liệu đo FPS để biết chính xác
// vì sao một tính năng không chạy trên máy thật. Có nút sao chép để gửi lại.
object Diag {
    fun report(gs: Gs): String {
        val sb = StringBuilder()
        fun line(s: String = "") { sb.append(s).append('\n') }
        line("Game Box ${gs.version} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val shz = Rt.shizukuState()
        line("Quyền: đang dùng=${Rt.backend} · Shizuku=${when (shz) { 2 -> "đã cấp quyền"; 1 -> "chạy, chưa cấp quyền"; else -> "không chạy" }}")
        line("Lỗi chạy lệnh gần nhất: ${Rt.lastErr.ifEmpty { "không có" }}")
        line()
        line("== Lệnh thử ==")
        if (Rt.backend == "none") line("(chưa có quyền Root/Shizuku nên không chạy được lệnh nào)")
        else {
            line("id: " + Rt.sh("id").firstOrNull().orEmpty().ifEmpty { "(không có kết quả)" })
            val wm = Rt.sh("wm size", "wm density")
            line("wm: " + if (wm.isEmpty()) "(không có kết quả)" else wm.joinToString(" | "))
            val sf = Rt.sh("dumpsys SurfaceFlinger --list | wc -l")
            line("SurfaceFlinger --list: " + (sf.firstOrNull()?.trim() ?: "(không có kết quả)") + " layer")
            val ts = Rt.sh("dumpsys SurfaceFlinger --timestats -dump 2>&1 | head -n 2")
            line("SurfaceFlinger --timestats: " + if (ts.isEmpty()) "(không có kết quả)" else ts.joinToString(" | ").take(120))
        }
        line()
        line("== Phiên chơi ==")
        line("game=${Rt.pkg.ifEmpty { "(không có)" }} · phiên lưu=${gs.sp.getString("sess", "")?.ifEmpty { "(không)" }} · dịch vụ overlay=${if (Overlay.alive) "đang chạy" else "không chạy"}")
        line()
        line("== Độ phân giải ==")
        val i = if (Rt.backend == "none") null else Wm.read()
        line("hiện tại: " + (i?.let { "vật lý ${it.pw}x${it.ph}/${it.pd}, kích thước ${if (it.ow > 0) "đang ghi đè ${it.ow}x${it.oh}" else "mặc định"}, mật độ ${if (it.od > 0) "đang ghi đè ${it.od}" else "mặc định"}" } ?: "không đọc được"))
        line("cài đặt đã lưu cho ${gs.sel.ifEmpty { "(chưa chọn game)" }}: mức=${gs.g("res", 100)}% · cách=${if (gs.g("texm", 0) == 0) "cả màn hình" else "Game Mode"} · fps=${gs.g("fps", 5)} · chế độ=${gs.g("gm", 0)}")
        // Mức giảm đã lưu của MỌI game: nếu bấm Chơi ở một game khác với game đã lưu mức giảm thì sẽ không có gì được hạ
        val saved = gs.sp.all.keys.filter { it.startsWith("res_") }.sortedBy { it }.joinToString("; ") { "${it.removePrefix("res_")}=${gs.sp.getInt(it, 100)}% (cách ${if (gs.sp.getInt("texm_" + it.removePrefix("res_"), 0) == 0) "cả màn hình" else "Game Mode"})" }
        line("mức giảm đã lưu của từng game: ${saved.ifEmpty { "(chưa game nào)" }}")
        line("app đang giữ hạ=${gs.sp.getBoolean("wm_on", false)} · gốc=${gs.sp.getString("wm_o", "")} · mục tiêu=${gs.sp.getString("wm_t", "")}")
        line("nhật ký (cũ -> mới):")
        val log = Wm.logText(gs.sp)
        line(if (log.isEmpty()) "  (chưa có lần áp nào)" else log.lines().joinToString("\n") { "  $it" })
        line()
        line("== FPS ==")
        line("hiện tại=${Rt.fpsNow} (-1 = không đo được)")
        line("40 lần đo gần nhất: ${Rt.meter.recent()}")
        line("lần đo mới nhất:")
        line(Rt.meter.diag.lines().joinToString("\n") { "  $it" })
        line("lần đo gần nhất có thấy layer của game:")
        line(Rt.meter.diagSeen.lines().joinToString("\n") { "  $it" })
        return sb.toString()
    }
}

@Composable
fun DiagDialog(gs: Gs, close: () -> Unit) {
    var text by remember { mutableStateOf("Đang thu thập...") }
    var tick by remember { mutableIntStateOf(0) }
    var testing by remember { mutableStateOf(false) }
    var testOut by remember { mutableStateOf("") }
    LaunchedEffect(tick) { text = withContext(Dispatchers.IO) { runCatching { Diag.report(gs) }.getOrElse { "Lỗi khi thu thập: $it" } } }
    Dialog(close, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(.94f).fillMaxHeight(.86f).clip(RoundedCornerShape(26.dp)).background(Color(0xFF0E1822)).border(1.dp, Color.White.copy(.12f), RoundedCornerShape(26.dp)).padding(18.dp)) {
            Text("Chẩn đoán", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Nếu một tính năng không chạy, bấm Sao chép rồi gửi nội dung này.", fontSize = 12.sp, color = Dim)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(.35f)).padding(10.dp)) {
                Text((if (testOut.isEmpty()) "" else "== Thử hạ 5 giây ==\n$testOut\n") + text, Modifier.verticalScroll(rememberScrollState()), fontSize = 11.sp, lineHeight = 15.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(10.dp))
            GhostBtn(if (testing) "Đang thử... màn hình sẽ thu nhỏ rồi tự trả lại" else "Thử hạ độ phân giải 5 giây", Modifier.fillMaxWidth()) {
                if (!testing) {
                    testing = true; testOut = "Đang thử..."
                    kotlin.concurrent.thread {
                        val r = runCatching { Wm.selfTest(gs.sp) }.getOrElse { "Lỗi khi thử: $it" }
                        android.os.Handler(android.os.Looper.getMainLooper()).post { testOut = r; testing = false; tick++ }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostBtn("Làm mới", Modifier.weight(1f)) { tick++ }
                GhostBtn("Sao chép", Modifier.weight(1.2f)) {
                    val cm = gs.ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Game Box chẩn đoán", text))
                    android.widget.Toast.makeText(gs.ctx, "Đã sao chép", android.widget.Toast.LENGTH_SHORT).show()
                }
                PrimaryBtn("Đóng", Modifier.weight(.8f), onClick = close)
            }
        }
    }
}
