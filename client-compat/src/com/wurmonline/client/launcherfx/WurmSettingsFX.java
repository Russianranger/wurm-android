package com.wurmonline.client.launcherfx;

import com.wurmonline.client.options.Options;
import com.wurmonline.client.settings.Profile;
import wurm.android.compat.KeybindStore;
import java.io.*;

/** Engine-facing settings helper without the desktop WurmStage/JavaFX hierarchy. */
public final class WurmSettingsFX {
    private static final KeybindStore bindings=new KeybindStore();
    private static final WurmSettingsFX instance=new WurmSettingsFX();
    public boolean keybindsNeedUpdate, closeSettings;
    private WurmSettingsFX() {}
    public static String androidCompatibilityVersion() { return "headless-keybinds-v1"; }
    public static void loadAllKeybinds(File file) {
        try { bindings.load(file.toPath()); }
        catch (IOException | IllegalArgumentException failure) { throw new IllegalStateException("KEYBINDS_LOAD_FAILED "+file, failure); }
    }
    public static void saveAllKeybinds() {
        Profile profile=Profile.getProfile();
        File directory=Options.keybindingsSource.value()==0 ? profile.getConfigDir() : profile.getPlayerDir();
        try { bindings.save(new File(directory,"keybindings.txt").toPath()); }
        catch (IOException failure) { throw new IllegalStateException("KEYBINDS_SAVE_FAILED "+directory, failure); }
    }
    public static boolean addKeybind(String action,String key,boolean primary) { instance.keybindsNeedUpdate=true; return bindings.add(action,key,primary); }
    public static boolean removeKeybind(String action) { instance.keybindsNeedUpdate=true; return bindings.remove(action); }
    public static String getKeybind(String action) { return bindings.get(action,0); }
    public static String getKeybind(String action,int index) { return bindings.get(action,index); }
    public static String isDuplicateKeybind(String key) { return bindings.owner(key); }
    public static WurmSettingsFX getInstance(boolean inLauncher) { return instance; }
    public void show() {
        System.out.println("[client-ui] OPEN_GRAPHICS_SETTINGS");
        closeSettings=true; // Let the HUD remove its empty desktop-settings placeholder.
    }
    public void close() { closeSettings=true; }
    public void restart() { closeSettings=false; }
    /** The inspected HUD passes null; its ActionEvent descriptor is privately redirected to Object. */
    public void restartAndClose(Object event) { closeSettings=false; }
}
