package org.totipo.tooling.publication;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.totipo.tooling.StrictJson;

/** Bounded streaming input and Python json.dumps-compatible output (ensure_ascii=True). */
final class Json {
    private Json() {}
    static Object parse(byte[] bytes, StrictJson.Profile profile) throws IOException {
        if (bytes.length > profile.bytes()) throw new IOException("JSON input exceeds byte limit");
        // Python reads UTF-8 text first. Jackson's automatic UTF-16/32 and BOM
        // detection must not introduce a broader serialized publication contract.
        java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
        if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf)
            throw new IOException("JSON UTF-8 BOM rejected");
        for (byte b : bytes) if (b == 0) throw new IOException("Raw NUL in JSON");
        try (var p = StrictJson.open(bytes, profile)) {
            p.nextToken();
            Object value = value(p);
            if (p.nextToken() != null) throw new IOException("Trailing JSON value");
            return value;
        }
    }
    private static Object value(JsonParser p) throws IOException {
        JsonToken t = p.currentToken();
        if (t == null) throw new IOException("Incomplete JSON");
        return switch (t) {
            case START_OBJECT -> {
                Map<String, Object> map = new LinkedHashMap<>();
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    if (p.currentToken() != JsonToken.FIELD_NAME) throw new IOException("Expected member");
                    String name = p.currentName();
                    p.nextToken();
                    map.put(name, value(p));
                }
                yield map;
            }
            case START_ARRAY -> {
                List<Object> list = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) list.add(value(p));
                yield list;
            }
            case VALUE_STRING -> p.getText();
            case VALUE_NUMBER_INT -> StrictJson.exactInteger(p);
            case VALUE_NUMBER_FLOAT -> new BigDecimal(p.getText());
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case VALUE_NULL -> null;
            default -> throw new IOException("Unexpected JSON token: " + t);
        };
    }
    static Map<String, Object> object(Object v) throws IOException {
        if (!(v instanceof Map<?, ?> m)) throw new IOException("Expected object");
        Map<String, Object> result = new LinkedHashMap<>();
        for (var e : m.entrySet()) {
            if (!(e.getKey() instanceof String s)) throw new IOException("Expected member name");
            result.put(s, e.getValue());
        }
        return result;
    }
    static List<?> array(Object v) throws IOException {
        if (!(v instanceof List<?> l)) throw new IOException("Expected array");
        return l;
    }
    static String string(Object v) throws IOException {
        if (!(v instanceof String s)) throw new IOException("Expected string");
        return s;
    }
    // Java UTF-16 order differs from Python code-point order above U+FFFF.
    private static final Comparator<String> ORDER = (a, b) -> {
        var x = a.codePoints().iterator(); var y = b.codePoints().iterator();
        while (x.hasNext() && y.hasNext()) {
            int c = Integer.compare(x.nextInt(), y.nextInt());
            if (c != 0) return c;
        }
        return Boolean.compare(x.hasNext(), y.hasNext());
    };
    static String dumps(Object v, boolean pretty) throws IOException { return render(v, pretty, 0); }
    private static String render(Object v, boolean pretty, int depth) throws IOException {
        if (v instanceof String s) return quote(s);
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        if (v == null) return "null";
        List<String> parts = new ArrayList<>();
        boolean map = v instanceof Map<?, ?>;
        if (map) {
            var m = object(v);
            for (String key : m.keySet().stream().sorted(ORDER).toList())
                parts.add(quote(key) + ": " + render(m.get(key), pretty, depth + 1));
        } else {
            for (Object item : array(v)) parts.add(render(item, pretty, depth + 1));
        }
        String open = map ? "{" : "[", close = map ? "}" : "]";
        if (parts.isEmpty()) return open + close;
        if (!pretty) return open + String.join(", ", parts) + close;
        String pad = "  ".repeat(depth + 1);
        return open + "\n" + pad + String.join(",\n" + pad, parts) + "\n" + "  ".repeat(depth) + close;
    }
    static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\");
                case '\b' -> b.append("\\b"); case '\f' -> b.append("\\f");
                case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
                default -> {
                    if (c < 32 || c > 126) b.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
