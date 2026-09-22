# AI Log: Sprint 1 Architecture Docs

**Date:** 2026-09-22 · **Tool:** Claude · **Author:** Rohan Veeranki
**Files:** `docs/architecture.md`, `docs/adr/ADR-001` to `ADR-004`, wiki contracts page

## Asked

Gave Claude the SOW, Chapter 6, our Sprint 1 file structure and README, and asked for an architecture write-up for our subsystem. Then asked it to export it as markdown files, and later to redo them after learning Shared Core will build a base profile class plus four extended classes (attribute split not decided yet).

## Produced

An architecture doc (pattern, module view, sequence diagram, data model, contracts, Docker setup, risks), four ADRs, and a separate wiki page for the interface contracts.

## Changed or rejected

- **ADR-003 rewritten** to "compose, don't inherit": our service is a separate container, so we can't subclass Shared Core's classes, and the SOW bans duplicating shared entities.
- **ADR-001 and data model updated:** Shared Core now stores profile data, so attributes are marked Shared Core / Us / TBD instead of presented as final.
- **ADR-004 left as Proposed:** the AI suggested Spring Boot, but the framework is a team decision.
- **Assumption not accepted:** the AI assumed the four extended types are the SOW roles; left open until Shared Core confirms.
- **Team review edits:** [add any PR review edits with reasons]
