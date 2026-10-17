# Mentor Matching — Sprint 3

Mentor Matching subsystem (Subsystem 4) for Mason CareerLaunch.

This service handles mentor and student matching profiles, ranked mentor recommendations, and the connection requests that link a student to a mentor.

- **Backend:** Java 21, no runtime dependencies (see [ADR-004](docs/adr/ADR-004-java-framework.md)); Gradle and JUnit 5 for build and tests only
- **Frontend:** plain HTML and JavaScript, served by the same process on one port
- **Architecture:** [docs/architecture.md](docs/architecture.md) · decisions in [docs/adr/](docs/adr/) · contracts in [v1](docs/interface-contracts-v1.md) (profiles, directory) and [v2](docs/interface-contracts-v2.md) (recommendations, connections)
- **Sprint 3 plan and retro:** [docs/sprint3.md](docs/sprint3.md)
- **CI:** every pull request into `main` runs `./gradlew build` ([workflow](.github/workflows/ci.yml)); the `lint-and-test` check must pass before merging

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

Then open <http://localhost:8080/>. The mentor and student forms are linked from there.

## Build and test

```bash
./gradlew build        # macOS/Linux
gradlew.bat build      # Windows
```

This compiles `backend/src`, runs the JUnit tests in `backend/test` and is exactly what CI runs. No Gradle install is needed; the wrapper downloads it.

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

**Sprint 3 — in progress**

- Connection requests and their lifecycle: pending → active → completed, or declined / withdrawn (`/connections`, [contracts v2](docs/interface-contracts-v2.md))
- A mentor's `activeMentees` now follows their active connections, so capacity in matching and the directory is real
- The SOW recommendations path, `GET /connections/recommendations/{userId}`
- Shared notes on an active connection

**Not started:** session scheduling, notifications to Subsystem 8, UI for connections.

## Layout

```
backend/src/       Java sources — domain, API and persistence layers
backend/test/      JUnit 5 tests
static/            HTML, JS and CSS served on the same port
docs/
  architecture.md            Architecture (Sprint 1, plus Sprint 3 additions)
  interface-contracts-v1.md  Profiles and directory
  interface-contracts-v2.md  Recommendations and connections
  sprint3.md                 Sprint 3 plan and retro
  adr/                       Architecture decision records
  ai-log/                    AI prompt logs
.github/workflows/ci.yml  CI check run on every PR
build.gradle       Build and test configuration
Dockerfile         Builds and runs the whole subsystem
```

### Where things live

| File | Layer | Responsibility |
| - | - | - |
| `static/*.html` | Presentation | Forms for each profile type |
| `static/app.js` | Presentation | Calls our endpoints, renders results and field errors |
| `MentoringApp.java` | API | Starts the server, defines routes, validates tokens and input |
| `ProfileBinding.java` | API | Request JSON to domain objects, one message per bad field |
| `ApiException.java` | API | The shared `{error, code, detail}` format |
| `Json.java` | API | JSON reader and writer (no Jackson; no runtime dependencies) |
| `EditProfile.java` | API | Applies a partial update for the signed-in role |
| `ConnectionService.java` | API | Who may make which connection move; keeps `activeMentees` in step |
| `MentorProfile.java` | Domain | Mentor attributes and their rules |
| `StudentMatchProfile.java` | Domain | Student matching attributes and their rules |
| `Connection.java`, `ConnectionStatus` | Domain | One connection and its lifecycle rules |
| `Industry`, `GuidanceArea`, `ContactMethod` | Domain | Shared vocabularies (ADR-002) |
| `ProfileText.java` | Domain | Free-text normalization both profiles share |
| `MentorMatcher.java` | Domain | Weighted compatibility score behind recommendations |
| `ProfileData.java` | Persistence | The store interface (ADR-001) |
| `InMemoryProfileData.java` | Persistence | In-memory implementation |
| `SharedCoreClient.java` | Persistence | Token validation and the base profile, live or stubbed |

## Known gaps

- **Everything resets on restart,** because storage is in memory until the attribute split with Shared Core is settled (ADR-001). That now includes connections.
- **Stub mode is still on by default** (see above).
- **No UI for connections yet;** the endpoints are usable with any HTTP client.
- **`Industry` values are placeholders** pending sign-off with Shared Core and Subsystems 2 and 8.
