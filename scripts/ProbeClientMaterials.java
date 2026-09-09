package wurm.graphics;

import client.ClientGraphicsPatch;
import client.ClientBuffers;
import client.DirectClientLaunch;
import java.lang.reflect.*;
import java.nio.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

/** Optional private real-material/blur draw probe; no proprietary resources in this source. */
public final class ProbeClientMaterials {
    public static void main(String[] args) throws Throwable {
        ClientGraphicsPatch.verifySelected(); ClientBuffers.preflight();
        Class<?> profile=Class.forName("com.wurmonline.client.settings.Profile");
        profile.getMethod("loadPlayer",String.class).invoke(profile.getMethod("getProfile").invoke(null),"Thor");
        DirectClientLaunch.prepareDisplay();
        Class<?> window=Class.forName("com.wurmonline.client.LwjglClient");
        var ctor=window.getDeclaredConstructor(); ctor.setAccessible(true); Object w=ctor.newInstance();
        var init=window.getDeclaredMethod("initWindow");init.setAccessible(true);init.invoke(w);
        try {
            Class<?> engine=Class.forName("com.wurmonline.client.WurmClientBase");
            Class<?> resources=Class.forName("com.wurmonline.client.resources.Resources");
            Object builtins=resources.getConstructor(java.io.File.class,java.util.List.class).newInstance(new java.io.File("packs"),java.util.List.of());
            var resourceField=engine.getDeclaredField("resourceManager");resourceField.setAccessible(true);resourceField.set(null,builtins);
            Class<?> unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
            Object instance=unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),engine);
            // Match real startup ordering; omitting initialize() leaves default
            // booleans false and would miss the fork's false-positive GL3.3 bug.
            Class<?> glHelper=Class.forName("com.wurmonline.client.util.GLHelper");
            glHelper.getMethod("initialize").invoke(null);
            var check=engine.getDeclaredMethod("checkSupportLevels");check.setAccessible(true);check.invoke(instance);
            Class<?> material=Class.forName("com.wurmonline.client.renderer.Material");
            material.getMethod("preload").invoke(null);
            Object blur=material.getMethod("load",String.class).invoke(null,"material.gaussblur");
            if(blur==null) throw new AssertionError("REAL_GAUSS_MATERIAL_NULL");
            Object program=material.getMethod("getProgram").invoke(blur);
            int id=(Integer)program.getClass().getMethod("getObject").invoke(program);
            if(glGetProgrami(id,GL_LINK_STATUS)!=GL_TRUE || glGetAttribLocation(id,"Position")!=0)
                throw new AssertionError("REAL_GAUSS_LINK_OR_POSITION_FAILED");
            for(String name:new String[]{"mvp","tex0","resolution","direction"})
                if(glGetUniformLocation(id,name)<0) throw new AssertionError("REAL_GAUSS_UNIFORM_MISSING "+name);
            System.out.println("WURM_GAUSS_MATERIAL_PASS linked=true Position=0 uniforms=4");
            draw(id);
            material.getMethod("unref").invoke(blur);
            if ((Boolean)glHelper.getMethod("useDeferredShading").invoke(null) ||
                (Boolean)glHelper.getMethod("useInstancing").invoke(null))
                throw new AssertionError("REAL_WURM_GL33_RENDERER_SELECTED_ON_GL21");
            Class<?> renderer=Class.forName("com.wurmonline.client.renderer.WorldRender");
            Object render=unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),renderer);
            if ((Boolean)renderer.getMethod("useAdvancedWater").invoke(render))
                throw new AssertionError("REAL_WURM_ADVANCED_WATER_SELECTED");
            Class<?> volume=Class.forName("com.wurmonline.client.renderer.cell.Volume");
            volume.getConstructor().newInstance();
            var occlusion=volume.getDeclaredField("materialOcclusion");occlusion.setAccessible(true);
            if(occlusion.get(null)!=null)throw new AssertionError("REAL_WURM_GL33_OCCLUSION_MATERIAL_CREATED");
            System.out.println("WURM_LEGACY_SELECTION_PASS deferred=false instancing=false advancedWater=false; real Volume constructor passes without GL33 material");
            System.out.println("WURM_MATERIAL_PROBE_PASS; real builtin materials and blur only, terrain/login not tested");
        } catch(InvocationTargetException error) { throw error.getCause(); }
        finally { Display.destroy(); }
        System.exit(0);
    }
    private static void draw(int program) {
        int texture=glGenTextures(), vbo=glGenBuffers();
        try {
            glBindTexture(GL_TEXTURE_2D,texture);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            ByteBuffer pixels=BufferUtils.createByteBuffer(16);
            for(int i=0;i<4;i++) pixels.put((byte)64).put((byte)128).put((byte)192).put((byte)255);
            pixels.flip();glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,2,2,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            FloatBuffer vertices=BufferUtils.createFloatBuffer(18);
            vertices.put(new float[]{-1,-1,0,1,-1,0,1,1,0,-1,-1,0,1,1,0,-1,1,0}).flip();
            glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,vertices,GL_STATIC_DRAW);
            glUseProgram(program);glUniform1i(glGetUniformLocation(program,"tex0"),0);
            glUniform2f(glGetUniformLocation(program,"resolution"),32,32);
            glUniform2f(glGetUniformLocation(program,"direction"),1,0);
            FloatBuffer identity=BufferUtils.createFloatBuffer(16);
            identity.put(new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1}).flip();
            glUniformMatrix4fv(glGetUniformLocation(program,"mvp"),false,identity);
            glViewport(0,0,32,32);glDisable(GL_DEPTH_TEST);glDisable(GL_BLEND);glDisable(GL_CULL_FACE);
            glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
            glEnableVertexAttribArray(0);glVertexAttribPointer(0,3,GL_FLOAT,false,0,0L);glDrawArrays(GL_TRIANGLES,0,6);
            ByteBuffer result=BufferUtils.createByteBuffer(4);glReadPixels(16,16,1,1,GL_RGBA,GL_UNSIGNED_BYTE,result);
            int gl=glGetError(),gles=NativeEgl.error();
            for(int i=0;i<4;i++) if(Math.abs((result.get(i)&255)-new int[]{64,128,192,255}[i])>1)
                throw new AssertionError("REAL_GAUSS_PIXEL_FAILED component="+i+" value="+(result.get(i)&255)+" GL="+gl+" GLES="+gles);
            if(gl!=0 || gles!=0)throw new AssertionError("REAL_GAUSS_DRAW_ERROR GL="+gl+" GLES="+gles);
            System.out.println("WURM_GAUSS_DRAW_PASS pixel=64,128,192,255 tolerance=1; actual imported blur math");
        } finally {
            glDisableVertexAttribArray(0);glBindBuffer(GL_ARRAY_BUFFER,0);glUseProgram(0);
            glDeleteBuffers(vbo);glDeleteTextures(texture);
        }
    }
}
