import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Session log rules on {@link Connection} and {@link SessionLogEntry}. */
class ConnectionSessionLogTest {

    private static final LocalDate LAST_WEEK = LocalDate.now(ZoneOffset.UTC).minusDays(7);

    private final UUID student = UUID.randomUUID();
    private final UUID mentor = UUID.randomUUID();

    private Connection active() {
        Connection c = new Connection(student, mentor, null);
        c.moveTo(ConnectionStatus.ACTIVE, mentor);
        return c;
    }

    @Test
    void eitherSideCanLogASessionAndTheLogKeepsOrder() {
        Connection c = active();
        SessionLogEntry first = c.addSession(LAST_WEEK, "  Resume review  ", student);
        SessionLogEntry second = c.addSession(LAST_WEEK.plusDays(1), "Mock interview", mentor);

        assertEquals("Resume review", first.getSummary());
        assertEquals(student, first.getLoggedBy());
        assertEquals(List.of(first, second), c.getSessions());
    }

    @Test
    void theReturnedLogIsReadOnly() {
        Connection c = active();
        c.addSession(LAST_WEEK, "Resume review", student);
        assertThrows(UnsupportedOperationException.class, () -> c.getSessions().clear());
        assertEquals(1, c.getSessions().size());
    }

    @Test
    void onlyAnActiveConnectionTakesSessions() {
        Connection pending = new Connection(student, mentor, null);
        assertThrows(IllegalStateException.class,
                () -> pending.addSession(LAST_WEEK, "Too early", student));

        Connection done = active();
        done.moveTo(ConnectionStatus.COMPLETED, student);
        assertThrows(IllegalStateException.class,
                () -> done.addSession(LAST_WEEK, "Too late", student));
        assertTrue(done.getSessions().isEmpty());
    }

    @Test
    void strangersCannotLogASession() {
        Connection c = active();
        assertThrows(IllegalArgumentException.class,
                () -> c.addSession(LAST_WEEK, "Not mine", UUID.randomUUID()));
    }

    @Test
    void badEntriesAreRefused() {
        Connection c = active();
        assertThrows(IllegalArgumentException.class, () -> c.addSession(null, "No date", student));
        assertThrows(IllegalArgumentException.class, () -> c.addSession(LAST_WEEK, "   ", student));
        assertThrows(IllegalArgumentException.class, () -> c.addSession(LAST_WEEK, null, student));
        assertThrows(IllegalArgumentException.class, () -> c.addSession(LAST_WEEK,
                "x".repeat(Connection.MAX_TEXT_LENGTH + 1), student));
        assertThrows(IllegalArgumentException.class, () -> c.addSession(
                LocalDate.now(ZoneOffset.UTC).plusDays(10), "Next month", student));
        assertTrue(c.getSessions().isEmpty());
    }

    @Test
    void tomorrowInUtcIsAllowedForTimeZones() {
        // Late evening in Virginia is already tomorrow in UTC.
        Connection c = active();
        c.addSession(LocalDate.now(ZoneOffset.UTC).plusDays(1), "Evening call", student);
        assertEquals(1, c.getSessions().size());
    }

    @Test
    void theLogIsCapped() {
        Connection c = active();
        for (int i = 0; i < Connection.MAX_SESSIONS; i++) {
            c.addSession(LAST_WEEK, "Session " + i, student);
        }
        assertThrows(IllegalStateException.class, () -> c.addSession(LAST_WEEK, "One more", student));
        assertEquals(Connection.MAX_SESSIONS, c.getSessions().size());
    }
}
