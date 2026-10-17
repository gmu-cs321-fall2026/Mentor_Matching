# Sprint 3 — Plan and Retrospective

CS 321, Fall 2026 · Mason CareerLaunch · Subsystem 4: Mentor Matching
Team members: Dillon Bajracharya, Jacob Livermon, Noah Carter, Rohan Veeranki, [FIFTH MEMBER'S NAME]

> Fill in every `[ ]` and `TBD` at sprint planning and again at the retro. Points and owners should match Jira.

## Plan

**Sprint goal:** A student can ask a recommended mentor to connect, the mentor can accept or decline, and the connection moves through its lifecycle with the mentor's capacity kept accurate.

**Course requirements for Sprint 3** (from the project page):

- Full feature set for the primary user flows
- Quality metrics tracked in the retro (see below)
- At least one defect from a Quality & Integration Team quality report addressed

### Stories

| Story | Points | Owner | Status |
| - | - | - | - |
| As a student, I can see mentors ranked by how well they match me (`GET /mentors/recommendations`, `GET /connections/recommendations/{userId}`) | TBD | TBD | Done in code |
| As a student, I can send a connection request to a mentor (`POST /connections`) | TBD | TBD | Done in code |
| As a mentor, I can accept or decline a request, and my capacity updates (`PUT /connections/{id}`) | TBD | TBD | Done in code |
| As a student or mentor, I can see my connections and keep shared notes on an active one | TBD | TBD | Done in code |
| As a student or mentor, I can complete or withdraw a connection | TBD | TBD | Done in code |
| UI: request, accept/decline and list connections in the web pages | TBD | TBD | Not started |
| Fix a defect from the Q&I quality report: [describe] | TBD | TBD | Not started |

### Agreements carried over from the Sprint 1 retro

- [ ] `main` is green before each Friday 4:30 pm sync (CI check `lint-and-test` is required on every PR)
- [ ] Method signatures on shared classes agreed at planning before anyone builds on them
- [ ] Every story estimated and assigned; Jira updated before each Friday sync
- [ ] AI prompt logs kept current in `docs/ai-log/`
- [ ] The retro planned a `docker build` step in CI; it is not in the workflow yet. Decide whether to add it this sprint.

## Quality metrics

Record these at the end of the sprint. Numbers come from GitHub (Actions tab, merged PRs) and from `./gradlew build`.

| Metric | Start of sprint | End of sprint |
| - | - | - |
| JUnit tests | 5 | [ ] |
| Tests passing on `main` | 5 / 5 | [ ] |
| PRs merged into `main` | — | [ ] |
| CI runs that failed, and why | — | [ ] |
| Q&I defects reported / fixed | [ ] | [ ] |
| Open bugs in Jira | [ ] | [ ] |

## Retrospective

**Sprint goal:** [Met / Partly met / Not met], and why in one sentence.

**What went well:**

[ ]

**What went poorly:**

[ ]

**What we're changing for Sprint 4:**

Sprint 4 requires CI to build and test inside the subsystem's container, branch protection, and a code-quality section in the retro.

[ ]

**Velocity:**

Committed: [ ] points. Completed: [ ] points.
