import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Our only door to Shared Core: token validation and the base user profile.
 *
 * <p>Two modes, chosen by whether {@code SHARED_CORE_URL} is set:
 *
 * <ul>
 *   <li><b>Live</b> — calls {@code GET /auth/validate} and
 *       {@code GET|PUT /users/{id}} over HTTP.</li>
 *   <li><b>Stub</b> — answers from memory. This is the config-flag fallback the
 *       architecture's risk table asks for so Sprint 1 is not blocked on Shared
 *       Core being late. <b>It must be removed before Sprint 2.</b></li>
 * </ul>
 *
 * <p>Stub tokens: {@code mentor-demo} and {@code student-demo} for the sample
 * users the HTML forms load with, or {@code <uuid>:<Role>} to act as anyone.
 */
final class SharedCoreClient {

    /** Fixed id behind the {@code mentor-demo} stub token, so demos are repeatable. */
    static final UUID DEMO_MENTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Fixed id behind the {@code student-demo} stub token. */
    static final UUID DEMO_STUDENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** The authenticated caller, as Shared Core describes them. */
    record Principal(UUID userId, String role) {
    }

    private final String baseUrl;
    private final boolean stub;
    private final HttpClient http;
    private final Map<UUID, Map<String, Object>> stubUsers = new ConcurrentHashMap<>();

    /** @param baseUrl Shared Core's root URL; null or blank turns on stub mode */
    SharedCoreClient(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.stub = this.baseUrl.isEmpty();
        this.http = stub ? null : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        if (stub) {
            stubUsers.put(DEMO_MENTOR_ID, demoUser("Demo Mentor", "mentor@example.com",
                    "MS Computer Science", 2018, "Capital One", "Senior Engineer"));
            stubUsers.put(DEMO_STUDENT_ID, demoUser("Demo Student", "student@example.com",
                    "BS Computer Science", 2027, null, null));
        }
    }

    /** True while running against the in-memory stub rather than real Shared Core. */
    boolean isStub() {
        return stub;
    }

    /**
     * Validates the caller's token.
     *
     * @param authorizationHeader the raw {@code Authorization} header, with or
     *     without a {@code Bearer} prefix
     * @return who the caller is and what role they hold
     * @throws ApiException 401 if the token is missing or rejected, 502 if
     *     Shared Core cannot be reached
     */
    Principal validateToken(String authorizationHeader) {
        String token = stripBearer(authorizationHeader);
        if (token.isEmpty()) {
            throw ApiException.unauthorized("supply an Authorization header");
        }
        return stub ? validateStubToken(token) : validateLiveToken(token);
    }

    /**
     * Reads the Shared Core-owned half of a profile: name, email, program,
     * graduation year and, for mentors, employer and title.
     *
     * @return the base attributes, or an empty map when Shared Core has no such
     *     user; never null, so a merged view still shows our own attributes
     */
    Map<String, Object> getUser(UUID userId) {
        if (stub) {
            Map<String, Object> user = stubUsers.get(userId);
            return user == null ? new LinkedHashMap<>() : new LinkedHashMap<>(user);
        }
        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/users/" + userId))
                .GET());
        if (response.statusCode() == 404) {
            return new LinkedHashMap<>();
        }
        if (response.statusCode() >= 300) {
            throw ApiException.sharedCoreUnavailable(
                    "GET /users/" + userId + " returned " + response.statusCode());
        }
        return readObject(response.body());
    }

    /**
     * Writes back the attributes Shared Core owns.
     *
     * <p>Called by {@link ProfileData} on save, the {@code PUT /users/{id}} step
     * in the architecture's sequence diagram. Sprint 1 sends nothing, because
     * every attribute our classes hold is either ours or still TBD; the call is
     * skipped rather than sent empty.
     */
    void updateUser(UUID userId, Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        if (stub) {
            stubUsers.computeIfAbsent(userId, id -> new LinkedHashMap<>()).putAll(attributes);
            return;
        }
        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/users/" + userId))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(Json.write(attributes))));
        if (response.statusCode() >= 300) {
            throw ApiException.sharedCoreUnavailable(
                    "PUT /users/" + userId + " returned " + response.statusCode());
        }
    }

    private Principal validateLiveToken(String token) {
        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/validate"))
                .header("Authorization", "Bearer " + token)
                .GET());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw ApiException.unauthorized("Shared Core rejected the token");
        }
        if (response.statusCode() >= 300) {
            throw ApiException.sharedCoreUnavailable(
                    "GET /auth/validate returned " + response.statusCode());
        }
        Map<String, Object> body = readObject(response.body());
        Object userId = body.get("userId");
        Object role = body.get("role");
        if (userId == null || role == null) {
            throw ApiException.sharedCoreUnavailable("/auth/validate omitted userId or role");
        }
        try {
            return new Principal(UUID.fromString(String.valueOf(userId)), String.valueOf(role));
        } catch (IllegalArgumentException e) {
            throw ApiException.sharedCoreUnavailable("/auth/validate returned a malformed userId");
        }
    }

    private Principal validateStubToken(String token) {
        if (token.equals("mentor-demo")) {
            return new Principal(DEMO_MENTOR_ID, "Mentor");
        }
        if (token.equals("student-demo")) {
            return new Principal(DEMO_STUDENT_ID, "Student");
        }
        int split = token.lastIndexOf(':');
        if (split > 0) {
            try {
                UUID userId = UUID.fromString(token.substring(0, split));
                String role = token.substring(split + 1);
                if (!role.isBlank()) {
                    stubUsers.computeIfAbsent(userId, id -> demoUser(
                            "Stub " + role, role.toLowerCase() + "@example.com", "Unknown", 2026,
                            null, null));
                    return new Principal(userId, role);
                }
            } catch (IllegalArgumentException e) {
                // fall through to the 401 below
            }
        }
        throw ApiException.unauthorized(
                "stub mode expects mentor-demo, student-demo or <uuid>:<Role>");
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return http.send(request.timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // A connection failure often carries no message, so name the failure itself.
            String detail = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            throw ApiException.sharedCoreUnavailable(detail);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.sharedCoreUnavailable("the call to Shared Core was interrupted");
        }
    }

    private static Map<String, Object> readObject(String body) {
        try {
            return Json.parseObject(body);
        } catch (IllegalArgumentException e) {
            throw ApiException.sharedCoreUnavailable("Shared Core returned unreadable JSON");
        }
    }

    private static String stripBearer(String header) {
        if (header == null) {
            return "";
        }
        String value = header.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            value = value.substring(7).trim();
        }
        return value;
    }

    private static Map<String, Object> demoUser(String name, String email, String program,
            int gradYear, String employer, String title) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("name", name);
        user.put("email", email);
        user.put("program", program);
        user.put("gradYear", gradYear);
        if (employer != null) {
            user.put("employer", employer);
        }
        if (title != null) {
            user.put("title", title);
        }
        return user;
    }
}
