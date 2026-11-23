import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Applies a partial profile update on behalf of the signed-in user.
 *
 * <p>Backs {@code PUT /mentors/profile} and {@code PUT /students/match-profile}.
 * The caller is already authenticated and role-checked by {@link MentoringApp};
 * this class decides which attributes that role may change and hands the rest
 * to the binder and the store.
 *
 * <p>Three things changed when this was wired up for Sprint 1:
 * <ol>
 *   <li>The editable sets are now {@link ProfileBinding#MENTOR_FIELDS} and
 *       {@link ProfileBinding#STUDENT_FIELDS} rather than short literal sets.
 *       The originals left out {@code industry}, {@code hoursPerMonth},
 *       {@code contactMethod}, {@code targetIndustries} and {@code targetRoles},
 *       which would have made those attributes unchangeable after create and
 *       failed the Sprint 1 goal of updating a profile end to end.</li>
 *   <li>Rejected fields raise an {@link ApiException} carrying one message per
 *       field, so the caller gets a 400 in the shared error format instead of a
 *       500 from an uncaught {@code IllegalArgumentException}.</li>
 *   <li>{@code profile.applyEdits(body)} became
 *       {@code ProfileBinding.apply(profile, body)}, keeping JSON maps out of
 *       the domain classes.</li>
 * </ol>
 *
 * <p>A refused edit changes nothing (Sprint 5). The body is applied to a draft
 * copy and the draft is validated; only then are the editable attributes
 * copied onto the stored profile. Before this, a request with one bad field,
 * or one that left the profile invalid (no guidance areas), returned 400 but
 * had already changed the stored profile. The stored object is updated rather
 * than replaced, so an {@code activeMentees} change made by an accept at the
 * same moment is never overwritten.
 */
public class EditProfile {

    /**
     * @param data the store to read and write through
     * @param userId the signed-in user, from the validated token
     * @param role that user's role, as Shared Core reported it
     * @param body the attributes to change; absent attributes are left alone
     * @return the merged profile after the edit
     * @throws ApiException 400 for a field this role may not set or a value the
     *     domain rejects, 404 if there is no profile to edit yet
     */
    public Object editProfile(ProfileData data, UUID userId, String role, Map<String, Object> body) {
        boolean isMentor = "Mentor".equalsIgnoreCase(role);
        Set<String> editable = isMentor
                ? ProfileBinding.MENTOR_FIELDS
                : ProfileBinding.STUDENT_FIELDS;

        Map<String, String> rejected = new LinkedHashMap<>();
        for (String field : body.keySet()) {
            if (!editable.contains(field)) {
                rejected.put(field, "cannot be edited by the " + role + " role");
            }
        }
        if (!rejected.isEmpty()) {
            throw ApiException.fields(rejected);
        }

        if (isMentor) {
            MentorProfile profile = data.findMentor(userId);
            MentorProfile draft = new MentorProfile(userId);
            copyEditable(profile, draft);
            draft.setActiveMentees(profile.getActiveMentees());
            ProfileBinding.apply(draft, body);
            require(draft.validate());
            copyEditable(draft, profile);
            return data.updateMentor(profile);
        }
        StudentMatchProfile profile = data.findStudent(userId);
        StudentMatchProfile draft = new StudentMatchProfile(userId);
        copyEditable(profile, draft);
        ProfileBinding.apply(draft, body);
        require(draft.validate());
        copyEditable(draft, profile);
        return data.updateStudent(profile);
    }

    /** Every attribute a mentor may edit; {@code activeMentees} belongs to connections. */
    private static void copyEditable(MentorProfile from, MentorProfile to) {
        to.setIndustry(from.getIndustry());
        to.setGuidanceAreas(from.getGuidanceAreas());
        to.setTechnicalDomains(from.getTechnicalDomains());
        to.setHoursPerMonth(from.getHoursPerMonth());
        to.setContactMethod(from.getContactMethod());
        to.setMaxMentees(from.getMaxMentees());
        to.setAcceptingMentees(from.isAcceptingMentees());
    }

    private static void copyEditable(StudentMatchProfile from, StudentMatchProfile to) {
        to.setTargetIndustries(from.getTargetIndustries());
        to.setGuidanceWanted(from.getGuidanceWanted());
        to.setTargetRoles(from.getTargetRoles());
        to.setTargetCompanies(from.getTargetCompanies());
        to.setPreferSameProgram(from.isPreferSameProgram());
    }

    private static void require(Map<String, String> fieldErrors) {
        if (!fieldErrors.isEmpty()) {
            throw ApiException.fields(fieldErrors);
        }
    }
}
