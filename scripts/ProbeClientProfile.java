import com.wurmonline.client.settings.Profile;
import com.wurmonline.client.launcherfx.WurmSettingsFX;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/** Optional private-JAR test; run in a disposable directory, never on a live profile. */
public final class ProbeClientProfile {
    public static void main(String[] args) throws Exception {
        Profile profile=Profile.getProfile();
        profile.loadPlayer("Thor");
        profile.associateConfig();
        var bindings=new java.io.File(profile.getConfigDir(),"keybindings.txt").toPath();
        byte[] before=Files.readAllBytes(bindings);
        profile.storeConfig();
        Object player=profile.launchProfile();
        if (!Arrays.equals(before,Files.readAllBytes(bindings))) throw new AssertionError("Unedited bindings changed");
        if (!"W".equals(WurmSettingsFX.getKeybind("MOVE_FORWARD")) || !"UP".equals(WurmSettingsFX.getKeybind("MOVE_FORWARD",1)))
            throw new AssertionError("Expected defaults from the qualified private client");
        System.out.println("[profile-probe] PROFILE_PROBE_PASS type="+player.getClass().getName()+" forward=W secondary=UP keyfileSha256="+
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(before))+"; no game rendering/login attempted");
    }
}
