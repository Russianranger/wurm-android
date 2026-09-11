/* Prefer depth precision for nearby overlapping geometry; preserve older GLES devices. */
static int wurm_choose_depth_config(EGLDisplay dpy, EGLConfig *out, EGLint *selected) {
    for (int bits = 24; bits >= 16; bits -= 8) {
        const EGLint attributes[] = { EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, bits, EGL_NONE };
        EGLint count = 0;
        if (!eglChooseConfig(dpy, attributes, out, 1, &count)) return 0;
        if (count == 0) continue;
        if (!eglGetConfigAttrib(dpy, *out, EGL_DEPTH_SIZE, selected) || *selected < bits) return 0;
        return 1;
    }
    return 0;
}
