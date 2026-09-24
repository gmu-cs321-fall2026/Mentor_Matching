/**
 * Industries a mentor works in and a student targets.
 *
 * <p>Shared by {@link MentorProfile} and {@link StudentMatchProfile} (ADR-002).
 * If Shared Core ends up owning the mentor's industry, it must use these same
 * values or matching stops being a set overlap.
 *
 * <p>TODO: placeholder list. The agreed values are an open question in
 * /docs/architecture.md and need sign-off from Shared Core and Subsystems 2 and 8.
 */
public enum Industry {
    TECHNOLOGY,
    FINANCE,
    HEALTHCARE,
    GOVERNMENT,
    DEFENSE,
    CONSULTING,
    EDUCATION,
    NONPROFIT,
    MANUFACTURING,
    MEDIA,
    RETAIL,
    OTHER
}
