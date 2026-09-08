package SteamJni;

import com.wurmonline.server.steam.SteamHandler;

public class SteamServerApi {

    private final SteamHandler steamHandler;
    private boolean connected = false;

    public final int eServerModeInvalid = 0;
    public final int eServerModeNoAuthentication = 1;
    public final int eServerModeAuthentication = 2;
    public final int eServerModeAuthenticationAndSecure = 3;

    public static final int beginAuthSessionResultOk = 0;
    public static final int beginAuthSessionResultDuplicateResult = 2;
    public static final boolean USE_GS_AUTH_API = true;

    public SteamServerApi(SteamHandler steamHandler) {
        this.steamHandler = steamHandler;
        System.out.println("[WurmARM64] SteamServerApi shim created.");
    }

    public void CreateCallback() {
        System.out.println("[WurmARM64] Steam callback initialized.");
    }

    public void DeleteCallback() {
    }

    public void SteamGameServer_RunCallbacks() {
    }

    public boolean SteamGameServer_Init(
            long ip,
            short steamPort,
            short gamePort,
            short queryPort,
            int serverMode,
            String version) {

        System.out.println(
            "[WurmARM64] Pretending Steam game server initialized."
        );
        System.out.println(
            "[WurmARM64] Game port=" + gamePort +
            " query port=" + queryPort +
            " mode=" + serverMode +
            " version=" + version
        );

        return true;
    }

    public void SetModDir(String value) {
        System.out.println("[WurmARM64] ModDir=" + value);
    }

    public void SetDedicatedServer(boolean value) {
    }

    public void SetProduct(String value) {
    }

    public void SetGameDescription(String value) {
    }

    public void SetGameTags(String value) {
        System.out.println("[WurmARM64] GameTags=" + value);
    }

    public void LogOnAnonymous() {
        System.out.println("[WurmARM64] Simulating anonymous Steam connection.");

        if (!connected) {
            connected = true;
            steamHandler.onSteamConnected();
        }
    }

    public void EnableHeartbeats(boolean value) {
    }

    public void LogOff() {
    }

    public void SteamGameServer_Shutdown() {
    }

    public void setMaxPlayerCount(int value) {
    }

    public void SetPasswordProtected(boolean value) {
    }

    public void SetServerName(String value) {
        System.out.println("[WurmARM64] ServerName=" + value);
    }

    public void SetBotCount(int value) {
    }

    public void SetMapName(String value) {
        System.out.println("[WurmARM64] MapName=" + value);
    }

    public void SetUserAchievement(int steamId, String achievement) {
    }

    public void GetUserAchievement(int steamId, String achievement) {
    }

    public void StoreUserStats(int steamId) {
    }

    public int BeginAuthSession(
            String steamId,
            byte[] ticket,
            long tokenLength) {

        /*
         * Offline Wurm servers normally bypass the Steam ticket
         * validation path. Returning OK here also prevents a Steam
         * dependency if something unexpectedly reaches this method.
         */
        System.out.println(
            "[WurmARM64] Synthetic authentication accepted for " + steamId
        );

        return beginAuthSessionResultOk;
    }

    public void EndAuthSession(String steamId) {
    }

    public void OnSteamServersConnected() {
        steamHandler.onSteamConnected();
    }

    public void OnValidateAuthTicketResponse(
            String steamId,
            boolean authenticated) {

        steamHandler.onValidateAuthTicketResponse(
            steamId,
            authenticated
        );
    }
}
