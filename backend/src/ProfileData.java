import java.util.Map;
import java.util.UUID;
import java.util.List;

/**
 * Save, find, update and list profiles.
 *
 * <p>An interface, per ADR-001, so controllers and domain classes depend only
 * on this contract. {@link InMemoryProfileData} is the Sprint 1 implementation;
 * moving to PostgreSQL when the attribute split with Shared Core is settled
 * means writing a second implementation and changing nothing above it.
 *
 * <p>Implementations are the only place that knows which attributes come from
 * Shared Core and which we store ourselves. Everything above sees one merged
 * profile.
 *
 * <p>Every method that writes must reject an invalid profile, so nothing can
 * reach the store in a state {@link MentorProfile#validate()} or
 * {@link StudentMatchProfile#validate()} would refuse — including through
 * {@link EditProfile}, which edits and saves without re-validating itself.
 */
interface ProfileData {

    /**
     * Stores a new mentor profile.
     *
     * @return the merged profile, ours plus Shared Core's
     * @throws ApiException 400 if invalid, 409 if the mentor already has one
     */
    Map<String, Object> saveMentor(MentorProfile profile);

    /**
     * @throws ApiException 404 if the mentor has no profile yet
     */
    MentorProfile findMentor(UUID userId);

    /**
     * Stores changes to an existing mentor profile.
     *
     * @return the merged profile
     * @throws ApiException 400 if the edits leave it invalid
     */
    Map<String, Object> updateMentor(MentorProfile profile);

    /**
     * Stores a new student matching profile.
     *
     * @return the merged profile
     * @throws ApiException 400 if invalid, 409 if the student already has one
     */
    Map<String, Object> saveStudent(StudentMatchProfile profile);

    /**
     * @throws ApiException 404 if the student has no profile yet
     */
    StudentMatchProfile findStudent(UUID userId);

    /**
     * Stores changes to an existing student matching profile.
     *
     * @return the merged profile
     * @throws ApiException 400 if the edits leave it invalid
     */
    Map<String, Object> updateStudent(StudentMatchProfile profile);

    /**
     * The mentor directory behind {@code GET /mentors}.
     *
     * <p>Filters combine with AND; a null filter is not applied. Ordering must
     * be stable between calls so paging does not repeat or skip a mentor.
     *
     * @param industry keep only mentors in this industry, or null for any
     * @param guidance keep only mentors offering this guidance area, or null for any
     * @param withCapacityOnly keep only mentors who can take another mentee now
     * @param page 1-based page number
     * @param size page size
     * @return {@code items}, {@code page}, {@code size}, {@code total} and {@code totalPages}
     */
    Map<String, Object> listMentors(Industry industry, GuidanceArea guidance,
            boolean withCapacityOnly, int page, int size);

    /** Our mentor attributes merged under Shared Core's base profile. */
    Map<String, Object> mentorView(MentorProfile profile);

    /** Our student attributes merged under Shared Core's base profile. */
    Map<String, Object> studentView(StudentMatchProfile profile);
    /**
     * Recommendations behind {@code GET /mentors/recommendations}: mentors with
     * room, best match first, leaving out anyone who scores 0.
     *
     * @return each mentor's view plus {@code rank} and {@code compatibilityScore}
     */
    List<Map<String, Object>> rankMentors(StudentMatchProfile student);

    /**
     * Stores a new connection request.
     *
     * @return the connection's view
     */
    Map<String, Object> saveConnection(Connection connection);

    /**
     * @throws ApiException 404 if there is no such connection
     */
    Connection findConnection(UUID connectionId);

    /**
     * Stores changes to an existing connection.
     *
     * @return the connection's view
     */
    Map<String, Object> updateConnection(Connection connection);

    /** True when this student and mentor already have a pending or active connection. */
    boolean hasOpenConnection(UUID studentId, UUID mentorId);

    /** Every connection the user is on, as student or mentor, newest first. */
    List<Map<String, Object>> listConnections(UUID userId);

    /** A connection with both people's names from Shared Core. */
    Map<String, Object> connectionView(Connection connection);
}
