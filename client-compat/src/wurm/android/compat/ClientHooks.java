package wurm.android.compat;

import com.wurmonline.client.steam.SteamAuthTicket;
import com.wurmonline.client.steam.SteamHandler;

/** Typed calls avoid reflecting over SteamHandler's unrelated JavaFX method signatures. */
public final class ClientHooks {
    public static Object initializeSteam() {
        SteamHandler handler = new SteamHandler();
        SteamHandler.SteamInitializeResults result = handler.initializeSteam();
        System.out.println("[client] STEAM_HANDLER_RESULT " + result + "; local adapter only");
        if (result != SteamHandler.SteamInitializeResults.InitSuccess)
            throw new IllegalStateException("Local Steam handler initialization failed: " + result);
        return handler;
    }
    public static void testTicket(Object value) {
        SteamHandler handler = (SteamHandler) value;
        try {
            handler.requestAuthTicket();
            SteamAuthTicket ticket = handler.getAuthTicket();
            byte[] bytes = ticket.getTicketArray();
            if (bytes.length == 0 || ticket.getTokenLen() != bytes.length || ticket.getAuthTicket() == 0)
                throw new IllegalStateException("LOCAL_TICKET_CONTRACT_FAILED");
            System.out.println("[client] STEAM_COMPAT_OK importedHandler=true importedTicket=true bytes=" + bytes.length + "; server acceptance NOT tested");
        } finally { handler.shutdownSteam(); }
    }
}
