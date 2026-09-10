package client;

import java.io.IOException;

/** Only the two inspected HUD settings callbacks are redirected; no JavaFX emulation. */
public final class ClientSettingsPatch {
    static final String HUD = "com/wurmonline/client/renderer/gui/HeadsUpDisplay.class";
    static final String ORIGINAL = "2d4cfa759ae6c725999f6c66d319b30f091a74f7f92419b81697b510bc25aba9";
    private static final String FROM = "javafx/application/Platform";
    private static final String TO = "wurm/android/compat/SettingsDispatch";
    private static final String EVENT = "(Ljavafx/event/ActionEvent;)V";
    private static final String OBJECT = "(Ljava/lang/Object;)V";
    static byte[] prepare(byte[] original) throws Exception {
        byte[] result = ClientGraphicsPatch.redirect(original, ORIGINAL, FROM, TO);
        return ClientGraphicsPatch.redirect(result, ClientGraphicsPatch.sha(result), EVENT, OBJECT);
    }
    static void verify(byte[] patched) throws Exception {
        byte[] original = ClientGraphicsPatch.redirect(patched, ClientGraphicsPatch.sha(patched), OBJECT, EVENT);
        original = ClientGraphicsPatch.redirect(original, ClientGraphicsPatch.sha(original), TO, FROM);
        if (!ClientGraphicsPatch.sha(original).equals(ORIGINAL)) throw new IOException("SETTINGS_PATCH_INTEGRITY_FAILED");
    }
}
