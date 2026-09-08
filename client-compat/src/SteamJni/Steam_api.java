package SteamJni;

import com.wurmonline.client.steam.SteamAuthTicket;
import com.wurmonline.client.steam.SteamHandler;
import wurm.android.compat.LocalSession;

/** Handwritten ABI adapter for the imported client's local/personal-server path only. */
public final class Steam_api {
    public boolean isUserStatsReady = false;
    private boolean initialized;
    public Steam_api(SteamHandler handler) { LocalSession.requireLocal(); }
    public static String androidCompatibilityVersion() { return "local-v1"; }
    public boolean SteamAPI_RestartAppIfNecessary(int app) { LocalSession.requireLocal(); return false; }
    public boolean SteamAPI_Init() {
        LocalSession.identity(); initialized = true;
        System.out.println("[client] STEAM_SHIM_INITIALIZED mode=local synthetic=true browsing=false; not Steam authentication");
        return true;
    }
    public void SteamAPI_ShutDown() { initialized = false; }
    public boolean IsSteamUserLoggedOn() { return initialized; }
    public String GetCSteamIDString() { return LocalSession.identity(); }
    public SteamAuthTicket GetAuthSessionTicket() {
        if (!initialized) throw new IllegalStateException("Local Steam adapter is not initialized");
        byte[] data = LocalSession.ticket();
        try {
            var ctor = SteamAuthTicket.class.getDeclaredConstructor(long.class, byte[].class, long.class);
            ctor.setAccessible(true);
            System.out.println("[client] LOCAL_TICKET_CREATED bytes=" + data.length + "; synthetic personal-server payload; acceptance unverified");
            return ctor.newInstance(1L, data, (long) data.length);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("CLIENT_TICKET_ABI_MISMATCH", e); }
    }
    public void CancelAuthTicket(long handle) {}
    public void SetOverlayNotificationPosition(int position) {}
    public boolean OverlayNeedsPresent() { return false; }
    public void SteamAPI_RunCallbacks() {}
    public void CreateCallback() {}
    public void DeleteCallback() {}
    public boolean RequestCurrentStats() { return false; }
    public void UserStatsReceived_t() {} // No Steam stats/achievement synchronization in local mode.
    public int GetStatInt(String name) { return 0; }
    public void SetStatInt(String name, int value) {}
    public float GetStatFloat(String name) { return 0; }
    public void SetStatFloat(String name, float value) {}
    public void setAchievements(String name) {}
    public boolean getAchievements(String name) { return false; }
    public void setClearAchievement(String name) {}
    public void StoreStats() {}
    private void noBrowsing() { throw new UnsupportedOperationException("STEAM_BROWSING_UNAVAILABLE: use local target 127.0.0.1:3724"); }
    public void RequestLANServerList() { noBrowsing(); }
    public void RequestInternetServerList() { noBrowsing(); }
    public void RequestFavoriteServerList() { noBrowsing(); }
    public void RequestHistoryServerList() { noBrowsing(); }
    public void RequestFriendsServerList() { noBrowsing(); }
    public void CancelServerListRequest() {}
    public void GameRichPresenceJoinRequested_t(String connect) { noBrowsing(); }
    public void SteamServersConnected_t() {}
    public void ServerResponded(String name, String map, long address, short port, int players, int maxPlayers, boolean password, int ping, boolean secure, short queryPort, String tags) { noBrowsing(); }
    public void ServerFailedToRespond() { noBrowsing(); }
    public void RefreshComplete() { noBrowsing(); }
    public void AddFavoriteServer(long address, short port, short queryPort, long time) {}
    public void RemoveFavoriteServer(long address, short port, short queryPort) {}
    public void AddHistoryServer(long address, short port, short queryPort, long time) {}
    public void RemoveHistoryServer(long address, short port, short queryPort) {}
    public void UpdateRichPresenceConnectionInfo(long address, short port, short queryPort, boolean password, String tags) {}
    public boolean StartWurmDedicatedServerProcess() { return false; } // Android owns its server service.
}
