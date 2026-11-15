package com.gamebox.helper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun soon(ctx: Context) = Toast.makeText(ctx, "Chức năng này sẽ có ở đợt tính năng tiếp theo.", Toast.LENGTH_SHORT).show()

// ---------- Trang chủ ----------

@Composable
fun HomePage(gs: Gs, goProfile: () -> Unit) {
    val ctx = gs.ctx
    var picker by remember { mutableStateOf(false) }
    val games = gs.games
    val recent = games.filter { (gs.lastMs[it.packageName] ?: 0L) > 0L }.maxByOrNull { gs.lastMs[it.packageName] ?: 0L }
        ?: games.firstOrNull { it.packageName == gs.sel } ?: games.firstOrNull()
    val others = games.filter { it.packageName != recent?.packageName }
    fun openGs(p: String) { gs.select(p); ctx.startActivity(Intent(ctx, MainActivity::class.java).putExtra("game", p)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trang chủ", Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.End) {
                Text("PLAYER ID", fontSize = 10.sp, letterSpacing = 2.sp, color = Dim)
                Text(gs.pid, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.bounce(onClick = goProfile)) { Avatar(48.dp) }
        }
        Row(Modifier.padding(top = 18.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Logo(104.dp)
            Spacer(Modifier.width(18.dp))
            Column {
                Text("TỐI ƯU GAME", fontSize = 11.sp, letterSpacing = 4.sp, color = Cy)
                Text("GAME BOX", fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, maxLines = 1)
                Text("v${gs.version}", fontSize = 12.sp, color = Dim)
            }
        }
        HLine()
        Spacer(Modifier.height(16.dp))

        GCard(gs.hz, Modifier.fillMaxWidth().enter(60)) {
            Row(Modifier.fillMaxWidth().bounce { ctx.startActivity(Intent(ctx, MainActivity::class.java)) }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.08f)).border(1.dp, Color.White.copy(.15f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Ico(Ic.Rocket, sz = 28.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Vào", fontSize = 12.sp, color = Dim)
                    Text("Game Space", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                ArrowBtn()
            }
            Spacer(Modifier.height(14.dp))
            HLine()
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).border(1.5.dp, Cy.copy(.7f), CircleShape), contentAlignment = Alignment.Center) { Ico(Ic.Shield, tint = Cy, sz = 20.dp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Quyền truy cập", fontSize = 12.sp, color = Dim)
                    Text(accessName(gs), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Bar(accessLevel(gs) / 3f, Modifier.weight(1f))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.size(38.dp).bounce { goProfile() }.clip(CircleShape).background(Cy), contentAlignment = Alignment.Center) { Ico(Ic.Plus, tint = OnAccent, sz = 20.dp) }
            }
        }

        Row(Modifier.padding(top = 26.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Thư viện game", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${games.size}", Modifier.padding(start = 10.dp), fontSize = 14.sp, color = Dim)
            Spacer(Modifier.weight(1f))
            Pill(Modifier.bounce { picker = true }) { Text("Thêm", fontWeight = FontWeight.Medium, fontSize = 14.sp); Ico(Ic.Plus, sz = 16.dp) }
        }

        if (recent == null) {
            Panel(Modifier.fillMaxWidth()) {
                Text("Chưa có game nào", fontWeight = FontWeight.Bold)
                Text("Bấm Thêm để chọn ứng dụng đưa vào thư viện.", fontSize = 13.sp, color = Dim)
            }
        } else {
            val p = recent.packageName; val ic = gs.icon(p); val sh = RoundedCornerShape(28.dp)
            Box(Modifier.fillMaxWidth().height(210.dp).enter(120).clip(sh).border(1.dp, Color.White.copy(.15f), sh)) {
                if (ic != null) Image(ic, null, Modifier.fillMaxSize().blur(30.dp), contentScale = ContentScale.Crop, alpha = .55f)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(.55f), Color.Transparent))))
                Column(Modifier.fillMaxHeight().padding(22.dp)) {
                    Text("CHƠI GẦN ĐÂY", fontSize = 11.sp, letterSpacing = 3.sp, color = Cy)
                    Spacer(Modifier.height(8.dp))
                    Text(gs.label(p), Modifier.fillMaxWidth(.6f), fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ico(Ic.Timer, sz = 16.dp, tint = Dim)
                        Spacer(Modifier.width(6.dp))
                        Text(gs.fmtPlay(p), fontSize = 14.sp, color = Color.White.copy(.85f))
                    }
                    Spacer(Modifier.weight(1f))
                    PrimaryBtn("Chơi", icon = Ic.Play) { gs.select(p); gs.play() }
                }
                if (ic != null) Image(ic, null, Modifier.align(Alignment.CenterEnd).padding(end = 22.dp).size(108.dp).clip(RoundedCornerShape(30.dp)))
            }
        }

        if (others.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("KHÁC", fontSize = 12.sp, letterSpacing = 3.sp, color = Dim)
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth().enter(180), pad = 0.dp) {
                others.forEachIndexed { i, a ->
                    val p = a.packageName; val ic = gs.icon(p)
                    if (i > 0) HLine()
                    Row(Modifier.fillMaxWidth().bounce { openGs(p) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (ic != null) Image(ic, null, Modifier.size(50.dp).clip(RoundedCornerShape(14.dp))) else Box(Modifier.size(50.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(gs.label(p), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(gs.fmtPlay(p), fontSize = 13.sp, color = Dim)
                        }
                        Ico(Ic.Back, Modifier.rotate(180f), Dim, 18.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
    if (picker) AppPicker(gs) { picker = false }
}

// ---------- Hồ sơ ----------

@Composable
private fun MiniMetric(label: String, value: String, unit: String, frac: Float, ic: Ic, mod: Modifier) {
    Panel(mod, pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 13.sp, color = Dim)
            Ico(ic, tint = Cy, sz = 16.dp)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(unit, Modifier.padding(start = 3.dp, bottom = 4.dp), fontSize = 13.sp, color = Cy)
        }
        Spacer(Modifier.height(8.dp))
        Bar(frac, Modifier.fillMaxWidth())
    }
}

@Composable
fun ProfilePage(gs: Gs, openAct: () -> Unit, openSettings: () -> Unit) {
    val lv = accessLevel(gs)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hồ sơ", Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.Bold)
            CircleBtn(Ic.Gear, 46.dp, onClick = openSettings)
        }
        Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(132.dp)
            Spacer(Modifier.height(16.dp))
            Text(gs.pid, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(tint = Cy) { Text("MIỄN PHÍ", fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = Cy) }
                Text("Không giới hạn", fontSize = 13.sp, color = Dim)
            }
        }
        Spacer(Modifier.height(24.dp))
        GCard(gs.hz, Modifier.fillMaxWidth().enter(60)) {
            Row(Modifier.fillMaxWidth().bounce(onClick = openAct), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(.08f)).border(1.dp, Color.White.copy(.15f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    Ico(Ic.Shield, tint = Cy, sz = 26.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Quyền truy cập Game Box", fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(accessDetail(gs), fontSize = 13.sp, color = Dim)
                }
                ArrowBtn()
            }
            Spacer(Modifier.height(16.dp))
            Row { Text("Phiên truy cập", Modifier.weight(1f), color = Dim, fontSize = 14.sp); Text(gs.fmtUp(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
            Spacer(Modifier.height(12.dp))
            Row { Text("Cấp độ", Modifier.weight(1f), color = Dim, fontSize = 14.sp); Text("LV ", color = Dim, fontSize = 13.sp); Text("$lv", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    val c by animateColorAsState(if (i < lv) Cy else Color.White.copy(.12f), tween(300), label = "lv")
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(c))
                }
            }
            Spacer(Modifier.height(14.dp))
            Row { Text("Điểm hiệu năng", Modifier.weight(1f), color = Dim, fontSize = 14.sp); Text("${gs.score}", fontWeight = FontWeight.Bold); Text(" / 100", color = Dim) }
            Spacer(Modifier.height(8.dp))
            Bar(gs.score / 100f, Modifier.fillMaxWidth())
        }

        Row(Modifier.padding(top = 28.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Giám sát thời gian thực", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Pill { Text("V${gs.version}-${Build.VERSION.SDK_INT}", fontSize = 12.sp, fontWeight = FontWeight.Medium) }
        }
        Row(Modifier.fillMaxWidth().height(176.dp).enter(100), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f).fillMaxHeight(), pad = 16.dp) {
                Box(Modifier.fillMaxSize()) {
                    Ico(Ic.Chip, tint = Cy, sz = 18.dp)
                    val f = animF(gs.ram / 100f)
                    Box(Modifier.align(Alignment.TopEnd).width(10.dp).fillMaxHeight().clip(CircleShape).background(Color.White.copy(.1f))) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(f.coerceIn(.02f, 1f)).clip(CircleShape).background(Cy))
                    }
                    Column(Modifier.align(Alignment.BottomStart)) {
                        Text("RAM", fontSize = 13.sp, color = Dim)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${animInt(gs.ram)}", fontSize = 42.sp, fontWeight = FontWeight.Bold)
                            Text("%", Modifier.padding(start = 3.dp, bottom = 7.dp), fontSize = 16.sp, color = Cy)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniMetric("Bộ nhớ", "${gs.disk}", "%", gs.disk / 100f, Ic.Layers, Modifier.weight(1f).fillMaxWidth())
                MiniMetric("CPU", f1((gs.st?.cpu ?: 0) / 1000.0), "GHz", (gs.st?.cpuP ?: 0) / 100f, Ic.Gauge, Modifier.weight(1f).fillMaxWidth())
            }
        }
        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth().enter(160), pad = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(Ic.Net, tint = Cy, sz = 24.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { Text("Mạng", fontSize = 14.sp); Text("8.8.8.8", fontSize = 12.sp, color = Dim) }
                Text(if (gs.ping < 0) "—" else "${animInt(gs.ping)}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(" ms", Modifier.padding(top = 8.dp), fontSize = 13.sp, color = Cy)
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).border(1.5.dp, Cy.copy(.6f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Ico(Ic.Phone, tint = Cy, sz = 28.dp) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(gs.model, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Android ${Build.VERSION.RELEASE} • ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}", fontSize = 13.sp, color = Dim)
            }
        }
        Spacer(Modifier.height(130.dp))
    }
}

// ---------- Tối ưu game ----------

@Composable
fun TabChip(ic: Ic, t: String, on: Boolean, onClick: () -> Unit) {
    val s = RoundedCornerShape(18.dp)
    val bg by animateColorAsState(if (on) Cy.copy(.14f) else Color.White.copy(.04f), tween(220), label = "tcb")
    val bc by animateColorAsState(if (on) Cy.copy(.6f) else Color.White.copy(.1f), tween(220), label = "tcc")
    val c by animateColorAsState(if (on) Cy else Color.White.copy(.75f), tween(220), label = "tct")
    Row(Modifier.bounce(onClick = onClick).clip(s).background(bg).border(1.dp, bc, s).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(on, enter = fadeIn(tween(200)) + expandHorizontally(tween(240)), exit = fadeOut(tween(100)) + shrinkHorizontally(tween(200))) {
            Row { Ico(ic, tint = c, sz = 18.dp); Spacer(Modifier.width(6.dp)) }
        }
        Text(t, color = c, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun OptPage(gs: Gs) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Text("Tối ưu game", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Mapping & màn hình cảm ứng", fontSize = 14.sp, color = Dim)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip(Ic.Game, "Mapping", tab == 0) { tab = 0 }
            TabChip(Ic.Touch, "Kiểm tra cảm ứng", tab == 1) { tab = 1 }
            TabChip(Ic.Bandage, "Sửa cảm ứng", tab == 2) { tab = 2 }
        }
        Spacer(Modifier.height(16.dp))
        AnimatedContent(tab, Modifier.fillMaxWidth(), label = "opt", transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(120)) }) { t ->
            Column(Modifier.fillMaxWidth()) {
                when (t) {
                    0 -> MappingTab(gs)
                    1 -> TouchCheckTab(gs)
                    else -> TouchFixTab(gs)
                }
            }
        }
        Spacer(Modifier.height(130.dp))
    }
}

@Composable
fun MappingTab(gs: Gs) {
    val ctx = gs.ctx
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("QUẢN LÝ MAPPING", Modifier.weight(1f), fontSize = 12.sp, letterSpacing = 3.sp, color = Dim)
        Pill(Modifier.bounce {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
            Toast.makeText(ctx, if (gs.importProfile(t)) "Đã nhập hồ sơ" else "Clipboard không có hồ sơ Game Box hợp lệ", Toast.LENGTH_SHORT).show()
        }) { Ico(Ic.Down, sz = 16.dp); Text("Nhập", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
    }
    Spacer(Modifier.height(14.dp))
    PrimaryBtn("Lưu cài đặt hiện tại", Modifier.fillMaxWidth(), icon = Ic.Plus) { gs.addProfile() }
    Spacer(Modifier.height(14.dp))
    if (gs.profiles.isEmpty()) {
        Panel(Modifier.fillMaxWidth(), pad = 22.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(80.dp).clip(RoundedCornerShape(22.dp)).background(Cy.copy(.08f)).border(1.dp, Cy.copy(.4f), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                    Ico(Ic.Game, tint = Cy, sz = 36.dp)
                }
                Spacer(Modifier.height(14.dp))
                Text("Chưa có hồ sơ nào", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Chỉnh Hz, FPS, độ phân giải trong Game Space rồi lưu chúng ở đây thành một hồ sơ.", fontSize = 13.sp, color = Dim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    } else {
        gs.profiles.forEach { pr ->
            Panel(Modifier.fillMaxWidth().padding(bottom = 10.dp), pad = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ic = gs.icon(pr.pkg)
                    if (ic != null) Image(ic, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))) else Box(Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pr.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        Text("Hz ${if ((pr.cfg["hz"] ?: 0) < 30) "mặc định" else pr.cfg["hz"].toString()} · Phân giải ${pr.cfg["res"] ?: 100}%", fontSize = 12.sp, color = Dim, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallBtn("Áp dụng") { gs.applyProfile(pr); Toast.makeText(ctx, "Đã áp dụng cho ${gs.label(pr.pkg)}", Toast.LENGTH_SHORT).show() }
                    SmallBtn("Chia sẻ") {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("GameBox", gs.profileJson(pr)))
                        Toast.makeText(ctx, "Đã sao chép hồ sơ vào clipboard", Toast.LENGTH_SHORT).show()
                    }
                    SmallBtn("Xoá", Bad) { gs.removeProfile(pr) }
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Panel(Modifier.fillMaxWidth(), pad = 16.dp, tint = Cy) {
        Row(verticalAlignment = Alignment.Top) {
            Ico(Ic.Info, tint = Cy, sz = 20.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                "Một hồ sơ lưu cài đặt đồ họa của một game: Hz, FPS, độ phân giải, chế độ, MSAA, driver. Macro, Kéo, Ngắm ổn định, Độ nhạy và Tâm ngắm sẽ được thêm vào hồ sơ khi overlay mới hoàn tất.",
                fontSize = 13.sp, color = Dim
            )
        }
    }
}

@Composable
private fun Cell(l: String, v: String, mod: Modifier) {
    Column(mod.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.04f)).padding(12.dp)) {
        Text(l, fontSize = 10.sp, letterSpacing = 1.5.sp, color = Dim, maxLines = 1)
        Text(v, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun TestRow(ic: Ic, title: String, sub: String, status: String, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth().padding(bottom = 10.dp).bounce(onClick = onClick), r = 20.dp, pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Cy.copy(.08f)).border(1.dp, Cy.copy(.3f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Ico(ic, tint = Cy, sz = 24.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(sub, fontSize = 12.sp, color = Dim)
            }
            Spacer(Modifier.width(8.dp))
            Text(status, Modifier.widthIn(max = 72.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Dim, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable
fun TouchCheckTab(gs: Gs) {
    val ctx = gs.ctx
    val dm = ctx.resources.displayMetrics
    val fingers = when {
        gs.pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND) -> "5+"
        gs.pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT) -> "2+"
        else -> "—"
    }
    Panel(Modifier.fillMaxWidth(), pad = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.Phone, tint = Cy, sz = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text("Màn hình máy này", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(gs.model, fontSize = 12.sp, color = Dim)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cell("TẦN SỐ QUÉT", "${gs.refresh} Hz", Modifier.weight(1f))
            Cell("TẦN SỐ LẤY MẪU", "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cell("SỐ NGÓN TỐI ĐA", fingers, Modifier.weight(1f))
            Cell("ĐỘ PHÂN GIẢI", "${maxOf(dm.widthPixels, dm.heightPixels)}×${minOf(dm.widthPixels, dm.heightPixels)}", Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(20.dp))
    Text("KIỂM TRA MÀN HÌNH", fontSize = 12.sp, letterSpacing = 3.sp, color = Dim)
    Spacer(Modifier.height(10.dp))
    TestRow(Ic.Grid, "Vùng chết", "Vuốt khắp màn hình để tìm vùng không phản hồi", "Chưa kiểm tra") { soon(ctx) }
    TestRow(Ic.Target, "Cảm ứng ma", "Không chạm màn hình trong 15 giây", "Chưa kiểm tra") { soon(ctx) }
    TestRow(Ic.Hand, "Đa điểm", "Đặt càng nhiều ngón tay càng tốt", "Chưa kiểm tra") { soon(ctx) }
    TestRow(Ic.Refresh, "Tần số lấy mẫu", "Liên tục trượt ngón tay, đo Hz cảm ứng", "Chưa kiểm tra") { soon(ctx) }
    TestRow(Ic.Pulse, "Độ chính xác / rung", "Vẽ một đường thẳng từ trái sang phải", "Chưa kiểm tra") { soon(ctx) }
}

@Composable
fun TouchFixTab(gs: Gs) {
    val ctx = gs.ctx
    val names = listOf("Đo", "Tìm lỗi", "Sửa", "Đo lại")
    val step = 0
    Panel(Modifier.fillMaxWidth(), pad = 14.dp) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 17.dp).fillMaxWidth(.75f).height(1.dp).background(Color.White.copy(.15f)))
            Row(Modifier.fillMaxWidth()) {
                names.forEachIndexed { i, n ->
                    val on = i == step
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(if (on) Color(0xFF0E2233) else Color(0xFF0B1620)).border(if (on) 2.dp else 1.dp, if (on) Cy else Color.White.copy(.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("${i + 1}", fontWeight = FontWeight.Bold, color = if (on) Color.White else Dim) }
                        Spacer(Modifier.height(6.dp))
                        Text(n, fontSize = 12.sp, color = if (on) Color.White else Dim, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Panel(Modifier.fillMaxWidth(), pad = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vòng chạm", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Vòng 1/3", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Cy)
        }
        Spacer(Modifier.height(8.dp))
        Text("3 vòng × 4 lần chạm. Chạm ô sáng nhanh nhất có thể.", fontSize = 13.sp, color = Dim)
        Spacer(Modifier.height(14.dp))
        repeat(2) { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = if (row == 0) 10.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) {
                    Box(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.04f)).border(1.dp, Color.White.copy(.08f), RoundedCornerShape(16.dp)))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryBtn("Bắt đầu đo", Modifier.fillMaxWidth(), icon = Ic.Play) { soon(ctx) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.04f)).padding(12.dp)) {
                Text("PHẢN HỒI TB", Modifier.weight(1f), fontSize = 10.sp, letterSpacing = 1.5.sp, color = Dim); Text("—", fontWeight = FontWeight.Bold)
            }
            Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.04f)).padding(12.dp)) {
                Text("TRƯỢT", Modifier.weight(1f), fontSize = 10.sp, letterSpacing = 1.5.sp, color = Dim); Text("0", fontWeight = FontWeight.Bold)
            }
        }
    }
}
