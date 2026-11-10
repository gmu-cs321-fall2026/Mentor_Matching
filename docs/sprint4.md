# Sprint 4 — Plan and Retrospective

CS 321, Fall 2026 · Mason CareerLaunch · Subsystem 4: Mentor Matching
Team members: Dillon Bajracharya, Jacob Livermon, Noah Carter, Rohan Veeranki, [FIFTH MEMBER'S NAME]

> Owners come from who committed the work on GitHub. Points must be copied from Jira; anything marked `TBD` or `[ ]` is waiting on that or on the retro meeting.

## Plan

**Sprint goal:** Every change to `main` is built and tested inside our container and smoke-tested as a running service, and `main` can only change through a reviewed, green pull request.

**Course requirements for Sprint 4** (as recorded at the end of Sprint 3; check them against the project page):

- CI builds and tests inside the subsystem's container
- Branch protection on `main`
- A code-quality section in the retro (see [Code quality](#code-quality))

### Stories

| Story | Points | Owner | Status |
| - | - | - | - |
| CI runs the JUnit tests inside the container (`Dockerfile` `test` stage, `docker-build-and-test` job) | TBD | Rohan | Done (PR #14) |
| Smoke test against the running container: pages, auth, one connection lifecycle (`scripts/smoke-test.sh`) | TBD | Rohan | Done (PR #14) |
| Branch protection on `main`: PRs only, `lint-and-test` and `docker-build-and-test` required | TBD | Rohan | [ ] confirm in Settings → Branches |
| Code-quality section in the retro | TBD | Rohan | Done (this file) |
| Fix a defect from the Q&I quality report (carried over from Sprint 3) | TBD | TBD | **Open** |

### Agreements carried over from the Sprint 3 retro

- [x] Add `docker build` and a smoke test to CI. The smoke test requests every page, including `/connections.html` and `/connections.js`. Against the code from before the Sprint 3 fix it fails with exactly those 401s.
- [ ] Turn on branch protection for `main`
- [ ] Each PR that changes a page or route says how it was tried in the running service. CI now covers the pages and the connection flow automatically, so this mainly matters for new pages and routes.
- [ ] Estimate, assign and describe every Jira ticket at planning (Cross Review 2)

## Code quality

Measured on `main` after PR #14. Coverage comes from JaCoCo, applied through a temporary Gradle init script; it is not part of the build yet. Warnings come from `javac -Xlint:all`.

### Metrics

| Metric | Value |
| - | - |
| Production code (Java, non-blank lines) | 2,702 in 18 files |
| Test code (Java, non-blank lines) | 449 in 3 files |
| JUnit tests | 26 passing, 0 failing (`ConnectionTest` 16, `ConnectionServiceTest` 5, `MentorMatcherTest` 5) |
| Smoke test checks in CI | 26 (8 pages and scripts, auth, one full connection lifecycle) |
| Line coverage from unit tests | 39.9% (471 / 1,180) |
| Branch coverage from unit tests | 35.9% (230 / 640) |
| Compiler warnings (`-Xlint:all`) | 0 |
| `TODO` / `FIXME` in code | 1 (`Industry` values are placeholders, waiting on Shared Core and Subsystems 2 and 8) |
| CI checks required on every PR | 2 (`lint-and-test`, `docker-build-and-test`) |
| Largest file | `MentoringApp.java`, 435 lines |

### Coverage by layer

| Layer | Classes | Line coverage |
| - | - | - |
| Domain | `Connection` 96%, `MentorMatcher` 92%, `ConnectionStatus` and enums 100%, `ProfileText` 81%, `MentorProfile` 70%, `StudentMatchProfile` 70% | High |
| API | `ConnectionService` 90%, `ApiException` 50%, `MentoringApp` 0%, `Json` 0%, `ProfileBinding` 0%, `EditProfile` 0% | Low outside `ConnectionService` |
| Persistence | `InMemoryProfileData` 76%, `SharedCoreClient` 26% | Medium |

### What the numbers say

- **The domain rules are well tested.** The connection lifecycle, capacity and matching score are where the business logic lives, and they are at 90% or more.
- **The API layer has no unit tests.** That is 529 executable lines across `MentoringApp`, `Json`, `ProfileBinding` and `EditProfile`. The smoke test now exercises the main paths end to end in CI, but error paths are untested: bad JSON, oversized bodies, field validation messages, and partial profile updates. Sprint 3's routing bug lived in `MentoringApp`.
- **`Json` is the riskiest untested class.** It is our own parser and writer (ADR-004 chose no runtime dependencies), with 105 branches and no tests. Every request and response goes through it.
- **`MentoringApp` keeps growing.** At 435 lines it holds the bootstrap and every route. The architecture doc already says to split it into one controller per resource as the service grows.
- **Some code is duplicated.** `connections.js` repeats the `call`, `pretty` and `el` helpers from `app.js`, and its own header comment says so.

### Actions

- [ ] Add `JsonTest` (round trips, escapes, nested values, malformed input) and `ProfileBindingTest` (one message per bad field)
- [ ] Add JaCoCo to `build.gradle` so CI reports coverage on every PR, and set a floor once the API tests exist
- [ ] Split routes out of `MentoringApp` into one class per resource (profiles, directory, connections)
- [ ] Move the shared helpers in `app.js` and `connections.js` into one script both pages load

## Quality metrics

| Metric | Start of sprint | End of sprint |
| - | - | - |
| JUnit tests | 26 | [ ] |
| Tests passing on `main` | 26 / 26 | [ ] |
| Smoke test checks | 0 | 26 |
| PRs merged into `main` | — | [ ] (#14 so far) |
| CI runs that failed, and why | — | [ ] from the Actions tab |
| Q&I defects reported / fixed | [ ] | [ ] |
| Open bugs in Jira | [ ] | [ ] |

## Retrospective

> Fill this in at the retro meeting.

**Sprint goal:** [Met / Partly met / Not met], and why in one sentence.

**What went well:**

[ ]

**What went poorly:**

[ ]

**What we're changing for Sprint 5:**

[ ]

**Velocity:**

Committed: [ ] points. Completed: [ ] points.
