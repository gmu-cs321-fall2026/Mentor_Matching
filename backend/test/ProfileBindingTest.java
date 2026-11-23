import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link ProfileBinding}: request JSON to profile attributes, with one message
 * per bad field and every field attempted even after one fails.
 */
class ProfileBindingTest {

    /* ---------- mentor ---------- */

    @Test
    void appliesAFullMentorBody() {
        MentorProfile profile = new MentorProfile(UUID.randomUUID());
        ProfileBinding.apply(profile, Map.of(
                "industry", "TECHNOLOGY",
                "guidanceAreas", List.of("RESUME_REVIEW", "TECHNICAL_DOMAIN"),
                "technicalDomains", List.of("Distributed systems"),
                "hoursPerMonth", 6L,
                "contactMethod", "EMAIL",
                "maxMentees", 3L,
                "acceptingMentees", false));

        assertEquals(Industry.TECHNOLOGY, profile.getIndustry());
        assertEquals(Set.of(GuidanceArea.RESUME_REVIEW, GuidanceArea.TECHNICAL_DOMAIN),
                profile.getGuidanceAreas());
        assertEquals(List.of("Distributed systems"), profile.getTechnicalDomains());
        assertEquals(6, profile.getHoursPerMonth());
        assertEquals(ContactMethod.EMAIL, profile.getContactMethod());
        assertEquals(3, profile.getMaxMentees());
        assertFalse(profile.isAcceptingMentees());
    }

    @Test
    void enumNamesAreToleratedInAnyCaseWithSpacesOrHyphens() {
        MentorProfile profile = new MentorProfile(UUID.randomUUID());
        ProfileBinding.apply(profile, Map.of(
                "guidanceAreas", List.of("resume review", "Mock-Interview"),
                "contactMethod", " video call "));
        assertEquals(Set.of(GuidanceArea.RESUME_REVIEW, GuidanceArea.MOCK_INTERVIEW),
                profile.getGuidanceAreas());
        assertEquals(ContactMethod.VIDEO_CALL, profile.getContactMethod());
    }

    @Test
    void formStyleValuesAreAccepted() {
        // HTML forms send numbers and booleans as text, and a single choice as a string.
        MentorProfile profile = new MentorProfile(UUID.randomUUID());
        ProfileBinding.apply(profile, Map.of(
                "hoursPerMonth", " 8 ",
                "maxMentees", 2.0,
                "acceptingMentees", "TRUE",
                "guidanceAreas", "RESUME_REVIEW"));
        assertEquals(8, profile.getHoursPerMonth());
        assertEquals(2, profile.getMaxMentees());
        assertTrue(profile.isAcceptingMentees());
        assertEquals(Set.of(GuidanceArea.RESUME_REVIEW), profile.getGuidanceAreas());
    }

    @Test
    void nullClearsOptionalMentorAttributes() {
        MentorProfile profile = new MentorProfile(UUID.randomUUID());
        ProfileBinding.apply(profile, Map.of("industry", "FINANCE", "guidanceAreas",
                List.of("RESUME_REVIEW")));
        Map<String, Object> clear = new HashMap<>();
        clear.put("industry", null);
        clear.put("guidanceAreas", null);
        clear.put("technicalDomains", null);
        ProfileBinding.apply(profile, clear);
        assertEquals(null, profile.getIndustry());
        assertTrue(profile.getGuidanceAreas().isEmpty());
        assertTrue(profile.getTechnicalDomains().isEmpty());
    }

    @Test
    void everyBadMentorFieldIsReportedAtOnce() {
        Map<String, Object> body = new HashMap<>();
        body.put("industry", "UNDERWATER_BASKETRY");
        body.put("guidanceAreas", List.of("RESUME_REVIEW", "NOPE"));
        body.put("technicalDomains", Arrays.asList("ok", null));
        body.put("hoursPerMonth", 2.5);
        body.put("contactMethod", "carrier pigeon");
        body.put("maxMentees", "lots");
        body.put("acceptingMentees", "maybe");
        body.put("activeMentees", 4L);
        body.put("favouriteColour", "green");

        Map<String, Object> detail = fieldErrors(
                () -> ProfileBinding.apply(new MentorProfile(UUID.randomUUID()), body));

        assertEquals(Set.of("industry", "guidanceAreas", "technicalDomains", "hoursPerMonth",
                "contactMethod", "maxMentees", "acceptingMentees", "activeMentees",
                "favouriteColour"), detail.keySet());
        assertTrue(((String) detail.get("industry")).startsWith("expected one of "));
        assertTrue(((String) detail.get("industry")).contains("TECHNOLOGY"));
        assertEquals("a whole number is required", detail.get("hoursPerMonth"));
        assertEquals("a whole number is required", detail.get("maxMentees"));
        assertEquals("expected true or false", detail.get("acceptingMentees"));
        // activeMentees belongs to connections, so a client may not set it.
        assertEquals("not a field you can set here", detail.get("activeMentees"));
        assertEquals("not a field you can set here", detail.get("favouriteColour"));
    }

