package com.gamebox.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import kotlin.concurrent.thread

// Độ phân giải đã hạ (wm size/density) và kết cấu ANGLE (thuộc tính/cài đặt hệ thống) còn nguyên sau khi máy khởi động lại,
// nên nếu máy tắt/khởi động lại giữa phiên chơi mà không trả lại thì màn hình sẽ kẹt ở độ phân giải thấp.
// Khi máy khởi động xong, thử trả lại ngay nếu đã có quyền (root, hoặc Shizuku tự chạy khi khởi động trên máy root). Chưa có quyền
// thì giữ nguyên cờ; app sẽ trả lại ở lần mở sau (xem khởi tạo trong Gs) hoặc ở nút "Khôi phục ngay".
// Hành động com.gamebox.helper.RES_REVERT chỉ được chấp nhận ở bản debug (CI dùng để mô phỏng khởi động lại).
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val dbg = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!(i.action == Intent.ACTION_BOOT_COMPLETED || (dbg && i.action == "com.gamebox.helper.RES_REVERT"))) return
        val sp = ctx.getSharedPreferences("gs", Context.MODE_PRIVATE)
        if (!sp.getBoolean("wm_on", false) && !sp.getBoolean("ang_on", false)) return
        val pr = goAsync()
        thread {
            try {
                // Binder của Shizuku có thể chưa về ngay sau khi tiến trình khởi động nên thử vài lần trong giới hạn của receiver
                for (n in 0 until 4) {
                    Rt.init()
                    if (Rt.backend != "none") {
                        Wm.restore(sp, "máy khởi động lại giữa phiên")
                        sp.edit().remove("sess").apply()
                        return@thread
                    }
                    Thread.sleep(2000)
                }
                Wm.log(sp, "Máy khởi động lại nhưng chưa có quyền Root/Shizuku để trả độ phân giải; mở Game Box sau khi bật Shizuku")
            } finally { pr.finish() }
        }
    }
}
