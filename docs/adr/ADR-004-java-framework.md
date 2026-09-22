# ADR-004: Java framework

**Status:** Proposed

**Context:** The team chose Java. We need HTTP routing, JSON binding and input validation with little setup.

**Decision:** Spring Boot (web + validation), unless the team prefers the JDK's built-in HTTP server for a lighter start.

**Consequences:** Spring Boot gives validation annotations, easy tests and a later JPA/PostgreSQL path, at the cost of more startup magic to learn.
