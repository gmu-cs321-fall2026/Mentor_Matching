import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Entry point: starts the server, defines the routes, validates the caller's
 * token through Shared Core, validates input and returns the shared error
 * format.
 *
 * <p>Built on the JDK's own {@code com.sun.net.httpserver}, the lighter option
 * ADR-004 allows. Spring Boot would need a dependency manager, and the Sprint 1
 * Dockerfile compiles with plain {@code javac}.
 *
 * <p>Configuration comes only from the environment, so nothing is hard-coded
 * for the Week 13 compose environment: {@code PORT}, {@code SHARED_CORE_URL},
 * {@code DB_URL} and {@code STATIC_DIR}.
 *
 * <p>As the service grows, split the handlers below into one controller per
 * resource and keep this class as the bootstrap only.
 */
public class MentoringApp {

    /** Largest request body we will read, so a bad client cannot exhaust memory. */
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    public static void main(String[] args) throws IOException {
        int port = intEnv("PORT", 8080);
        String sharedCoreUrl = env("SHARED_CORE_URL", "");
        String dbUrl = env("DB_URL", "");
        Path staticDir = Path.of(env("STATIC_DIR", "static")).toAbsolutePath().normalize();

        SharedCoreClient sharedCore = new SharedCoreClient(sharedCoreUrl);
        // The only place that names an implementation; everything else uses the
        // ProfileData interface, per ADR-001.
        ProfileData data = new InMemoryProfileData(sharedCore);
        EditProfile editProfile = new EditProfile();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        // Longest matching prefix wins, so /mentors/profile is reached before /mentors.
        server.createContext("/mentors/profile", route(exchange ->
                mentorProfile(exchange, sharedCore, data, editProfile)));
        server.createContext("/students/match-profile", route(exchange ->
                studentProfile(exchange, sharedCore, data, editProfile)));
        server.createContext("/mentors", route(exchange ->
                mentorDirectory(exchange, sharedCore, data)));
        server.createContext("/enums", route(MentoringApp::enums));
        server.createContext("/health", route(MentoringApp::health));
        server.createContext("/", route(exchange -> staticFile(exchange, staticDir)));
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("Mentor Matching listening on port " + port);
        System.out.println("  static files: " + staticDir);
        System.out.println("  shared core:  " + (sharedCore.isStub()
                ? "STUB MODE - no SHARED_CORE_URL set; remove before Sprint 2"
                : sharedCoreUrl));
        System.out.println("  storage:      in memory for Sprint 1; DB_URL="
                + (dbUrl.isEmpty() ? "(unset)" : dbUrl));
        if (sharedCore.isStub()) {
            System.out.println("  stub tokens:  mentor-demo, student-demo, <uuid>:<Role>");
        }
    }

    /** {@code POST|GET|PUT /mentors/profile} — the mentor's own profile. */
    private static void mentorProfile(HttpExchange exchange, SharedCoreClient sharedCore,
            ProfileData data, EditProfile editProfile) throws IOException {
        requireExactPath(exchange, "/mentors/profile");
        SharedCoreClient.Principal caller = authenticate(exchange, sharedCore);
        requireRole(caller, "Mentor");

        switch (exchange.getRequestMethod()) {
            case "POST" -> {
                MentorProfile profile = new MentorProfile(caller.userId());
                ProfileBinding.apply(profile, readBody(exchange));
                respond(exchange, 201, data.saveMentor(profile));
            }
            case "GET" -> respond(exchange, 200, data.mentorView(data.findMentor(caller.userId())));
            case "PUT" -> respond(exchange, 200, editProfile.editProfile(
                    data, caller.userId(), caller.role(), readBody(exchange)));
            default -> throw ApiException.methodNotAllowed("use POST, GET or PUT");
        }
    }

    /** {@code POST|GET|PUT /students/match-profile} — the student's own profile. */
    private static void studentProfile(HttpExchange exchange, SharedCoreClient sharedCore,
            ProfileData data, EditProfile editProfile) throws IOException {
        requireExactPath(exchange, "/students/match-profile");
        SharedCoreClient.Principal caller = authenticate(exchange, sharedCore);
        requireRole(caller, "Student");

        switch (exchange.getRequestMethod()) {
            case "POST" -> {
                StudentMatchProfile profile = new StudentMatchProfile(caller.userId());
                ProfileBinding.apply(profile, readBody(exchange));
                respond(exchange, 201, data.saveStudent(profile));
            }
            case "GET" -> respond(exchange, 200, data.studentView(data.findStudent(caller.userId())));
            case "PUT" -> respond(exchange, 200, editProfile.editProfile(
                    data, caller.userId(), caller.role(), readBody(exchange)));
            default -> throw ApiException.methodNotAllowed("use POST, GET or PUT");
        }
    }

    /**
     * {@code GET /mentors} — the paginated, filterable directory.
     *
     * <p>Open to any authenticated role: students browse it, and staff will
     * report on it. Query parameters: {@code industry}, {@code guidance},
     * {@code withCapacityOnly}, {@code page}, {@code size}.
     */
    private static void mentorDirectory(HttpExchange exchange, SharedCoreClient sharedCore,
            ProfileData data) throws IOException {
        requireExactPath(exchange, "/mentors");
        authenticate(exchange, sharedCore);
        if (!exchange.getRequestMethod().equals("GET")) {
            throw ApiException.methodNotAllowed("use GET");
        }

        Map<String, String> query = query(exchange);
        Map<String, String> errors = new LinkedHashMap<>();

        Industry industry = enumParam(Industry.class, query.get("industry"), "industry", errors);
        GuidanceArea guidance = enumParam(GuidanceArea.class, query.get("guidance"),
                "guidance", errors);
        boolean withCapacityOnly = booleanParam(query.get("withCapacityOnly"),
                "withCapacityOnly", false, errors);
        int page = intParam(query.get("page"), "page", 1, 1, Integer.MAX_VALUE, errors);
        int size = intParam(query.get("size"), "size", DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE, errors);

        if (!errors.isEmpty()) {
            throw ApiException.fields(errors);
        }
        respond(exchange, 200, data.listMentors(industry, guidance, withCapacityOnly, page, size));
    }

