import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code GET|POST /connections/{id}/sessions} through {@link ConnectionService}:
 * the default date, the status code and field each bad request gets, and who
 * may read the log.
 */
class ConnectionServiceSessionLogTest {

    /** 8:30 pm Eastern on Nov 20, which is already Nov 21 in UTC. */
    private static final Instant EVENING_IN_VIRGINIA = Instant.parse("2026-11-21T01:30:00Z");

    private InMemoryProfileData data;
    private ConnectionService service;
    private UUID student;
    private UUID mentor;
    private UUID connectionId;

    @BeforeEach
    void setUp() {
        data = new InMemoryProfileData(new SharedCoreClient(null));
        service = new ConnectionService(data, Clock.fixed(EVENING_IN_VIRGINIA, ZoneOffset.UTC));
        student = saveStudent();
        mentor = saveMentor();
        connectionId = UUID.fromString((String) service.request(student,
                Map.of("mentorId", mentor.toString())).get("id"));
        service.update(mentor, connectionId, Map.of("status", "ACTIVE"));
    }

    @Test
    void withoutADateTheSessionIsDatedTodayOnCampusNotInUtc() {
        Map<String, Object> entry = log(student, null, "Resume review");
        assertEquals(LocalDate.parse("2026-11-20"), entry.get("heldOn"));
        assertEquals("Resume review", entry.get("summary"));
        assertEquals(student, entry.get("loggedBy"));
    }

    @Test
    void aGivenDateIsKept() {
        Map<String, Object> entry = log(mentor, " 2026-11-17 ", "Mock interview");
        assertEquals(LocalDate.parse("2026-11-17"), entry.get("heldOn"));
        assertEquals(mentor, entry.get("loggedBy"));
    }

    @Test
    void bothSidesSeeTheLogOnTheConnectionAndAtItsOwnRoute() {
        log(student, "2026-11-10", "First call");
        log(mentor, "2026-11-17", "Second call");

        List<Map<String, Object>> sessions = service.listSessions(mentor, connectionId);
        assertEquals(2, sessions.size());
        assertEquals("First call", sessions.get(0).get("summary"));
        assertEquals(sessions, service.listSessions(student, connectionId));
        assertEquals(sessions, service.view(student, connectionId).get("sessions"));
    }

    @Test
    void badBodiesAre400WithTheRightField() {
        assertEquals("summary", field(() -> log(student, "2026-11-17", null)));
        assertEquals("summary", field(() -> log(student, "2026-11-17", "   ")));
        assertEquals("summary", field(() -> service.logSession(student, connectionId,
                Map.of("summary", 42))));
        assertEquals("summary", field(() -> log(student, "2026-11-17",
                "x".repeat(Connection.MAX_TEXT_LENGTH + 1))));
        assertEquals("heldOn", field(() -> log(student, "last tuesday", "Call")));
        assertEquals("heldOn", field(() -> log(student, "2099-01-01", "Call")));

        Map<String, Object> body = new HashMap<>();
        body.put("summary", "Call");
        body.put("room", "B101");
        assertEquals("room", field(() -> service.logSession(student, connectionId, body)));

        assertTrue(service.listSessions(student, connectionId).isEmpty());
    }

    @Test
    void strangersSee404AndAClosedConnectionIs409() {
        UUID stranger = saveStudent();
        assertEquals(404, status(() -> log(stranger, null, "Call")));
        assertEquals(404, status(() -> service.listSessions(stranger, connectionId)));

        log(student, null, "Last call");
        service.update(student, connectionId, Map.of("status", "COMPLETED"));
        assertEquals(409, status(() -> log(student, null, "After the end")));
        // The log stays readable as a record.
        assertEquals(1, service.listSessions(mentor, connectionId).size());
    }

    private Map<String, Object> log(UUID by, String heldOn, String summary) {
        Map<String, Object> body = new HashMap<>();
        if (heldOn != null) {
            body.put("heldOn", heldOn);
        }
        if (summary != null) {
            body.put("summary", summary);
        }
        return service.logSession(by, connectionId, body);
    }

    private static int status(Runnable call) {
        return assertThrows(ApiException.class, call::run).status();
    }

    /** The one field a 400 names. */
    @SuppressWarnings("unchecked")
    private static String field(Runnable call) {
        ApiException e = assertThrows(ApiException.class, call::run);
        assertEquals(400, e.status());
        Map<String, Object> detail = (Map<String, Object>) e.body().get("detail");
        assertEquals(1, detail.size(), "exactly one field: " + detail);
        return detail.keySet().iterator().next();
    }

    private UUID saveStudent() {
        StudentMatchProfile profile = new StudentMatchProfile(UUID.randomUUID());
        profile.setGuidanceWanted(Set.of(GuidanceArea.RESUME_REVIEW));
        data.saveStudent(profile);
        return profile.getUserId();
    }

    private UUID saveMentor() {
        MentorProfile profile = new MentorProfile(UUID.randomUUID());
        profile.setGuidanceAreas(Set.of(GuidanceArea.RESUME_REVIEW));
        profile.setHoursPerMonth(4);
        profile.setContactMethod(ContactMethod.EMAIL);
        profile.setMaxMentees(2);
        data.saveMentor(profile);
        return profile.getUserId();
    }
}
