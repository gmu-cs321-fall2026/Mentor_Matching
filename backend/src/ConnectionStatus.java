/**
 * Where a mentor connection stands.
 *
 * <p>The SOW lifecycle is pending, then active, then completed or withdrawn.
 * {@code DECLINED} is the mentor saying no to a pending request. Which moves
 * are allowed, and by whom, is decided in {@link Connection#isAllowed}.
 */
public enum ConnectionStatus {
    /** Sent by the student, waiting on the mentor. */
    PENDING,
    /** Accepted by the mentor; counts against the mentor's capacity. */
    ACTIVE,
    /** The mentor turned the request down. Final. */
    DECLINED,
    /** The mentorship ran its course. Final. */
    COMPLETED,
    /** Either side backed out, before or after it started. Final. */
    WITHDRAWN;

    /** True while the connection is still open: pending or active. */
    public boolean isOpen() {
        return this == PENDING || this == ACTIVE;
    }
}