    /**
     * {@code GET /enums} — the agreed vocabularies, so the forms never hold a
     * second copy of a list ADR-002 says must match on both sides.
     */
    private static void enums(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw ApiException.methodNotAllowed("use GET");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("industries", names(Industry.values()));
        body.put("guidanceAreas", names(GuidanceArea.values()));
        body.put("contactMethods", names(ContactMethod.values()));
        respond(exchange, 200, body);
    }

    /** {@code GET /health} — a liveness check for compose and the gateway. */
    private static void health(HttpExchange exchange) throws IOException {
        respond(exchange, 200, Map.of("status", "ok"));
    }

    /** Serves the forms and their script from {@code STATIC_DIR} on the same port. */
    private static void staticFile(HttpExchange exchange, Path staticDir) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            throw ApiException.methodNotAllowed("use GET");
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        Path file = staticDir.resolve(path.substring(1)).normalize();
        if (!file.startsWith(staticDir) || !Files.isRegularFile(file)) {
            throw ApiException.notFound("no such file " + path);
        }
        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType(file));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * Wraps a handler so no endpoint can return an unhandled exception.
     *
     * <p>Anything we anticipated arrives as an {@link ApiException} and keeps
     * its status; anything else is logged and reported as a 500 with no
     * internal detail, per SOW Section 6.
     */
    private static HttpHandler route(Route handler) {
        return exchange -> {
            try {
                handler.handle(exchange);
            } catch (ApiException e) {
                respondQuietly(exchange, e.status(), e.body());
            } catch (RuntimeException | IOException e) {
                System.err.println(exchange.getRequestMethod() + " "
                        + exchange.getRequestURI() + " failed");
                e.printStackTrace();
                respondQuietly(exchange, 500, ApiException.internal().body());
            } finally {
                exchange.close();
            }
        };
    }

    /** A handler that may fail; {@link #route} decides what the failure looks like. */
    private interface Route {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static SharedCoreClient.Principal authenticate(HttpExchange exchange,
            SharedCoreClient sharedCore) {
        return sharedCore.validateToken(exchange.getRequestHeaders().getFirst("Authorization"));
    }

    private static void requireRole(SharedCoreClient.Principal caller, String expected) {
        if (!expected.equalsIgnoreCase(caller.role())) {
            throw ApiException.forbidden("this endpoint is for the " + expected
                    + " role; your token says " + caller.role());
        }
    }

    /** A context also catches deeper paths, so reject anything but the exact route. */
    private static void requireExactPath(HttpExchange exchange, String expected) {
        if (!exchange.getRequestURI().getPath().equals(expected)) {
            throw ApiException.notFound("no route for " + exchange.getRequestURI().getPath());
        }
    }

    private static Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        byte[] bytes;
        try (InputStream in = exchange.getRequestBody()) {
            bytes = in.readNBytes(MAX_BODY_BYTES + 1);
        }
        if (bytes.length > MAX_BODY_BYTES) {
            throw ApiException.badRequest("body_too_large",
                    "the body may be at most " + MAX_BODY_BYTES + " bytes");
        }
        try {
            return Json.parseObject(new String(bytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("malformed_json", e.getMessage());
        }
    }

    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Used on the error path, where a second failure must not mask the first. */
    private static void respondQuietly(HttpExchange exchange, int status, Object body) {
        try {
            respond(exchange, status, body);
        } catch (IOException e) {
            System.err.println("could not send the error response: " + e.getMessage());
        }
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> params = new LinkedHashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return params;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int split = pair.indexOf('=');
            String name = split < 0 ? pair : pair.substring(0, split);
            String value = split < 0 ? "" : pair.substring(split + 1);
            params.put(decode(name), decode(value));
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static <E extends Enum<E>> E enumParam(Class<E> type, String value, String name,
            Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String wanted = value.trim().toUpperCase().replaceAll("[\\s-]+", "_");
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(wanted)) {
                return constant;
            }
        }
        errors.put(name, "expected one of " + String.join(", ", names(type.getEnumConstants())));
        return null;
    }

    private static boolean booleanParam(String value, String name, boolean fallback,
            Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String text = value.trim().toLowerCase();
        if (text.equals("true")) {
            return true;
        }
        if (text.equals("false")) {
            return false;
        }
        errors.put(name, "expected true or false");
        return fallback;
    }

    private static int intParam(String value, String name, int fallback, int min, int max,
            Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min || parsed > max) {
                errors.put(name, "expected a number from " + min + " to " + max);
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException e) {
            errors.put(name, "expected a whole number");
            return fallback;
        }
    }

    private static List<String> names(Enum<?>[] constants) {
        List<String> names = new ArrayList<>();
        for (Enum<?> constant : constants) {
            names.add(constant.name());
        }
        return names;
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int intEnv(String name, int fallback) {
        String value = env(name, "");
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println(name + "=" + value + " is not a number; using " + fallback);
            return fallback;
        }
    }
}
