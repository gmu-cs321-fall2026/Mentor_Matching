import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Lifecycle rules of {@link Connection}, straight from interface contracts v2. */
class ConnectionTest {

    private final UUID student = UUID.randomUUID();
    private final UUID mentor = UUID.randomUUID();

    private Connection pending() {
        return new Connection(student, mentor, "Hi!");
    }

    private Connection active() {
        Connection c = pending();
        c.moveTo(ConnectionStatus.ACTIVE, mentor);
        return c;
    }

    /** A connection sitting in the given status, reached by legal moves only. */
    private Connection inStatus(ConnectionStatus status) {
        Connection c = pending();
        switch (status) {
            case PENDING -> { }
            case ACTIVE -> c.moveTo(ConnectionStatus.ACTIVE, mentor);
            case DECLINED -> c.moveTo(ConnectionStatus.DECLINED, mentor);
            case WITHDRAWN -> c.moveTo(ConnectionStatus.WITHDRAWN, student);
            case COMPLETED -> {
                c.moveTo(ConnectionStatus.ACTIVE, mentor);
                c.moveTo(ConnectionStatus.COMPLETED, student);
            }
        }
        assertEquals(status, c.getStatus());
        return c;
    }

    /* ---------- creation ---------- */

    @Test
    void newConnectionIsPendingWithNoNotes() {
        Connection c = pending();
        assertEquals(ConnectionStatus.PENDING, c.getStatus());
        assertEquals("", c.getNotes());
        assertEquals(student, c.getStudentId());
        assertEquals(mentor, c.getMentorId());
        assertFalse(c.getUpdatedAt().isBefore(c.getCreatedAt()));
    }

    @Test
    void messageIsTrimmedAndNullBecomesEmpty() {
        assertEquals("Hi!", new Connection(student, mentor, "  Hi!  ").getMessage());
        assertEquals("", new Connection(student, mentor, null).getMessage());
    }

