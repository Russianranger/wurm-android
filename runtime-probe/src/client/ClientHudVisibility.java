package client;

import java.lang.reflect.Field;
import java.util.List;

/** Inspected HUD recovery on the owning game thread, without toggling or rebuilding UI. */
public final class ClientHudVisibility {
    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field=owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
    public static void command(String action) throws ReflectiveOperationException {
        if (!List.of("observe", "restore-focus", "restore-button").contains(action))
            throw new IllegalArgumentException("Unknown HUD command");
        Class<?> engine=Class.forName("com.wurmonline.client.WurmClientBase");
        if (field(engine,"gameThread").get(null) != Thread.currentThread())
            throw new IllegalStateException("HUD access requires the game thread");
        Object client=field(engine,"clientObject").get(null);
        if (client == null || field(engine,"startupRenderer").get(client) != null) {
            if (!action.equals("observe")) System.out.println("[client-ui] HUD_RESTORE_SKIPPED startup-active");
            return;
        }
        Object hud=field(engine,"hud").get(client);
        if (hud == null) return;
        Field visible=field(hud.getClass(),"visible");
        boolean before=visible.getBoolean(hud);
        if (action.equals("observe")) {
            System.out.println("[client-ui] HUD_STATE visible="+before);
        } else {
            // setVisible(true) is idempotent. A toggle would hide an already visible HUD.
            hud.getClass().getMethod("setVisible",boolean.class).invoke(hud,true);
            System.out.println("[client-ui] HUD_RESTORED reason="+action+" before="+before+" after="+visible.getBoolean(hud));
        }
    }
}
