/**
 * Kinds of help a mentor offers and a student asks for.
 *
 * <p>Shared by {@link MentorProfile} and {@link StudentMatchProfile} so Sprint 3
 * matching is a set intersection with an explainable reason (ADR-002).
 * The final value list still has to be agreed with Shared Core and
 * Subsystems 2 and 8; adding a value is a code change plus a contract update.
 */
public enum GuidanceArea {
    RESUME_REVIEW,
    MOCK_INTERVIEW,
    INDUSTRY_INSIGHT,
    NEGOTIATION,
    GENERAL_CAREER,
    /** Paired with free-text technical domains on the mentor side. */
    TECHNICAL_DOMAIN
}
