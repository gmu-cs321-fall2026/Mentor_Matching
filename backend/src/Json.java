import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer.
 *
 * <p>Hand-rolled because the Docker build compiles with plain {@code javac} and
 * no dependency manager (ADR-004 fallback), so Jackson or Gson are not on the
 * classpath. Covers exactly what the Sprint 1 contracts need: objects, arrays,
 * strings, numbers, booleans and null.
 *
 * <p>Parsed numbers come back as {@link Long} when integral and {@link Double}
 * otherwise; {@link ProfileBinding} coerces them to the types the domain wants.
 */
final class Json {

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    /**
     * @param text a complete JSON document
     * @return a Map, List, String, Long, Double, Boolean or null
     * @throws IllegalArgumentException if the text is not valid JSON
     */
    static Object parse(String text) {
        Json reader = new Json(text == null ? "" : text);
        reader.skipWhitespace();
        Object value = reader.readValue();
        reader.skipWhitespace();
        if (reader.pos < reader.text.length()) {
            throw new IllegalArgumentException("unexpected text after the JSON value");
        }
        return value;
    }

    /**
     * Parses a request body that must be a JSON object.
     *
     * @param text the body; blank counts as an empty object so a bodyless
     *     request reports missing fields rather than a parse error
     * @throws IllegalArgumentException if the text is not a JSON object
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String text) {
        if (text == null || text.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        return (Map<String, Object>) value;
    }

    /** Serializes maps, iterables, numbers, booleans, enums and null; anything else by toString. */
    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out);
        return out.toString();
    }

    private static void writeValue(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(String.valueOf(entry.getKey()), out);
                out.append(':');
                writeValue(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof Iterable<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(item, out);
            }
            out.append(']');
        } else if (value instanceof Boolean || value instanceof Number) {
            out.append(value);
        } else if (value instanceof Enum<?> constant) {
            writeString(constant.name(), out);
        } else {
            writeString(String.valueOf(value), out);
        }
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private Object readValue() {
        char c = peek();
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> {
                expect("true");
                yield Boolean.TRUE;
            }
            case 'f' -> {
                expect("false");
                yield Boolean.FALSE;
            }
            case 'n' -> {
                expect("null");
                yield null;
            }
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw new IllegalArgumentException("expected a field name");
            }
            String key = readString();
            skipWhitespace();
            if (peek() != ':') {
                throw new IllegalArgumentException("expected ':' after field " + key);
            }
            pos++;
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = peek();
            pos++;
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw new IllegalArgumentException("expected ',' or '}' in object");
            }
        }
    }

    private List<Object> readArray() {
        List<Object> items = new ArrayList<>();
        pos++;
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return items;
        }
        while (true) {
            skipWhitespace();
            items.add(readValue());
            skipWhitespace();
            char c = peek();
            pos++;
            if (c == ']') {
                return items;
            }
            if (c != ',') {
                throw new IllegalArgumentException("expected ',' or ']' in array");
            }
        }
    }

    private String readString() {
        pos++;
        StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw new IllegalArgumentException("unterminated string");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw new IllegalArgumentException("unterminated escape");
            }
            char escape = text.charAt(pos++);
            switch (escape) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw new IllegalArgumentException("truncated unicode escape");
                    }
                    out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> throw new IllegalArgumentException("bad escape \\" + escape);
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String token = text.substring(start, pos);
        if (token.isEmpty()) {
            throw new IllegalArgumentException("expected a value at position " + start);
        }
        try {
            if (token.contains(".") || token.contains("e") || token.contains("E")) {
                return Double.valueOf(token);
            }
            return Long.valueOf(token);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad number " + token);
        }
    }

    private void expect(String literal) {
        if (!text.startsWith(literal, pos)) {
            throw new IllegalArgumentException("expected " + literal);
        }
        pos += literal.length();
    }

    private char peek() {
        if (pos >= text.length()) {
            throw new IllegalArgumentException("unexpected end of JSON");
        }
        return text.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }
}
