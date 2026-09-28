import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An error that maps straight onto the shared error format from SOW Section 6:
 * {@code {"error": "...", "code": "...", "detail": "..."}}.
 *
 * <p>Thrown from any layer and turned into a response by the one handler
 * wrapper in {@link MentoringApp}, so no endpoint can leak an unhandled
 * exception to the caller.
 *
 * <p>{@code detail} is a string for most failures and a field-to-message map
 * for validation failures, which is how a 400 reports every bad field at once.
 */
class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final transient Object detail;

    ApiException(int status, String code, String message, Object detail) {
        super(message);
        this.status = status;
        this.code = code;
        this.detail = detail;
    }

    int status() {
        return status;
    }

    /** The response body in the shared error format. */
    Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", getMessage());
        body.put("code", code);
        body.put("detail", detail);
        return body;
    }

    /** 400 with one message per bad field, per the architecture's failure paths. */
    static ApiException fields(Map<String, String> fieldErrors) {
        return new ApiException(400, "validation_failed",
                "one or more fields are invalid", new LinkedHashMap<>(fieldErrors));
    }

    /** 400 for a body that is not usable at all, such as malformed JSON. */
    static ApiException badRequest(String code, String detail) {
        return new ApiException(400, code, "the request could not be processed", detail);
    }

    /** 401 for a missing, malformed or rejected token. */
    static ApiException unauthorized(String detail) {
        return new ApiException(401, "unauthorized", "authentication is required", detail);
    }

    /** 403 when the token is good but the role is wrong, e.g. a student creating a mentor profile. */
    static ApiException forbidden(String detail) {
        return new ApiException(403, "forbidden", "this role may not perform that action", detail);
    }

    static ApiException notFound(String detail) {
        return new ApiException(404, "not_found", "the requested resource does not exist", detail);
    }

    static ApiException methodNotAllowed(String detail) {
        return new ApiException(405, "method_not_allowed", "that method is not supported here", detail);
    }

    static ApiException conflict(String detail) {
        return new ApiException(409, "conflict", "the resource is already in that state", detail);
    }

    /** 502 when Shared Core is unreachable, so the caller can tell it apart from our own bugs. */
    static ApiException sharedCoreUnavailable(String detail) {
        return new ApiException(502, "shared_core_unavailable", "Shared Core did not answer", detail);
    }

    /** 500 for anything we failed to anticipate; the cause is logged, never returned. */
    static ApiException internal() {
        return new ApiException(500, "internal_error", "something went wrong on our side",
                "see the service log");
    }
}
