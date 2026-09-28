import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Turns request JSON into domain objects, collecting one message per bad field.
 *
 * <p>This is the "validates input" half of the API layer, kept out of the
 * domain classes so they never learn about JSON maps. Only fields present in
 * the body are touched, so the same code serves a create and a partial update.
 *
 * <p>Every field is attempted even after one fails, so a caller fixing a form
 * sees all its errors at once rather than one per round trip.
 */
final class ProfileBinding {

    /** Mentor attributes a client may send. {@code activeMentees} is absent on purpose: connections own it in Sprint 3. */
    static final Set<String> MENTOR_FIELDS = Set.of(
            "industry", "guidanceAreas", "technicalDomains", "hoursPerMonth",
            "contactMethod", "maxMentees", "acceptingMentees");

    /** Student attributes a client may send. */
    static final Set<String> STUDENT_FIELDS = Set.of(
            "targetIndustries", "targetRoles", "guidanceWanted",
            "targetCompanies", "preferSameProgram");

    private ProfileBinding() {
    }

    /**
     * Applies a mentor request body to a profile.
     *
     * @throws ApiException 400 listing every field that could not be applied
     */
    static void apply(MentorProfile profile, Map<String, Object> body) {
        Map<String, String> errors = new LinkedHashMap<>();
        rejectUnknown(body, MENTOR_FIELDS, errors);

        bind(body, "industry", errors, value ->
                profile.setIndustry(value == null ? null : toEnum(Industry.class, value)));
        bind(body, "guidanceAreas", errors, value ->
                profile.setGuidanceAreas(toEnumSet(GuidanceArea.class, value)));
        bind(body, "technicalDomains", errors, value ->
                profile.setTechnicalDomains(toStringList(value)));
        bind(body, "hoursPerMonth", errors, value ->
                profile.setHoursPerMonth(toInt(value)));
        bind(body, "contactMethod", errors, value ->
                profile.setContactMethod(value == null ? null : toEnum(ContactMethod.class, value)));
        bind(body, "maxMentees", errors, value ->
                profile.setMaxMentees(toInt(value)));
        bind(body, "acceptingMentees", errors, value ->
                profile.setAcceptingMentees(toBoolean(value)));

        if (!errors.isEmpty()) {
            throw ApiException.fields(errors);
        }
    }

    /**
     * Applies a student request body to a profile.
     *
     * @throws ApiException 400 listing every field that could not be applied
     */
    static void apply(StudentMatchProfile profile, Map<String, Object> body) {
        Map<String, String> errors = new LinkedHashMap<>();
        rejectUnknown(body, STUDENT_FIELDS, errors);

        bind(body, "targetIndustries", errors, value ->
                profile.setTargetIndustries(toEnumSet(Industry.class, value)));
        bind(body, "targetRoles", errors, value ->
                profile.setTargetRoles(toStringList(value)));
        bind(body, "guidanceWanted", errors, value ->
                profile.setGuidanceWanted(toEnumSet(GuidanceArea.class, value)));
        bind(body, "targetCompanies", errors, value ->
                profile.setTargetCompanies(toStringList(value)));
        bind(body, "preferSameProgram", errors, value ->
                profile.setPreferSameProgram(toBoolean(value)));

        if (!errors.isEmpty()) {
            throw ApiException.fields(errors);
        }
    }

    /** Runs one field's setter, turning its complaint into a field error. */
    private static void bind(Map<String, Object> body, String field,
            Map<String, String> errors, Consumer<Object> setter) {
        if (!body.containsKey(field)) {
            return;
        }
        try {
            setter.accept(body.get(field));
        } catch (IllegalArgumentException e) {
            errors.put(field, e.getMessage());
        }
    }

    /** Names an unexpected field rather than ignoring it, so typos surface immediately. */
    private static void rejectUnknown(Map<String, Object> body, Set<String> allowed,
            Map<String, String> errors) {
        for (String field : body.keySet()) {
            if (!allowed.contains(field)) {
                errors.put(field, "not a field you can set here");
            }
        }
    }

    /** Accepts an enum name in any case, with spaces or hyphens for underscores. */
    private static <E extends Enum<E>> E toEnum(Class<E> type, Object value) {
        String name = String.valueOf(value).trim().toUpperCase().replaceAll("[\\s-]+", "_");
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("expected one of " + names(type));
    }

    /** Accepts an array of enum names, or a single name for convenience. */
    private static <E extends Enum<E>> Set<E> toEnumSet(Class<E> type, Object value) {
        Set<E> result = EnumSet.noneOf(type);
        if (value == null) {
            return result;
        }
        if (!(value instanceof List<?> items)) {
            result.add(toEnum(type, value));
            return result;
        }
        for (Object item : items) {
            if (item == null) {
                throw new IllegalArgumentException("entries may not be null");
            }
            result.add(toEnum(type, item));
        }
        return result;
    }

    /** Accepts an array of strings, or a single string. Trimming and deduping happen in the domain. */
    private static List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        if (!(value instanceof List<?> items)) {
            result.add(String.valueOf(value));
            return result;
        }
        for (Object item : items) {
            if (item == null) {
                throw new IllegalArgumentException("entries may not be null");
            }
            result.add(String.valueOf(item));
        }
        return result;
    }

    /** Accepts a JSON number or a numeric string; rejects anything fractional. */
    private static int toInt(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("a whole number is required");
        }
        if (value instanceof Number number) {
            double asDouble = number.doubleValue();
            if (asDouble != Math.floor(asDouble)) {
                throw new IllegalArgumentException("a whole number is required");
            }
            return (int) asDouble;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("a whole number is required");
        }
    }

    /** Accepts a JSON boolean or the strings "true"/"false", since form fields arrive as text. */
    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        if (text.equals("true")) {
            return true;
        }
        if (text.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException("expected true or false");
    }

    private static String names(Class<? extends Enum<?>> type) {
        List<String> names = new ArrayList<>();
        for (Enum<?> constant : type.getEnumConstants()) {
            names.add(constant.name());
        }
        return String.join(", ", names);
    }
}
