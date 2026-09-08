package org.lwjgl.opengl;

/** Legacy LWJGL2 owner for the ARB program query already exposed by LWJGL3. */
public final class ARBProgram {
    private ARBProgram() {}
    public static int glGetProgramiARB(int target, int parameter) {
        return ARBVertexProgram.glGetProgramiARB(target, parameter);
    }
}
