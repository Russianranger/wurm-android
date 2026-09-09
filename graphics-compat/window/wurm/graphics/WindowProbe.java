package wurm.graphics;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import static org.lwjgl.opengl.GL11.*;

/** Interactive test of the same Display and input queues consumed by Wurm. */
public final class WindowProbe {
    public static void main(String[] args) {
        int exit = 42;
        try {
            Display.destroy(); // No window: must not throw and mask an earlier startup error.
            Display.setDisplayMode(new DisplayMode(640,360));
            Display.create();
            long deadline = System.nanoTime()+90_000_000_000L;
            float x=0, y=0; int moves=0;
            while (!Display.isCloseRequested() && System.nanoTime()<deadline) {
                Display.processMessages();
                while (Keyboard.next()) System.out.println("[window] LWJGL_KEY code="+Keyboard.getEventKey()+" down="+Keyboard.getEventKeyState());
                while (Mouse.next()) {
                    if (Mouse.getEventButton()==-1 && Mouse.getEventDWheel()==0 && moves++ % 30 == 0)
                        System.out.println("[window] LWJGL_MOUSE_MOVE x="+Mouse.getEventX()+" y="+Mouse.getEventY()+" dx="+Mouse.getEventDX()+" dy="+Mouse.getEventDY());
                    if (Mouse.getEventButton()!=-1 || Mouse.getEventDWheel()!=0)
                        System.out.println("[window] LWJGL_MOUSE button="+Mouse.getEventButton()+" down="+Mouse.getEventButtonState()+" wheel="+Mouse.getEventDWheel());
                }
                int wheel=Mouse.getDWheel();
                if (wheel!=0) System.out.println("[window] LWJGL_WHEEL_POLL delta="+wheel);
                if(Keyboard.isKeyDown(Keyboard.KEY_W)) y+=.02f;
                if(Keyboard.isKeyDown(Keyboard.KEY_S)) y-=.02f;
                if(Keyboard.isKeyDown(Keyboard.KEY_A)) x-=.02f;
                if(Keyboard.isKeyDown(Keyboard.KEY_D)) x+=.02f;
                x=Math.max(-.8f,Math.min(.8f,x)); y=Math.max(-.8f,Math.min(.8f,y));
                glViewport(0,0,640,360); glClearColor(.05f,.15f,.35f,1); glClear(GL_COLOR_BUFFER_BIT);
                glMatrixMode(GL_PROJECTION); glLoadIdentity(); glMatrixMode(GL_MODELVIEW); glLoadIdentity();
                glColor3f(Mouse.isButtonDown(0)?0:1, Mouse.isButtonDown(1)?1:.4f, .1f);
                glBegin(GL_TRIANGLES); glVertex2f(x-.15f,y-.15f); glVertex2f(x+.15f,y-.15f); glVertex2f(x,y+.15f); glEnd();
                float mx=Mouse.getX()/320f-1, my=Mouse.getY()/180f-1;
                glColor3f(0,1,1); glBegin(GL_LINES);
                glVertex2f(mx-.025f,my); glVertex2f(mx+.025f,my); glVertex2f(mx,my-.04f); glVertex2f(mx,my+.04f); glEnd();
                Display.update(false);
            }
            Display.destroy();
            Display.destroy();
            if (Display.isCreated() || Display.getWindow() != 0) throw new IllegalStateException("WINDOW_DESTROY_STATE_INVALID");
            System.out.println("[window] WINDOW_PROBE_PASS; inspect input markers separately; Wurm login NOT tested");
            exit=0;
        } catch (Throwable failure) {
            System.out.println("[window] WINDOW_PROBE_FAIL "+failure); failure.printStackTrace(System.out);
            try { WindowBackend.close(); } catch(Throwable cleanup) { cleanup.printStackTrace(System.out); }
        }
        System.exit(exit);
    }
}
