/**
 * A domain complaint about one request field's value.
 *
 * <p>Carries the field name, so the service layer can report it without
 * guessing from the message text.
 */
public class InvalidFieldException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The request field the complaint is about, e.g. {@code startsAt}. */
    public String getField() {
        return field;
    }
}