    @Test
    void idsAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new Connection(null, mentor, "x"));
        assertThrows(IllegalArgumentException.class, () -> new Connection(student, null, "x"));
    }

    @Test
    void messageLimitIsTwoThousandCharacters() {
        String atLimit = "a".repeat(Connection.MAX_TEXT_LENGTH);
        assertEquals(atLimit, new Connection(student, mentor, atLimit).getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> new Connection(student, mentor, atLimit + "a"));
    }

    /* ---------- the transition table ---------- */

    @Test
    void isAllowedMatchesTheContractTableForEverySideAndStatus() {
        Set<String> allowed = Set.of(
                "PENDING>ACTIVE:mentor",
                "PENDING>DECLINED:mentor",
                "PENDING>WITHDRAWN:student",
                "ACTIVE>COMPLETED:mentor",
                "ACTIVE>COMPLETED:student",
                "ACTIVE>WITHDRAWN:mentor",
                "ACTIVE>WITHDRAWN:student");

        for (ConnectionStatus from : ConnectionStatus.values()) {
            for (ConnectionStatus to : ConnectionStatus.values()) {
                for (boolean actorIsMentor : new boolean[] {true, false}) {
                    String key = from + ">" + to + ":" + (actorIsMentor ? "mentor" : "student");
                    assertEquals(allowed.contains(key),
                            Connection.isAllowed(from, to, actorIsMentor), key);
                }
            }
        }
    }

    @Test
    void mentorAcceptsAndDeclinesStudentWithdraws() {
        assertEquals(ConnectionStatus.ACTIVE, inStatus(ConnectionStatus.ACTIVE).getStatus());
        assertEquals(ConnectionStatus.DECLINED, inStatus(ConnectionStatus.DECLINED).getStatus());
        assertEquals(ConnectionStatus.WITHDRAWN, inStatus(ConnectionStatus.WITHDRAWN).getStatus());
    }

    @Test
    void eitherSideCanCompleteOrWithdrawAnActiveConnection() {
        for (UUID actor : new UUID[] {mentor, student}) {
            Connection toComplete = active();
            toComplete.moveTo(ConnectionStatus.COMPLETED, actor);
            assertEquals(ConnectionStatus.COMPLETED, toComplete.getStatus());

            Connection toWithdraw = active();
            toWithdraw.moveTo(ConnectionStatus.WITHDRAWN, actor);
            assertEquals(ConnectionStatus.WITHDRAWN, toWithdraw.getStatus());
        }
    }

    @Test
    void studentCannotAcceptOrDeclineAndMentorCannotWithdrawAPendingRequest() {
        Connection c = pending();
        assertThrows(IllegalStateException.class, () -> c.moveTo(ConnectionStatus.ACTIVE, student));
        assertThrows(IllegalStateException.class, () -> c.moveTo(ConnectionStatus.DECLINED, student));
        assertThrows(IllegalStateException.class, () -> c.moveTo(ConnectionStatus.WITHDRAWN, mentor));
        assertEquals(ConnectionStatus.PENDING, c.getStatus());
    }

    @Test
    void declinedCompletedAndWithdrawnAreFinal() {
        ConnectionStatus[] finals = {
            ConnectionStatus.DECLINED, ConnectionStatus.COMPLETED, ConnectionStatus.WITHDRAWN};
        for (ConnectionStatus end : finals) {
            for (ConnectionStatus to : ConnectionStatus.values()) {
                for (UUID actor : new UUID[] {mentor, student}) {
                    Connection c = inStatus(end);
                    assertThrows(IllegalStateException.class, () -> c.moveTo(to, actor),
                            end + " -> " + to);
                    assertEquals(end, c.getStatus());
                }
            }
        }
    }

    @Test
    void anActiveConnectionCannotGoBackToPending() {
        Connection c = active();
        assertThrows(IllegalStateException.class, () -> c.moveTo(ConnectionStatus.PENDING, mentor));
        assertEquals(ConnectionStatus.ACTIVE, c.getStatus());
    }

    /* ---------- notes ---------- */

    @Test
    void notesAreRefusedUnlessTheConnectionIsActive() {
        for (ConnectionStatus status : ConnectionStatus.values()) {
            if (status == ConnectionStatus.ACTIVE) {
                continue;
            }
            Connection c = inStatus(status);
            assertThrows(IllegalStateException.class, () -> c.setNotes("hello"), status.name());
        }
    }

    @Test
    void notesOnAnActiveConnectionAreTrimmedAndStored() {
        Connection c = active();
        c.setNotes("  Weekly on Tuesdays  ");
        assertEquals("Weekly on Tuesdays", c.getNotes());
    }

    @Test
    void notesLimitIsTwoThousandCharactersAndAFailedSaveKeepsTheOldNotes() {
        Connection c = active();
        c.setNotes("keep me");

        String atLimit = "n".repeat(Connection.MAX_TEXT_LENGTH);
        c.setNotes(atLimit);
        assertEquals(atLimit, c.getNotes());

        assertThrows(IllegalArgumentException.class, () -> c.setNotes(atLimit + "n"));
        assertEquals(atLimit, c.getNotes());
    }

    @Test
    void notesStayReadableAfterTheConnectionCompletes() {
        Connection c = active();
        c.setNotes("Great sessions");
        c.moveTo(ConnectionStatus.COMPLETED, student);
        assertEquals("Great sessions", c.getNotes());
        assertThrows(IllegalStateException.class, () -> c.setNotes("edit after the fact"));
    }

    /* ---------- people and identity ---------- */

    @Test
    void participantsAreTheStudentAndTheMentorOnly() {
        Connection c = pending();
        assertTrue(c.isParticipant(student));
        assertTrue(c.isParticipant(mentor));
        assertFalse(c.isParticipant(UUID.randomUUID()));
        assertTrue(c.isMentor(mentor));
        assertFalse(c.isMentor(student));
    }

    @Test
    void connectionsAreEqualOnlyWhenTheyShareAnId() {
        Connection a = pending();
        Connection b = pending();
        assertEquals(a, a);
        assertNotEquals(a, b);
        assertEquals(a.hashCode(), a.hashCode());
    }
}
