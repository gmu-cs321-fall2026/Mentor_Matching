import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * One entry in a connection's session log: a meeting that happened, who
 * recorded it, and a short summary.
 *
 * <p>Immutable, so entries can be read by any thread without locking. Like
 * {@link Connection}, it holds only ids for people; names come from Shared Core
 * at read time (ADR-003).
 */
public final class SessionLogEntry {

    /** Latest date we accept for a session, in days past today (UTC), to tolerate time zones. */
    private static final int FUTURE_TOLERANCE_DAYS = 1;

    private final UUID id;
    private final LocalDate heldOn;
    private final String summary;
    private final UUID loggedBy;
    private final Instant loggedAt;

    /**
     * @param heldOn the day the session took place
     * @param summary what was covered or agreed; required, trimmed
     * @param loggedBy the participant recording it
     * @throws IllegalArgumentException if a value is missing, the summary is too
     *     long, or the date is in the future
     */
    public SessionLogEntry(LocalDate heldOn, String summary, UUID loggedBy) {
        if (heldOn == null) {
            throw new IllegalArgumentException("heldOn is required");
        }
        if (loggedBy == null) {
            throw new IllegalArgumentException("loggedBy is required");
        }
        if (heldOn.isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(FUTURE_TOLERANCE_DAYS))) {
            throw new IllegalArgumentException("a session cannot be in the future");
        }
        String trimmed = summary == null ? "" : summary.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("summary is required");
        }
        if (trimmed.length() > Connection.MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("summary may be at most "
                    + Connection.MAX_TEXT_LENGTH + " characters");
        }
        this.id = UUID.randomUUID();
        this.heldOn = heldOn;
        this.summary = trimmed;
        this.loggedBy = loggedBy;
        this.loggedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getHeldOn() {
        return heldOn;
    }

    public String getSummary() {
        return summary;
    }

    public UUID getLoggedBy() {
        return loggedBy;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }

    /** Two entries are the same entry when they share an id. */
    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof SessionLogEntry && id.equals(((SessionLogEntry) other).id));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SessionLogEntry{id=" + id + ", heldOn=" + heldOn + ", loggedBy=" + loggedBy + "}";
    }
}
