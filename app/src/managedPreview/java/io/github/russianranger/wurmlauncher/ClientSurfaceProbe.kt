package io.github.russianranger.wurmlauncher

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Qualifies an Android-owned GLES surface only, not a desktop GL/LWJGL context. */
class ClientSurfaceProbe(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {
    init { setEGLContextClientVersion(2); setRenderer(this); renderMode = RENDERMODE_WHEN_DIRTY }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        ClientSession.log("[graphics] ANDROID_SURFACE_CREATED GLES2; Wurm/LWJGL adapter NOT attached")
        for ((name, code) in listOf("VENDOR" to GLES20.GL_VENDOR, "RENDERER" to GLES20.GL_RENDERER, "VERSION" to GLES20.GL_VERSION))
            ClientSession.log("[graphics] ANDROID_GL_$name ${GLES20.glGetString(code)}")
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0,0,width,height); ClientSession.log("[graphics] ANDROID_SURFACE_SIZE ${width}x$height")
    }
    override fun onDrawFrame(gl: GL10?) { GLES20.glClearColor(.086f,.118f,.149f,1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT) }
}
