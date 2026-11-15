package com.gamebox.helper

import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import java.util.zip.ZipFile

// Nhận diện GPU của máy và engine/renderer của game để gợi ý mức giảm kết cấu.
// GPU lấy bằng một EGL context nhỏ (không cần quyền); engine lấy từ danh sách thư viện native trong APK của game.
object Gpu {
    @Volatile var vendor = ""
    @Volatile var renderer = ""
    @Volatile var glVersion = ""
    private var done = false

    @Synchronized
    fun detect() {
        if (done) return
        done = true
        runCatching {
            val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val v = IntArray(2)
            if (!EGL14.eglInitialize(dpy, v, 0, v, 1)) return
            val attr = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
            val cfg = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
            EGL14.eglChooseConfig(dpy, attr, 0, cfg, 0, 1, n, 0)
            if (n[0] == 0) return
            val ctx = EGL14.eglCreateContext(dpy, cfg[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            val sf = EGL14.eglCreatePbufferSurface(dpy, cfg[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(dpy, sf, sf, ctx)
            vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: ""
            renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
            glVersion = GLES20.glGetString(GLES20.GL_VERSION) ?: ""
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(dpy, sf)
            EGL14.eglDestroyContext(dpy, ctx)
            EGL14.eglTerminate(dpy)
        }
    }

    // 0 yếu, 1 trung bình, 2 mạnh, -1 không rõ
    fun tier(r: String = renderer): Int {
        val s = r.lowercase()
        Regex("adreno.*?(\\d{3})").find(s)?.let { m ->
            val n = m.groupValues[1].toInt()
            return if (n >= 730) 2 else if (n >= 618) 1 else 0
        }
        Regex("mali-?g?(\\d{2,3})").find(s)?.let { m ->
            val n = m.groupValues[1].toInt()
            return when {
                "immortalis" in s || n >= 710 -> 2
                n in 57..99 || n in 610..699 -> 1
                else -> 0
            }
        }
        if ("mali-t" in s || "powervr" in s || "videocore" in s) return 0
        if ("xclipse" in s || "immortalis" in s) return 2
        return -1
    }

    fun tierName(t: Int) = when (t) { 0 -> "Tầm thấp"; 1 -> "Tầm trung"; 2 -> "Cao cấp"; else -> "Chưa rõ" }

    // Mức gợi ý (chỉ số trong danh sách Gốc/Nhẹ/Retro/Khối/Cực đại): GPU yếu thì giảm nhiều hơn
    fun suggest(t: Int) = when (t) { 0 -> 2; 1 -> 1; 2 -> 0; else -> 1 }

    class Game(val engine: String, val api: String)

    // Engine từ tên thư viện native; API đồ họa từ các <uses-feature> game khai báo
    fun game(ctx: Context, pkg: String): Game {
        val pm = ctx.packageManager
        val libs = HashSet<String>()
        runCatching {
            val ai = pm.getApplicationInfo(pkg, 0)
            val files = listOfNotNull(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())
            for (f in files) runCatching {
                ZipFile(f).use { z -> z.entries().asSequence().map { it.name }.filter { it.startsWith("lib/") && it.endsWith(".so") }.forEach { libs.add(it.substringAfterLast('/')) } }
            }
            ai.nativeLibraryDir?.let { d -> java.io.File(d).list()?.forEach { libs.add(it) } }
        }
        val eng = when {
            libs.any { it.startsWith("libunity") || it == "libil2cpp.so" } -> "Unity"
            libs.any { it.startsWith("libUE4") || it.startsWith("libUnreal") || it.startsWith("libUE5") } -> "Unreal Engine"
            libs.any { it.contains("cocos", true) } -> "Cocos"
            libs.any { it.contains("godot", true) } -> "Godot"
            libs.any { it.contains("gdx", true) } -> "libGDX"
            libs.any { it.contains("flutter", true) } -> "Flutter"
            libs.any { it.startsWith("libgame") || it.startsWith("libmain") } -> "Native"
            libs.isEmpty() -> "Không đọc được"
            else -> "Khác"
        }
        var vk = false; var gl = ""
        runCatching {
            val pi = pm.getPackageInfo(pkg, PackageManager.GET_CONFIGURATIONS)
            pi.reqFeatures?.forEach { f ->
                if (f.name == "android.hardware.vulkan.version" || f.name == "android.hardware.vulkan.level") vk = true
                if (f.name == null && f.reqGlEsVersion != 0) gl = "OpenGL ES ${f.reqGlEsVersion shr 16}.${f.reqGlEsVersion and 0xffff}"
            }
        }
        val api = when {
            vk && gl.isNotEmpty() -> "Vulkan / $gl"
            vk -> "Vulkan"
            gl.isNotEmpty() -> gl
            libs.any { it.contains("vulkan", true) } -> "Vulkan"
            else -> "OpenGL ES"
        }
        return Game(eng, api)
    }
}
