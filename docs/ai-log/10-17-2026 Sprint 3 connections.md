Asked: Asked Claude Code (Claude Opus 5.5) to move the Mentor Matching repo to Sprint 3 on a new `sprint-3` branch of the team repo. Three parts: (1) build the connection requests feature from the SOW (`POST /connections`, `PUT /connections/{id}`, `GET /connections/recommendations/{userId}`), (2) update the docs from Sprint 1 to Sprint 3, and (3) add a Sprint 3 plan and retro template. Commit locally; I push and open the PR myself.

Produced:
- New domain classes `Connection` and `ConnectionStatus` (PENDING, ACTIVE, DECLINED, COMPLETED, WITHDRAWN). `Connection` owns the lifecycle table: only the mentor can accept or decline, only the student can cancel a pending request, and either side can complete or withdraw an active one.
- `ConnectionService`, which works like `EditProfile`. It checks who may make each move, adds 1 to the mentor's `activeMentees` on accept and takes 1 away when an active connection ends, and checks everything before changing anything, so a rejected request leaves no partial change.
- New `ProfileData` / `InMemoryProfileData` methods to save, find, update and list connections, keeping ADR-001's single persistence boundary.
- Routes in `MentoringApp`: `POST`/`GET /connections`, `GET`/`PUT /connections/{id}`, and `GET /connections/recommendations/{userId}`, which reuses `rankMentors`.
- `ConnectionServiceTest` with 5 tests: the full lifecycle and capacity count, wrong-side moves (403) and moves out of final states (409), a full mentor, one open connection per pair, and rejected updates changing nothing. All 10 tests pass with `gradlew build`. The AI also started the server and walked through request → accept → list over HTTP.
- Docs: README rewritten for Sprint 3, a Sprint 3 section in `architecture.md` with a lifecycle diagram, the new `interface-contracts-v2.md`, and the `sprint3.md` plan and retro template.

Changed or rejected:
- The first draft of `ConnectionService.update` could save new notes and then fail on the status change, leaving a half-applied update. Rewritten during the session to check the move, capacity and notes length before changing anything; a test covers this.
- The first README draft said recommendations and CI were "Sprint 2 — done". Changed to "Since Sprint 1", because we didn't confirm which sprint they landed in.
- The Shared Core stub was not removed, even though the Sprint 1 docs said it would be gone by Sprint 2. The tests and demos depend on it, so the docs now say it is still on and must be off in the compose environment.
- The Gradle wrapper files changed to 9.7.1 outside the session (probably a VS Code extension). They were left out of the commit, and the build was re-run on the committed 8.14.3 wrapper to confirm it passes.
- Not built yet: the connections UI, session scheduling, and notifications to Subsystem 8.
- TODO (me): review the code and tests myself and note anything I changed or rejected, with my reasons.
