package wurm.graphics;

import java.nio.*;
import java.nio.file.*;
import org.lwjgl.Version;
import org.lwjgl.opengl.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

/** Real LWJGL -> GL4ES -> GLES shader/readback gate; does not load Wurm or claim a game window. */
public final class GraphicsProbe {
    private static GlChecks gl;
    private static void log(String text) { System.out.println("[graphics] " + text); }
    private static void check(boolean ok, String reason) { if (!ok) throw new IllegalStateException(reason); }
    private static int nextError() {
        int translated = glGetError();
        // GL4ES may report only its emulated state. Also check the current GLES
        // context so a driver error cannot be hidden by an intervening shim call.
        return translated != GL_NO_ERROR ? translated : NativeEgl.error();
    }
    private static int shader(int type, String source) {
        String stage = type == GL_VERTEX_SHADER ? "vertex" : "fragment";
        int id = gl.get(stage+".createShader", () -> glCreateShader(type));
        gl.run(stage+".shaderSource", () -> glShaderSource(id, source));
        gl.run(stage+".compileShader", () -> glCompileShader(id));
        int logLength = gl.get(stage+".infoLogLength", () -> glGetShaderi(id, GL_INFO_LOG_LENGTH));
        // GL4ES rejects a zero-size shader-log request. Successful shaders (and
        // Mesa's cached shaders) can report length 0; LWJGL's convenience overload
        // passes that straight through. Request at least the terminating byte.
        check(logLength >= 0 && logLength <= 1024*1024, "Invalid shader log length: "+logLength);
        gl.run(stage+".shaderInfoLog", () -> log("SHADER_LOG type="+type+" length="+logLength+" "+
            glGetShaderInfoLog(id, Math.max(1, logLength))));
        check(gl.get(stage+".compileStatus", () -> glGetShaderi(id, GL_COMPILE_STATUS)) != GL_FALSE,
            "GLSL shader compilation failed: "+stage);
        return id;
    }
    private static void verify(ByteBuffer pixels, int width, int x, int y, int r, int g, int b) {
        int at = (y*width+x)*4;
        int actualR = pixels.get(at)&255, actualG = pixels.get(at+1)&255, actualB = pixels.get(at+2)&255;
        check(Math.abs(actualR-r)<=12 && Math.abs(actualG-g)<=12 && Math.abs(actualB-b)<=12,
            "Pixel mismatch at "+x+","+y+": "+actualR+","+actualG+","+actualB);
    }
    public static void main(String[] args) {
        int result = 0;
        boolean opened = false, glLoaded = false;
        try {
            check(args.length == 2, "Expected packaged GL4ES path and private frame path");
            log("JVM_START java="+System.getProperty("java.version")+" arch="+System.getProperty("os.arch"));
            log("CLASSPATH "+System.getProperty("java.class.path"));
            log("NATIVE_PATH "+System.getProperty("java.library.path"));
            log("ENTRY wurm.graphics.GraphicsProbe; no Wurm classes or Steam; graphics diagnostic only");
            log("LWJGL_NATIVE_BEGIN"); log("LWJGL_VERSION "+Version.getVersion());
            NativeEgl.open(args[0], 320, 180); opened = true;
            GL.create(args[0]); glLoaded = true;
            GLCapabilities caps = GL.createCapabilities();
            gl = new GlChecks(GraphicsProbe::nextError, GraphicsProbe::log);
            gl.check("createCapabilities");
            log("LWJGL_CONTEXT_READY GL="+glGetString(GL_VERSION)+" renderer="+glGetString(GL_RENDERER)+
                " vendor="+glGetString(GL_VENDOR)+" GLSL="+glGetString(GL_SHADING_LANGUAGE_VERSION));
            gl.check("contextStrings");
            log("GL_CAPABILITIES OpenGL11="+caps.OpenGL11+" OpenGL20="+caps.OpenGL20+" OpenGL21="+caps.OpenGL21);
            check(caps.OpenGL20, "GL4ES desktop OpenGL 2.0 API incomplete");
            int vertex = shader(GL_VERTEX_SHADER, "#version 120\nattribute vec2 position; void main(){gl_Position=vec4(position,0.0,1.0);}");
            int fragment = shader(GL_FRAGMENT_SHADER, "#version 120\nvoid main(){gl_FragColor=vec4(1.0,0.4,0.1,1.0);}");
            int program = gl.get("createProgram", GL20::glCreateProgram);
            gl.run("attachVertexShader", () -> glAttachShader(program, vertex));
            gl.run("attachFragmentShader", () -> glAttachShader(program, fragment));
            gl.run("bindAttribLocation", () -> glBindAttribLocation(program, 0, "position"));
            gl.run("linkProgram", () -> glLinkProgram(program));
            gl.run("programInfoLog", () -> log("PROGRAM_LOG "+glGetProgramInfoLog(program)));
            check(gl.get("linkStatus", () -> glGetProgrami(program, GL_LINK_STATUS)) != GL_FALSE, "Shader link failed");
            log("GLSL120_PROGRAM_READY");
            int vbo = gl.get("genBuffers", GL15::glGenBuffers);
            gl.run("bindArrayBuffer", () -> glBindBuffer(GL_ARRAY_BUFFER, vbo));
            FloatBuffer points = ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asFloatBuffer();
            points.put(new float[]{-0.8f,-0.8f,0.8f,-0.8f,0f,0.8f}).flip();
            gl.run("bufferData", () -> glBufferData(GL_ARRAY_BUFFER, points, GL_STATIC_DRAW));
            gl.run("useProgram", () -> glUseProgram(program));
            gl.run("enableVertexAttribArray", () -> glEnableVertexAttribArray(0));
            gl.run("vertexAttribPointer", () -> glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0L));
            int[][] sizes = {{320,180},{640,360},{320,180}};
            for (int i=0; i<sizes.length; i++) {
                int w=sizes[i][0], h=sizes[i][1];
                String frame = "frame"+(i+1)+".";
                if (i!=0) { NativeEgl.resize(w,h); gl.check(frame+"resize"); }
                gl.run(frame+"viewport", () -> glViewport(0,0,w,h));
                gl.run(frame+"disableDither", () -> glDisable(GL_DITHER));
                gl.run(frame+"clearColor", () -> glClearColor(0.05f,0.15f,0.35f,1));
                gl.run(frame+"clear", () -> glClear(GL_COLOR_BUFFER_BIT));
                gl.run(frame+"drawArrays", () -> glDrawArrays(GL_TRIANGLES,0,3));
                gl.run(frame+"finish", GL11::glFinish);
                ByteBuffer pixels=ByteBuffer.allocateDirect(w*h*4);
                gl.run(frame+"readPixels", () -> glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,pixels));
                verify(pixels,w,w/2,h/2,255,102,26); verify(pixels,w,2,2,13,38,89);
                log("FRAME_VERIFY_OK sequence="+(i+1)+" size="+w+"x"+h+" center=orange corner=blue");
                FrameFile.write(Path.of(args[1]),w,h,i+1,pixels);
                log("FRAME_PUBLISHED sequence="+(i+1)); NativeEgl.swap();
                gl.check(frame+"swap");
                log("PBUFFER_SWAP_OK sequence="+(i+1)); Thread.sleep(1200);
            }
            glDisableVertexAttribArray(0); glBindBuffer(GL_ARRAY_BUFFER,0); glDeleteBuffers(vbo);
            glUseProgram(0); glDeleteProgram(program); glDeleteShader(vertex); glDeleteShader(fragment);
            gl.check("deleteResources");
        } catch (Throwable failure) {
            result=42; log("GRAPHICS_PROBE_FAIL "+failure); failure.printStackTrace(System.out);
        } finally {
            try { if (glLoaded) { GL.setCapabilities(null); GL.destroy(); } }
            catch (Throwable failure) { result=42; log("GL_TEARDOWN_FAIL "+failure); }
            try { if (opened) NativeEgl.close(); }
            catch (Throwable failure) { result=42; log("EGL_TEARDOWN_FAIL "+failure); }
        }
        if (result == 0) log("GRAPHICS_PROBE_PASS frames=3 teardown=ok; Wurm window/login/gameplay NOT tested");
        log("GRAPHICS_PROBE_EXIT code="+result);
        System.exit(result);
    }
}
