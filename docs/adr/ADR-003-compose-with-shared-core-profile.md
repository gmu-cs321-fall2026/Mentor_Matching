# ADR-003: Compose with the Shared Core profile, don't inherit

**Status:** Accepted

**Context:** Shared Core is building a base profile class and four extended classes. Our service runs in a separate container, so we cannot import their classes, and the SOW forbids duplicating shared entities.

**Decision:** Our `MentorProfile` and `StudentMatchProfile` hold `userId` and our own attributes only. Shared Core attributes come from `GET /users/{id}` as JSON and are mapped in at read time. We never subclass or copy their attributes.

**Consequences:** No stale copies, and each team deploys independently. Every profile read costs one extra call to Shared Core, fine at course scale. Their schema changes after Sprint 2 require written notice to us, per the SOW.
