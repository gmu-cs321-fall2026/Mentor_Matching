# Mentor Matching — Sprint 1

Mentor Matching subsystem (Subsystem 4) for Mason CareerLaunch.

This service handles creating and managing the mentor and student profiles that feed the broader CareerLaunch matching platform.

- **Backend:** Java 21, no external dependencies (see [ADR-004](docs/adr/ADR-004-java-framework.md))
- **Frontend:** plain HTML and JavaScript, served by the same process on one port
- **Architecture:** [docs/architecture.md](docs/architecture.md) · decisions in [docs/adr/](docs/adr/) · contracts in [docs/interface-contracts-v1.md](docs/interface-contracts-v1.md)

Meeting weekly Friday @ 4:30pm for sync meetings.

## Run it

With Docker, which is all the Sprint 1 deliverable requires:

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

## Configuration

Everything comes from the environment; nothing is hard-coded, so the Week 13 compose environment can wire us in.

| Variable | Default | Purpose |
| - | - | - |
| `PORT` | `8080` | Port to listen on |
| `SHARED_CORE_URL` | *(unset)* | Shared Core's root URL. **Unset turns on stub mode.** |
| `DB_URL` | *(unset)* | Recorded for Sprint 2; Sprint 1 keeps profiles in memory |
| `STATIC_DIR` | `static` | Where the HTML/JS live |

### Stub mode

Shared Core's `/auth/validate` and `/users/{id}` may not be ready, so leaving `SHARED_CORE_URL` unset answers both from memory. This is the config-flag fallback on the architecture's risk list, and **it has to be removed before Sprint 2.** The service prints a warning at startup while it is on.

Tokens it accepts:

| Token | Acts as |
| - | - |
| `mentor-demo` | a mentor with a filled-in Shared Core base profile |
| `student-demo` | a student with a filled-in Shared Core base profile |
| `<uuid>:<Role>` | anyone you like, e.g. `33333333-3333-3333-3333-333333333333:Mentor` |

## Sprint 1 scope

**Done**

- Create, read and update a mentor profile
- Create, read and update a student matching profile
- Paginated, filterable mentor directory (`GET /mentors`)
- Forms for both profile types, plus a directory browser
- Architecture and four ADRs recorded; contracts v1 drafted for the wiki
- Dockerfile that builds and runs with nothing but `docker build` and `docker run`

**Out of scope, coming later:** recommendations, connection requests, session scheduling, notifications. `MentorMatcher` is a Sprint 3 head start and is deliberately not wired to any route.

## Layout

```
backend/src/     Java sources — domain, API and persistence layers
static/          HTML, JS and CSS served on the same port
docs/
  architecture.md          Sprint 1 architecture
  interface-contracts-v1.md  Copy of the wiki contract page
  adr/                     Architecture decision records
  ai-log/                  AI prompt logs
Dockerfile       Builds and runs the whole subsystem
```

### Where things live

| File | Layer | Responsibility |
| - | - | - |
| `static/*.html` | Presentation | Forms for each profile type |
| `static/app.js` | Presentation | Calls our endpoints, renders results and field errors |
| `MentoringApp.java` | API | Starts the server, defines routes, validates tokens and input |
| `ProfileBinding.java` | API | Request JSON to domain objects, one message per bad field |
| `ApiException.java` | API | The shared `{error, code, detail}` format |
| `Json.java` | API | JSON reader and writer (no Jackson without a build tool) |
| `MentorProfile.java` | Domain | Mentor attributes and their rules |
| `StudentMatchProfile.java` | Domain | Student matching attributes and their rules |
| `Industry`, `GuidanceArea`, `ContactMethod` | Domain | Shared vocabularies (ADR-002) |
| `ProfileText.java` | Domain | Free-text normalization both profiles share |
| `EditProfile.java` | API | Applies a partial update for the signed-in role |
| `ProfileData.java` | Persistence | The store interface (ADR-001) |
| `InMemoryProfileData.java` | Persistence | Sprint 1 implementation |
| `SharedCoreClient.java` | Persistence | Token validation and the base profile, live or stubbed |
| `MentorMatcher.java` | — | Sprint 3 compatibility scoring, not yet wired up |

## Known gaps

- **No automated tests.** With no dependency manager there is no JUnit either; picking a test framework is tied to the ADR-004 decision.
- **Profiles reset on restart,** because storage is in memory until the attribute split with Shared Core is settled (ADR-001).
- **`Industry` values are placeholders** pending sign-off with Shared Core and Subsystems 2 and 8.
