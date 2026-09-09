#!/usr/bin/env python3
"""Adapt pinned Pojav Java GLFW to the owned single-context EGL child.

Only generated build copies change. The original source archive is shipped too.
"""
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def body(text, signature, replacement):
    start = text.index(signature)
    opening = text.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[:opening] + '{\n' + replacement + '\n    }' + text[end:]


def build(pojav, candidate, probe, output, annotations):
    output.mkdir()
    source = pojav/'jre_lwjgl3glfw/src/main/java'
    text = (source/'org/lwjgl/glfw/GLFW.java').read_text()
    text = text.replace('package org.lwjgl.glfw;', 'package org.lwjgl.glfw;\nimport wurm.graphics.WindowBackend;')
    start = text.index('        try {\n            System.loadLibrary("pojavexec");')
    end = text.index('        mGLFWErrorCallback', start)
    text = text[:start] + '        mGLFWWindowWidth = 960; mGLFWWindowHeight = 540;\n' + text[end:]
    start = text.index('    private static final SharedLibrary GLFW =')
    end = text.index('    @SuppressWarnings("unused") // Used by pojavexec', start)
    text = text[:start] + text[end:]
    text = re.sub(r'private static native long nglfwSet(\w+)Callback\(long window, long ptr\);',
                  r'private static long nglfwSet\1Callback(long window, long ptr) { return ptr; }', text)
    text = text.replace('private static native void nglfwSetShowingWindow(long window);',
                        'private static void nglfwSetShowingWindow(long window) {}')
    text = text.replace('private static native long internalGetGamepadDataPointer();', '')
    replacements = {
        'public static boolean glfwInit()': 'if (!isGLFWReady) mGLFWInitialTime = System.nanoTime(); return isGLFWReady = true;',
        'public static void glfwTerminate()': 'WindowBackend.close(); isGLFWReady = false;',
        'public static long glfwGetCurrentContext()': 'return WindowBackend.current();',
        'public static void glfwMakeContextCurrent(': 'WindowBackend.makeCurrent(window);',
        'public static void glfwSwapBuffers(': 'WindowBackend.swap();',
        'public static void glfwSwapInterval(': '/* Readback is paced by WindowBackend; no display swap interval. */',
        'public static long nglfwCreateContext(': 'throw new UnsupportedOperationException("Shared contexts are not qualified");',
        'public static long glfwCreateWindow(': '''if (share != 0) throw new UnsupportedOperationException("Shared window context");
        long ptr = WindowBackend.open(width, height);
        GLFWWindowProperties win = new GLFWWindowProperties();
        win.width = width; win.height = height; win.title = title;
        win.inputModes.put(GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        win.windowAttribs.put(GLFW_FOCUSED, 1); win.windowAttribs.put(GLFW_VISIBLE, 1);
        mGLFWWindowMap.put(ptr, win); mainContext = ptr; return ptr;''',
        'public static void glfwDestroyWindow(': 'WindowBackend.close(); mGLFWWindowMap.remove(window); mainContext = 0;',
        'public static void glfwWindowHint(int': '/* One RGBA8/depth16 EGL pbuffer configuration; logged by backend. */',
        'public static void glfwPollEvents()': 'WindowBackend.poll();',
        'public static boolean glfwJoystickPresent(': 'return false;',
        'public static int glfwGetKey(': 'int code=org.lwjgl.input.KeyCodes.toLwjglKey(key); return code>0 ? org.lwjgl.input.GLFWInputImplementation.singleton.key_down_buffer[code] : 0;',
        'public static int glfwGetMouseButton(': 'return org.lwjgl.input.GLFWInputImplementation.singleton.mouse_buffer[button];',
        'public static boolean glfwJoystickIsGamepad(': 'return false;',
        'public static boolean glfwGetGamepadState(': 'return false;',
        'public static void glfwSetWindowSize(long': '''WindowBackend.resize(width, height);
        internalGetWindow(window).width = width; internalGetWindow(window).height = height;
        if (mGLFWWindowSizeCallbackI != null) mGLFWWindowSizeCallbackI.invoke(window, width, height);
        if (mGLFWFramebufferSizeCallbackI != null) mGLFWFramebufferSizeCallbackI.invoke(window, width, height);'''
    }
    for signature, replacement in replacements.items(): text = body(text, signature, replacement)
    text = re.sub(r'public static native void nglfwGetCursorPos\(([^;]+)\);',
                  r'public static void nglfwGetCursorPos(\1) { if(xpos != null) xpos.put(0, WindowBackend.x()); if(ypos != null) ypos.put(0, WindowBackend.y()); }', text)
    text = re.sub(r'public static native void nglfwGetCursorPosA\(([^;]+)\);',
                  r'public static void nglfwGetCursorPosA(\1) { if(xpos != null) xpos[0]=WindowBackend.x(); if(ypos != null) ypos[0]=WindowBackend.y(); }', text)
    text = re.sub(r'public static native void glfwSetCursorPos\(([^;]+)\);',
                  r'public static void glfwSetCursorPos(\1) { WindowBackend.cursor(xpos, ypos); }', text)
    target = output/'GLFW.java'; target.write_text(text)
    sources = [target, source/'org/lwjgl/glfw/GLFWWindowProperties.java', source/'org/lwjgl/glfw/Callbacks.java']
    sources += sorted((source/'android/util').glob('*.java'))
    sources += sorted((ROOT/'graphics-compat/window').rglob('*.java'))
    # Reuse the established, tested input wire parser; do not duplicate validation.
    sources += [ROOT/'runtime-probe/src/client/DesktopInput.java']
    cp = ':'.join(map(str, [candidate, probe, annotations]))
    result = subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-cp', cp,
                             '-d', str(output/'classes'), *map(str, sources)], capture_output=True, text=True)
    (output/'compile.log').write_text(result.stdout + result.stderr)
    if result.returncode: raise RuntimeError(result.stderr[-6000:])
    jar_path = output/'wurm-window.jar'
    with zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as jar:
        for path in sorted((output/'classes').rglob('*.class')):
            entry = zipfile.ZipInfo(path.relative_to(output/'classes').as_posix(), (1980,1,1,0,0,0))
            jar.writestr(entry, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        jar.writestr('META-INF/LICENSE.pojav.txt', (pojav/'LICENSE').read_bytes())
        jar.writestr('META-INF/WURM-CHANGES.txt', 'Java GLFW adapted to one owned EGL pbuffer. Platform native hooks replaced; input uses LWJGLX queues. Full source and build adapter included in corresponding source release.\n')
    return jar_path
