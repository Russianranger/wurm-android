package org.lwjgl.opengl;
import java.nio.FloatBuffer;
/** Authored GL boundary: track draw data/order without a native graphics context. */
public class GL11 {
 public static int vertexId,draws;public static long drawHash=1;
 public static void glVertexPointer(int n,int t,int stride,long offset){if(GL15.bound==0)throw new AssertionError("missing VBO");vertexId=GL15.bound;}
 public static void glTexCoordPointer(int n,int t,int stride,long offset){if(GL15.bound==0)throw new AssertionError("missing texture VBO");}
 public static void glDrawArrays(int mode,int first,int count){
  float[] a=GL15.live.get(vertexId);if(a==null||first<0||count<0||(first+count)*5>a.length)throw new AssertionError("invalid draw");
  drawHash=drawHash*31+mode;drawHash=drawHash*31+count;
  for(int i=first*5;i<(first+count)*5;i++)drawHash=drawHash*31+Float.floatToRawIntBits(a[i]);draws++;
 }
 public static void glColor4f(float r,float g,float b,float a){drawHash=31*drawHash+Float.floatToRawIntBits(r);drawHash=31*drawHash+Float.floatToRawIntBits(g);drawHash=31*drawHash+Float.floatToRawIntBits(b);drawHash=31*drawHash+Float.floatToRawIntBits(a);}
 public static void glLoadMatrix(FloatBuffer b){for(int i=b.position();i<b.limit();i++)drawHash=31*drawHash+Float.floatToRawIntBits(b.get(i));}
 public static void glLoadIdentity(){drawHash=31*drawHash+1;}
 public static void glMatrixMode(int mode){}public static void glPushMatrix(){}public static void glPopMatrix(){}
 public static void glEnable(int mode){}public static void glDisable(int mode){}
 public static void glEnableClientState(int i){}public static void glDisableClientState(int i){}
 public static void glPolygonMode(int a,int b){}public static void glPolygonOffset(float a,float b){}
 public static void glAlphaFunc(int a,float b){}public static void glDepthFunc(int a){}public static void glDepthMask(boolean b){}
 public static void glBlendFunc(int a,int b){}public static void glBindTexture(int a,int b){}
 public static void glTexEnvf(int a,int b,float c){}public static void glFogi(int a,int b){}public static void glFogf(int a,float b){}
 public static void glFog(int a,FloatBuffer b){}public static void glMaterial(int a,int b,FloatBuffer c){}public static void glMaterialf(int a,int b,float c){}
}
