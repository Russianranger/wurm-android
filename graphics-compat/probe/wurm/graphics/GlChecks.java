package wurm.graphics;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

/** Attribute errors to the immediately preceding operation; never turn an error into a pass. */
public final class GlChecks {
    private final IntSupplier error;
    private final Consumer<String> log;
    public GlChecks(IntSupplier error, Consumer<String> log) { this.error = error; this.log = log; }
    public void run(String stage, Runnable operation) {
        log.accept("GL_BEGIN stage=" + stage);
        operation.run();
        check(stage);
    }
    public int get(String stage, IntSupplier operation) {
        log.accept("GL_BEGIN stage=" + stage);
        int value = operation.getAsInt();
        check(stage);
        return value;
    }
    public void check(String stage) {
        StringBuilder errors = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            int code = error.getAsInt();
            if (code == 0) {
                if (errors.length() == 0) { log.accept("GL_OK stage=" + stage); return; }
                break;
            }
            if (errors.length() != 0) errors.append(',');
            errors.append(String.format("0x%04X", code)).append('(').append(name(code)).append(')');
            if (i == 15) errors.append("; error drain limit reached");
        }
        String message = "stage=" + stage + " errors=" + errors;
        log.accept("GL_ERROR " + message);
        throw new IllegalStateException(message);
    }
    private static String name(int code) {
        switch (code) {
            case 0x0500: return "GL_INVALID_ENUM";
            case 0x0501: return "GL_INVALID_VALUE";
            case 0x0502: return "GL_INVALID_OPERATION";
            case 0x0503: return "GL_STACK_OVERFLOW";
            case 0x0504: return "GL_STACK_UNDERFLOW";
            case 0x0505: return "GL_OUT_OF_MEMORY";
            case 0x0506: return "GL_INVALID_FRAMEBUFFER_OPERATION";
            case 0x0507: return "GL_CONTEXT_LOST";
            default: return "UNKNOWN_GL_ERROR";
        }
    }
}
