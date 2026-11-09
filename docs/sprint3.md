# Sprint 3 — Plan and Retrospective

CS 321, Fall 2026 · Mason CareerLaunch · Subsystem 4: Mentor Matching
Team members: Dillon Bajracharya, Jacob Livermon, Noah Carter, Rohan Veeranki, [FIFTH MEMBER'S NAME]

> Owners below come from who committed the work on GitHub. Points must still be copied from Jira; anything marked `TBD` is waiting on that.

## Plan

**Sprint goal:** A student can ask a recommended mentor to connect, the mentor can accept or decline, and the connection moves through its lifecycle with the mentor's capacity kept accurate.

**Course requirements for Sprint 3** (from the project page):

- Full feature set for the primary user flows
- Quality metrics tracked in the retro (see below)
- At least one defect from a Quality & Integration Team quality report addressed

### Stories

| Story | Points | Owner | Status |
| - | - | - | - |
| As a student, I can see mentors ranked by how well they match me (`GET /mentors/recommendations`, `GET /connections/recommendations/{userId}`) | TBD | Rohan (API), Noah (student page) | Done |
| As a student, I can send a connection request to a mentor (`POST /connections`) | TBD | Rohan | Done |
| As a mentor, I can accept or decline a request, and my capacity updates (`PUT /connections/{id}`) | TBD | Rohan; Jacob (status and capacity update order) | Done |
| As a student or mentor, I can see my connections and keep shared notes on an active one | TBD | Rohan | Done |
| As a student or mentor, I can complete or withdraw a connection | TBD | Rohan | Done |
| UI: request, accept/decline and list connections in the web pages (`connections.html`, `connections.js`) | TBD | Dillon | Done |
| Tests for the connection lifecycle rules (`ConnectionTest`) | TBD | Dillon | Done |
| Validate `/connections` routes so unknown paths return `404` | TBD | Jacob | Done |
| Fix: the connections page could not load (see defects below) | TBD | Rohan | Done |
| Fix a defect from the Q&I quality report: [describe] | TBD | TBD | **Open** |

### Defects fixed this sprint

- **The connections page never loaded.** The JDK HTTP server matches routes by prefix, so the `/connections` API route also caught `/connections.html` and `/connections.js` and answered `401` because they carried no token. Found by running the service at the end of the sprint. Fixed in `MentoringApp`: only `/connections` and `/connections/...` go to the API, and other paths under that prefix are served as static files.
- **The page's script was named `Connections.js` but loaded as `connections.js`.** That works on Windows, where filenames ignore case, but not in the Linux container. Renamed the file to `connections.js`.
- The home page now links to the connections page and lists the v2 endpoints.

### Agreements carried over from the Sprint 1 retro

- [x] `main` is green before each Friday 4:30 pm sync (CI check `lint-and-test` is required on every PR)
- [ ] Method signatures on shared classes agreed at planning before anyone builds on them
- [ ] Every story estimated and assigned; Jira updated before each Friday sync
- [x] AI prompt logs kept current in `docs/ai-log/`
- [ ] The retro planned a `docker build` step in CI. It was not added in Sprint 3; it moves to Sprint 4, which requires CI to build and test inside the container.

## Quality metrics

Numbers come from git history and `./gradlew build` on `main` at the end of the sprint. CI run results need the GitHub Actions tab.

| Metric | Start of sprint | End of sprint |
| - | - | - |
| JUnit tests | 5 | 26 (`MentorMatcherTest` 5, `ConnectionServiceTest` 16, `ConnectionTest` 5) |
| Tests passing on `main` | 5 / 5 | 26 / 26 |
| PRs merged into `main` | — | 11 (#1–#9, #11, #12), plus the sprint-close PR |
| Members with merged commits this sprint | — | 4 (Dillon, Jacob, Noah, Rohan) |
| CI runs that failed, and why | — | [ ] from the Actions tab |
| Q&I defects reported / fixed | [ ] | [ ] |
| Defects found and fixed by the team | — | 2 (connections page blocked by the route prefix; script filename case) |
| Open bugs in Jira | [ ] | [ ] |

## Retrospective

> Drafted from the repository history. Edit it at the retro meeting so it reflects what the team agrees on.

**Sprint goal:** Met. Every connection move works end to end through the API and the connections page, and the mentor's capacity follows accepts and endings.

**What went well:**

- Contracts v2 and the connection lifecycle were written down before the UI was built, so the connections page used the right field names and status values from the start.
- Tests grew from 5 to 26, and every PR into `main` went through the required `lint-and-test` check.
- Work was spread out: four members merged their own PRs this sprint, which addresses the Cross Review 2 feedback that most commits came from one person.

**What went poorly:**

- Nobody ran the connections page against the service before merging, so it stayed broken (blocked by the route prefix, with the wrong script filename case) until the end of the sprint. CI only compiles and runs unit tests, so it could not catch either problem.
- The docs fell behind: the README and this file said "no UI for connections" after the UI was merged.
- Points and owners were not filled in Jira or here during the sprint, which Cross Review 2 also flagged.

**What we're changing for Sprint 4:**

Sprint 4 requires CI to build and test inside the subsystem's container, branch protection, and a code-quality section in the retro.

- Add `docker build` plus a smoke test to CI. The smoke test starts the container and requests `/`, `/connections.html`, `/connections.js` and `/health`, which would have caught both page bugs.
- Turn on branch protection for `main`.
- Each PR that changes a page or route must say in its description how it was tried in the running service.
- Estimate, assign and describe every Jira ticket at planning, as Cross Review 2 asked.

**Velocity:**

Committed: [ ] points. Completed: [ ] points (from Jira).
