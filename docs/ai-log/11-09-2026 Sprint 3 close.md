Asked: Asked Claude Code (Claude Opus 5.5) to summarize where the architecture and sprints stand, then to "close sprint 3": bring the code and docs in line with what was merged to `main`, fix anything blocking the Sprint 3 flows, and fill in the Sprint 3 plan, metrics and retro.

Produced:
- Worked on a new branch, `sprint-3-close`, cut from `origin/main`, because the local `sprint-3` branch was behind PRs #3–#12.
- Ran the service locally and tried the whole connection flow with `curl`: create both profiles, get recommendations, send a request, check that a student can't accept (403), accept as the mentor, add notes, complete, and confirm `activeMentees` goes up and back down.
- Found that `/connections.html` and `/connections.js` returned `401`. `HttpServer` contexts match by prefix, so the `/connections` API context caught the page and its script. Fixed in `MentoringApp`: only `/connections` and `/connections/...` reach the API, and every other path under that prefix goes to `staticFile`.
- Renamed `static/Connections.js` to `connections.js` to match the `<script src>`. The old name only worked because Windows ignores filename case; the Linux container doesn't.
- Linked the connections page from `index.html` and added the v2 endpoints to its table.
- Updated the README (Sprint 3 marked done, connections UI listed, the "no UI" known gap removed), added the page and a note about the route prefix to the Sprint 3 section of `architecture.md`, and rewrote `sprint3.md` with story owners, metrics (26/26 tests, 11 PRs merged) and a draft retro.
- `gradlew.bat build` passes: 26 tests, 0 failures.

Changed or rejected:
- Left out a local, uncommitted Gradle wrapper upgrade (8.14.3 to 9.7.1). It was not part of Sprint 3, CI pins 8.14.3, and a major Gradle upgrade should be its own reviewed PR. It is saved in `git stash`.
- Reviewed Jacob's reorder in `ConnectionService.update` (commit 074d498) and made no change. Every check still runs before anything changes, so it behaves the same as before.
- Story points, CI failure counts and the Q&I defect were not filled in. They live in Jira, the GitHub Actions tab and the Q&I report, which the AI could not see, so they stay as `TBD`/`[ ]` rather than invented numbers.
- The retro is a draft based on the repository history. TODO (team): edit it at the retro meeting.
