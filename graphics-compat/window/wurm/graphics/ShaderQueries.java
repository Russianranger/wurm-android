package wurm.graphics;

import java.nio.IntBuffer;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBShaderObjects;
import static org.lwjgl.opengl.GL11.glGetError;
import static org.lwjgl.opengl.GL20.*;

/** Exercise the real LWJGL2 reflection ABI before imported material loading. */
public final class ShaderQueries {
    private static int compile(int stage, String source) {
        int shader = glCreateShader(stage);
        try {
            glShaderSource(shader, source); glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0)
                throw new IllegalStateException("SHADER_QUERY_COMPILE_FAILED " + glGetShaderInfoLog(shader));
            return shader;
        } catch (RuntimeException | Error error) { glDeleteShader(shader); throw error; }
    }
    private static IntBuffer outputs() {
        IntBuffer values = BufferUtils.createIntBuffer(6);
        for (int i = 0; i < 6; i++) values.put(i, -7);
        values.position(2); values.limit(4); return values;
    }
    private static void checkOutputs(IntBuffer values, int type, String name) {
        if (values.position()!=2 || values.limit()!=4 || values.get(2)!=1 || values.get(3)!=type || values.get(0)!=-7 || values.get(1)!=-7)
            throw new IllegalStateException("SHADER_QUERY_OUTPUT_FAILED name=" + name + " size=" + values.get(2) + " type=" + values.get(3) + " position=" + values.position());
        if (values.duplicate().clear().get(4)!=-7 || values.duplicate().clear().get(5)!=-7)
            throw new IllegalStateException("SHADER_QUERY_OUTPUT_OVERFLOW " + name);
    }
    public static void verify() {
        int vertex=0, fragment=0, program=0;
        try {
            vertex=compile(GL_VERTEX_SHADER,"#version 120\nattribute vec3 Position; uniform mat4 mvp; void main(){gl_Position=mvp*vec4(Position,1.0);}");
            fragment=compile(GL_FRAGMENT_SHADER,"#version 120\nuniform vec4 tint; uniform sampler2D image; void main(){gl_FragColor=texture2D(image,vec2(0.5))*tint;}");
            program=glCreateProgram(); glAttachShader(program,vertex); glAttachShader(program,fragment);
            glBindAttribLocation(program,0,"Position"); glLinkProgram(program);
            if(glGetProgrami(program,GL_LINK_STATUS)==0) throw new IllegalStateException("SHADER_QUERY_LINK_FAILED "+glGetProgramInfoLog(program));
            Map<String,Integer> expected=Map.of("mvp",GL_FLOAT_MAT4,"tint",GL_FLOAT_VEC4,"image",GL_SAMPLER_2D);
            Set<String> seen=new HashSet<>();
            for(int i=0;i<glGetProgrami(program,GL_ACTIVE_UNIFORMS);i++) {
                IntBuffer core=outputs(), arb=outputs();
                String name=glGetActiveUniform(program,i,128,core);
                String arbName=ARBShaderObjects.glGetActiveUniformARB(program,i,128,arb);
                if(!name.equals(arbName)) throw new IllegalStateException("SHADER_QUERY_NAME_MISMATCH");
                Integer type=expected.get(name);
                if(type!=null) { checkOutputs(core,type,name); checkOutputs(arb,type,name); seen.add(name); }
            }
            if(!seen.equals(expected.keySet())) throw new IllegalStateException("SHADER_QUERY_MISSING_UNIFORMS "+seen);
            IntBuffer attribute=outputs(); String name=glGetActiveAttrib(program,0,128,attribute);
            if(!name.equals("Position") || glGetAttribLocation(program,name)!=0) throw new IllegalStateException("SHADER_QUERY_ATTRIBUTE_FAILED "+name);
            checkOutputs(attribute,GL_FLOAT_VEC3,name);
            int gl=glGetError(), gles=NativeEgl.error();
            if(gl!=0 || gles!=0) throw new IllegalStateException("SHADER_QUERY_GL_ERROR GL="+gl+" GLES="+gles);
            System.out.println("[graphics] SHADER_QUERY_PASS core/ARB uniform size/type; positioned buffers preserved; Position=0; GLSL120 linked");
        } finally {
            if(program!=0) glDeleteProgram(program);
            if(fragment!=0) glDeleteShader(fragment);
            if(vertex!=0) glDeleteShader(vertex);
        }
    }
}
