# ADR-004: Java framework

**Status:** Proposed — needs a decision at the next Friday sync

**Context:** The team chose Java. We need HTTP routing, JSON binding and input validation with little setup.

**Decision:** Spring Boot (web + validation), unless the team prefers the JDK's built-in HTTP server for a lighter start.

**Consequences:** Spring Boot gives validation annotations, easy tests and a later JPA/PostgreSQL path, at the cost of more startup magic to learn.

## What Sprint 1 actually built, and why

Sprint 1 is implemented on **the JDK's built-in HTTP server** (`com.sun.net.httpserver`), this ADR's stated alternative. The status stays Proposed because the team has not met on it; this section records the situation so the sync is a real decision rather than a rubber stamp.

The reason is the Sprint 1 Dockerfile. It builds with `javac` and no Maven or Gradle, so there is no way to resolve a Spring Boot dependency. Choosing Spring Boot means also adding a build tool and a wrapper to the container, which is a bigger change than the walking skeleton needed.

What that cost us, all of it small and contained:

- `Json` is a hand-rolled JSON reader and writer, about 80 lines, because Jackson is not on the classpath.
- `ProfileBinding` does by hand what `@Valid` and the binding annotations would do, and produces the same per-field 400 body.
- Routing is a handful of `createContext` calls in `MentoringApp` rather than annotated controllers.

**If the team picks Spring Boot at the sync,** the work is: add Maven or Gradle, change the Dockerfile's build stage, replace `MentoringApp`'s routing with `@RestController` classes, and delete `Json` and most of `ProfileBinding`. The domain classes, `ProfileData` and its implementation, and the whole frontend are untouched, because none of them import anything framework-specific. That is the layering ADR-001 asked for doing its job.

**If the team keeps the JDK server,** mark this Accepted and note that a test framework still has to be chosen separately — with no dependency manager there is no JUnit either, which is the real gap this choice leaves.
