package client;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Remap only the inspected damaged sound fallback to an authored silent WAV. */
public final class ClientSoundResources {
    static final String PREFIX="com/wurmonline/client/resources/";
    static final String MAPPINGS=PREFIX+"mappings.txt";
    static final String MAP_SHA="e6705f4781fce58e0d24c6f6971025ba098b81c9e469bcfc713c15a07b010dd8";
    static final String OLD="res/missingsound.ogg", NEW="res/wurm-android-missing-sound.wav";
    static final String RESOURCE=PREFIX+NEW;
    static final String ORIGINAL="2d54bb2275eee9604fb564ff447c44eef3eec04f5d3c072da24ac6e6ba2325c9";
    static byte[] remap(byte[] original, boolean reverse) throws IOException {
        String text=new String(original,StandardCharsets.UTF_8);
        return ClientShaderResources.replace(text,reverse?NEW:OLD,reverse?OLD:NEW,1).getBytes(StandardCharsets.UTF_8);
    }
    static byte[] prepare(byte[] mappings, byte[] originalSound) throws Exception {
        if(!ClientGraphicsPatch.sha(mappings).equals(MAP_SHA) || !ClientGraphicsPatch.sha(originalSound).equals(ORIGINAL))
            throw new IOException("SOUND_FALLBACK_UNSUPPORTED mappingsSha256="+ClientGraphicsPatch.sha(mappings)+" soundSha256="+ClientGraphicsPatch.sha(originalSound));
        byte[] changed=remap(mappings,false); verifyMappings(changed);
        System.out.println("[client-audio] "+java.time.Instant.now()+" SOUND_FALLBACK_PREPARED source=known-corrupt replacement=authored-100ms-silent-wav channels=1 rate=44100; normal sound mappings and imported packs unchanged");
        return changed;
    }
    static void verifyMappings(byte[] changed) throws Exception {
        if(!ClientGraphicsPatch.sha(remap(changed,true)).equals(MAP_SHA)) throw new IOException("SOUND_MAPPING_INTEGRITY_FAILED");
    }
    static byte[] silentWav() {
        int dataBytes=4410*2;
        ByteBuffer b=ByteBuffer.allocate(44+dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+dataBytes);
        b.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1);
        b.putInt(44100).putInt(88200).putShort((short)2).putShort((short)16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataBytes);
        return b.array(); // Zero-initialized PCM: 100 ms mono 16-bit silence.
    }
    static void verify(byte[] changed) throws IOException {
        if(!Arrays.equals(changed,silentWav())) throw new IOException("SOUND_FALLBACK_INTEGRITY_FAILED");
    }
}
