package com.gamebox.helper

import android.content.SharedPreferences

// Chỉ bản debug: CI chạy các lớp này bằng app_process dưới quyền shell (cùng quyền với Shizuku) để thử đúng mã của app
// trên emulator: CLASSPATH=<base.apk> app_process /system/bin com.gamebox.helper.FpsCli <gói> <số lần>
private fun local(cmd: String): List<String> = try {
    val p = ProcessBuilder("sh", "-c", cmd).start()
    val r = p.inputStream.bufferedReader().readLines()
    p.waitFor()
    r
} catch (e: Exception) { listOf("EXC $e") }

object FpsCli {
    @JvmStatic
    fun main(a: Array<String>) {
        val pkg = a.getOrNull(0) ?: "com.gamebox.helper"
        val n = a.getOrNull(1)?.toIntOrNull() ?: 8
        // Mẫu "--list" đúng định dạng Android 15 lấy từ máy thật (Samsung SM-A055F): tên layer nằm trong RequestedLayerState{...}
        val sample = listOf(
            "RequestedLayerState{f50362f ActivityRecordInputSink com.dts.freefireth/.FFMainActivity#84796 parentId=84789 z=-2147483648}",
            "RequestedLayerState{Background for SurfaceView[com.dts.freefireth/com.dts.freefireth.FFMainActivity]#84949 parentId=84940 z=-2147483648}",
            "RequestedLayerState{SurfaceView[com.dts.freefireth/com.dts.freefireth.FFMainActivity](BLAST)#84950 parentId=84940 z=-1}",
            "com.other/Main#1"
        )
        val names = FpsParse.layerNames(sample, "com.dts.freefireth")
        val okNames = names == listOf(
            "SurfaceView[com.dts.freefireth/com.dts.freefireth.FFMainActivity](BLAST)#84950",
            "f50362f ActivityRecordInputSink com.dts.freefireth/.FFMainActivity#84796"
        )
        println("FPSCLI tự kiểm tra tên layer Android 15: ${if (okNames) "ĐÚNG" else "SAI $names"}")
        val m = FpsMeter { local("{\n$it\n} 2>/dev/null") }
        for (i in 0 until n) {
            val f = m.measure(pkg, System.nanoTime() / 1_000_000L)
            println("FPSCLI $i fps=$f")
            m.diag.lines().forEach { println("FPSCLI    $it") }
            Thread.sleep(1000)
        }
    }
}

private class MemPrefs : SharedPreferences {
    val m = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = m
    override fun getString(k: String, d: String?) = m[k] as? String ?: d
    override fun getStringSet(k: String, d: MutableSet<String>?) = d
    override fun getInt(k: String, d: Int) = m[k] as? Int ?: d
    override fun getLong(k: String, d: Long) = m[k] as? Long ?: d
    override fun getFloat(k: String, d: Float) = m[k] as? Float ?: d
    override fun getBoolean(k: String, d: Boolean) = m[k] as? Boolean ?: d
    override fun contains(k: String) = m.containsKey(k)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = MemEditor(m)
}

private class MemEditor(val m: HashMap<String, Any?>) : SharedPreferences.Editor {
    val pending = HashMap<String, Any?>()
    val del = HashSet<String>()
    override fun putString(k: String, v: String?): SharedPreferences.Editor { pending[k] = v; return this }
    override fun putStringSet(k: String, v: MutableSet<String>?): SharedPreferences.Editor { pending[k] = v; return this }
    override fun putInt(k: String, v: Int): SharedPreferences.Editor { pending[k] = v; return this }
    override fun putLong(k: String, v: Long): SharedPreferences.Editor { pending[k] = v; return this }
    override fun putFloat(k: String, v: Float): SharedPreferences.Editor { pending[k] = v; return this }
    override fun putBoolean(k: String, v: Boolean): SharedPreferences.Editor { pending[k] = v; return this }
    override fun remove(k: String): SharedPreferences.Editor { del.add(k); return this }
    override fun clear(): SharedPreferences.Editor { m.clear(); return this }
    override fun commit(): Boolean { apply(); return true }
    override fun apply() { del.forEach { m.remove(it) }; pending.forEach { (k, v) -> m[k] = v }; pending.clear(); del.clear() }
}

