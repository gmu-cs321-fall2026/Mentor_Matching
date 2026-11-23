import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link EditProfile}, behind {@code PUT /mentors/profile} and
 * {@code PUT /students/match-profile}: a partial update, limited to the
 * caller's role, that changes nothing when it is refused.
 */
class EditProfileTest {

    private final EditProfile edit = new EditProfile();
    private InMemoryProfileData data;
    private UUID mentor;
    private UUID student;

    @BeforeEach
    void setUp() {
        data = new InMemoryProfileData(new SharedCoreClient(null));

        MentorProfile mentorProfile = new MentorProfile(UUID.randomUUID());
        ProfileBinding.apply(mentorProfile, Map.of("industry", "TECHNOLOGY",
                "guidanceAreas", List.of("RESUME_REVIEW"), "hoursPerMonth", 4L,
                "contactMethod", "EMAIL", "maxMentees", 2L));
        data.saveMentor(mentorProfile);
        mentor = mentorProfile.getUserId();

        StudentMatchProfile studentProfile = new StudentMatchProfile(UUID.randomUUID());
        ProfileBinding.apply(studentProfile, Map.of("targetIndustries", List.of("TECHNOLOGY")));
        data.saveStudent(studentProfile);
        student = studentProfile.getUserId();
    }

    @Test
    void aMentorUpdateChangesOnlyTheFieldsSent() {
        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) edit.editProfile(data, mentor, "Mentor",
                Map.of("hoursPerMonth", 10L));
        assertEquals(10, view.get("hoursPerMonth"));
        assertEquals(10, data.findMentor(mentor).getHoursPerMonth());
        assertEquals(Industry.TECHNOLOGY, data.findMentor(mentor).getIndustry());
        assertEquals(ContactMethod.EMAIL, data.findMentor(mentor).getContactMethod());
    }

    @Test
    void aStudentUpdateChangesOnlyTheFieldsSent() {
        edit.editProfile(data, student, "Student", Map.of("targetRoles", List.of("Analyst")));
        StudentMatchProfile saved = data.findStudent(student);
        assertEquals(List.of("Analyst"), saved.getTargetRoles());
        assertEquals(Set.of(Industry.TECHNOLOGY), saved.getTargetIndustries());
    }

    @Test
    void theRoleIsMatchedWithoutCase() {
        edit.editProfile(data, mentor, "mentor", Map.of("maxMentees", 3L));
        assertEquals(3, data.findMentor(mentor).getMaxMentees());
    }

    @Test
    void fieldsOfTheOtherRoleAreRefusedByName() {
        Map<String, Object> detail = fieldErrors(() -> edit.editProfile(data, student, "Student",
                Map.of("maxMentees", 5L, "targetRoles", List.of("Analyst"))));
        assertEquals(Map.of("maxMentees", "cannot be edited by the Student role"), detail);
        // Refused as a whole: the allowed field was not applied either.
        assertEquals(List.of(), data.findStudent(student).getTargetRoles());

        detail = fieldErrors(() -> edit.editProfile(data, mentor, "Mentor",
                Map.of("targetIndustries", List.of("FINANCE"))));
        assertEquals(Set.of("targetIndustries"), detail.keySet());
    }

    @Test
    void editingAProfileThatDoesNotExistIs404() {
        ApiException e = assertThrows(ApiException.class, () -> edit.editProfile(
                data, UUID.randomUUID(), "Mentor", Map.of("hoursPerMonth", 5L)));
        assertEquals(404, e.status());
    }

    @Test
    void aBadValueChangesNothingEvenInTheFieldsThatWereFine() {
        fieldErrors(() -> edit.editProfile(data, mentor, "Mentor",
                Map.of("hoursPerMonth", 12L, "industry", "NOT_AN_INDUSTRY")));
        assertEquals(4, data.findMentor(mentor).getHoursPerMonth());
        assertEquals(Industry.TECHNOLOGY, data.findMentor(mentor).getIndustry());
    }

    @Test
    void anEditThatLeavesTheProfileInvalidChangesNothing() {
        // Each value is fine alone, but a mentor must offer at least one guidance area.
        Map<String, Object> detail = fieldErrors(() -> edit.editProfile(data, mentor, "Mentor",
                Map.of("guidanceAreas", List.of(), "hoursPerMonth", 9L)));
        assertEquals(Set.of("guidanceAreas"), detail.keySet());
        assertEquals(Set.of(GuidanceArea.RESUME_REVIEW), data.findMentor(mentor).getGuidanceAreas());
        assertEquals(4, data.findMentor(mentor).getHoursPerMonth());

        fieldErrors(() -> edit.editProfile(data, student, "Student",
                Map.of("targetIndustries", List.of())));
        assertEquals(Set.of(Industry.TECHNOLOGY), data.findStudent(student).getTargetIndustries());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fieldErrors(Runnable call) {
        ApiException e = assertThrows(ApiException.class, call::run);
        assertEquals(400, e.status());
        return (Map<String, Object>) e.body().get("detail");
    }
}
