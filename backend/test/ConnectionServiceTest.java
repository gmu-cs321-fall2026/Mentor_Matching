import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the connection lifecycle in {@link ConnectionService}, against the
 * in-memory store with Shared Core in stub mode.
 */
class ConnectionServiceTest {

    private InMemoryProfileData data;
    private ConnectionService service;
    private UUID student;
    private UUID mentor;

    @BeforeEach
    void setUp() {
        data = new InMemoryProfileData(new SharedCoreClient(null));
        service = new ConnectionService(data);
        student = saveStudent();
        mentor = saveMentor(2);
    }

    @Test
    void acceptingAndCompletingMovesStatusAndMentorCapacity() {
        String id = request(student, mentor);
        assertEquals(ConnectionStatus.PENDING, statusOf(id));
        assertEquals(0, data.findMentor(mentor).getActiveMentees());

        // Status names are accepted in any case.
        update(mentor, id, Map.of("status", "active"));
        assertEquals(ConnectionStatus.ACTIVE, statusOf(id));
        assertEquals(1, data.findMentor(mentor).getActiveMentees());

        update(student, id, Map.of("status", "COMPLETED", "notes", "Met three times."));
        assertEquals(ConnectionStatus.COMPLETED, statusOf(id));
        assertEquals("Met three times.", data.findConnection(UUID.fromString(id)).getNotes());
        assertEquals(0, data.findMentor(mentor).getActiveMentees());
    }

    @Test
    void onlyTheRightSideCanMakeEachMove() {
        String id = request(student, mentor);

        // A student cannot accept their own request; a mentor cannot withdraw it.
        assertEquals(403, status(() -> update(student, id, Map.of("status", "ACTIVE"))));
        assertEquals(403, status(() -> update(mentor, id, Map.of("status", "WITHDRAWN"))));
        // Nobody can jump straight from PENDING to COMPLETED.
        assertEquals(409, status(() -> update(mentor, id, Map.of("status", "COMPLETED"))));

        update(mentor, id, Map.of("status", "DECLINED"));
        // DECLINED is final.
        assertEquals(409, status(() -> update(mentor, id, Map.of("status", "ACTIVE"))));
        assertEquals(ConnectionStatus.DECLINED, statusOf(id));

        // Someone not on the connection cannot see that it exists.
        UUID stranger = saveStudent();
        assertEquals(404, status(() -> service.view(stranger, UUID.fromString(id))));
    }

    @Test
    void aFullMentorCannotAcceptOrReceiveMoreRequests() {
        UUID busyMentor = saveMentor(1);
        UUID otherStudent = saveStudent();
        String first = request(student, busyMentor);
        String second = request(otherStudent, busyMentor);

        update(busyMentor, first, Map.of("status", "ACTIVE"));

        assertEquals(409, status(() -> update(busyMentor, second, Map.of("status", "ACTIVE"))));
        assertEquals(ConnectionStatus.PENDING, statusOf(second));
        assertEquals(1, data.findMentor(busyMentor).getActiveMentees());
        assertEquals(409, status(() -> request(saveStudent(), busyMentor)));

        // Withdrawing an active connection frees the place again.
        update(student, first, Map.of("status", "WITHDRAWN"));
        assertEquals(0, data.findMentor(busyMentor).getActiveMentees());
        update(busyMentor, second, Map.of("status", "ACTIVE"));
        assertEquals(ConnectionStatus.ACTIVE, statusOf(second));
    }

    @Test
    void onlyOneOpenConnectionPerPair() {
        String id = request(student, mentor);
        assertEquals(409, status(() -> request(student, mentor)));

        update(student, id, Map.of("status", "WITHDRAWN"));
        // Once the first one is closed the student may ask again.
        request(student, mentor);
        assertEquals(2, service.listFor(student).size());
        assertEquals(2, service.listFor(mentor).size());
    }

    @Test
    void rejectedUpdatesChangeNothing() {
        String id = request(student, mentor);

        // Notes need an active connection.
        assertEquals(409, status(() -> update(student, id, Map.of("notes", "hello"))));
        // A bad status or unknown field is a 400, and the good part is not applied.
        Map<String, Object> mixed = new HashMap<>();
        mixed.put("status", "ARCHIVED");
        mixed.put("color", "blue");
        assertEquals(400, status(() -> update(mentor, id, mixed)));
        // Accepting with notes that are too long fails before capacity is taken.
        String tooLong = "x".repeat(Connection.MAX_TEXT_LENGTH + 1);
        assertEquals(400, status(() -> update(mentor, id, Map.of("status", "ACTIVE", "notes", tooLong))));

        assertEquals(ConnectionStatus.PENDING, statusOf(id));
        assertEquals("", data.findConnection(UUID.fromString(id)).getNotes());
        assertEquals(0, data.findMentor(mentor).getActiveMentees());

        // A request needs a real mentor id.
        assertEquals(400, status(() -> service.request(student, Map.of("mentorId", "not-an-id"))));
        assertEquals(404, status(() -> request(student, UUID.randomUUID())));
    }

    private String request(UUID from, UUID to) {
        return (String) service.request(from, Map.of("mentorId", to.toString())).get("id");
    }

    private void update(UUID caller, String id, Map<String, Object> body) {
        service.update(caller, UUID.fromString(id), body);
    }

    private ConnectionStatus statusOf(String id) {
        return data.findConnection(UUID.fromString(id)).getStatus();
    }

    /** The HTTP status the call fails with. */
    private static int status(Runnable call) {
        ApiException e = assertThrows(ApiException.class, call::run);
        return e.status();
    }

    private UUID saveStudent() {
        StudentMatchProfile profile = new StudentMatchProfile(UUID.randomUUID());
        profile.setGuidanceWanted(Set.of(GuidanceArea.RESUME_REVIEW));
        data.saveStudent(profile);
        return profile.getUserId();
    }

    private UUID saveMentor(int maxMentees) {
        MentorProfile profile = new MentorProfile(UUID.randomUUID());
        profile.setGuidanceAreas(Set.of(GuidanceArea.RESUME_REVIEW));
        profile.setHoursPerMonth(4);
        profile.setContactMethod(ContactMethod.EMAIL);
        profile.setMaxMentees(maxMentees);
        data.saveMentor(profile);
        assertTrue(data.findMentor(profile.getUserId()).hasCapacity());
        return profile.getUserId();
    }
}