// Thử Wm.read/apply/restore bằng quyền shell thật: áp 60%, đọc lại, khôi phục
object WmCli {
    @JvmStatic
    fun main(a: Array<String>) {
        Rt.backend = "shizuku"
        Rt.exec = { local(it) }
        val sp = MemPrefs()
        val pct = a.getOrNull(0)?.toIntOrNull() ?: 60
        fun show(t: String) { val i = Wm.read(); println("WMCLI $t: " + (i?.let { "phys ${it.pw}x${it.ph}/${it.pd} override ${it.ow}x${it.oh}/${it.od}" } ?: "không đọc được")) }
        show("trước")
        println("WMCLI apply($pct) = ${Wm.apply(sp, pct)}")
        show("sau áp")
        Thread.sleep(1500)
        Wm.restore(sp, "thử nghiệm")
        Thread.sleep(500)
        show("sau khôi phục")
        println("WMCLI nhật ký:"); Wm.logText(sp).lines().forEach { println("WMCLI   $it") }
        println("WMCLI cờ còn lại: wm_on=${sp.getBoolean("wm_on", false)}")
        // Nút "Thử hạ 5 giây" của hộp Chẩn đoán
        Wm.selfTest(sp).lines().filter { it.isNotEmpty() }.forEach { println("WMCLI selftest: $it") }
        show("sau selftest")
        println("WMCLI cờ sau selftest: wm_on=${sp.getBoolean("wm_on", false)}")
    }
}

// Thử cơ chế "kết cấu thật" (Angle.kt) bằng quyền shell thật: dò máy, đặt, đọc lại, trả về đúng giá trị gốc.
// Emulator thường không có gói ANGLE hệ thống nên apply thường bị từ chối; bản force bỏ qua kiểm tra đó để thử riêng phần đặt/trả lại.
object AngleCli {
    @JvmStatic
    fun main(a: Array<String>) {
        Rt.backend = "shizuku"
        Rt.exec = { local(it) }
        val sp = MemPrefs()
        println("ANGLECLI dò máy: " + Angle.report(sp).lines().filter { it.isNotEmpty() }.joinToString(" ¦ "))
        fun st() = Rt.sh("echo \"pkgs=\$(settings get global angle_gl_driver_selection_pkgs) vals=\$(settings get global angle_gl_driver_selection_values) prop=[\$(getprop debug.angle.feature_overrides_enabled)]\"").firstOrNull()
        println("ANGLECLI chuỗi tính năng: mức3+hard=${Angle.features(3, true, false)} · mức0+sharp=${Angle.features(0, false, true)} · mức0=[${Angle.features(0, false, false)}]")
        println("ANGLECLI trước: ${st()}")
        println("ANGLECLI apply thường: \"${Angle.apply(sp, "com.gamebox.helper", 3, true, false)}\" lastOk=${Angle.lastOk} cờ=${sp.getBoolean("ang_on", false)}")
        println("ANGLECLI apply force: \"${Angle.apply(sp, "com.gamebox.helper", 3, true, true, force = true)}\" lastOk=${Angle.lastOk} cờ=${sp.getBoolean("ang_on", false)}")
        println("ANGLECLI giữa: ${st()}")
        Angle.restore(sp, "thử nghiệm")
        println("ANGLECLI sau khôi phục: ${st()} cờ=${sp.getBoolean("ang_on", false)}")
        println("ANGLECLI nhật ký:"); Wm.logText(sp).lines().forEach { println("ANGLECLI   $it") }
    }
}

// Chạy Phòng thử đồ họa (GfxLab) bằng quyền shell thật trên emulator: kiểm tra không lỗi và in kết quả từng dòng
object GfxCli {
    @JvmStatic
    fun main(a: Array<String>) {
        Rt.backend = "shizuku"
        Rt.exec = { local(it) }
        val pkg = a.getOrNull(0) ?: "com.gamebox.helper"
        val rows = GfxLab.run(MemPrefs(), pkg, true) { }
        rows.forEach { println("GFXCLI [${it.tag}] ${it.name}: ${it.detail.replace('\n', ' ').take(400)}") }
        // Cách game render: đo API thật của game và câu mô tả cho màn Kết cấu
        val sp = MemPrefs()
        Render.measure(sp, pkg, 2) { println("GFXCLI nói: $it") }
        println("GFXCLI render: ${Render.seenText(sp, pkg)} · nhật ký: ${Wm.logText(sp).replace('\n', ' ¦ ')}")
        println("GFXCLI tham số unity: gles=${Render.arg(1)} vulkan=${Render.arg(2)} tự động=[${Render.arg(0)}]")
    }
}
