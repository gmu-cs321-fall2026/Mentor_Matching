import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code POST|PUT|GET /connections/{id}/schedule} through {@link ConnectionService}:
 * the status code and field each bad request gets, and that a refused request
 * changes nothing. The clock is fixed so "in the future" never drifts.
 */
class ConnectionServiceScheduleTest {

    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");

    private InMemoryProfileData data;
    private ConnectionService service;
    private UUID student;
    private UUID mentor;
    private UUID connectionId;

    @BeforeEach
    void setUp() {
        data = new InMemoryProfileData(new SharedCoreClient(null));
        service = new ConnectionService(data, Clock.fixed(NOW, ZoneOffset.UTC));
        student = saveStudent();
        mentor = saveMentor();
        connectionId = UUID.fromString((String) service.request(student,
                Map.of("mentorId", mentor.toString())).get("id"));
        service.update(mentor, connectionId, Map.of("status", "ACTIVE"));
    }

    @Test
    void theWholeFlowProposeConfirmThenCancel() {
        Map<String, Object> proposed = propose(student, "2026-11-24T18:00:00-05:00", "Zoom");
        assertEquals(SessionProposal.Status.PROPOSED, proposed.get("status"));
        // An offset is converted to the exact instant.
        assertEquals("2026-11-24T23:00:00Z", proposed.get("startsAt"));
        assertEquals(student.toString(), proposed.get("proposedBy"));

        Map<String, Object> confirmed = answer(mentor, proposed.get("id"), "confirmed");
        assertEquals(SessionProposal.Status.CONFIRMED, confirmed.get("status"));
        assertEquals(mentor.toString(), confirmed.get("confirmedBy"));

        // Both sides see it on the connection and in the list.
        assertEquals(confirmed, service.view(student, connectionId).get("schedule"));
        assertEquals(confirmed, service.listFor(mentor).get(0).get("schedule"));
        assertEquals(confirmed, service.viewSchedule(mentor, connectionId));

        assertNull(answer(student, proposed.get("id"), "CANCELLED"));
        assertNull(service.view(student, connectionId).get("schedule"));
        assertEquals(404, status(() -> service.viewSchedule(student, connectionId)));
    }

    @Test
    void badProposalsAre400WithTheRightField() {
        assertEquals("startsAt", field(() -> propose(student, null, null)));
        assertEquals("startsAt", field(() -> propose(student, "next tuesday", null)));
        assertEquals("startsAt", field(() -> propose(student, "2026-11-24T18:00", null))); // no offset
        assertEquals("startsAt", field(() -> propose(student, "2026-11-19T18:00:00Z", null))); // past
        assertEquals("startsAt", field(() -> propose(student, "2028-01-01T18:00:00Z", null))); // > 1 year
        assertEquals("note", field(() -> propose(student, "2026-11-24T18:00:00Z",
                "x".repeat(SessionProposal.MAX_NOTE_LENGTH + 1))));

        Map<String, Object> body = new HashMap<>();
        body.put("startsAt", "2026-11-24T18:00:00Z");
        body.put("room", "B101");
        assertEquals("room", field(() -> service.proposeSession(student, connectionId, body)));

        assertNull(service.view(student, connectionId).get("schedule"));
    }

    @Test
    void confirmingYourOwnProposalIs403() {
        Map<String, Object> proposed = propose(mentor, "2026-11-24T18:00:00Z", null);
        assertEquals(403, status(() -> answer(mentor, proposed.get("id"), "CONFIRMED")));
        assertEquals(SessionProposal.Status.PROPOSED, service.viewSchedule(student, connectionId).get("status"));
    }

    @Test
    void staleOrMissingProposalsAre409() {
        assertEquals(409, status(() -> answer(mentor, UUID.randomUUID().toString(), "CONFIRMED")));

        Map<String, Object> old = propose(student, "2026-11-24T18:00:00Z", null);
        propose(student, "2026-11-25T18:00:00Z", null);
        assertEquals(409, status(() -> answer(mentor, old.get("id"), "CONFIRMED")));
        assertEquals(409, status(() -> answer(mentor, old.get("id"), "CANCELLED")));
    }

    @Test
    void badAnswersAre400() {
        Map<String, Object> proposed = propose(student, "2026-11-24T18:00:00Z", null);
        assertEquals("status", field(() -> answer(mentor, proposed.get("id"), "MAYBE")));
        assertEquals("proposalId", field(() -> answer(mentor, "not-an-id", "CONFIRMED")));
        assertEquals("proposalId", field(() -> service.answerSchedule(mentor, connectionId,
                Map.of("status", "CONFIRMED"))));
    }

    @Test
    void onlyAnActiveConnectionCanBeScheduledAndStrangersSee404() {
        UUID stranger = saveStudent();
        assertEquals(404, status(() -> propose(stranger, "2026-11-24T18:00:00Z", null)));
        assertEquals(404, status(() -> service.viewSchedule(stranger, connectionId)));

        propose(student, "2026-11-24T18:00:00Z", null);
        service.update(student, connectionId, Map.of("status", "COMPLETED"));
        // The unconfirmed proposal went with the active connection.
        assertNull(service.view(student, connectionId).get("schedule"));
        assertEquals(409, status(() -> propose(student, "2026-11-24T18:00:00Z", null)));
    }

    private Map<String, Object> propose(UUID by, String startsAt, String note) {
        Map<String, Object> body = new HashMap<>();
        if (startsAt != null) {
            body.put("startsAt", startsAt);
        }
        if (note != null) {
            body.put("note", note);
        }
        return service.proposeSession(by, connectionId, body);
    }

    private Map<String, Object> answer(UUID by, Object proposalId, String status) {
        return service.answerSchedule(by, connectionId,
                Map.of("proposalId", String.valueOf(proposalId), "status", status));
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
