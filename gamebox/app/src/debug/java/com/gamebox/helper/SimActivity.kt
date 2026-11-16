package com.gamebox.helper

import android.app.Activity
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.sin

// Chỉ bản debug: giả lập một game vẽ liên tục bằng OpenGL lên SurfaceView, để CI đo FPS đúng loại layer mà game thật dùng
class SimActivity : Activity() {
    private lateinit var v: GLSurfaceView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        v = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(object : GLSurfaceView.Renderer {
                var t = 0f
                override fun onSurfaceCreated(gl: GL10?, c: EGLConfig?) {}
                override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { GLES20.glViewport(0, 0, w, h) }
                override fun onDrawFrame(gl: GL10?) {
                    t += .05f
                    GLES20.glClearColor(abs(sin(t)), abs(sin(t * .7f)), .3f, 1f)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                }
            })
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        setContentView(v)
    }

    override fun onResume() { super.onResume(); v.onResume() }
    override fun onPause() { super.onPause(); v.onPause() }
}