    @Test
    void domainRangesComeBackAsFieldErrors() {
        Map<String, Object> detail = fieldErrors(() -> ProfileBinding.apply(
                new MentorProfile(UUID.randomUUID()),
                Map.of("hoursPerMonth", 41L, "maxMentees", -1L)));
        assertEquals(Set.of("hoursPerMonth", "maxMentees"), detail.keySet());
        assertTrue(((String) detail.get("hoursPerMonth")).contains("between"));
    }

    @Test
    void nullIsNotAWholeNumberOrABoolean() {
        Map<String, Object> body = new HashMap<>();
        body.put("hoursPerMonth", null);
        body.put("acceptingMentees", null);
        Map<String, Object> detail = fieldErrors(
                () -> ProfileBinding.apply(new MentorProfile(UUID.randomUUID()), body));
        assertEquals(Set.of("hoursPerMonth", "acceptingMentees"), detail.keySet());
    }

    @Test
    void anEmptyBodyChangesNothing() {
        MentorProfile profile = new MentorProfile(UUID.randomUUID());
        ProfileBinding.apply(profile, Map.of());
        assertTrue(profile.getGuidanceAreas().isEmpty());
    }

    /* ---------- student ---------- */

    @Test
    void appliesAFullStudentBody() {
        StudentMatchProfile profile = new StudentMatchProfile(UUID.randomUUID());
        ProfileBinding.apply(profile, Map.of(
                "targetIndustries", List.of("TECHNOLOGY", "finance"),
                "targetRoles", List.of("Software Engineer"),
                "guidanceWanted", "RESUME_REVIEW",
                "targetCompanies", "Capital One",
                "preferSameProgram", "false"));

        assertEquals(Set.of(Industry.TECHNOLOGY, Industry.FINANCE), profile.getTargetIndustries());
        assertEquals(List.of("Software Engineer"), profile.getTargetRoles());
        assertEquals(Set.of(GuidanceArea.RESUME_REVIEW), profile.getGuidanceWanted());
        assertEquals(List.of("Capital One"), profile.getTargetCompanies());
        assertFalse(profile.isPreferSameProgram());
    }

    @Test
    void everyBadStudentFieldIsReportedAtOnce() {
        Map<String, Object> body = new HashMap<>();
        body.put("targetIndustries", Arrays.asList("TECHNOLOGY", null));
        body.put("guidanceWanted", List.of("WIZARDRY"));
        body.put("targetRoles", List.of("x".repeat(ProfileText.MAX_ENTRY_LENGTH + 1)));
        body.put("targetCompanies", Arrays.asList(null, "Acme"));
        body.put("preferSameProgram", 1L);
        body.put("maxMentees", 2L); // a mentor field

        Map<String, Object> detail = fieldErrors(
                () -> ProfileBinding.apply(new StudentMatchProfile(UUID.randomUUID()), body));

        assertEquals(Set.of("targetIndustries", "guidanceWanted", "targetRoles",
                "targetCompanies", "preferSameProgram", "maxMentees"), detail.keySet());
        assertEquals("entries may not be null", detail.get("targetIndustries"));
        assertEquals("entries may not be null", detail.get("targetCompanies"));
    }

    @Test
    void tooManyListEntriesIsAFieldError() {
        List<String> eleven = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k");
        Map<String, Object> detail = fieldErrors(() -> ProfileBinding.apply(
                new StudentMatchProfile(UUID.randomUUID()), Map.of("targetRoles", eleven)));
        assertEquals(Set.of("targetRoles"), detail.keySet());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fieldErrors(Runnable call) {
        ApiException e = assertThrows(ApiException.class, call::run);
        assertEquals(400, e.status());
        assertEquals("validation_failed", e.body().get("code"));
        return (Map<String, Object>) e.body().get("detail");
    }
}
