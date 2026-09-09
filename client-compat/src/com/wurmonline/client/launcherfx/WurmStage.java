package com.wurmonline.client.launcherfx;

/** Engine icon-resource helper only. Android does not instantiate a desktop Stage. */
public final class WurmStage {
    private WurmStage() {}
    public static String androidCompatibilityVersion() { return "headless-icons-v1"; }
    public static String[] getIconNames() {
        System.out.println("[client] ICON_RESOURCES source=imported-client javafx=false");
        String[] names = new String[4];
        for (int i=0, size=128; i<names.length; i++, size/=2) names[i] = "/icon2_" + size + ".png";
        return names;
    }
}
