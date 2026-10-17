import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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

    private final UUID id;
    private final UUID studentId;
    private final UUID mentorId;
    private final String message;
    private final Instant createdAt;

    private ConnectionStatus status = ConnectionStatus.PENDING;
    private String notes = "";
    private Instant updatedAt;

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
        status = next;
        touch();
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
