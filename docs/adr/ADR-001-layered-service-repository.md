# ADR-001: Layered service with a repository interface

**Status:** Accepted

**Context:** Shared Core will store the base profile and its role extensions, but which attributes they hold versus us is not agreed. We still need working persistence this sprint.

**Decision:** Make `ProfileData` an interface. The Sprint 1 implementation stores our attributes in memory and reads/writes Shared Core attributes through `/users/{id}` (stubbed if Shared Core is not ready). Controllers and domain classes depend only on the interface.

**Consequences:** We can demo and test immediately. When the attribute split is final, or we move to PostgreSQL, only `ProfileData` changes. Our in-memory data resets on container restart until then.
