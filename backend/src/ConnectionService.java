import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

/**
 * Creates connection requests and moves them through their lifecycle.
 *
 * <p>Backs {@code POST /connections} and {@code PUT /connections/{id}}. The
 * caller is already authenticated by {@link MentoringApp}; this class checks
 * who may do what, keeps the mentor's {@code activeMentees} in step with their
 * active connections, and turns domain complaints into {@link ApiException}s.
 *
 * <p>Methods that change capacity are {@code synchronized}, so two accepts
 * arriving together cannot push a mentor past {@code maxMentees}. That is
 * enough for the single in-memory store; a database implementation would need
 * a transaction instead.
 */
public class ConnectionService {

    /**
     * The time zone a session's default date is taken in. Everyone using the
     * platform is on GMU's campus clock, so "today" means today in Virginia, not
     * in UTC: a session logged at 8 pm Eastern is still dated that day.
     */
    static final ZoneId CAMPUS_ZONE = ZoneId.of("America/New_York");

    /** Fields a client may send to {@code PUT /connections/{id}}. */
    static final Set<String> UPDATE_FIELDS = Set.of("status", "notes");
    /** Fields a client may send to {@code POST /connections/{id}/sessions}. */
    static final Set<String> SESSION_FIELDS = Set.of("heldOn", "summary");
    /** Fields a client may send to {@code POST /connections/{id}/schedule}. */
    static final Set<String> PROPOSE_FIELDS = Set.of("startsAt", "note");
    /** Fields a client may send to {@code PUT /connections/{id}/schedule}. */
    static final Set<String> ANSWER_FIELDS = Set.of("proposalId", "status");

    private final ProfileData data;
    private final Clock clock;

    public ConnectionService(ProfileData data) {
        this(data, Clock.systemUTC());
    }

    /** @param clock the time source for scheduling rules; tests pass a fixed clock */
    public ConnectionService(ProfileData data, Clock clock) {
        this.data = data;
        this.clock = clock;
    }

    /**
     * A student asks a mentor to connect.
     *
     * @param studentId the signed-in student
     * @param body {@code mentorId} (required) and {@code message} (optional)
     * @return the new connection, status {@code PENDING}
     * @throws ApiException 400 for a bad body, 404 if either profile is missing,
     *     409 if the mentor has no room or the pair already has an open connection
     */
    public synchronized Map<String, Object> request(UUID studentId, Map<String, Object> body) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (String field : body.keySet()) {
            if (!field.equals("mentorId") && !field.equals("message")) {
                errors.put(field, "not a field you can set here");
            }
        }
        UUID mentorId = null;
        Object rawMentorId = body.get("mentorId");
        if (rawMentorId == null) {
            errors.put("mentorId", "required");
        } else {
            try {
                mentorId = UUID.fromString(String.valueOf(rawMentorId).trim());
            } catch (IllegalArgumentException e) {
                errors.put("mentorId", "expected a user id");
            }
        }
        Object message = body.get("message");
        if (message != null && !(message instanceof String)) {
            errors.put("message", "expected text");
        }
        if (!errors.isEmpty()) {
            throw ApiException.fields(errors);
        }

        // Both sides need a profile: matching and the mentor's view both read them.
        data.findStudent(studentId);
        MentorProfile mentor = data.findMentor(mentorId);
        if (!mentor.hasCapacity()) {
            throw ApiException.conflict("this mentor is not taking new mentees right now");
        }
        if (data.hasOpenConnection(studentId, mentorId)) {
            throw ApiException.conflict("you already have an open connection with this mentor");
        }

