import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A proposed or confirmed time for the next mentoring session on a connection.
 *
 * <p>One participant proposes a time; the other confirms it. Mentor Matching
 * does not book rooms or send invites, so the optional {@code note} says where
 * or how to meet (a video link, a room). Immutable: confirming returns a new
 * object, so a proposal can be read by any thread without locking.
 */
public final class SessionProposal {

    /** Where a proposal stands. */
    public enum Status {
        /** Waiting for the other participant. */
        PROPOSED,
        /** The other participant agreed to the time. */
        CONFIRMED
    }

    /** Longest note we store with a proposal. */
    public static final int MAX_NOTE_LENGTH = 500;
    /** Furthest ahead a session may be proposed. */
    public static final Duration MAX_LEAD_TIME = Duration.ofDays(365);

    private final UUID id;
    private final Instant startsAt;
    private final String note;
    private final UUID proposedBy;
    private final Instant proposedAt;
    private final Status status;
    private final UUID confirmedBy;
    private final Instant confirmedAt;

    /**
     * A new proposal.
     *
     * @param startsAt when the session would start
     * @param note where or how to meet; optional, trimmed
     * @param proposedBy the participant proposing it
     * @param now the current time, passed in so tests can fix the clock
     * @throws InvalidFieldException if the time is missing, not in the future,
     *     more than a year away, or the note is too long
     */
    public SessionProposal(Instant startsAt, String note, UUID proposedBy, Instant now) {
        if (startsAt == null) {
            throw new InvalidFieldException("startsAt", "required");
        }
        if (!startsAt.isAfter(now)) {
            throw new InvalidFieldException("startsAt", "must be in the future");
        }
        if (startsAt.isAfter(now.plus(MAX_LEAD_TIME))) {
            throw new InvalidFieldException("startsAt", "must be within a year");
        }
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            throw new InvalidFieldException("note",
                    "may be at most " + MAX_NOTE_LENGTH + " characters");
        }
        this.id = UUID.randomUUID();
        this.startsAt = startsAt;
        this.note = trimmed;
        this.proposedBy = Objects.requireNonNull(proposedBy, "proposedBy");
        this.proposedAt = now;
        this.status = Status.PROPOSED;
        this.confirmedBy = null;
        this.confirmedAt = null;
    }

    private SessionProposal(SessionProposal proposed, UUID confirmedBy, Instant confirmedAt) {
        this.id = proposed.id;
        this.startsAt = proposed.startsAt;
        this.note = proposed.note;
        this.proposedBy = proposed.proposedBy;
        this.proposedAt = proposed.proposedAt;
        this.status = Status.CONFIRMED;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt;
    }

    /**
     * The same proposal, confirmed. Who may confirm is checked by
     * {@link Connection#confirmSchedule}.
     */
    SessionProposal confirmedBy(UUID by, Instant now) {
        return new SessionProposal(this, by, now);
    }

    public UUID getId() {
        return id;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public String getNote() {
        return note;
    }

    public UUID getProposedBy() {
        return proposedBy;
    }

    public Instant getProposedAt() {
        return proposedAt;
    }

    public Status getStatus() {
        return status;
    }

    /** Null until confirmed. */
    public UUID getConfirmedBy() {
        return confirmedBy;
    }

    /** Null until confirmed. */
    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    /** The JSON shape from interface contracts v2, "Session scheduling". */
    public Map<String, Object> toView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", id.toString());
        view.put("startsAt", startsAt.toString());
        view.put("note", note);
        view.put("status", status);
        view.put("proposedBy", proposedBy.toString());
        view.put("proposedAt", proposedAt.toString());
        view.put("confirmedBy", confirmedBy == null ? null : confirmedBy.toString());
        view.put("confirmedAt", confirmedAt == null ? null : confirmedAt.toString());
        return view;
    }

    @Override
    public String toString() {
        return "SessionProposal{id=" + id + ", startsAt=" + startsAt + ", status=" + status + "}";
    }
}
