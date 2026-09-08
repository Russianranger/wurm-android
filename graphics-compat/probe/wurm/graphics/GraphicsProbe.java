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
    private static void log(String text) { System.out.println("[graphics] " + text); }
    private static void check(boolean ok, String reason) { if (!ok) throw new IllegalStateException(reason); }
    private static int shader(int type, String source) {
        int id = glCreateShader(type); glShaderSource(id, source); glCompileShader(id);
        log("SHADER_LOG type="+type+" "+glGetShaderInfoLog(id));
        check(glGetShaderi(id, GL_COMPILE_STATUS) != GL_FALSE, "GLSL shader compilation failed");
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
            log("LWJGL_CONTEXT_READY GL="+glGetString(GL_VERSION)+" renderer="+glGetString(GL_RENDERER)+
                " vendor="+glGetString(GL_VENDOR)+" GLSL="+glGetString(GL_SHADING_LANGUAGE_VERSION));
            log("GL_CAPABILITIES OpenGL11="+caps.OpenGL11+" OpenGL20="+caps.OpenGL20+" OpenGL21="+caps.OpenGL21);
            check(caps.OpenGL20, "GL4ES desktop OpenGL 2.0 API incomplete");
            int vertex = shader(GL_VERTEX_SHADER, "#version 120\nattribute vec2 position; void main(){gl_Position=vec4(position,0.0,1.0);}");
            int fragment = shader(GL_FRAGMENT_SHADER, "#version 120\nvoid main(){gl_FragColor=vec4(1.0,0.4,0.1,1.0);}");
            int program = glCreateProgram(); glAttachShader(program, vertex); glAttachShader(program, fragment);
            glBindAttribLocation(program, 0, "position"); glLinkProgram(program);
            log("PROGRAM_LOG "+glGetProgramInfoLog(program));
            check(glGetProgrami(program, GL_LINK_STATUS) != GL_FALSE, "Shader link failed");
            log("GLSL120_PROGRAM_READY");
            int vbo = glGenBuffers(); glBindBuffer(GL_ARRAY_BUFFER, vbo);
            FloatBuffer points = ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asFloatBuffer();
            points.put(new float[]{-0.8f,-0.8f,0.8f,-0.8f,0f,0.8f}).flip();
            glBufferData(GL_ARRAY_BUFFER, points, GL_STATIC_DRAW);
            glUseProgram(program); glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0L);
            int[][] sizes = {{320,180},{640,360},{320,180}};
            for (int i=0; i<sizes.length; i++) {
                int w=sizes[i][0], h=sizes[i][1];
                if (i!=0) NativeEgl.resize(w,h);
                glViewport(0,0,w,h); glDisable(GL_DITHER); glClearColor(0.05f,0.15f,0.35f,1);
                glClear(GL_COLOR_BUFFER_BIT); glDrawArrays(GL_TRIANGLES,0,3); glFinish();
                ByteBuffer pixels=ByteBuffer.allocateDirect(w*h*4);
                glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
                check(glGetError()==GL_NO_ERROR,"OpenGL draw/readback error");
                verify(pixels,w,w/2,h/2,255,102,26); verify(pixels,w,2,2,13,38,89);
                log("FRAME_VERIFY_OK sequence="+(i+1)+" size="+w+"x"+h+" center=orange corner=blue");
                FrameFile.write(Path.of(args[1]),w,h,i+1,pixels);
                log("FRAME_PUBLISHED sequence="+(i+1)); NativeEgl.swap();
                log("PBUFFER_SWAP_OK sequence="+(i+1)); Thread.sleep(1200);
            }
            glDisableVertexAttribArray(0); glBindBuffer(GL_ARRAY_BUFFER,0); glDeleteBuffers(vbo);
            glUseProgram(0); glDeleteProgram(program); glDeleteShader(vertex); glDeleteShader(fragment);
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
