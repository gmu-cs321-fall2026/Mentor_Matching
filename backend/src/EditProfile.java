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
            ProfileBinding.apply(profile, body);
            return data.updateMentor(profile);
        }
        StudentMatchProfile profile = data.findStudent(userId);
        ProfileBinding.apply(profile, body);
        return data.updateStudent(profile);
    }
}
