package wurm.android.compat;

/** Called only by the hash-verified HUD's show/close settings callbacks. */
public final class SettingsDispatch {
    private SettingsDispatch() {}
    public static void runLater(Runnable callback) {
        // Both inspected callbacks now call owned, non-JavaFX WurmSettingsFX methods.
        // Run on the calling game thread, which owns the HUD state.
        java.util.Objects.requireNonNull(callback).run();
    }
}
