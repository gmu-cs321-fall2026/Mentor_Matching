import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Domain object for what a mentor offers and how much of it.
 *
 * <p>Holds {@code userId} and the attributes our service owns. Name, email,
 * program, graduation year, employer and title belong to Shared Core and are
 * merged in by {@link ProfileData} at read time; this class never stores or
 * inherits them (ADR-003).
 *
 * <p>Attributes in the Sprint 1 data model whose owner is still TBD
 * ({@code industry}, {@code hoursPerMonth}, {@code contactMethod}) live here
 * for now. If they move to Shared Core, only {@link ProfileData} changes.
 *
 * <p>Setters reject values that are wrong on their own. Rules that span fields
 * are checked by {@link #validate()}, so the API layer can return one 400 with
 * per-field detail instead of failing on the first bad field, and so a JSON
 * payload can be bound in any field order without a half-built profile
 * tripping a pairwise rule.
 *
 * @see StudentMatchProfile the other side of the match, built the same way
 */
public class MentorProfile {

    /** Fewest hours a month a mentor may pledge, per the data model. */
    public static final int MIN_HOURS_PER_MONTH = 1;

    /** Most hours a month a mentor may pledge, per the data model. */
    public static final int MAX_HOURS_PER_MONTH = 40;

    private final UUID userId;

    private final Set<GuidanceArea> guidanceAreas = EnumSet.noneOf(GuidanceArea.class);
    private final List<String> technicalDomains = new ArrayList<>();

    private Industry industry;
    private ContactMethod contactMethod;
    private int hoursPerMonth;
    private int maxMentees;
    private int activeMentees;
    private boolean acceptingMentees = true;

    /**
     * @param userId the Shared Core user this profile belongs to
     * @throws IllegalArgumentException if {@code userId} is null
     */
    public MentorProfile(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    /**
     * The industry the mentor works in, or null when not given.
     *
     * <p>Owner is TBD and likely Shared Core. Whoever owns it, the values stay
     * this enum so matching against a student's targets is a set overlap
     * (ADR-002).
     */
    public Industry getIndustry() {
        return industry;
    }

    /** @param industry the mentor's industry, or null to clear it */
    public void setIndustry(Industry industry) {
        this.industry = industry;
    }

    /** Kinds of help the mentor offers; same enum the student asks with (ADR-002). */
    public Set<GuidanceArea> getGuidanceAreas() {
        return Collections.unmodifiableSet(guidanceAreas);
    }

    /**
     * Replaces the guidance areas offered. Null entries are rejected;
     * duplicates collapse.
     *
     * @param areas the new set, or null/empty to clear it
     */
    public void setGuidanceAreas(Set<GuidanceArea> areas) {
        guidanceAreas.clear();
        if (areas == null) {
            return;
        }
        for (GuidanceArea area : areas) {
            if (area == null) {
                throw new IllegalArgumentException("guidanceAreas may not contain a null entry");
            }
            guidanceAreas.add(area);
        }
    }

    /**
     * Specific technical subjects the mentor can cover, free text per ADR-002.
     *
     * <p>Only meaningful alongside {@link GuidanceArea#TECHNICAL_DOMAIN};
     * {@link #validate()} enforces that pairing.
     */
    public List<String> getTechnicalDomains() {
        return Collections.unmodifiableList(technicalDomains);
    }

    /**
     * Replaces the technical domains. Entries are trimmed, blanks dropped and
     * case-insensitive duplicates collapsed to the first spelling given.
     *
     * @param domains the new list, or null/empty to clear it
     */
    public void setTechnicalDomains(List<String> domains) {
        ProfileText.replaceList(technicalDomains, domains, "technicalDomains");
    }

    /**
     * Hours a month the mentor pledges, or 0 when never set.
     *
     * <p>0 cannot be stored through the setter, so it only ever means unset,
     * which {@link #validate()} reports as a missing field.
     */
    public int getHoursPerMonth() {
        return hoursPerMonth;
    }

    /**
     * @param hoursPerMonth between {@value #MIN_HOURS_PER_MONTH} and
     *     {@value #MAX_HOURS_PER_MONTH}
     * @throws IllegalArgumentException if outside that range
     */
    public void setHoursPerMonth(int hoursPerMonth) {
        if (hoursPerMonth < MIN_HOURS_PER_MONTH || hoursPerMonth > MAX_HOURS_PER_MONTH) {
            throw new IllegalArgumentException("hoursPerMonth must be between "
                    + MIN_HOURS_PER_MONTH + " and " + MAX_HOURS_PER_MONTH);
        }
        this.hoursPerMonth = hoursPerMonth;
    }

    /** How the mentor prefers to meet, or null when not given. */
    public ContactMethod getContactMethod() {
        return contactMethod;
    }

    /** @param contactMethod the preferred method, or null to clear it */
    public void setContactMethod(ContactMethod contactMethod) {
        this.contactMethod = contactMethod;
    }

    /** How many mentees the mentor will take in total; the capacity matching respects. */
    public int getMaxMentees() {
        return maxMentees;
    }

    /**
     * Sets the capacity. The rule that it may not fall below
     * {@link #getActiveMentees()} is checked by {@link #validate()}, not here,
     * so a profile update can raise both numbers in either order.
     *
     * @param maxMentees zero or more
     * @throws IllegalArgumentException if negative
     */
    public void setMaxMentees(int maxMentees) {
        if (maxMentees < 0) {
            throw new IllegalArgumentException("maxMentees may not be negative");
        }
        this.maxMentees = maxMentees;
    }

    /** How many mentees the mentor currently has. */
    public int getActiveMentees() {
        return activeMentees;
    }

    /**
     * Sets the current mentee count. Owned by connections in Sprint 3, not by
     * the profile form, so the API layer should not bind this from mentor input.
     *
     * @param activeMentees zero or more
     * @throws IllegalArgumentException if negative
     */
    public void setActiveMentees(int activeMentees) {
        if (activeMentees < 0) {
            throw new IllegalArgumentException("activeMentees may not be negative");
        }
        this.activeMentees = activeMentees;
    }

    /**
     * True when the mentor is open to new mentees. Defaults to true on a new
     * profile; turning it off pauses matching without deleting the profile.
     */
    public boolean isAcceptingMentees() {
        return acceptingMentees;
    }

    public void setAcceptingMentees(boolean acceptingMentees) {
        this.acceptingMentees = acceptingMentees;
    }

    /**
     * True when this mentor can take another mentee right now: not paused, and
     * not yet at capacity.
     *
     * <p>The filter behind {@code GET /mentors} and, in Sprint 3, the check
     * matching makes before proposing a mentor.
     */
    public boolean hasCapacity() {
        return acceptingMentees && activeMentees < maxMentees;
    }

    /**
     * Checks the rules that span fields, so the caller can report them together.
     *
     * <ul>
     *   <li>At least one guidance area, or there is nothing to match on
     *   <li>Technical domains and {@link GuidanceArea#TECHNICAL_DOMAIN} go
     *       together: neither is allowed without the other
     *   <li>Hours a month set and within range
     *   <li>A contact method chosen
     *   <li>Capacity not below the current mentee count
     * </ul>
     *
     * @return field name to message, empty when the profile is valid; iteration
     *     order is stable so the 400 body reads the same every time
     */
    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();

        if (guidanceAreas.isEmpty()) {
            errors.put("guidanceAreas", "offer at least one guidance area");
        } else if (guidanceAreas.contains(GuidanceArea.TECHNICAL_DOMAIN)
                && technicalDomains.isEmpty()) {
            errors.put("technicalDomains",
                    "list at least one technical domain when offering technical domain guidance");
        }

        if (!technicalDomains.isEmpty() && !guidanceAreas.contains(GuidanceArea.TECHNICAL_DOMAIN)) {
            errors.put("technicalDomains",
                    "technical domains apply only when technical domain guidance is offered");
        }

        if (hoursPerMonth < MIN_HOURS_PER_MONTH || hoursPerMonth > MAX_HOURS_PER_MONTH) {
            errors.put("hoursPerMonth", "choose between " + MIN_HOURS_PER_MONTH
                    + " and " + MAX_HOURS_PER_MONTH + " hours a month");
        }

        if (contactMethod == null) {
            errors.put("contactMethod", "choose a preferred contact method");
        }

        if (maxMentees < activeMentees) {
            errors.put("maxMentees", "capacity may not be below the current "
                    + activeMentees + " mentees");
        }

        return errors;
    }

    /** Convenience for callers that only need a yes or no. */
    public boolean isValid() {
        return validate().isEmpty();
    }

    /** Two profiles are the same profile when they describe the same user. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MentorProfile)) {
            return false;
        }
        return userId.equals(((MentorProfile) other).userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "MentorProfile{userId=" + userId
                + ", industry=" + industry
                + ", guidanceAreas=" + guidanceAreas
                + ", technicalDomains=" + technicalDomains
                + ", hoursPerMonth=" + hoursPerMonth
                + ", contactMethod=" + contactMethod
                + ", maxMentees=" + maxMentees
                + ", activeMentees=" + activeMentees
                + ", acceptingMentees=" + acceptingMentees
                + "}";
    }
}
