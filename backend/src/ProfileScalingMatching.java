import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Calculates a mentor/student compatibility score in the range 0.0 to 1.0.
 *
 * <p>The score is a weighted average of scaled components. Each component is
 * scaled to 0-1 first, so the weights below are the only place that decides how
 * much each factor matters.
 *
 * <pre>
 *   guidance overlap     0.35   share of the student's wanted guidance areas the mentor offers
 *   industry overlap     0.25   share of the student's target industries the mentor covers
 *   role overlap         0.20   share of the student's target roles the mentor covers
 *   experience           0.10   min(years, 15) / 15
 *   available capacity   0.10   1 - currentStudents / maxStudents
 * </pre>
 * The weights sum to 1.0. The 0.05 originally planned for "same program" was
 * given to role overlap because the mentor class has no program data.
 *
 * <p>Design rules:
 * <ul>
 *   <li>Overlap is coverage of what the student asked for, not Jaccard, so a
 *       mentor is not penalized for offering extra expertise.</li>
 *   <li>If the student left an overlap category empty, that component is dropped
 *       and the remaining weights are renormalized, so the student is not
 *       penalized for a field they did not fill in.</li>
 *   <li>A mentor with no capacity scores 0 (hard filter, not a soft penalty).</li>
 *   <li>A student who fails {@link StudentMatchProfile#isValid()} scores 0.</li>
 * </ul>
 *
 * <p>ASSUMPTIONS (the current MentorProfile has no GuidanceArea/Industry sets):
 * <ol>
 *   <li>The mentor's free-text {@code expertiseAreas} stand in for guidance
 *       areas, industries and roles. Student enum values are converted to text
 *       (RESUME_REVIEW becomes "resume review") and compared to expertise text.</li>
 *   <li>{@code targetCompanies} and {@code preferSameProgram} are not scored:
 *       the mentor class has no employer history or program field.</li>
 *   <li>Scores are doubles from 0.0 to 1.0, not percentages.</li>
 * </ol>
 */
public final class MentorMatcher {

    public static final double WEIGHT_GUIDANCE = 0.35;
    public static final double WEIGHT_INDUSTRY = 0.25;
    public static final double WEIGHT_ROLE = 0.20;
    public static final double WEIGHT_EXPERIENCE = 0.10;
    public static final double WEIGHT_CAPACITY = 0.10;

    /** Years of experience at or above this count as a full experience score. */
    public static final int EXPERIENCE_CAP_YEARS = 15;

    private MentorMatcher() {
    }

    /**
     * @param student the student's matching preferences
     * @param mentor  the mentor being evaluated
     * @return compatibility from 0.0 (no match or not eligible) to 1.0 (ideal)
     * @throws IllegalArgumentException if either argument is null
     */
    public static double calculateCompatibility(StudentMatchProfile student, MentorProfile mentor) {
        if (student == null || mentor == null) {
            throw new IllegalArgumentException("student and mentor are required");
        }
        if (!student.isValid() || !mentor.hasCapacity()) {
            return 0.0;
        }

        Set<String> expertise = normalizedExpertise(mentor);

        double weightedSum = 0.0;
        double totalWeight = 0.0;

        Set<String> guidanceTerms = enumTerms(student.getGuidanceWanted());
        if (!guidanceTerms.isEmpty()) {
            weightedSum += WEIGHT_GUIDANCE * coverage(guidanceTerms, expertise);
            totalWeight += WEIGHT_GUIDANCE;
        }

        Set<String> industryTerms = enumTerms(student.getTargetIndustries());
        if (!industryTerms.isEmpty()) {
            weightedSum += WEIGHT_INDUSTRY * coverage(industryTerms, expertise);
            totalWeight += WEIGHT_INDUSTRY;
        }

        Set<String> roleTerms = textTerms(student.getTargetRoles());
        if (!roleTerms.isEmpty()) {
            weightedSum += WEIGHT_ROLE * coverage(roleTerms, expertise);
            totalWeight += WEIGHT_ROLE;
        }

        // Experience and capacity always apply.
        weightedSum += WEIGHT_EXPERIENCE * experienceScore(mentor.getYearsOfExperience());
        totalWeight += WEIGHT_EXPERIENCE;

        weightedSum += WEIGHT_CAPACITY * capacityScore(mentor);
        totalWeight += WEIGHT_CAPACITY;

        return clamp01(weightedSum / totalWeight);
    }

    /** Fraction of the student's terms that at least one mentor expertise matches. */
    static double coverage(Set<String> studentTerms, Set<String> mentorExpertise) {
        if (studentTerms.isEmpty()) {
            return 0.0;
        }
        int matched = 0;
        for (String term : studentTerms) {
            for (String expertise : mentorExpertise) {
                if (termsMatch(term, expertise)) {
                    matched++;
                    break;
                }
            }
        }
        return (double) matched / studentTerms.size();
    }

    /**
     * Two normalized terms match when they are equal or one appears in the other
     * as whole words. "java" matches "java developer" but not "javascript".
     */
    static boolean termsMatch(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        String paddedA = " " + a + " ";
        String paddedB = " " + b + " ";
        return paddedA.contains(paddedB) || paddedB.contains(paddedA);
    }

    /** Scales years to 0-1 against a fixed cap, so scores do not drift as mentors are added. */
    static double experienceScore(int years) {
        int bounded = Math.min(Math.max(years, 0), EXPERIENCE_CAP_YEARS);
        return (double) bounded / EXPERIENCE_CAP_YEARS;
    }

    /** 1.0 for an empty roster down toward 0.0 as the mentor fills up. Only called when hasCapacity() is true. */
    static double capacityScore(MentorProfile mentor) {
        int max = mentor.getMaxStudents();
        if (max <= 0) {
            return 0.0;
        }
        return clamp01(1.0 - (double) mentor.getCurrentStudentCount() / max);
    }

    /** Lower-cases, turns underscores/hyphens into spaces and collapses whitespace. */
    static String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("[_\\-\\s]+", " ");
    }

    private static Set<String> enumTerms(Set<? extends Enum<?>> values) {
        Set<String> terms = new HashSet<>();
        for (Enum<?> value : values) {
            terms.add(normalize(value.name()));
        }
        return terms;
    }

    private static Set<String> textTerms(List<String> values) {
        Set<String> terms = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                terms.add(normalize(value));
            }
        }
        return terms;
    }

    /** MentorProfile allows null entries and exposes its raw list, so read defensively. */
    private static Set<String> normalizedExpertise(MentorProfile mentor) {
        List<String> raw = mentor.getExpertiseAreas();
        return raw == null ? new HashSet<>() : textTerms(raw);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
