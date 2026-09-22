# ADR-002: Shared enums for industry and guidance areas

**Status:** Accepted

**Context:** The Sprint 3 matching algorithm must be explainable, and free-text fields make overlap scoring unreliable.

**Decision:** Mentor and student profiles use the same `Industry` and `GuidanceArea` enums; free text only for roles, companies and technical domains.

**Consequences:** Matching becomes set intersection with clear reasons ("you both chose Finance and Mock interviews"). Adding a category means a code change and a contract update.
