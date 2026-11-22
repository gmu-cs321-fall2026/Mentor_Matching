import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Session scheduling rules on {@link Connection}: propose, confirm, cancel. */
class ConnectionScheduleTest {

    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");
    private static final Instant TOMORROW = NOW.plus(Duration.ofDays(1));

    private final UUID student = UUID.randomUUID();
    private final UUID mentor = UUID.randomUUID();

    private Connection active() {
        Connection c = new Connection(student, mentor, null);
        c.moveTo(ConnectionStatus.ACTIVE, mentor);
        return c;
    }

    /* ---------- proposing ---------- */

    @Test
    void eitherSideCanProposeOnAnActiveConnection() {
        for (UUID by : new UUID[] {student, mentor}) {
            Connection c = active();
            SessionProposal p = c.proposeSchedule(TOMORROW, "  Zoom  ", by, NOW);
            assertEquals(SessionProposal.Status.PROPOSED, p.getStatus());
            assertEquals(by, p.getProposedBy());
            assertEquals("Zoom", p.getNote());
            assertEquals(p, c.getSchedule());
        }
    }

    @Test
    void proposingNeedsAnActiveConnection() {
        Connection pending = new Connection(student, mentor, null);
        assertThrows(IllegalStateException.class,
                () -> pending.proposeSchedule(TOMORROW, null, student, NOW));
        assertNull(pending.getSchedule());
    }

    @Test
    void strangersCannotPropose() {
        Connection c = active();
        assertThrows(IllegalArgumentException.class,
                () -> c.proposeSchedule(TOMORROW, null, UUID.randomUUID(), NOW));
        assertNull(c.getSchedule());
    }

    @Test
    void theTimeMustBeInTheFutureAndWithinAYear() {
        Connection c = active();
        assertEquals("startsAt", fieldOf(() -> c.proposeSchedule(NOW, null, student, NOW)));
        assertEquals("startsAt", fieldOf(() -> c.proposeSchedule(NOW.minusSeconds(60), null, student, NOW)));
        assertEquals("startsAt", fieldOf(() -> c.proposeSchedule(
                NOW.plus(SessionProposal.MAX_LEAD_TIME).plusSeconds(1), null, student, NOW)));
        assertEquals("startsAt", fieldOf(() -> c.proposeSchedule(null, null, student, NOW)));
        // The limits themselves are allowed.
        c.proposeSchedule(NOW.plusSeconds(1), null, student, NOW);
        c.proposeSchedule(NOW.plus(SessionProposal.MAX_LEAD_TIME), null, student, NOW);
    }

    @Test
    void noteLimitIsFiveHundredCharacters() {
        Connection c = active();
        String atLimit = "n".repeat(SessionProposal.MAX_NOTE_LENGTH);
        assertEquals(atLimit, c.proposeSchedule(TOMORROW, atLimit, student, NOW).getNote());
        assertEquals("note", fieldOf(() -> c.proposeSchedule(TOMORROW, atLimit + "n", student, NOW)));
    }

    @Test
    void aNewProposalReplacesTheOldOneEvenIfConfirmed() {
        Connection c = active();
        SessionProposal first = c.proposeSchedule(TOMORROW, null, student, NOW);
        c.confirmSchedule(first.getId(), mentor, NOW);

        SessionProposal second = c.proposeSchedule(TOMORROW.plusSeconds(3600), null, mentor, NOW);
        assertNotEquals(first.getId(), second.getId());
        assertEquals(SessionProposal.Status.PROPOSED, c.getSchedule().getStatus());
    }

    /* ---------- confirming ---------- */

    @Test
    void theOtherSideConfirms() {
        Connection c = active();
        SessionProposal p = c.proposeSchedule(TOMORROW, null, student, NOW);
        SessionProposal confirmed = c.confirmSchedule(p.getId(), mentor, NOW);

        assertEquals(SessionProposal.Status.CONFIRMED, confirmed.getStatus());
        assertEquals(p.getId(), confirmed.getId());
        assertEquals(mentor, confirmed.getConfirmedBy());
        assertEquals(NOW, confirmed.getConfirmedAt());
        assertEquals(TOMORROW, confirmed.getStartsAt());
    }

