import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Normalization shared by the free-text lists on both profiles.
 *
 * <p>ADR-002 keeps free text only for roles, companies and technical domains.
 * Those lists are all cleaned the same way here so the two domain classes
 * cannot drift apart on what counts as a duplicate or an over-long entry.
 */
final class ProfileText {

    /** Most entries a free-text list may hold. */
    static final int MAX_LIST_ENTRIES = 10;

    /** Longest a single free-text entry may be. */
    static final int MAX_ENTRY_LENGTH = 100;

    private ProfileText() {
    }

    /**
     * Trims entries, drops blanks and collapses case-insensitive duplicates to
     * the first spelling given, then checks the length and count limits.
     *
     * <p>Returns a new list rather than mutating the caller's field, so a
     * rejected value leaves the profile untouched.
     *
     * @param values the submitted entries, or null to mean an empty list
     * @param field the attribute name, used in the exception message so the API
     *     layer can report it against the right form field
     * @return the cleaned entries, in the order they were given
     * @throws IllegalArgumentException if an entry is null, too long, or there
     *     are more than {@link #MAX_LIST_ENTRIES} of them
     */
    static List<String> normalizeList(List<String> values, String field) {
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
        return cleaned;
    }

    /**
     * Copies a submitted list into a profile's field after normalizing it.
     *
     * @param target the profile's own list, cleared and refilled in place
     * @param values the submitted entries, or null/empty to clear the field
     * @param field the attribute name for error messages
     */
    static void replaceList(List<String> target, List<String> values, String field) {
        List<String> cleaned = normalizeList(values, field);
        target.clear();
        target.addAll(cleaned);
    }
}
