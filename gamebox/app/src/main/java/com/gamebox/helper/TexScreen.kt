package com.gamebox.helper

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

// Màn hình Kết cấu: so sánh trước/sau bằng icon game, chọn mức giảm độ phân giải render
@Composable
fun TexScreen(gs: Gs) {
    val icon = gs.icon(gs.sel)
    val name = gs.selName
    val levels = listOf(100, 85, 70, 60, 50)
    val names = listOf("Gốc", "Nhẹ", "Retro", "Khối", "Cực đại")
    val grid = listOf(192, 96, 48, 24, 12)
    val res = gs.g("res", 100)
    var lvl by remember { mutableIntStateOf(levels.indices.minByOrNull { abs(levels[it] - res) } ?: 0) }
    var on by remember { mutableStateOf(true) }
    var pos by remember { mutableFloatStateOf(.5f) }
    val after = remember(icon, lvl) {
        icon?.let {
            val small = android.graphics.Bitmap.createScaledBitmap(it.asAndroidBitmap(), grid[lvl], grid[lvl], true)
            android.graphics.Bitmap.createScaledBitmap(small, 192, 192, false).asImageBitmap()
        }
    }
    BackHandler { gs.tex = false }
    val tag = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(.55f)).padding(horizontal = 10.dp, vertical = 4.dp)
    Box(Modifier.fillMaxSize().background(Color(0xF2080E14)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }) {
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().enter(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(44.dp).bounce { gs.tex = false }.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.08f)), contentAlignment = Alignment.Center) {
                        Ico(Ic.Back, sz = 22.dp)
                    }
                    Text("Kết cấu", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, Color.White.copy(.2f), RoundedCornerShape(20.dp))) {
                    if (icon != null && after != null) {
                        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ -> pos = (change.position.x / size.width).coerceIn(0f, 1f) }
                        }) {
                            val w = size.width.toInt(); val h = size.height.toInt()
                            drawImage(icon, dstSize = IntSize(w, h), filterQuality = FilterQuality.None)
                            clipRect(left = size.width * pos) { drawImage(after, dstSize = IntSize(w, h), filterQuality = FilterQuality.None) }
                            drawLine(Color.White, Offset(size.width * pos, 0f), Offset(size.width * pos, size.height), strokeWidth = 2.dp.toPx())
                            drawCircle(Color.White, 16.dp.toPx(), Offset(size.width * pos, size.height / 2))
                        }
                    }
                    Text("TRƯỚC", tag.align(Alignment.TopStart).padding(10.dp), fontSize = 10.sp, letterSpacing = 2.sp)
                    Text("SAU", tag.align(Alignment.TopEnd).padding(10.dp), fontSize = 10.sp, letterSpacing = 2.sp)
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.06f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Image(icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Bật cho $name", fontSize = 12.sp, color = Color.White.copy(.65f))
                    }
                    GSwitch(on) { on = it }
                }
            }
            Column(Modifier.weight(1.3f).fillMaxHeight().enter(80).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Lbl("CÀI NHANH")
                Seg(names, lvl, Modifier.fillMaxWidth()) { lvl = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Chi tiết kết cấu", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(names[lvl], color = Cy, fontWeight = FontWeight.Bold)
                }
                GSlider(lvl.toFloat(), 0f..4f, steps = 3, mod = Modifier.fillMaxWidth()) { lvl = it.roundToInt() }
                Text("Độ phân giải render của game: ${levels[lvl]}%", fontSize = 13.sp, color = Color.White.copy(.8f))
                Text("Đổi khi đang chơi? Hãy đóng game rồi mở lại.", fontSize = 12.sp, color = Color.White.copy(.65f))
                Text("Mức càng cao game càng nhẹ nhưng hình càng vỡ. Bản xem trước chỉ mô phỏng, cần Android 13+ để áp dụng.", fontSize = 12.sp, color = Color.White.copy(.65f))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GhostBtn("Đặt lại gốc", icon = Ic.Reset) { lvl = 0 }
                    PrimaryBtn("Lưu", Modifier.weight(1f), icon = Ic.Save) { gs.s("res", if (on) levels[lvl] else 100); gs.tex = false }
                }
            }
        }
    }
}
