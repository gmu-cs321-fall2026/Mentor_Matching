import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.LocalDate;

/**
 * Domain object for one student-to-mentor connection request and its lifecycle.
 *
 * <p>Holds only ids for the two people; names and emails come from Shared Core
 * at read time, as with the profiles (ADR-003). The state machine lives here so
 * every layer above asks the same question the same way:
 *
 * <pre>
 *   PENDING -> ACTIVE      mentor accepts
 *   PENDING -> DECLINED    mentor declines
 *   PENDING -> WITHDRAWN   student cancels the request
 *   ACTIVE  -> COMPLETED   either side
 *   ACTIVE  -> WITHDRAWN   either side
 * </pre>
 *
 * Capacity is not checked here; {@link ConnectionService} owns the mentor's
 * {@code activeMentees} count, because it changes a second object.
 */
public class Connection {

    /** Longest request message or notes text we store. */
    public static final int MAX_TEXT_LENGTH = 2000;
    /** Most session entries one connection will hold. */
    public static final int MAX_SESSIONS = 200;

    private final UUID id;
    private final UUID studentId;
    private final UUID mentorId;
    private final String message;
    private final Instant createdAt;
    private final List<SessionLogEntry> sessions = new CopyOnWriteArrayList<>();

    private ConnectionStatus status = ConnectionStatus.PENDING;
    private String notes = "";
    private Instant updatedAt;
    /** The next session's proposed or confirmed time; null when nothing is scheduled. */
    private SessionProposal schedule;

