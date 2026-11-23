Asked: Asked Claude Code (Claude Opus 5.5) to complete SCRUM-31: unit tests for the API layer and coverage reporting in CI.

Produced:
- `JsonTest`: every value type, field order, escapes, round trips, and malformed input (each case must come back as an `IllegalArgumentException`, which becomes a 400).
- `ProfileBindingTest`: full bodies, tolerant enum names, form-style values, and one message per bad field with every field reported at once.
- `EditProfileTest`: partial updates, role limits, 404, and that a refused edit changes nothing.
- `MentoringAppTest`: every route over real HTTP against stub-mode Shared Core. Covers auth (401/403), the shared error format, 404/405 for bad paths and methods, bad bodies (malformed, too large, too deep), directory filters and paging, recommendations, a full connection with session log and schedule, and static files including a path-traversal check.
- `SharedCoreClientTest`: live mode against a fake Shared Core (valid, rejected and missing tokens; 5xx, unreadable JSON, missing fields, unreachable host; `/users/{id}` reads and writes), plus stub mode.
- `MentoringApp.start(port, sharedCore, staticDir)`, split out of `main` so tests can run the real server on a free port. No change in behaviour; the smoke test still passes.
- JaCoCo in `build.gradle` (HTML, XML and CSV reports) and a coverage floor of 85% lines and 75% branches, enforced by `./gradlew build`. `scripts/coverage-summary.sh` turns the CSV into a Markdown table that CI adds to the job summary; the HTML report is uploaded as an artifact.
- Coverage went from 39.9% to 93.6% of lines and from 35.9% to 87.3% of branches. There are now 119 tests.

Bugs the tests found, each confirmed failing before the fix:
- `EditProfile` changed the stored profile before validating it, so a refused `PUT` still changed it. One bad field kept the others' changes, and `"guidanceAreas": []` stored a profile that breaks the validation rules. It now applies the body to a draft, validates the draft, and only then copies the editable fields onto the stored profile. The stored object is updated rather than replaced, so a concurrent accept's `activeMentees` change is not lost.
- `Json` recursed without a limit, so a 64 KB body of brackets threw `StackOverflowError`. The route wrapper does not catch it, so the caller got no response. Nesting is now capped at 100 levels and gets a 400 `malformed_json`. A bad `\u` escape now says so instead of passing on Java's `NumberFormatException` message.
- `SharedCoreClient` trimmed `Authorization: Bearer ` (no token) to `Bearer`, then sent that to Shared Core as the token, which turned into a 502. It is now a 401 without calling Shared Core.

Changed or rejected:
- Did not change the directory's maximum page size (100) to the SOW's 50. Contract v1 documents 100, and changing it after the feature freeze needs the team's agreement.
- Set the floor below the measured numbers so small changes don't fail CI; raise it if coverage stays high.
- JaCoCo 0.8.12, which supports the Java 21 used in CI and the Java 22 on this laptop.
