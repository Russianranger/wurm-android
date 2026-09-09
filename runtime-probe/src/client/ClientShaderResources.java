package client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Convert only two SHA-verified imported blur resources to equivalent GLSL 1.20 syntax. */
public final class ClientShaderResources {
    private static final String PREFIX = "com/wurmonline/client/resources/programs/gaussBlur.";
    static final Map<String, String> ORIGINALS = Map.of(
        PREFIX + "vertex.shader", "b0506b81c61958bf599068766a139f5a5fafe7f8536c268c53a492d6cee33bda",
        PREFIX + "fragment.shader", "2ab15620aef10c2fb6e778ec1265e0df00a40508c6fdda7d763a43fb8fd69b9b");
    static String replace(String text, String from, String to, int expected) throws IOException {
        int count = 0, position = 0;
        while ((position = text.indexOf(from, position)) >= 0) { count++; position += from.length(); }
        if (count != expected) throw new IOException("CLIENT_SHADER_RULE_MISMATCH expected=" + expected + " actual=" + count);
        return text.replace(from, to);
    }
    // Kept package-private for authored fixture tests. Production pins the whole
    // original byte stream and verifies the reversed stream, including line endings.
    static byte[] convert(byte[] bytes, boolean vertex, boolean reverse) throws IOException {
        String text = new String(bytes, StandardCharsets.UTF_8);
        String[][] rules = vertex ? new String[][] {
            {"#version 330", "#version 120", "1"},
            {"layout (location = 0) in vec3 Position;", "attribute vec3 Position;", "1"}
        } : new String[][] {
            {"#version 330", "#version 120", "1"},
            {"layout (location = 0) out vec4 diffuseOut;", "#define diffuseOut gl_FragColor", "1"},
            {"texture(", "texture2D(", "7"}
        };
        for (String[] rule : rules)
            text = replace(text, rule[reverse ? 1 : 0], rule[reverse ? 0 : 1], Integer.parseInt(rule[2]));
        return text.getBytes(StandardCharsets.UTF_8);
    }
    static byte[] prepare(String name, byte[] original) throws Exception {
        if (!ClientGraphicsPatch.sha(original).equals(ORIGINALS.get(name)))
            throw new IOException("CLIENT_SHADER_RESOURCE_UNSUPPORTED name=" + name + " sha256=" + ClientGraphicsPatch.sha(original));
        byte[] changed = convert(original, name.endsWith("vertex.shader"), false);
        verify(name, changed);
        System.out.println("[client] SHADER_RESOURCE_PREPARED name=" + name + " originalSha256=" + ClientGraphicsPatch.sha(original) + " adaptedSha256=" + ClientGraphicsPatch.sha(changed) + " dialect=120; blur arithmetic unchanged");
        return changed;
    }
    static void verify(String name, byte[] changed) throws Exception {
        byte[] restored = convert(changed, name.endsWith("vertex.shader"), true);
        if (!ClientGraphicsPatch.sha(restored).equals(ORIGINALS.get(name)))
            throw new IOException("CLIENT_SHADER_RESOURCE_INTEGRITY_FAILED name=" + name);
    }
}
