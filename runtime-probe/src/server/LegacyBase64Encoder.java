package server;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/** The constructor/encode(byte[]) ABI used by the inspected LoginHandler only. */
public final class LegacyBase64Encoder {
    private static final AtomicBoolean observed = new AtomicBoolean();

    public String encode(byte[] input) {
        String encoded = encodeValue(input);
        if (observed.compareAndSet(false, true))
            System.out.println("[server-login] BASE64_ENCODE_ACTIVE inputBytes=" + input.length +
                " outputChars=" + encoded.length() + "; credential data omitted");
        return encoded;
    }

    static String encodeValue(byte[] input) {
        String encoded = Base64.getEncoder().encodeToString(input);
        // Legacy encode (unlike encodeBuffer) terminates full 57-byte lines,
        // including the last full line, but never a final partial line.
        // LoginHandler's SHA-1 digest is 20 bytes: 28 characters, no newline.
        int fullLines = input.length / 57;
        if (fullLines == 0) return encoded;
        StringBuilder result = new StringBuilder();
        for (int line = 0; line < fullLines; line++)
            result.append(encoded, line * 76, (line + 1) * 76).append(System.lineSeparator());
        return result.append(encoded, fullLines * 76, encoded.length()).toString();
    }
}
