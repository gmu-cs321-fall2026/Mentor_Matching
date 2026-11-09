Asked: Asked Claude Code (Claude Opus 5.5) to start Sprint 4's CI requirement, building and testing inside the subsystem's container, on a new `sprint-4-ci` branch. It should also add a smoke test that would have caught the Sprint 3 connections-page bugs.

Produced:
- A `test` stage in the `Dockerfile` that runs `./gradlew build` (compile plus JUnit) inside an `eclipse-temurin:21-jdk` container. Gradle and the dependencies download in their own layer, so source changes don't download them again. A plain `docker build` skips this stage, so the image still builds the old way.
- `scripts/smoke-test.sh`, which needs only `sh`, `curl` and `grep`. It checks that every page and script returns 200, that `/connections` still returns 401 without a token, and that one connection goes PENDING → ACTIVE → COMPLETED with `activeMentees` going 0 → 1 → 0. It also checks that a student can't accept their own request (403).
- A second CI job, `docker-build-and-test`. It runs the test stage, builds the runtime image, starts the container, waits for `/health`, runs the smoke test, and prints the container logs at the end.
- A `.dockerignore` so host `build/`, `.git` and docs stay out of the build context, and a `.gitattributes` that keeps `gradlew` and `*.sh` on LF endings.
- README updates for the two checks and how to run them locally.

Changed or rejected:
- The test stage uses the Ubuntu JDK image, not Alpine like the build stage, because Gradle's native helpers expect glibc. The runtime image stays on the small Alpine JRE.
- Did not make the runtime image depend on the test stage. That would make every plain `docker build` run Gradle and download JUnit, slowing down the compose environment. CI runs the test stage explicitly.
- `gradlew` was CRLF in this Windows checkout (`core.autocrlf=true`), which would break running it in the container. Fixed it twice: `.gitattributes` for future checkouts, and a `sed` in the Dockerfile in case an old checkout still has CRLF.
- Docker isn't installed on this laptop, so the Docker steps haven't run yet; the first PR run is their first test. The smoke test was run against the service on local Java: it passed on the current code (26 checks) and failed on the commit before the Sprint 3 fix (ae8a9e5), with 401 on `/connections.html` and `/connections.js`. That shows it catches the bug. The workflow YAML was checked with PyYAML.
- Branch protection is a repository setting, not code. TODO (me): once this merges, add `docker-build-and-test` as a required check next to `lint-and-test`.
