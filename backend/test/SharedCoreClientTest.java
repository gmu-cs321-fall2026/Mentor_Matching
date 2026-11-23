import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link SharedCoreClient} in both modes. Live mode runs against a fake Shared
 * Core on a local port, so the calls we will make in the shared docker-compose
 * environment, and every way they can fail, are tested before we get there.
 */
class SharedCoreClientTest {

    private static final UUID USER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private HttpServer fake;
    private String fakeUrl;
    /** What the fake answers next, keyed by path. */
    private final Map<String, Answer> answers = new java.util.concurrent.ConcurrentHashMap<>();
    /** Every request the fake received: method, path, Authorization header, body. */
    private final List<String[]> received = new CopyOnWriteArrayList<>();

    private record Answer(int status, String body) {
    }

    @BeforeEach
    void startFakeSharedCore() throws IOException {
        fake = HttpServer.create(new InetSocketAddress(0), 0);
        fake.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new String[] {exchange.getRequestMethod(), path,
                    exchange.getRequestHeaders().getFirst("Authorization"), body});
            Answer answer = answers.getOrDefault(path, new Answer(404, "{}"));
            byte[] bytes = answer.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(answer.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        fake.start();
        // A trailing slash is trimmed, so this also checks that.
        fakeUrl = "http://localhost:" + fake.getAddress().getPort() + "/";
    }

    @AfterEach
    void stopFakeSharedCore() {
        fake.stop(0);
    }

    private SharedCoreClient live() {
        return new SharedCoreClient(fakeUrl);
    }

    /* ---------- live: /auth/validate ---------- */

    @Test
    void aValidTokenBecomesThePrincipal() {
        answers.put("/auth/validate", new Answer(200,
                "{\"userId\":\"" + USER + "\",\"role\":\"Student\",\"email\":\"x@gmu.edu\"}"));
        SharedCoreClient client = live();
        assertFalse(client.isStub());

        SharedCoreClient.Principal caller = client.validateToken("Bearer abc.def");
        assertEquals(USER, caller.userId());
        assertEquals("Student", caller.role());

        String[] request = received.get(0);
        assertEquals("GET", request[0]);
        assertEquals("/auth/validate", request[1]);
        assertEquals("Bearer abc.def", request[2]);
    }

    @Test
    void theTokenIsForwardedWithOrWithoutABearerPrefix() {
        answers.put("/auth/validate", new Answer(200,
                "{\"userId\":\"" + USER + "\",\"role\":\"Mentor\"}"));
        live().validateToken("  raw-token  ");
        assertEquals("Bearer raw-token", received.get(0)[2]);
    }

    @Test
    void aRejectedTokenIs401() {
        for (int status : new int[] {401, 403}) {
            answers.put("/auth/validate", new Answer(status, "{\"error\":\"no\"}"));
            assertEquals(401, failure(() -> live().validateToken("Bearer t")));
        }
    }

    @Test
    void noTokenIs401WithoutCallingSharedCore() {
        assertEquals(401, failure(() -> live().validateToken(null)));
        assertEquals(401, failure(() -> live().validateToken("Bearer   ")));
        assertTrue(received.isEmpty());
    }

    @Test
    void aBrokenAnswerFromSharedCoreIs502() {
        answers.put("/auth/validate", new Answer(500, "oops"));
        assertEquals(502, failure(() -> live().validateToken("t")));

        answers.put("/auth/validate", new Answer(200, "not json"));
        assertEquals(502, failure(() -> live().validateToken("t")));

        answers.put("/auth/validate", new Answer(200, "{\"userId\":\"" + USER + "\"}"));
        assertEquals(502, failure(() -> live().validateToken("t")));

        answers.put("/auth/validate", new Answer(200, "{\"userId\":\"nope\",\"role\":\"Student\"}"));
        assertEquals(502, failure(() -> live().validateToken("t")));
    }

