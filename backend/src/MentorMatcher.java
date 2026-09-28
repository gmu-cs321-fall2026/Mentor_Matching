import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Calculates a mentor/student compatibility score in the range 0.0 to 1.0.
 *
 * <p><b>Sprint 3 work, not Sprint 1.</b> It lives here as a head start and is
 * not wired to any endpoint; matching, recommendations and connections are all
 * listed as out of scope for Sprint 1.
 *
 * <p>The score is a weighted average of scaled components. Each component is
 * scaled to 0-1 first, so the weights below are the only place that decides how
 * much each factor matters.
 *
 * <pre>
 *   guidance overlap     0.35   share of the student's wanted guidance areas the mentor offers
 *   industry overlap     0.25   whether the mentor's industry is one the student is targeting
 *   role overlap         0.20   share of the student's target roles the mentor's domains cover
 *   available capacity   0.10   1 - activeMentees / maxMentees
 * </pre>
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
 * <p>CHANGED when Sprint 1 landed: the first draft scored a mentor's free-text
 * {@code expertiseAreas} against text converted from the student's enums, and
 * read {@code getYearsOfExperience()}, {@code getMaxStudents()} and
 * {@code getCurrentStudentCount()}. None of those exist on
 * {@link MentorProfile}, so the file did not compile. It now uses the data model
 * in /docs/architecture.md:
 * <ol>
 *   <li>Guidance and industry are compared as enum set overlap rather than text.
 *       ADR-002 chose shared enums precisely so this is an intersection with an
 *       explainable reason, and keeps free text out of scored categories.</li>
 *   <li>Role overlap compares the student's {@code targetRoles} against the
 *       mentor's {@code technicalDomains}, the two free-text fields ADR-002 does
 *       allow, still using the whole-word {@link #termsMatch} rule.</li>
 *   <li>The 0.10 experience component is gone: years of experience is not an
 *       attribute in the mentor data model. The weights now sum to 0.90 and the
 *       renormalization already in {@code calculateCompatibility} handles it, so
 *       scores still span 0.0 to 1.0. If the team wants experience scored, it
 *       has to be added to the data model and the wiki contract first.</li>
 *   <li>Capacity reads {@code maxMentees} and {@code activeMentees}.</li>
 * </ol>
 * The weighting scheme, the coverage-not-Jaccard choice, the renormalization and
 * the hard capacity filter are all as first written.
 */
public final class MentorMatcher {

    public static final double WEIGHT_GUIDANCE = 0.35;
    public static final double WEIGHT_INDUSTRY = 0.25;
    public static final double WEIGHT_ROLE = 0.20;
    public static final double WEIGHT_CAPACITY = 0.10;

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

        double weightedSum = 0.0;
        double totalWeight = 0.0;

        Set<GuidanceArea> guidanceWanted = student.getGuidanceWanted();
        if (!guidanceWanted.isEmpty()) {
            weightedSum += WEIGHT_GUIDANCE * enumCoverage(guidanceWanted, mentor.getGuidanceAreas());
            totalWeight += WEIGHT_GUIDANCE;
        }

        Set<Industry> targetIndustries = student.getTargetIndustries();
        if (!targetIndustries.isEmpty()) {
            boolean covered = mentor.getIndustry() != null
                    && targetIndustries.contains(mentor.getIndustry());
            weightedSum += WEIGHT_INDUSTRY * (covered ? 1.0 : 0.0);
            totalWeight += WEIGHT_INDUSTRY;
        }

        Set<String> roleTerms = textTerms(student.getTargetRoles());
        if (!roleTerms.isEmpty()) {
            weightedSum += WEIGHT_ROLE * coverage(roleTerms, textTerms(mentor.getTechnicalDomains()));
            totalWeight += WEIGHT_ROLE;
        }
        if (weightedSum <= 0.0) {
            return 0.0;
        }

        // Capacity always applies.
        weightedSum += WEIGHT_CAPACITY * capacityScore(mentor);
        totalWeight += WEIGHT_CAPACITY;

        return clamp01(weightedSum / totalWeight);
    }

    /** Fraction of the student's wanted areas the mentor also offers (ADR-002 set overlap). */
    static <E extends Enum<E>> double enumCoverage(Set<E> wanted, Set<E> offered) {
        if (wanted.isEmpty()) {
            return 0.0;
        }
        int matched = 0;
        for (E value : wanted) {
            if (offered.contains(value)) {
                matched++;
            }
        }
        return (double) matched / wanted.size();
    }

    /** Fraction of the student's terms that at least one mentor term matches. */
    static double coverage(Set<String> studentTerms, Set<String> mentorTerms) {
        if (studentTerms.isEmpty()) {
            return 0.0;
        }
        int matched = 0;
        for (String term : studentTerms) {
            for (String mentorTerm : mentorTerms) {
                if (termsMatch(term, mentorTerm)) {
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

    /** 1.0 for an empty roster down toward 0.0 as the mentor fills up. Only called when hasCapacity() is true. */
    static double capacityScore(MentorProfile mentor) {
        int max = mentor.getMaxMentees();
        if (max <= 0) {
            return 0.0;
        }
        return clamp01(1.0 - (double) mentor.getActiveMentees() / max);
    }

    /** Lower-cases, turns underscores/hyphens into spaces and collapses whitespace. */
    static String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("[_\\-\\s]+", " ");
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

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
