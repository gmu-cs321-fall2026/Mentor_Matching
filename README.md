# Mentor Matching — Sprint 5 (feature freeze)

Mentor Matching subsystem (Subsystem 4) for Mason CareerLaunch.

This service handles mentor and student matching profiles, ranked mentor recommendations, and the connections that link a student to a mentor: requests, accept or decline, contact by email, a session log and session scheduling.

- **Backend:** Java 21, no runtime dependencies (see [ADR-004](docs/adr/ADR-004-java-framework.md)); Gradle and JUnit 5 for build and tests only
- **Frontend:** plain HTML and JavaScript, served by the same process on one port
- **Architecture:** [docs/architecture.md](docs/architecture.md) · decisions in [docs/adr/](docs/adr/) · contracts in [v1](docs/interface-contracts-v1.md) (profiles, directory) and [v2](docs/interface-contracts-v2.md) (recommendations, connections)
- **Sprint plans and retros:** [Sprint 1](docs/sprint1-retrospective.md) · [Sprint 3](docs/sprint3.md) · [Sprint 4](docs/sprint4.md) (includes the code-quality report)
- **CI:** every pull request into `main` runs two checks ([workflow](.github/workflows/ci.yml)). `lint-and-test` runs `./gradlew build`, which also checks coverage against a floor (85% of lines, 75% of branches) and posts a coverage table on the run's summary page. `docker-build-and-test` runs the JUnit tests inside the container, builds the image, starts it and runs [the smoke test](scripts/smoke-test.sh). Both must pass before merging

Meeting weekly Friday @ 4:30pm for sync meetings.

## Run it

With Docker:

```bash
docker build -t mentor-matching .
docker run --rm -p 8080:8080 mentor-matching
```

Or straight from a JDK 21 checkout:

```bash
mkdir -p out && javac -d out $(find backend/src -name "*.java")
java -cp out MentoringApp
```

Then open <http://localhost:8080/>. The mentor and student forms and the connections page are linked from there.

## Build and test

```bash
./gradlew build        # macOS/Linux
gradlew.bat build      # Windows
```

This compiles `backend/src`, runs the JUnit tests in `backend/test`, writes the JaCoCo coverage report and fails if coverage is under the floor, the same as the `lint-and-test` check. No Gradle install is needed; the wrapper downloads it.

Coverage report: open `build/reports/jacoco/test/html/index.html`, or print a table with `sh scripts/coverage-summary.sh`. The API layer is tested over real HTTP: `MentoringAppTest` starts the service on a free port in stub mode, and `SharedCoreClientTest` runs live mode against a fake Shared Core.

What the `docker-build-and-test` check runs:

```bash
docker build --target test .                      # JUnit tests inside the container
docker build -t mentor-matching .
docker run -d --name mentor-matching -p 8080:8080 mentor-matching
sh scripts/smoke-test.sh http://localhost:8080    # pages, auth and one connection lifecycle
```

The smoke test needs a fresh service in stub mode, because it creates the demo profiles. It also works against `java -cp out MentoringApp`. It covers every page, auth, and one connection from request to completion, including the session log and scheduling.

## Configuration

Everything comes from the environment; nothing is hard-coded, so the Week 13 compose environment can wire us in.

| Variable | Default | Purpose |
| - | - | - |
| `PORT` | `8080` | Port to listen on |
| `SHARED_CORE_URL` | *(unset)* | Shared Core's root URL. **Unset turns on stub mode.** |
| `DB_URL` | *(unset)* | Read and logged at startup; storage is still in memory |
| `STATIC_DIR` | `static` | Where the HTML/JS live |

### Stub mode

Leaving `SHARED_CORE_URL` unset answers Shared Core's `/auth/validate` and `/users/{id}` from memory. It was planned to be removed before Sprint 2 and is **still on**: it is what the tests and local demos use. It must not be used in the shared compose environment; the service prints a warning at startup while it is on.

Tokens it accepts:

| Token | Acts as |
| - | - |
| `mentor-demo` | a mentor with a filled-in Shared Core base profile |
| `student-demo` | a student with a filled-in Shared Core base profile |
| `<uuid>:<Role>` | anyone you like, e.g. `33333333-3333-3333-3333-333333333333:Mentor` |

## Scope by sprint

**Sprint 1 — done**

- Create, read and update a mentor profile
- Create, read and update a student matching profile
- Paginated, filterable mentor directory (`GET /mentors`)
- Forms for both profile types, plus a directory browser
- Architecture and four ADRs recorded; contracts v1 drafted for the wiki
- Dockerfile that builds and runs with nothing but `docker build` and `docker run`

**Since Sprint 1 — done**

- Ranked recommendations from `MentorMatcher` (`GET /mentors/recommendations`)
- Gradle build, JUnit tests and a GitHub Actions check required on every PR into `main`

**Sprint 3 — done** ([plan and retro](docs/sprint3.md))

- Connection requests and their lifecycle: pending → active → completed, or declined / withdrawn (`/connections`, [contracts v2](docs/interface-contracts-v2.md))
- A mentor's `activeMentees` now follows their active connections, so capacity in matching and the directory is real
- The SOW recommendations path, `GET /connections/recommendations/{userId}`
- Shared notes on an active connection
- Connections page (`static/connections.html`): students request a recommended mentor, mentors accept or decline, either side completes or ends a connection and keeps shared notes
- Recommended mentors on the student profile page

**Sprint 4 — done** ([plan, code quality and retro](docs/sprint4.md))

