package poc;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.wurmonline.server.Server;
import com.wurmonline.server.ServerDirInfo;
import com.wurmonline.server.ServerLauncher;
import com.wurmonline.server.gui.folders.Folders;
import com.wurmonline.server.gui.folders.GameFolder;

public final class AndroidServerMain {

    public static void main(String[] args) throws Exception {

        String worldName =
            args.length > 0 ? args[0] : "Adventure";

        Path world =
            Paths.get(worldName)
                 .toAbsolutePath()
                 .normalize();

        System.out.println(
            "============================================"
        );
        System.out.println(
            " Wurm Unlimited ARM64 Android POC"
        );
        System.out.println(
            "============================================"
        );

        System.out.println(
            "[WurmARM64] World: " + world
        );

        ServerDirInfo.setPath(world);

        GameFolder gameFolder = GameFolder.fromPath(world);

        if (gameFolder == null) {
            throw new IllegalStateException(
                "Unable to create GameFolder from: " + world
            );
        }

        System.out.println(
            "[WurmARM64] GameFolder recognized: "
            + gameFolder.getPath()
        );

        boolean distLoaded = Folders.loadDist();

        System.out.println(
            "[WurmARM64] Dist folder loaded: " + distLoaded
        );

        boolean currentSet = Folders.setCurrent(gameFolder);

        if (!currentSet) {
            throw new IllegalStateException(
                "Unable to set current Wurm GameFolder."
            );
        }

        System.out.println(
            "[WurmARM64] Current GameFolder set."
        );

        ServerLauncher launcher =
            new ServerLauncher();


Server.getInstance().setIsPS(true);

System.out.println(
    "[WurmARM64] Personal-server mode forced: "
    + Server.getInstance().isPS()
);
        System.out.println(
            "[WurmARM64] Starting server in offline mode..."
        );

        launcher.runServer(false, true);

        System.out.println(
            "[WurmARM64] runServer() returned."
        );

        System.out.println(
            "[WurmARM64] Keeping JVM alive for server threads..."
        );

        while (true) {
            Thread.sleep(60000);
        }
    }
}
