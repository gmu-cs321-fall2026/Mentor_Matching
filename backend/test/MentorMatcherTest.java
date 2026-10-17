import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MentorMatcher} scoring and the ranking built on it in
 * {@link InMemoryProfileData#rankMentors}.
 *
 * <p>Expected scores are worked out by hand from the weights documented on
 * {@link MentorMatcher}: guidance 0.35, industry 0.25, role 0.20, capacity 0.10,
 * renormalized over the components the student filled in.
 */
class MentorMatcherTest {

    private static final double DELTA = 1e-9;

    @Test
    void betterMatchingMentorScoresHigher() {
        StudentMatchProfile student = student(
                Set.of(GuidanceArea.RESUME_REVIEW, GuidanceArea.MOCK_INTERVIEW),
                Set.of(Industry.TECHNOLOGY));

        MentorProfile strong = mentor(
                Set.of(GuidanceArea.RESUME_REVIEW, GuidanceArea.MOCK_INTERVIEW),
                Industry.TECHNOLOGY, 3, 0);
        MentorProfile weak = mentor(
                Set.of(GuidanceArea.RESUME_REVIEW), Industry.FINANCE, 3, 0);

        double strongScore = MentorMatcher.calculateCompatibility(student, strong);
        double weakScore = MentorMatcher.calculateCompatibility(student, weak);

        // Everything covered: (0.35 + 0.25 + 0.10) / 0.70
        assertEquals(1.0, strongScore, DELTA);
        // Half the guidance, wrong industry: (0.35 * 0.5 + 0 + 0.10) / 0.70
        assertEquals(0.275 / 0.70, weakScore, DELTA);
        assertTrue(strongScore > weakScore);
    }

    @Test
    void skipsUnfilledFieldsAndScalesCapacityByHowFullTheMentorIs() {
        // No target roles, so the role weight is dropped rather than scored as 0.
        StudentMatchProfile student = student(
                Set.of(GuidanceArea.RESUME_REVIEW, GuidanceArea.NEGOTIATION),
                Set.of(Industry.FINANCE));

        MentorProfile halfFull = mentor(
                Set.of(GuidanceArea.RESUME_REVIEW), Industry.FINANCE, 4, 2);

        // (0.35 * 0.5 + 0.25 * 1 + 0.10 * (1 - 2/4)) / (0.35 + 0.25 + 0.10)
        assertEquals(0.475 / 0.70, MentorMatcher.calculateCompatibility(student, halfFull), DELTA);
    }

    @Test
    void scoresZeroForNoOverlapOrNoCapacity() {
        StudentMatchProfile student = student(
                Set.of(GuidanceArea.MOCK_INTERVIEW), Set.of(Industry.HEALTHCARE));

        // Free capacity alone is not a match.
        MentorProfile unrelated = mentor(
                Set.of(GuidanceArea.NEGOTIATION), Industry.RETAIL, 5, 0);
        assertEquals(0.0, MentorMatcher.calculateCompatibility(student, unrelated));

        // A perfect fit is filtered out once the mentor is full...
        MentorProfile full = mentor(
                Set.of(GuidanceArea.MOCK_INTERVIEW), Industry.HEALTHCARE, 2, 2);
        assertEquals(0.0, MentorMatcher.calculateCompatibility(student, full));

        // ...or has paused new mentees.
        MentorProfile paused = mentor(
                Set.of(GuidanceArea.MOCK_INTERVIEW), Industry.HEALTHCARE, 2, 0);
        paused.setAcceptingMentees(false);
        assertEquals(0.0, MentorMatcher.calculateCompatibility(student, paused));

        // A student with nothing to match on fails isValid() and scores 0.
        StudentMatchProfile empty = new StudentMatchProfile(UUID.randomUUID());
        MentorProfile perfect = mentor(
                Set.of(GuidanceArea.MOCK_INTERVIEW), Industry.HEALTHCARE, 2, 0);
        assertEquals(0.0, MentorMatcher.calculateCompatibility(empty, perfect));
    }

    @Test
    void roleMatchingUsesWholeWordsNotSubstrings() {
        StudentMatchProfile student = student(Set.of(GuidanceArea.TECHNICAL_DOMAIN), Set.of());
        student.setTargetRoles(List.of("Java Developer"));

        MentorProfile javaMentor = mentor(Set.of(GuidanceArea.TECHNICAL_DOMAIN), null, 1, 0);
        javaMentor.setTechnicalDomains(List.of("Java"));
        MentorProfile javascriptMentor = mentor(Set.of(GuidanceArea.TECHNICAL_DOMAIN), null, 1, 0);
        javascriptMentor.setTechnicalDomains(List.of("JavaScript"));

        // "java" is a whole word of "java developer": (0.35 + 0.20 + 0.10) / 0.65
        assertEquals(1.0, MentorMatcher.calculateCompatibility(student, javaMentor), DELTA);
        // "javascript" only contains "java" as a substring, so the role part scores 0.
        assertEquals(0.45 / 0.65,
                MentorMatcher.calculateCompatibility(student, javascriptMentor), DELTA);
    }

    @Test
    void rankMentorsOrdersByScoreAndLeavesOutNonMatches() {
        InMemoryProfileData data = new InMemoryProfileData(new SharedCoreClient(null));
        StudentMatchProfile student = student(
                Set.of(GuidanceArea.RESUME_REVIEW, GuidanceArea.MOCK_INTERVIEW),
                Set.of(Industry.TECHNOLOGY));

        // Nobody saved yet: nothing to recommend.
        assertTrue(data.rankMentors(student).isEmpty());

        MentorProfile partial = mentor(Set.of(GuidanceArea.RESUME_REVIEW), Industry.FINANCE, 3, 0);
        MentorProfile best = mentor(
                Set.of(GuidanceArea.RESUME_REVIEW, GuidanceArea.MOCK_INTERVIEW),
                Industry.TECHNOLOGY, 3, 0);
        MentorProfile unrelated = mentor(Set.of(GuidanceArea.NEGOTIATION), Industry.RETAIL, 3, 0);
        MentorProfile full = mentor(
                Set.of(GuidanceArea.RESUME_REVIEW, GuidanceArea.MOCK_INTERVIEW),
                Industry.TECHNOLOGY, 1, 1);
        // Saved out of order on purpose.
        data.saveMentor(partial);
        data.saveMentor(unrelated);
        data.saveMentor(full);
        data.saveMentor(best);

        List<Map<String, Object>> ranked = data.rankMentors(student);

        assertEquals(2, ranked.size(), "zero-score and full mentors are left out");
        assertEquals(best.getUserId().toString(), ranked.get(0).get("userId"));
        assertEquals(1, ranked.get(0).get("rank"));
        assertEquals(partial.getUserId().toString(), ranked.get(1).get("userId"));
        assertEquals(2, ranked.get(1).get("rank"));
        assertTrue((double) ranked.get(0).get("compatibilityScore")
                > (double) ranked.get(1).get("compatibilityScore"));
    }

    private static StudentMatchProfile student(Set<GuidanceArea> guidance, Set<Industry> industries) {
        StudentMatchProfile student = new StudentMatchProfile(UUID.randomUUID());
        student.setGuidanceWanted(guidance);
        student.setTargetIndustries(industries);
        return student;
    }

    /** A mentor that also passes {@link MentorProfile#validate()}, so it can be saved. */
    private static MentorProfile mentor(Set<GuidanceArea> guidance, Industry industry,
                                        int maxMentees, int activeMentees) {
        MentorProfile mentor = new MentorProfile(UUID.randomUUID());
        mentor.setGuidanceAreas(guidance);
        mentor.setIndustry(industry);
        mentor.setHoursPerMonth(4);
        mentor.setContactMethod(ContactMethod.EMAIL);
        mentor.setMaxMentees(maxMentees);
        mentor.setActiveMentees(activeMentees);
        return mentor;
    }
}