- CI builds and runs the JUnit tests inside the container, then starts it and runs [the smoke test](scripts/smoke-test.sh)
- Branch protection on `main`: pull requests only, with `lint-and-test` and `docker-build-and-test` required
- Code-quality report: coverage, warnings and actions

**Sprint 5 — feature set complete** (contracts: [v2](docs/interface-contracts-v2.md))

- Contact by email: while a connection is `ACTIVE`, each side sees the other's email from Shared Core, with an "Email your mentor / mentee" button on the connections page. Mentor Matching has no messaging, as the SOW asks
- Session log (`GET|POST /connections/{id}/sessions`): either side records a session that took place, with a date and summary
- Session scheduling (`GET|POST|PUT /connections/{id}/schedule`): one side proposes a time, the other confirms or cancels it, and a confirmed time downloads as an `.ics` calendar file
- Fix: a session logged in the evening without a date was dated the next day, because "today" was taken in UTC. It now uses GMU's time zone (`America/New_York`)
- Unit tests for the session log and the scheduling rules; session log and scheduling added to the smoke test and to contracts v2
- API-layer tests (SCRUM-31): `JsonTest`, `ProfileBindingTest`, `EditProfileTest`, `MentoringAppTest` (every route over HTTP) and `SharedCoreClientTest` (live mode against a fake Shared Core). 119 tests in all; line coverage is up from 39.9% to 93.6%, and branch coverage from 35.9% to 87.3%
- JaCoCo coverage in the build and in CI, with a floor of 85% of lines and 75% of branches
- Fixes the new tests found: a refused profile `PUT` (one bad field, or a result that is not valid) returned 400 but had already changed the stored profile; deeply nested JSON overflowed the stack and got no response at all instead of a 400; an `Authorization: Bearer ` header with no token was sent to Shared Core as the token `Bearer`, which turned into a 502 instead of a 401

**Not done yet:**

- Notifications (Subsystem 6): `POST /notifications/dispatch` on a new request and on accept or decline
- Pagination on `GET /connections`, `GET /connections/{id}/sessions` and the recommendations routes (the SOW asks for it on every list endpoint)
- Running against the real Shared Core in the shared docker-compose environment (see [Stub mode](#stub-mode))

## Layout

```
backend/src/       Java sources — domain, API and persistence layers
backend/test/      JUnit 5 tests
static/            HTML, JS and CSS served on the same port
docs/
  architecture.md            Architecture (Sprint 1, plus Sprint 3 additions)
  interface-contracts-v1.md  Profiles and directory
  interface-contracts-v2.md  Recommendations, connections, session log and scheduling
  sprint3.md                 Sprint 3 plan and retro
  sprint4.md                 Sprint 4 plan, code quality and retro
  adr/                       Architecture decision records
  ai-log/                    AI prompt logs
.github/workflows/ci.yml  CI checks run on every PR
scripts/smoke-test.sh     Smoke test against a running service
scripts/coverage-summary.sh  Coverage table from the JaCoCo report (used by CI)
build.gradle       Build and test configuration
Dockerfile         Builds and runs the whole subsystem; its `test` stage runs the JUnit tests
```

### Where things live

| File | Layer | Responsibility |
| - | - | - |
| `static/*.html` | Presentation | Forms for each profile type, plus the connections page |
| `static/app.js` | Presentation | Calls our endpoints, renders results and field errors |
| `static/connections.js` | Presentation | Connections page: request, accept/decline, complete/withdraw, shared notes, email, next session |
| `MentoringApp.java` | API | Starts the server, defines routes, validates tokens and input |
| `ProfileBinding.java` | API | Request JSON to domain objects, one message per bad field |
| `ApiException.java` | API | The shared `{error, code, detail}` format |
| `Json.java` | API | JSON reader and writer (no Jackson; no runtime dependencies) |
| `EditProfile.java` | API | Applies a partial update for the signed-in role |
| `ConnectionService.java` | API | Who may make which connection move; keeps `activeMentees` in step; session log and scheduling |
| `MentorProfile.java` | Domain | Mentor attributes and their rules |
| `StudentMatchProfile.java` | Domain | Student matching attributes and their rules |
| `Connection.java`, `ConnectionStatus` | Domain | One connection and its lifecycle rules |
| `SessionLogEntry.java` | Domain | One recorded session on a connection |
| `SessionProposal.java` | Domain | A proposed or confirmed time for the next session |
| `InvalidFieldException.java` | Domain | A complaint about one named field's value, which the API reports as a `400` |
| `Industry`, `GuidanceArea`, `ContactMethod` | Domain | Shared vocabularies (ADR-002) |
| `ProfileText.java` | Domain | Free-text normalization both profiles share |
| `MentorMatcher.java` | Domain | Weighted compatibility score behind recommendations |
| `ProfileData.java` | Persistence | The store interface (ADR-001) |
| `InMemoryProfileData.java` | Persistence | In-memory implementation |
| `SharedCoreClient.java` | Persistence | Token validation and the base profile, live or stubbed |

## Known gaps

- **Everything resets on restart,** because storage is in memory until the attribute split with Shared Core is settled (ADR-001). That includes connections, session logs and schedules.
- **Notifications are not sent yet.** A mentor sees new requests by opening the connections page.
- **List endpoints other than `GET /mentors` are not paginated** (see Sprint 5 above).
- **Email sharing assumes** Shared Core's `/users/{id}` returns `email` and that showing it to an active mentor or mentee is allowed; both still need confirming with Shared Core.
- **Stub mode is still on by default** (see above).
- **`Industry` values are placeholders** pending sign-off with Shared Core and Subsystems 2 and 8.