    @Test
    void nobodyConfirmsTheirOwnProposal() {
        Connection c = active();
        SessionProposal p = c.proposeSchedule(TOMORROW, null, student, NOW);
        assertThrows(IllegalCallerException.class, () -> c.confirmSchedule(p.getId(), student, NOW));
        assertEquals(SessionProposal.Status.PROPOSED, c.getSchedule().getStatus());
    }

    @Test
    void aReplacedProposalCannotBeConfirmed() {
        Connection c = active();
        SessionProposal old = c.proposeSchedule(TOMORROW, null, student, NOW);
        c.proposeSchedule(TOMORROW.plusSeconds(3600), null, student, NOW);
        assertThrows(IllegalStateException.class, () -> c.confirmSchedule(old.getId(), mentor, NOW));
    }

    @Test
    void confirmingTwiceOrWithNothingProposedIsRefused() {
        Connection c = active();
        assertThrows(IllegalStateException.class,
                () -> c.confirmSchedule(UUID.randomUUID(), mentor, NOW));

        SessionProposal p = c.proposeSchedule(TOMORROW, null, student, NOW);
        c.confirmSchedule(p.getId(), mentor, NOW);
        assertThrows(IllegalStateException.class, () -> c.confirmSchedule(p.getId(), mentor, NOW));
    }

    @Test
    void aTimeThatHasPassedCannotBeConfirmed() {
        Connection c = active();
        SessionProposal p = c.proposeSchedule(TOMORROW, null, student, NOW);
        Instant later = TOMORROW.plusSeconds(1);
        assertThrows(IllegalStateException.class, () -> c.confirmSchedule(p.getId(), mentor, later));
        assertEquals(SessionProposal.Status.PROPOSED, c.getSchedule().getStatus());
    }

    /* ---------- cancelling ---------- */

    @Test
    void eitherSideCanWithdrawDeclineOrCancel() {
        for (UUID by : new UUID[] {student, mentor}) {
            Connection c = active();
            SessionProposal p = c.proposeSchedule(TOMORROW, null, student, NOW);
            c.cancelSchedule(p.getId(), by);
            assertNull(c.getSchedule());

            SessionProposal q = c.proposeSchedule(TOMORROW, null, student, NOW);
            c.confirmSchedule(q.getId(), mentor, NOW);
            c.cancelSchedule(q.getId(), by);
            assertNull(c.getSchedule());
        }
    }

    @Test
    void cancellingAReplacedProposalIsRefused() {
        Connection c = active();
        SessionProposal old = c.proposeSchedule(TOMORROW, null, student, NOW);
        SessionProposal current = c.proposeSchedule(TOMORROW.plusSeconds(60), null, mentor, NOW);
        assertThrows(IllegalStateException.class, () -> c.cancelSchedule(old.getId(), student));
        assertEquals(current, c.getSchedule());
    }

    /* ---------- the connection closing ---------- */

    @Test
    void closingTheConnectionDropsAnUnconfirmedProposal() {
        Connection c = active();
        c.proposeSchedule(TOMORROW, null, student, NOW);
        c.moveTo(ConnectionStatus.COMPLETED, mentor);
        assertNull(c.getSchedule());
    }

    @Test
    void closingTheConnectionKeepsAConfirmedTimeAsARecordButFreezesIt() {
        Connection c = active();
        SessionProposal p = c.proposeSchedule(TOMORROW, null, student, NOW);
        c.confirmSchedule(p.getId(), mentor, NOW);
        c.moveTo(ConnectionStatus.WITHDRAWN, student);

        assertEquals(SessionProposal.Status.CONFIRMED, c.getSchedule().getStatus());
        assertThrows(IllegalStateException.class, () -> c.cancelSchedule(p.getId(), student));
        assertThrows(IllegalStateException.class,
                () -> c.proposeSchedule(TOMORROW, null, student, NOW));
    }

    /** The field an {@link InvalidFieldException} names. */
    private static String fieldOf(Runnable call) {
        return assertThrows(InvalidFieldException.class, call::run).getField();
    }
}
