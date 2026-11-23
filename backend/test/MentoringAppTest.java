import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real routes in {@link MentoringApp}, over HTTP, against stub-mode Shared
 * Core: status codes, the shared error format, auth and role checks, input
 * validation, and static files. Each test gets a fresh server and store.
 */
class MentoringAppTest {

    private static final String MENTOR = "mentor-demo";
    private static final String STUDENT = "student-demo";
    private static final String MENTOR_ID = SharedCoreClient.DEMO_MENTOR_ID.toString();
    private static final String STUDENT_ID = SharedCoreClient.DEMO_STUDENT_ID.toString();
    private static final String MENTOR_BODY = "{\"industry\":\"TECHNOLOGY\","
            + "\"guidanceAreas\":[\"RESUME_REVIEW\"],\"hoursPerMonth\":4,"
            + "\"contactMethod\":\"EMAIL\",\"maxMentees\":2}";
    private static final String STUDENT_BODY = "{\"targetIndustries\":[\"TECHNOLOGY\"],"
            + "\"guidanceWanted\":[\"RESUME_REVIEW\"]}";

    @TempDir
    Path temp;

    private HttpServer server;
    private String base;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws IOException {
        Path pages = Files.createDirectory(temp.resolve("static"));
        Files.writeString(pages.resolve("index.html"), "<h1>home</h1>");
        Files.writeString(pages.resolve("connections.html"), "<script src=\"connections.js\"></script>");
        Files.writeString(pages.resolve("connections.js"), "// page script");
        Files.writeString(pages.resolve("styles.css"), "body {}");
        Files.writeString(pages.resolve("logo.png"), "png");
        Files.writeString(temp.resolve("secret.txt"), "outside the static folder");

        server = MentoringApp.start(0, new SharedCoreClient(null), pages);
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    /* ---------- public routes and static files ---------- */

    @Test
    void healthAndEnumsArePublic() throws Exception {
        Reply health = get("/health", null);
        assertEquals(200, health.status);
        assertEquals("application/json; charset=utf-8", health.contentType);
        assertEquals(Map.of("status", "ok"), health.json());

        Reply enums = get("/enums", null);
        assertEquals(200, enums.status);
        assertTrue(((List<?>) enums.json().get("industries")).contains("TECHNOLOGY"));
        assertTrue(((List<?>) enums.json().get("guidanceAreas")).contains("RESUME_REVIEW"));
        assertTrue(((List<?>) enums.json().get("contactMethods")).contains("EMAIL"));
        assertError(send("POST", "/enums", null, "{}"), 405, "method_not_allowed");
    }

    @Test
    void pagesAreServedWithTheirContentType() throws Exception {
        assertPage("/", "text/html; charset=utf-8", "<h1>home</h1>");
        assertPage("/index.html", "text/html; charset=utf-8", "<h1>home</h1>");
        assertPage("/styles.css", "text/css; charset=utf-8", "body {}");
        assertPage("/logo.png", "application/octet-stream", "png");
        // These share the /connections prefix with the API but need no token.
        assertPage("/connections.html", "text/html; charset=utf-8", "connections.js");
        assertPage("/connections.js", "application/javascript; charset=utf-8", "page script");
    }

    @Test
    void missingFilesAndFilesOutsideTheFolderAre404() throws Exception {
        assertError(get("/nope.html", null), 404, "not_found");
        assertError(get("/connectionsfoo", MENTOR), 404, "not_found");
        Reply escape = get("/../secret.txt", null);
        assertEquals(404, escape.status);
        assertFalse(escape.body.contains("outside the static folder"));
        assertError(send("POST", "/index.html", null, ""), 405, "method_not_allowed");
    }

    /* ---------- auth ---------- */

    @Test
    void protectedRoutesNeedAToken() throws Exception {
        for (String path : List.of("/mentors", "/mentors/profile", "/students/match-profile",
                "/mentors/recommendations", "/connections", "/connections/" + UUID.randomUUID())) {
            assertError(get(path, null), 401, "unauthorized");
        }
        assertError(get("/mentors", "not-a-real-token"), 401, "unauthorized");
        assertError(get("/mentors", "not-a-uuid:Mentor"), 401, "unauthorized");
    }

    @Test
    void theBearerPrefixIsOptionalAndCaseInsensitive() throws Exception {
        Reply reply = sendWithHeader("GET", "/mentors", "bearer " + MENTOR, null);
        assertEquals(200, reply.status);
    }

    @Test
    void theWrongRoleIs403() throws Exception {
        assertError(send("POST", "/mentors/profile", STUDENT, MENTOR_BODY), 403, "forbidden");
        assertError(send("POST", "/students/match-profile", MENTOR, STUDENT_BODY), 403, "forbidden");
        assertError(get("/mentors/recommendations", MENTOR), 403, "forbidden");
        assertError(send("POST", "/connections", MENTOR, "{}"), 403, "forbidden");
    }

    /* ---------- profiles ---------- */

    @Test
    void mentorProfileCreateReadUpdate() throws Exception {
        assertError(get("/mentors/profile", MENTOR), 404, "not_found");

        Reply created = send("POST", "/mentors/profile", MENTOR, MENTOR_BODY);
        assertEquals(201, created.status);
        assertEquals("TECHNOLOGY", created.json().get("industry"));
        // Shared Core's half of the profile is merged in.
        assertEquals("Demo Mentor", created.json().get("name"));

        assertError(send("POST", "/mentors/profile", MENTOR, MENTOR_BODY), 409, "conflict");
        assertEquals(4L, get("/mentors/profile", MENTOR).json().get("hoursPerMonth"));

        Reply updated = send("PUT", "/mentors/profile", MENTOR, "{\"hoursPerMonth\":10}");
        assertEquals(200, updated.status);
        assertEquals(10L, updated.json().get("hoursPerMonth"));
        assertEquals("EMAIL", updated.json().get("contactMethod"));

        assertError(send("DELETE", "/mentors/profile", MENTOR, null), 405, "method_not_allowed");
        assertError(get("/mentors/profile/extra", MENTOR), 404, "not_found");
    }

    @Test
    void studentProfileCreateReadUpdate() throws Exception {
        Reply created = send("POST", "/students/match-profile", STUDENT, STUDENT_BODY);
        assertEquals(201, created.status);
        assertEquals(List.of("TECHNOLOGY"), created.json().get("targetIndustries"));

        assertEquals(200, get("/students/match-profile", STUDENT).status);
        Reply updated = send("PUT", "/students/match-profile", STUDENT,
                "{\"targetRoles\":[\"Analyst\"]}");
        assertEquals(List.of("Analyst"), updated.json().get("targetRoles"));
        assertEquals(List.of("TECHNOLOGY"), updated.json().get("targetIndustries"));

        assertError(send("PATCH", "/students/match-profile", STUDENT, "{}"), 405,
                "method_not_allowed");
    }

    @Test
    void badFieldsAre400WithOneMessageEach() throws Exception {
        Reply reply = send("POST", "/mentors/profile", MENTOR,
                "{\"industry\":\"NOPE\",\"hoursPerMonth\":\"many\",\"extra\":1}");
        assertError(reply, 400, "validation_failed");
        assertEquals(Set.of("industry", "hoursPerMonth", "extra"), detail(reply).keySet());

        // Values that bind but leave the profile incomplete.
        reply = send("POST", "/mentors/profile", MENTOR, "{\"industry\":\"FINANCE\"}");
        assertError(reply, 400, "validation_failed");
        assertTrue(detail(reply).keySet().containsAll(
                Set.of("guidanceAreas", "hoursPerMonth", "contactMethod")));
        assertError(get("/mentors/profile", MENTOR), 404, "not_found");

        // An update that is refused leaves the profile as it was.
        send("POST", "/mentors/profile", MENTOR, MENTOR_BODY);
        assertError(send("PUT", "/mentors/profile", MENTOR,
                "{\"hoursPerMonth\":12,\"guidanceAreas\":[]}"), 400, "validation_failed");
        Map<String, Object> after = get("/mentors/profile", MENTOR).json();
        assertEquals(4L, after.get("hoursPerMonth"));
        assertEquals(List.of("RESUME_REVIEW"), after.get("guidanceAreas"));
    }

    @Test
    void unusableBodiesAre400() throws Exception {
        assertError(send("POST", "/mentors/profile", MENTOR, "{\"industry\":"), 400,
                "malformed_json");
        assertError(send("POST", "/mentors/profile", MENTOR, "[1,2]"), 400, "malformed_json");
        assertError(send("POST", "/mentors/profile", MENTOR,
                "[".repeat(20_000) + "]".repeat(20_000)), 400, "malformed_json");

        String huge = "{\"note\":\"" + "x".repeat(70 * 1024) + "\"}";
        assertError(send("POST", "/mentors/profile", MENTOR, huge), 400, "body_too_large");

        // No body at all reports the missing fields, not a parse error.
        Reply empty = send("POST", "/mentors/profile", MENTOR, null);
        assertError(empty, 400, "validation_failed");
    }

    /* ---------- directory ---------- */

    @Test
    void theDirectoryFiltersAndPages() throws Exception {
        send("POST", "/mentors/profile", MENTOR, MENTOR_BODY);
        String financeMentor = UUID.randomUUID() + ":Mentor";
        send("POST", "/mentors/profile", financeMentor, "{\"industry\":\"FINANCE\","
                + "\"guidanceAreas\":[\"NEGOTIATION\"],\"hoursPerMonth\":2,"
                + "\"contactMethod\":\"PHONE\",\"maxMentees\":0}");

        Map<String, Object> all = get("/mentors", STUDENT).json();
        assertEquals(2L, all.get("total"));
        assertEquals(1L, all.get("page"));
        assertEquals(20L, all.get("size"));
        assertEquals(1L, all.get("totalPages"));

        assertEquals(1L, get("/mentors?industry=finance", STUDENT).json().get("total"));
        assertEquals(1L, get("/mentors?guidance=resume%20review", STUDENT).json().get("total"));
        // The finance mentor has no capacity.
        assertEquals(1L, get("/mentors?withCapacityOnly=true", STUDENT).json().get("total"));
        assertEquals(2L, get("/mentors?withCapacityOnly=&industry=", STUDENT).json().get("total"));

        Map<String, Object> second = get("/mentors?size=1&page=2", STUDENT).json();
        assertEquals(1, ((List<?>) second.get("items")).size());
        assertEquals(2L, second.get("totalPages"));
        assertEquals(0, ((List<?>) get("/mentors?page=9", STUDENT).json().get("items")).size());
    }

    @Test
    void badDirectoryParametersAre400ByName() throws Exception {
        Reply reply = get("/mentors?industry=NOPE&guidance=NOPE&withCapacityOnly=yes"
                + "&page=0&size=abc", STUDENT);
        assertError(reply, 400, "validation_failed");
        assertEquals(Set.of("industry", "guidance", "withCapacityOnly", "page", "size"),
                detail(reply).keySet());
        assertEquals(Set.of("size"), detail(get("/mentors?size=101", STUDENT)).keySet());
        assertError(send("POST", "/mentors", STUDENT, "{}"), 405, "method_not_allowed");
        assertError(get("/mentors/nobody", STUDENT), 404, "not_found");
    }

    /* ---------- recommendations ---------- */

    @Test
    void recommendationsAtBothPaths() throws Exception {
        assertError(get("/mentors/recommendations", STUDENT), 404, "not_found");

        send("POST", "/mentors/profile", MENTOR, MENTOR_BODY);
        send("POST", "/students/match-profile", STUDENT, STUDENT_BODY);

        List<?> ranked = getList("/mentors/recommendations", STUDENT);
        assertEquals(1, ranked.size());
        Map<?, ?> top = (Map<?, ?>) ranked.get(0);
        assertEquals(MENTOR_ID, top.get("userId"));
        assertEquals(1L, top.get("rank"));
        assertEquals(ranked, getList("/connections/recommendations/" + STUDENT_ID, STUDENT));

        assertError(get("/connections/recommendations/" + UUID.randomUUID(), STUDENT), 403,
                "forbidden");
        assertError(get("/connections/recommendations/not-an-id", STUDENT), 404, "not_found");
        assertError(send("POST", "/connections/recommendations/" + STUDENT_ID, STUDENT, "{}"),
                405, "method_not_allowed");
        assertError(send("POST", "/mentors/recommendations", STUDENT, "{}"), 405,
                "method_not_allowed");
        assertError(get("/mentors/recommendations/extra", STUDENT), 404, "not_found");
    }

    /* ---------- connections ---------- */

    @Test
    void aConnectionThroughEveryRoute() throws Exception {
        send("POST", "/mentors/profile", MENTOR, MENTOR_BODY);
        send("POST", "/students/match-profile", STUDENT, STUDENT_BODY);

        Reply requested = send("POST", "/connections", STUDENT,
                "{\"mentorId\":\"" + MENTOR_ID + "\",\"message\":\"Hi\"}");
        assertEquals(201, requested.status);
        assertEquals("PENDING", requested.json().get("status"));
        String id = (String) requested.json().get("id");
        String path = "/connections/" + id;

        assertEquals(1, getList("/connections", MENTOR).size());
        assertEquals(200, get(path, STUDENT).status);

        Reply accepted = send("PUT", path, MENTOR, "{\"status\":\"ACTIVE\"}");
        assertEquals("ACTIVE", accepted.json().get("status"));
        assertEquals("student@example.com", accepted.json().get("studentEmail"));

        assertEquals(201, send("POST", path + "/sessions", MENTOR,
                "{\"summary\":\"First call\",\"heldOn\":\"2026-11-20\"}").status);
        assertEquals(1, getList(path + "/sessions", STUDENT).size());

        assertError(get(path + "/schedule", STUDENT), 404, "not_found");
        String nextWeek = java.time.Instant.now().plus(java.time.Duration.ofDays(7))
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        Reply proposed = send("POST", path + "/schedule", STUDENT,
                "{\"startsAt\":\"" + nextWeek + "\",\"note\":\"Zoom\"}");
        assertEquals(201, proposed.status, proposed.body);
        Reply confirmed = send("PUT", path + "/schedule", MENTOR, "{\"proposalId\":\""
                + proposed.json().get("id") + "\",\"status\":\"CONFIRMED\"}");
        assertEquals("CONFIRMED", confirmed.json().get("status"));
        assertEquals("CONFIRMED", get(path + "/schedule", STUDENT).json().get("status"));

        Reply done = send("PUT", path, STUDENT, "{\"status\":\"COMPLETED\"}");
        assertEquals("COMPLETED", done.json().get("status"));
        assertEquals(0L, get("/mentors/profile", MENTOR).json().get("activeMentees"));
    }

    @Test
    void connectionRoutesRefuseBadPathsAndMethods() throws Exception {
        String id = UUID.randomUUID().toString();
        assertError(get("/connections/not-an-id", MENTOR), 404, "not_found");
        assertError(get("/connections/" + id, MENTOR), 404, "not_found");
        assertError(get("/connections/" + id + "/unknown", MENTOR), 404, "not_found");
        assertError(get("/connections/not-an-id/sessions", MENTOR), 404, "not_found");
        assertError(get("/connections/" + id + "/sessions/extra", MENTOR), 404, "not_found");

        assertError(send("DELETE", "/connections", MENTOR, null), 405, "method_not_allowed");
        assertError(send("DELETE", "/connections/" + id, MENTOR, null), 405,
                "method_not_allowed");
        assertError(send("PUT", "/connections/" + id + "/sessions", MENTOR, "{}"), 405,
                "method_not_allowed");
        assertError(send("DELETE", "/connections/" + id + "/schedule", MENTOR, null), 405,
                "method_not_allowed");
    }

    @Test
    void connectionRequestsAreValidated() throws Exception {
        send("POST", "/students/match-profile", STUDENT, STUDENT_BODY);
        Reply reply = send("POST", "/connections", STUDENT, "{\"mentorId\":\"nope\"}");
        assertError(reply, 400, "validation_failed");
        assertTrue(detail(reply).containsKey("mentorId"));
        assertError(send("POST", "/connections", STUDENT,
                "{\"mentorId\":\"" + UUID.randomUUID() + "\"}"), 404, "not_found");
    }

    /* ---------- helpers ---------- */

    private record Reply(int status, String contentType, String body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> json() {
            return (Map<String, Object>) Json.parse(body);
        }
    }

    private Reply get(String path, String token) throws Exception {
        return send("GET", path, token, null);
    }

    private List<?> getList(String path, String token) throws Exception {
        Reply reply = get(path, token);
        assertEquals(200, reply.status, reply.body);
        return (List<?>) Json.parse(reply.body);
    }

    private Reply send(String method, String path, String token, String body) throws Exception {
        return sendWithHeader(method, path, token == null ? null : "Bearer " + token, body);
    }

    /** Sends {@code authorization} as the whole Authorization header, prefix and all. */
    private Reply sendWithHeader(String method, String path, String authorization, String body)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        HttpResponse<String> response = http.send(request.build(),
                HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(),
                response.headers().firstValue("Content-Type").orElse(""), response.body());
    }

    private void assertPage(String path, String contentType, String contains) throws Exception {
        Reply reply = get(path, null);
        assertEquals(200, reply.status, path);
        assertEquals(contentType, reply.contentType, path);
        assertTrue(reply.body.contains(contains), path);
    }

    /** Every error uses the shared {error, code, detail} format from SOW Section 6. */
    private static void assertError(Reply reply, int status, String code) {
        assertEquals(status, reply.status, reply.body);
        assertEquals("application/json; charset=utf-8", reply.contentType);
        Map<String, Object> json = reply.json();
        assertEquals(Set.of("error", "code", "detail"), json.keySet(), reply.body);
        assertEquals(code, json.get("code"));
        assertNotNull(json.get("error"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> detail(Reply reply) {
        return (Map<String, Object>) reply.json().get("detail");
    }
}
