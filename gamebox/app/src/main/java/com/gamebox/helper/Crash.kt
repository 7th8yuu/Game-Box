package com.gamebox.helper

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

// Ghi lại stack trace khi app crash để lần mở sau hiện ra màn báo lỗi thay vì chết lặng.
class CrashApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val f = Crash.file(base)
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                f.writeText("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nThread: ${t.name}\n\n${Log.getStackTraceString(e)}")
            }
            old?.uncaughtException(t, e)
        }
    }
}

object Crash {
    fun file(c: Context) = File(c.filesDir, "crash.txt")

    fun pending(c: Context): Boolean = file(c).let { it.exists() && it.length() > 0 }

    // Màn báo lỗi dựng bằng View thường, không phụ thuộc Compose
    fun view(a: Activity, onContinue: () -> Unit): View {
        val f = file(a)
        val txt = runCatching { f.readText() }.getOrDefault("")
        val d = a.resources.displayMetrics.density
        val pad = (16 * d).toInt()
        val tv = TextView(a).apply {
            text = txt; textSize = 11f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true)
            setTextColor(0xFFE6EDF3.toInt()); setPadding(pad, pad, pad, pad)
        }
        val head = TextView(a).apply {
            text = "Game Box bị lỗi ở lần mở trước. Chụp màn hình này gửi lại để sửa."
            textSize = 14f; setTextColor(0xFF7DD3FC.toInt()); setPadding(pad, pad, pad, 0)
        }
        val copy = Button(a).apply {
            text = "Sao chép"
            setOnClickListener { a.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("crash", txt)) }
        }
        val go = Button(a).apply { text = "Xoá báo lỗi và thử lại"; setOnClickListener { f.delete(); onContinue() } }
        val bar = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(go, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF0A1118.toInt())
            addView(head); addView(bar)
            addView(ScrollView(a).apply { addView(tv) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }
}
