# Mentor Matching: Interface Contracts v1

Subsystem 4 of Mason CareerLaunch. Every route below is served by our container behind the Shared Core gateway.

> **The project wiki is the source of truth for other teams.** This file is the copy generated from the Sprint 1 implementation, so the wiki page can be written from something that matches the running service. Keep them in step: if you change a route, change both.

## Conventions

- JSON in, JSON out; `Content-Type: application/json`.
- Every route except `GET /enums` and `GET /health` needs `Authorization: Bearer <token>`. We validate it with Shared Core's `GET /auth/validate` and use the `userId` and `role` it returns.
- A profile route only ever reads or writes **the caller's own** profile. There is no route for reading someone else's profile by id in Sprint 1.
- Enum values are sent as their constant names (`RESUME_REVIEW`). Input is tolerant of case, spaces and hyphens; output is always the canonical name.
- Every error uses the shared format from SOW Section 6:

```json
{ "error": "one or more fields are invalid", "code": "validation_failed",
  "detail": { "hoursPerMonth": "choose between 1 and 40 hours a month" } }
```

`detail` is an object of field-to-message for validation failures and a plain string otherwise.

## Status codes

| Code | When |
| - | - |
| 200 | Read or update succeeded |
| 201 | Profile created |
| 400 | `validation_failed`, `malformed_json` or `body_too_large` |
| 401 | `unauthorized` — token missing, malformed or rejected |
| 403 | `forbidden` — valid token, wrong role |
| 404 | `not_found` — no profile yet, or no such route |
| 405 | `method_not_allowed` |
| 409 | `conflict` — a profile already exists; use PUT |
| 500 | `internal_error` — never carries internal detail |
| 502 | `shared_core_unavailable` — Shared Core did not answer |

## Endpoints

| Method and path | Role | Purpose |
| - | - | - |
| `POST /mentors/profile` | Mentor | Create the caller's mentor profile |
| `GET /mentors/profile` | Mentor | Read the caller's mentor profile |
| `PUT /mentors/profile` | Mentor | Update the caller's mentor profile |
| `POST /students/match-profile` | Student | Create the caller's matching profile |
| `GET /students/match-profile` | Student | Read the caller's matching profile |
| `PUT /students/match-profile` | Student | Update the caller's matching profile |
| `GET /mentors` | Any signed-in role | Paginated, filterable mentor directory |
| `GET /enums` | Public | The agreed `Industry`, `GuidanceArea` and `ContactMethod` values |
| `GET /health` | Public | Liveness check for compose and the gateway |

### Mentor profile

Request fields. Everything here is ours or still TBD in the data model; `userId` comes from the token, and `activeMentees` is owned by connections in Sprint 3 and rejected if sent.

| Field | Type | Rules |
| - | - | - |
| `industry` | `Industry` or null | — |
| `guidanceAreas` | array of `GuidanceArea` | at least one |
| `technicalDomains` | array of string | required with `TECHNICAL_DOMAIN`, rejected without it; max 10 entries, 100 chars each |
| `hoursPerMonth` | int | 1 to 40 |
| `contactMethod` | `ContactMethod` | required |
| `maxMentees` | int | 0 or more, and not below `activeMentees` |
| `acceptingMentees` | boolean | defaults to true on create |

```
POST /mentors/profile
{ "industry": "FINANCE", "guidanceAreas": ["RESUME_REVIEW", "MOCK_INTERVIEW"],
  "hoursPerMonth": 8, "contactMethod": "VIDEO_CALL", "maxMentees": 3, "acceptingMentees": true }

201 Created
{ "userId": "1111...", "name": "Demo Mentor", "email": "mentor@example.com",
  "program": "MS Computer Science", "gradYear": 2018,
  "employer": "Capital One", "title": "Senior Engineer",
  "industry": "FINANCE", "guidanceAreas": ["RESUME_REVIEW", "MOCK_INTERVIEW"],
  "technicalDomains": [], "hoursPerMonth": 8, "contactMethod": "VIDEO_CALL",
  "maxMentees": 3, "activeMentees": 0, "acceptingMentees": true, "hasCapacity": true }
```

`name`, `email`, `program`, `gradYear`, `employer` and `title` are Shared Core's, merged in on read. `hasCapacity` is derived: `acceptingMentees && activeMentees < maxMentees`.

`PUT` takes the same fields and applies only the ones present, so a partial update is fine. Free-text entries are trimmed, blanks dropped, and case-insensitive duplicates collapsed.

### Student matching profile

| Field | Type | Rules |
| - | - | - |
| `targetIndustries` | array of `Industry` | at least one of this or `guidanceWanted` |
| `guidanceWanted` | array of `GuidanceArea` | at least one of this or `targetIndustries` |
| `targetRoles` | array of string | max 10 entries, 100 chars each |
| `targetCompanies` | array of string | max 10 entries, 100 chars each |
| `preferSameProgram` | boolean | defaults to false |

```
POST /students/match-profile
{ "targetIndustries": ["FINANCE", "TECHNOLOGY"], "targetRoles": ["Data Analyst", "Quant"],
  "guidanceWanted": ["RESUME_REVIEW"], "targetCompanies": ["Capital One"], "preferSameProgram": true }

201 Created  — the fields above, merged under userId, name, email, program and gradYear.
```

### Mentor directory

`GET /mentors?industry=FINANCE&guidance=RESUME_REVIEW&withCapacityOnly=true&page=1&size=20`

| Parameter | Default | Notes |
| - | - | - |
| `industry` | any | an `Industry` value |
| `guidance` | any | a `GuidanceArea` value |
| `withCapacityOnly` | `false` | keep only mentors who can take another mentee |
| `page` | 1 | 1-based |
| `size` | 20 | 1 to 100 |

Filters combine with AND. Results are ordered by `userId`, so paging is stable.

```json
{ "items": [ { "...one mentor profile..." } ],
  "page": 1, "size": 20, "total": 6, "totalPages": 1 }
```

A page past the end returns `200` with an empty `items` array, not a 404.

## Open items before this goes on the wiki

- **Confirm the `/mentors` and `/students` prefixes with Shared Core** so nothing clashes in the gateway. This is already on the architecture's risk list.
- **`GET /enums` is our addition,** not something the SOW asked for. It exists so the forms do not keep a second copy of the `Industry` and `GuidanceArea` lists that ADR-002 requires to match on both sides. Worth agreeing before other teams depend on it.
- **The `Industry` values are placeholders.** The agreed list needs sign-off from Shared Core and Subsystems 2 and 8.
- **The attribute split is unsettled.** `industry`, `hoursPerMonth` and `contactMethod` are served by us today and may move to Shared Core; if they do, they leave these request bodies.
