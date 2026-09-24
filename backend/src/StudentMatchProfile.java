import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Domain object for a student's matching preferences.
 *
 * <p>Holds {@code userId} and the attributes our service owns. Name, email,
 * program and graduation year belong to Shared Core and are merged in by
 * {@link ProfileData} at read time; this class never stores or inherits them
 * (ADR-003).
 *
 * <p>Attributes in the Sprint 1 data model whose owner is still TBD
 * ({@code targetIndustries}, {@code targetRoles}) live here for now. If they
 * move to Shared Core, only {@link ProfileData} changes.
 *
 * <p>Setters reject values that are wrong on their own. Rules that span fields
 * are checked by {@link #validate()}, so the API layer can return one 400 with
 * per-field detail instead of failing on the first bad field.
 */
public class StudentMatchProfile {

    /** Most entries a free-text list may hold. */
    public static final int MAX_LIST_ENTRIES = 10;

    /** Longest a single free-text entry may be. */
    public static final int MAX_ENTRY_LENGTH = 100;

    private final UUID userId;

    private final Set<Industry> targetIndustries = EnumSet.noneOf(Industry.class);
    private final Set<GuidanceArea> guidanceWanted = EnumSet.noneOf(GuidanceArea.class);
    private final List<String> targetRoles = new ArrayList<>();
    private final List<String> targetCompanies = new ArrayList<>();

    private boolean preferSameProgram;

    /**
     * @param userId the Shared Core user this profile belongs to
     * @throws IllegalArgumentException if {@code userId} is null
     */
    public StudentMatchProfile(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    /** Industries the student wants to move into. */
    public Set<Industry> getTargetIndustries() {
        return Collections.unmodifiableSet(targetIndustries);
    }

    /**
     * Replaces the target industries. Null entries are rejected; duplicates collapse.
     *
     * @param industries the new set, or null/empty to clear it
     */
    public void setTargetIndustries(Set<Industry> industries) {
        targetIndustries.clear();
        if (industries == null) {
            return;
        }
        for (Industry industry : industries) {
            if (industry == null) {
                throw new IllegalArgumentException("targetIndustries may not contain a null entry");
            }
            targetIndustries.add(industry);
        }
    }

    /** Kinds of help the student is asking for; same enum the mentor offers (ADR-002). */
    public Set<GuidanceArea> getGuidanceWanted() {
        return Collections.unmodifiableSet(guidanceWanted);
    }

    /**
     * Replaces the guidance areas wanted. Null entries are rejected; duplicates collapse.
     *
     * @param areas the new set, or null/empty to clear it
     */
    public void setGuidanceWanted(Set<GuidanceArea> areas) {
        guidanceWanted.clear();
        if (areas == null) {
            return;
        }
        for (GuidanceArea area : areas) {
            if (area == null) {
                throw new IllegalArgumentException("guidanceWanted may not contain a null entry");
            }
            guidanceWanted.add(area);
        }
    }

    /** Job titles the student is aiming for, free text per ADR-002. */
    public List<String> getTargetRoles() {
        return Collections.unmodifiableList(targetRoles);
    }

    /**
     * Replaces the target roles. Entries are trimmed, blanks dropped and
     * case-insensitive duplicates collapsed to the first spelling given.
     *
     * @param roles the new list, or null/empty to clear it
     */
    public void setTargetRoles(List<String> roles) {
        replaceTextList(targetRoles, roles, "targetRoles");
    }

    /** Employers the student would like a mentor to have worked at. */
    public List<String> getTargetCompanies() {
        return Collections.unmodifiableList(targetCompanies);
    }

    /**
     * Replaces the target companies, normalized the same way as target roles.
     *
     * @param companies the new list, or null/empty to clear it
     */
    public void setTargetCompanies(List<String> companies) {
        replaceTextList(targetCompanies, companies, "targetCompanies");
    }

    /** True when the student would rather match a mentor from their own degree program. */
    public boolean isPreferSameProgram() {
        return preferSameProgram;
    }

    public void setPreferSameProgram(boolean preferSameProgram) {
        this.preferSameProgram = preferSameProgram;
    }

    /**
     * Checks the rules that span fields, so the caller can report them together.
     *
     * <p>Sprint 1 has one: a profile needs at least one target industry or one
     * guidance area, otherwise matching has nothing to work from.
     *
     * @return field name to message, empty when the profile is valid; iteration
     *     order is stable so the 400 body reads the same every time
     */
    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (targetIndustries.isEmpty() && guidanceWanted.isEmpty()) {
            String message = "choose at least one target industry or guidance area";
            errors.put("targetIndustries", message);
            errors.put("guidanceWanted", message);
        }
        return errors;
    }

    /** Convenience for callers that only need a yes or no. */
    public boolean isValid() {
        return validate().isEmpty();
    }

    /**
     * Trims, drops blanks and collapses case-insensitive duplicates, then
     * enforces the per-entry and per-list limits before touching {@code target}.
     */
    private static void replaceTextList(List<String> target, List<String> values, String field) {
        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value == null) {
                    throw new IllegalArgumentException(field + " may not contain a null entry");
                }
                String trimmed = value.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.length() > MAX_ENTRY_LENGTH) {
                    throw new IllegalArgumentException(
                            field + " entries may be at most " + MAX_ENTRY_LENGTH + " characters");
                }
                if (seen.add(trimmed.toLowerCase())) {
                    cleaned.add(trimmed);
                }
            }
        }
        if (cleaned.size() > MAX_LIST_ENTRIES) {
            throw new IllegalArgumentException(
                    field + " may list at most " + MAX_LIST_ENTRIES + " entries");
        }
        target.clear();
        target.addAll(cleaned);
    }

    /** Two profiles are the same profile when they describe the same user. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StudentMatchProfile)) {
            return false;
        }
        return userId.equals(((StudentMatchProfile) other).userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "StudentMatchProfile{userId=" + userId
                + ", targetIndustries=" + targetIndustries
                + ", guidanceWanted=" + guidanceWanted
                + ", targetRoles=" + targetRoles
                + ", targetCompanies=" + targetCompanies
                + ", preferSameProgram=" + preferSameProgram
                + "}";
    }
}
