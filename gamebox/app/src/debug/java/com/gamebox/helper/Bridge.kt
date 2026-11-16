package com.gamebox.helper

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

// Chỉ bản debug: mô phỏng Shizuku trên emulator để CI chạy trọn luồng của app.
// ShellServer chạy dưới quyền shell (app_process, cùng quyền với Shizuku) và thực thi lệnh app gửi qua localhost;
// BridgeInit nối Rt.exec tới nó khi có tệp cờ files/bridge.
object ShellServer {
    @JvmStatic
    fun main(a: Array<String>) {
        val port = a.getOrNull(0)?.toIntOrNull() ?: 8765
        val ss = ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"))
        println("SHELLSRV nghe cổng $port")
        while (true) {
            val s = ss.accept()
            Thread {
                try {
                    val cmd = s.getInputStream().readBytes().toString(Charsets.UTF_8)
                    val p = ProcessBuilder("sh", "-c", cmd).redirectError(File("/dev/null")).start()
                    val out = p.inputStream.readBytes()
                    p.waitFor()
                    s.getOutputStream().write(out)
                    s.getOutputStream().flush()
                } catch (e: Exception) {
                    println("SHELLSRV lỗi $e")
                } finally {
                    runCatching { s.close() }
                }
            }.start()
        }
    }
}

class BridgeInit : ContentProvider() {
    override fun onCreate(): Boolean {
        val c = context ?: return false
        if (File(c.filesDir, "bridge").exists()) {
            Rt.exec = { cmd ->
                try {
                    Socket("127.0.0.1", 8765).use { s ->
                        s.getOutputStream().write(cmd.toByteArray())
                        s.shutdownOutput()
                        s.getInputStream().readBytes().toString(Charsets.UTF_8).lines().dropLastWhile { it.isEmpty() }
                    }
                } catch (e: Exception) { emptyList() }
            }
            Rt.init()
        }
        return true
    }

    override fun query(u: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}
