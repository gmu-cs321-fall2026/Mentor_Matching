import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link Json}, our own parser and writer (ADR-004 chose no runtime
 * dependencies). Every request and response goes through it, so malformed
 * input must always come back as an {@link IllegalArgumentException}, which
 * {@code MentoringApp} turns into a 400.
 */
class JsonTest {

    /* ---------- parsing values ---------- */

    @Test
    void parsesEveryKindOfValue() {
        assertEquals("hi", Json.parse("\"hi\""));
        assertEquals(42L, Json.parse("42"));
        assertEquals(-7L, Json.parse("-7"));
        assertEquals(1.5, Json.parse("1.5"));
        assertEquals(1200.0, Json.parse("1.2e3"));
        assertEquals(1.0E-2, Json.parse("1E-2"));
        assertEquals(Boolean.TRUE, Json.parse("true"));
        assertEquals(Boolean.FALSE, Json.parse("false"));
        assertNull(Json.parse("null"));
    }

    @Test
    void parsesNestedObjectsAndArraysKeepingFieldOrder() {
        Object parsed = Json.parse(
                " { \"b\" : [1, \"two\", {\"c\": null}], \"a\": {}, \"z\": [] } ");
        Map<String, Object> expected = new LinkedHashMap<>();
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("c", null);
        expected.put("b", List.of(1L, "two", inner));
        expected.put("a", Map.of());
        expected.put("z", List.of());
        assertEquals(expected, parsed);
        assertEquals(List.of("b", "a", "z"), new ArrayList<>(((Map<?, ?>) parsed).keySet()));
    }

    @Test
    void decodesEveryEscape() {
        assertEquals("\" \\ / \b \f \n \r \t é",
                Json.parse("\"\\\" \\\\ \\/ \\b \\f \\n \\r \\t \\u00e9\""));
    }

    @Test
    void aBlankOrMissingBodyIsAnEmptyObject() {
        assertEquals(Map.of(), Json.parseObject(null));
        assertEquals(Map.of(), Json.parseObject(""));
        assertEquals(Map.of(), Json.parseObject("   \n"));
    }

    @Test
    void aBodyThatIsNotAnObjectIsRefused() {
        assertMalformed(() -> Json.parseObject("[1, 2]"), "expected a JSON object");
        assertMalformed(() -> Json.parseObject("\"text\""), "expected a JSON object");
    }

    /* ---------- malformed input ---------- */

    @Test
    void malformedDocumentsAreRefusedWithAReason() {
        assertMalformed(() -> Json.parse(""), "unexpected end");
        assertMalformed(() -> Json.parse("{"), "unexpected end");
        assertMalformed(() -> Json.parse("{\"a\":1"), "unexpected end");
        assertMalformed(() -> Json.parse("{\"a\" 1}"), "expected ':'");
        assertMalformed(() -> Json.parse("{a: 1}"), "expected a field name");
        assertMalformed(() -> Json.parse("{\"a\":1 \"b\":2}"), "expected ',' or '}'");
        assertMalformed(() -> Json.parse("[1 2]"), "expected ',' or ']'");
        assertMalformed(() -> Json.parse("{\"a\":1} extra"), "unexpected text");
        assertMalformed(() -> Json.parse("tru"), "expected true");
        assertMalformed(() -> Json.parse("nul"), "expected null");
        assertMalformed(() -> Json.parse("fals"), "expected false");
        assertMalformed(() -> Json.parse("@"), "expected a value");
        assertMalformed(() -> Json.parse("1.2.3"), "bad number");
        assertMalformed(() -> Json.parse("--1"), "bad number");
    }

    @Test
    void brokenStringsAreRefused() {
        assertMalformed(() -> Json.parse("\"open"), "unterminated string");
        assertMalformed(() -> Json.parse("\"ends in \\"), "unterminated escape");
        assertMalformed(() -> Json.parse("\"\\x\""), "bad escape");
        assertMalformed(() -> Json.parse("\"\\u12\""), "truncated unicode escape");
        assertMalformed(() -> Json.parse("\"\\uzzzz\""), "bad unicode escape");
    }

    @Test
    void deepNestingIsRefusedInsteadOfOverflowingTheStack() {
        // A 64 KB body of brackets used to throw StackOverflowError, which the
        // route wrapper does not catch, so the caller got no response at all.
        String deep = "[".repeat(60_000) + "]".repeat(60_000);
        assertMalformed(() -> Json.parse(deep), "nested too deeply");
        String deepObject = "{\"a\":".repeat(10_000) + "1" + "}".repeat(10_000);
        assertMalformed(() -> Json.parse(deepObject), "nested too deeply");
    }

    @Test
    void reasonableNestingIsFine() {
        String nested = "[".repeat(Json.MAX_DEPTH) + "]".repeat(Json.MAX_DEPTH);
        assertTrue(Json.parse(nested) instanceof List);
    }

    /* ---------- writing ---------- */

    @Test
    void writesEveryKindOfValue() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("s", "text");
        value.put("n", 3);
        value.put("d", 2.5);
        value.put("t", true);
        value.put("none", null);
        value.put("e", GuidanceArea.RESUME_REVIEW);
        value.put("list", List.of(1, "x"));
        value.put("set", new java.util.TreeSet<>(List.of("b", "a")));
        value.put("empty", Map.of());
        assertEquals("{\"s\":\"text\",\"n\":3,\"d\":2.5,\"t\":true,\"none\":null,"
                + "\"e\":\"RESUME_REVIEW\",\"list\":[1,\"x\"],\"set\":[\"a\",\"b\"],\"empty\":{}}",
                Json.write(value));
    }

    @Test
    void writesOtherObjectsAsTheirText() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertEquals("\"11111111-1111-1111-1111-111111111111\"", Json.write(id));
        assertEquals("\"2026-11-23\"", Json.write(LocalDate.of(2026, 11, 23)));
        assertEquals("null", Json.write(null));
    }

    @Test
    void escapesWhatJsonRequires() {
        assertEquals("\"\\\" \\\\ \\b \\f \\n \\r \\t \\u0001 é /\"",
                Json.write("\" \\ \b \f \n \r \t \u0001 é /"));
    }

    @Test
    void whatIsWrittenParsesBackTheSame() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", "Line one\nline \"two\" \\ \u0007");
        value.put("count", 12L);
        value.put("ratio", 0.25);
        value.put("tags", Arrays.asList("a", null, true));
        value.put("nested", Map.of("deeper", List.of(Map.of("k", false))));
        assertEquals(value, Json.parse(Json.write(value)));
    }

    private static void assertMalformed(Runnable call, String reason) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call::run);
        assertTrue(e.getMessage() != null && e.getMessage().contains(reason),
                "expected a message containing '" + reason + "' but got '" + e.getMessage() + "'");
    }
}