    @Test
    void anUnreachableSharedCoreIs502() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        SharedCoreClient client = new SharedCoreClient("http://localhost:" + closedPort);
        ApiException e = assertThrows(ApiException.class, () -> client.validateToken("t"));
        assertEquals(502, e.status());
        assertEquals("shared_core_unavailable", e.body().get("code"));
    }

    /* ---------- live: /users/{id} ---------- */

    @Test
    void getUserReturnsTheBaseProfile() {
        answers.put("/users/" + USER, new Answer(200,
                "{\"name\":\"Ada\",\"email\":\"ada@gmu.edu\",\"gradYear\":2027}"));
        Map<String, Object> user = live().getUser(USER);
        assertEquals("Ada", user.get("name"));
        assertEquals("ada@gmu.edu", user.get("email"));
        assertEquals(2027L, user.get("gradYear"));
    }

    @Test
    void anUnknownUserIsAnEmptyProfile() {
        assertEquals(Map.of(), live().getUser(USER));
    }

    @Test
    void getUserFailuresAre502() {
        answers.put("/users/" + USER, new Answer(503, ""));
        assertEquals(502, failure(() -> live().getUser(USER)));
        answers.put("/users/" + USER, new Answer(200, "[1]"));
        assertEquals(502, failure(() -> live().getUser(USER)));
    }

    @Test
    void updateUserSendsJsonAndSkipsEmptyUpdates() {
        SharedCoreClient client = live();
        client.updateUser(USER, Map.of());
        client.updateUser(USER, null);
        assertTrue(received.isEmpty());

        answers.put("/users/" + USER, new Answer(200, "{}"));
        client.updateUser(USER, Map.of("program", "BS CS"));
        String[] request = received.get(0);
        assertEquals("PUT", request[0]);
        assertEquals("{\"program\":\"BS CS\"}", request[3]);

        answers.put("/users/" + USER, new Answer(500, ""));
        assertEquals(502, failure(() -> client.updateUser(USER, Map.of("program", "x"))));
    }

    /* ---------- stub mode ---------- */

    @Test
    void stubModeKnowsTheDemoUsers() {
        SharedCoreClient stub = new SharedCoreClient("   ");
        assertTrue(stub.isStub());
        assertEquals(new SharedCoreClient.Principal(SharedCoreClient.DEMO_MENTOR_ID, "Mentor"),
                stub.validateToken("Bearer mentor-demo"));
        assertEquals(new SharedCoreClient.Principal(SharedCoreClient.DEMO_STUDENT_ID, "Student"),
                stub.validateToken("student-demo"));
        assertEquals("Demo Mentor", stub.getUser(SharedCoreClient.DEMO_MENTOR_ID).get("name"));
        assertEquals("student@example.com",
                stub.getUser(SharedCoreClient.DEMO_STUDENT_ID).get("email"));
    }

    @Test
    void stubModeActsAsAnyoneFromAUuidAndRoleToken() {
        SharedCoreClient stub = new SharedCoreClient(null);
        SharedCoreClient.Principal caller = stub.validateToken(USER + ":Mentor");
        assertEquals(USER, caller.userId());
        assertEquals("Mentor", caller.role());
        assertEquals("mentor@example.com", stub.getUser(USER).get("email"));

        assertEquals(401, failure(() -> stub.validateToken("nope")));
        assertEquals(401, failure(() -> stub.validateToken("not-a-uuid:Mentor")));
        assertEquals(401, failure(() -> stub.validateToken(USER + ": ")));
    }

    @Test
    void stubModeStoresUpdatesAndReturnsCopies() {
        SharedCoreClient stub = new SharedCoreClient(null);
        UUID someone = UUID.randomUUID();
        assertEquals(Map.of(), stub.getUser(someone));

        stub.updateUser(someone, Map.of("program", "BS CS"));
        assertEquals("BS CS", stub.getUser(someone).get("program"));

        // Changing what getUser returned does not change the stored user.
        stub.getUser(someone).put("program", "changed");
        assertEquals("BS CS", stub.getUser(someone).get("program"));
        assertNull(stub.getUser(someone).get("name"));
    }

    private static int failure(Runnable call) {
        return assertThrows(ApiException.class, call::run).status();
    }
}
