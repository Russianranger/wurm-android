package client;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One numeric-alignment matcher per rendering worker; never retains item text. */
public final class ClientInventoryNumbers {
    private static final ThreadLocal<Matcher> CURRENT = new ThreadLocal<>();
    private ClientInventoryNumbers() { }

    public static boolean matches(String value, String expression) {
        // Keep String.matches receiver/null/regex semantics and the original expression.
        Objects.requireNonNull(value);
        Matcher matcher = CURRENT.get();
        if (matcher == null || !matcher.pattern().pattern().equals(expression)) {
            matcher = Pattern.compile(expression).matcher("");
            CURRENT.set(matcher); // Replace, never accumulate patterns or item values.
        }
        try { return matcher.reset(value).matches(); }
        finally { matcher.reset(""); }
    }
}