    /**
     * A new pending request.
     *
     * @param studentId the student sending it
     * @param mentorId the mentor it is sent to
     * @param message an optional note to the mentor, or null
     * @throws IllegalArgumentException if an id is missing or the message is too long
     */
    public Connection(UUID studentId, UUID mentorId, String message) {
        if (studentId == null || mentorId == null) {
            throw new IllegalArgumentException("studentId and mentorId are required");
        }
        this.id = UUID.randomUUID();
        this.studentId = studentId;
        this.mentorId = mentorId;
        this.message = checkText(message, "message");
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    /**
     * Whether a move from one status to another is allowed for this side.
     *
     * @param actorIsMentor true when the mentor is asking, false for the student
     */
    public static boolean isAllowed(ConnectionStatus from, ConnectionStatus to, boolean actorIsMentor) {
        return switch (from) {
            case PENDING -> actorIsMentor
                    ? to == ConnectionStatus.ACTIVE || to == ConnectionStatus.DECLINED
                    : to == ConnectionStatus.WITHDRAWN;
            case ACTIVE -> to == ConnectionStatus.COMPLETED || to == ConnectionStatus.WITHDRAWN;
            default -> false;
        };
    }

    /**
     * Moves to a new status on behalf of one participant.
     *
     * @throws IllegalStateException if {@link #isAllowed} says no
     */
    public void moveTo(ConnectionStatus next, UUID actorId) {
        if (!isAllowed(status, next, isMentor(actorId))) {
            throw new IllegalStateException("cannot move from " + status + " to " + next);
        }
        // A time nobody agreed to means nothing once the connection closes.
        // A confirmed one is kept as a record of what was arranged.
        if (schedule != null && schedule.getStatus() == SessionProposal.Status.PROPOSED) {
            schedule = null;
        }
        status = next;
        touch();
    }

    /** The next session's proposed or confirmed time, or null when nothing is scheduled. */
    public SessionProposal getSchedule() {
        return schedule;
    }

    /**
     * Either participant proposes a time for the next session. A new proposal
     * replaces whatever was there, proposed or confirmed: that is how a session
     * is rescheduled.
     *
     * @throws IllegalStateException if the connection is not active
     * @throws IllegalArgumentException if the caller is not on the connection
     * @throws InvalidFieldException if the time or note is invalid
     */
    public SessionProposal proposeSchedule(Instant startsAt, String note, UUID by, Instant now) {
        if (status != ConnectionStatus.ACTIVE) {
            throw new IllegalStateException("sessions can only be scheduled on an active connection");
        }
        if (!isParticipant(by)) {
            throw new IllegalArgumentException("only the student or mentor can propose a time");
        }
        schedule = new SessionProposal(startsAt, note, by, now);
        touch();
        return schedule;
    }

    /**
     * The other participant agrees to the proposed time.
     *
     * @param proposalId the proposal the caller saw, so a stale page cannot
     *     confirm a time that has since been replaced
     * @throws IllegalCallerException if the caller made the proposal
     * @throws IllegalStateException if the connection is not active, nothing is
     *     proposed, the proposal was replaced or is already confirmed, or its
     *     time has passed
     */
    public SessionProposal confirmSchedule(UUID proposalId, UUID by, Instant now) {
        SessionProposal current = currentProposal(proposalId);
        if (current.getStatus() == SessionProposal.Status.CONFIRMED) {
            throw new IllegalStateException("this time is already confirmed");
        }
        if (current.getProposedBy().equals(by)) {
            throw new IllegalCallerException("the other participant has to confirm your proposal");
        }
        if (!current.getStartsAt().isAfter(now)) {
            throw new IllegalStateException("the proposed time has passed; propose a new one");
        }
        schedule = current.confirmedBy(by, now);
        touch();
        return schedule;
    }

    /**
     * Either participant clears the schedule: the proposer withdraws their
     * proposal, the other side declines it, or either side cancels a confirmed
     * session.
     *
     * @throws IllegalStateException if the connection is not active, nothing is
     *     scheduled, or the proposal was replaced
     */
    public void cancelSchedule(UUID proposalId, UUID by) {
        currentProposal(proposalId);
        if (!isParticipant(by)) {
            throw new IllegalArgumentException("only the student or mentor can cancel a session");
        }
        schedule = null;
        touch();
    }

    private SessionProposal currentProposal(UUID proposalId) {
        if (status != ConnectionStatus.ACTIVE) {
            throw new IllegalStateException("sessions can only be scheduled on an active connection");
        }
        if (schedule == null) {
            throw new IllegalStateException("no session time has been proposed");
        }
        if (!schedule.getId().equals(proposalId)) {
            throw new IllegalStateException("that proposal was replaced by a newer one; refresh and try again");
        }
        return schedule;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getMentorId() {
        return mentorId;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    /** The student's note sent with the request; empty when none was given. */
    public String getMessage() {
        return message;
    }

    /** Shared notes both sides can keep once the connection is active. */
    public String getNotes() {
        return notes;
    }

    /**
     * @throws IllegalStateException if the connection is not active
     * @throws IllegalArgumentException if the text is too long
     */
    public void setNotes(String notes) {
        if (status != ConnectionStatus.ACTIVE) {
            throw new IllegalStateException("notes can only be kept on an active connection");
        }
        this.notes = checkText(notes, "notes");
        touch();
    }

    /** The session log, oldest first. The returned list is a read-only snapshot. */
    public List<SessionLogEntry> getSessions() {
        return Collections.unmodifiableList(new ArrayList<>(sessions));
    }
    
    /**
     * Records a session on an active connection.
     *
     * @throws IllegalStateException if the connection is not active or the log is full
     * @throws IllegalArgumentException if the entry's values are invalid
     */
    public SessionLogEntry addSession(LocalDate heldOn, String summary, UUID authorId) {
        if (status != ConnectionStatus.ACTIVE) {
            throw new IllegalStateException("sessions can only be logged on an active connection");
        }
        if (!isParticipant(authorId)) {
            throw new IllegalArgumentException("only the student or mentor can log a session");
        }
        if (sessions.size() >= MAX_SESSIONS) {
            throw new IllegalStateException("this connection's session log is full");
        }
        SessionLogEntry entry = new SessionLogEntry(heldOn, summary, authorId);
        sessions.add(entry);
        touch();
        return entry;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** True when the user is the student or the mentor on this connection. */
    public boolean isParticipant(UUID userId) {
        return studentId.equals(userId) || mentorId.equals(userId);
    }

    public boolean isMentor(UUID userId) {
        return mentorId.equals(userId);
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    private static String checkText(String text, String field) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(field + " may be at most "
                    + MAX_TEXT_LENGTH + " characters");
        }
        return trimmed;
    }

    /** Two connections are the same connection when they share an id. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Connection)) {
            return false;
        }
        return id.equals(((Connection) other).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Connection{id=" + id
                + ", studentId=" + studentId
                + ", mentorId=" + mentorId
                + ", status=" + status
                + "}";
    }
}