        try {
            return data.saveConnection(new Connection(studentId, mentorId, (String) message));
        } catch (IllegalArgumentException e) {
            throw ApiException.fields(Map.of("message", e.getMessage()));
        }
    }

    /**
     * A participant changes the status, the shared notes, or both.
     *
     * @param userId the signed-in user
     * @param connectionId the connection to change
     * @param body {@code status} and/or {@code notes}
     * @return the connection after the change
     * @throws ApiException 400 for a bad body, 403 if the move belongs to the
     *     other side, 404 if the connection does not exist or the caller is not on
     *     it, 409 if the move is not allowed from the current status or the
     *     mentor has filled up since the request was sent
     */
    public synchronized Map<String, Object> update(UUID userId, UUID connectionId,
            Map<String, Object> body) {
        Connection connection = findFor(userId, connectionId);

        Map<String, String> errors = new LinkedHashMap<>();
        for (String field : body.keySet()) {
            if (!UPDATE_FIELDS.contains(field)) {
                errors.put(field, "not a field you can set here");
            }
        }
        if (body.isEmpty()) {
            errors.put("status", "send a status, notes, or both");
        }
        ConnectionStatus next = null;
        if (body.containsKey("status")) {
            next = toStatus(body.get("status"));
            if (next == null) {
                errors.put("status", "expected one of PENDING, ACTIVE, DECLINED, COMPLETED, WITHDRAWN");
            }
        }
        Object notes = body.get("notes");
        if (body.containsKey("notes") && !(notes instanceof String)) {
            errors.put("notes", "expected text");
        } else if (notes != null && ((String) notes).trim().length() > Connection.MAX_TEXT_LENGTH) {
            errors.put("notes", "may be at most " + Connection.MAX_TEXT_LENGTH + " characters");
        }
        if (!errors.isEmpty()) {
            throw ApiException.fields(errors);
        }

        // Everything that can fail is checked before anything changes, so a
        // rejected request leaves the connection and the mentor as they were.
        boolean changesStatus = next != null && next != connection.getStatus();
        MentorProfile mentor = data.findMentor(connection.getMentorId());
        if (changesStatus) {
            checkMove(connection, next, userId, mentor);
        }
        // Notes need an active connection either before or after this change
        // (keep notes while completing, or add them while accepting).
        boolean activeBefore = connection.getStatus() == ConnectionStatus.ACTIVE;
        if (notes != null && !activeBefore && !(changesStatus && next == ConnectionStatus.ACTIVE)) {
            throw ApiException.conflict("notes can only be kept on an active connection");
        }

        if (notes != null && activeBefore) {
            connection.setNotes((String) notes);
        }
        boolean wasActive = connection.getStatus() == ConnectionStatus.ACTIVE;
        if (changesStatus) {
            connection.moveTo(next, userId);
            if (next == ConnectionStatus.ACTIVE) {
                mentor.setActiveMentees(mentor.getActiveMentees() + 1);
            } else if (wasActive) {
                mentor.setActiveMentees(Math.max(0, mentor.getActiveMentees() - 1));
            }
        }
        if (notes != null && !activeBefore) {
            connection.setNotes((String) notes);
        }
        if (changesStatus && (next == ConnectionStatus.ACTIVE || wasActive)) {
            data.updateMentor(mentor);
        }
        return withSessions(data.updateConnection(connection), connection);
    }

    /**
     * One connection, for either participant.
     *
     * @throws ApiException 404 if it does not exist or the caller is not on it
     */
    public Map<String, Object> view(UUID userId, UUID connectionId) {
        Connection connection = findFor(userId, connectionId);
        return withSessions(data.connectionView(connection), connection);
    }

    /** Every connection the user is on, as student or mentor, newest first. */
    public List<Map<String, Object>> listFor(UUID userId) {
        return data.listConnections(userId);
    }

    /**
     * Either participant records a session on an active connection.
     *
     * @param body {@code summary} (required) and {@code heldOn} as YYYY-MM-DD
     *     (optional, defaults to today in {@link #CAMPUS_ZONE})
     * @return the new entry
     * @throws ApiException 400 for a bad body, 404 if the connection does not exist
     *     or the caller is not on it, 409 if the connection is not active or its log is full
     */
    public synchronized Map<String, Object> logSession(UUID userId, UUID connectionId,
            Map<String, Object> body) {
        Connection connection = findFor(userId, connectionId);
    
        Map<String, String> errors = new LinkedHashMap<>();
        for (String field : body.keySet()) {
            if (!SESSION_FIELDS.contains(field)) {
                errors.put(field, "not a field you can set here");
            }
        }
        Object summary = body.get("summary");
        if (summary == null) {
            errors.put("summary", "required");
        } else if (!(summary instanceof String)) {
            errors.put("summary", "expected text");
        }
        LocalDate heldOn = LocalDate.now(clock.withZone(CAMPUS_ZONE));
        Object rawDate = body.get("heldOn");
        if (rawDate != null) {
            try {
                heldOn = LocalDate.parse(String.valueOf(rawDate).trim());
            } catch (DateTimeParseException e) {
                errors.put("heldOn", "expected a date like 2026-11-17");
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.fields(errors);
        }
    
        try {
            return sessionView(connection.addSession(heldOn, (String) summary, userId));
        } catch (IllegalArgumentException e) {
            // Remaining complaints are about one field's value (too long, future date).
            String field = e.getMessage().startsWith("heldOn") || e.getMessage().contains("future")
                    ? "heldOn" : "summary";
            throw ApiException.fields(Map.of(field, e.getMessage()));
        } catch (IllegalStateException e) {
            throw ApiException.conflict(e.getMessage());
        }
    }
    
    /** The session log for one connection, oldest first, for either participant. */
    public List<Map<String, Object>> listSessions(UUID userId, UUID connectionId) {
        return sessionViews(findFor(userId, connectionId));
    }
    
    /**
     * Either participant proposes a time for the next session.
     *
     * @param body {@code startsAt} (required, ISO 8601 with an offset, e.g.
     *     {@code 2026-11-24T18:00:00-05:00}) and {@code note} (optional)
     * @return the new proposal, status {@code PROPOSED}
     * @throws ApiException 400 for a bad body, 404 if the connection does not exist
     *     or the caller is not on it, 409 if the connection is not active
     */
    public synchronized Map<String, Object> proposeSession(UUID userId, UUID connectionId,
            Map<String, Object> body) {
        Connection connection = findFor(userId, connectionId);

        Map<String, String> errors = unknownFields(body, PROPOSE_FIELDS);
        Instant startsAt = null;
        Object rawStart = body.get("startsAt");
        if (rawStart == null) {
            errors.put("startsAt", "required");
        } else {
            startsAt = parseInstant(String.valueOf(rawStart));
            if (startsAt == null) {
                errors.put("startsAt", "expected a date and time like 2026-11-24T18:00:00-05:00");
            }
        }
        Object note = body.get("note");
        if (note != null && !(note instanceof String)) {
            errors.put("note", "expected text");
        }
        if (!errors.isEmpty()) {
            throw ApiException.fields(errors);
        }

        try {
            return connection.proposeSchedule(startsAt, (String) note, userId, clock.instant()).toView();
        } catch (InvalidFieldException e) {
            throw ApiException.fields(Map.of(e.getField(), e.getMessage()));
        } catch (IllegalStateException e) {
            throw ApiException.conflict(e.getMessage());
        }
    }

    /**
     * A participant answers the current proposal: {@code CONFIRMED} accepts it,
     * {@code CANCELLED} withdraws, declines, or cancels it.
     *
     * @param body {@code proposalId} and {@code status}, both required
     * @return the schedule after the change; {@code null} once cancelled
     * @throws ApiException 400 for a bad body, 403 if the caller tries to confirm
     *     their own proposal, 404 if the connection does not exist or the caller is
     *     not on it, 409 if there is nothing to answer, the proposal was replaced or
     *     already confirmed, its time has passed, or the connection is not active
     */
    public synchronized Map<String, Object> answerSchedule(UUID userId, UUID connectionId,
            Map<String, Object> body) {
        Connection connection = findFor(userId, connectionId);

        Map<String, String> errors = unknownFields(body, ANSWER_FIELDS);
        UUID proposalId = null;
        Object rawId = body.get("proposalId");
        if (rawId == null) {
            errors.put("proposalId", "required");
        } else {
            try {
                proposalId = UUID.fromString(String.valueOf(rawId).trim());
            } catch (IllegalArgumentException e) {
                errors.put("proposalId", "expected a proposal id");
            }
        }
        String answer = body.get("status") == null ? null
                : String.valueOf(body.get("status")).trim().toUpperCase();
        if (!"CONFIRMED".equals(answer) && !"CANCELLED".equals(answer)) {
            errors.put("status", "expected CONFIRMED or CANCELLED");
        }
        if (!errors.isEmpty()) {
            throw ApiException.fields(errors);
        }

        try {
            if (answer.equals("CONFIRMED")) {
                return connection.confirmSchedule(proposalId, userId, clock.instant()).toView();
            }
            connection.cancelSchedule(proposalId, userId);
            return null;
        } catch (IllegalCallerException e) {
            throw ApiException.forbidden(e.getMessage());
        } catch (IllegalStateException e) {
            throw ApiException.conflict(e.getMessage());
        }
    }

    /**
     * The current proposal or confirmed time, for either participant.
     *
     * @throws ApiException 404 if the connection does not exist, the caller is not
     *     on it, or nothing is scheduled
     */
    public Map<String, Object> viewSchedule(UUID userId, UUID connectionId) {
        SessionProposal schedule = findFor(userId, connectionId).getSchedule();
        if (schedule == null) {
            throw ApiException.notFound("no session is scheduled on this connection");
        }
        return schedule.toView();
    }

    private static Map<String, String> unknownFields(Map<String, Object> body, Set<String> allowed) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (String field : body.keySet()) {
            if (!allowed.contains(field)) {
                errors.put(field, "not a field you can set here");
            }
        }
        return errors;
    }

    /** ISO 8601 with an offset or a Z; null when it is neither. */
    private static Instant parseInstant(String text) {
        try {
            return OffsetDateTime.parse(text.trim()).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Map<String, Object> withSessions(Map<String, Object> view, Connection connection) {
        Map<String, Object> copy = new LinkedHashMap<>(view);
        copy.put("sessions", sessionViews(connection));
        return copy;
    }
    
    private static List<Map<String, Object>> sessionViews(Connection connection) {
        List<Map<String, Object>> views = new ArrayList<>();
        for (SessionLogEntry entry : connection.getSessions()) {
            views.add(sessionView(entry));
        }
        return views;
    }
    
    private static Map<String, Object> sessionView(SessionLogEntry entry) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", entry.getId());
        view.put("heldOn", entry.getHeldOn());
        view.put("summary", entry.getSummary());
        view.put("loggedBy", entry.getLoggedBy());
        view.put("loggedAt", entry.getLoggedAt());
        return view;
    }

    /** 403 if the move belongs to the other side, 409 if it is not allowed or the mentor is full. */
    private static void checkMove(Connection connection, ConnectionStatus next, UUID userId,
            MentorProfile mentor) {
        ConnectionStatus from = connection.getStatus();
        boolean actorIsMentor = connection.isMentor(userId);
        if (!Connection.isAllowed(from, next, actorIsMentor)) {
            if (Connection.isAllowed(from, next, !actorIsMentor)) {
                throw ApiException.forbidden("only the " + (actorIsMentor ? "student" : "mentor")
                        + " can move this connection from " + from + " to " + next);
            }
            throw ApiException.conflict("a " + from + " connection cannot become " + next);
        }
        if (next == ConnectionStatus.ACTIVE && !mentor.hasCapacity()) {
            throw ApiException.conflict("the mentor has no room for another mentee");
        }
    }

    /** Not-on-it reads as not-found, so ids cannot be probed for other people's connections. */
    private Connection findFor(UUID userId, UUID connectionId) {
        Connection connection = data.findConnection(connectionId);
        if (!connection.isParticipant(userId)) {
            throw ApiException.notFound("no connection " + connectionId);
        }
        return connection;
    }

    private static ConnectionStatus toStatus(Object value) {
        String name = String.valueOf(value).trim().toUpperCase().replaceAll("[\\s-]+", "_");
        for (ConnectionStatus status : ConnectionStatus.values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return null;
    }
}
