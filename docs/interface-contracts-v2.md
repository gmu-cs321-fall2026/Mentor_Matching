# Mentor Matching: Interface Contracts v2

Subsystem 4 of Mason CareerLaunch. **v2 adds recommendations and connections; everything in [v1](interface-contracts-v1.md) still holds** (conventions, error format, profiles, directory).

> **The project wiki is the source of truth for other teams.** This file is generated from the running service so the wiki page can be written from something that matches it. If you change a route, change both.

## New endpoints

| Method and path | Role | Purpose |
| - | - | - |
| `GET /mentors/recommendations` | Student | The caller's ranked mentor recommendations |
| `GET /connections/recommendations/{userId}` | Student | Same result at the SOW path; `{userId}` must be the caller |
| `POST /connections` | Student | Send a connection request to a mentor |
| `GET /connections` | Any signed-in role | The caller's connections, as student or mentor, newest first |
| `GET /connections/{id}` | Either participant | One connection |
| `PUT /connections/{id}` | Either participant | Accept, decline, withdraw, complete, or edit shared notes |

## Recommendations

Mentors with room to take a mentee, best match first. Mentors who score 0 are left out, so an empty array means no mentor matches yet. Each item is the mentor's profile (as in `GET /mentors`) plus:

| Field | Type | Notes |
| - | - | - |
| `rank` | int | 1 is the best match |
| `compatibilityScore` | number | 0 to 1; weights and rules are documented on `MentorMatcher` |

Errors: `404` if the caller has no matching profile yet; `403` if `{userId}` is someone else.

## Connections

### Lifecycle

| From | To | Who |
| - | - | - |
| `PENDING` | `ACTIVE` | mentor (accept) |
| `PENDING` | `DECLINED` | mentor |
| `PENDING` | `WITHDRAWN` | student (cancel the request) |
| `ACTIVE` | `COMPLETED` | either |
| `ACTIVE` | `WITHDRAWN` | either |

`DECLINED`, `COMPLETED` and `WITHDRAWN` are final. Moving to `ACTIVE` adds 1 to the mentor's `activeMentees`; leaving `ACTIVE` takes 1 away. That is the only way `activeMentees` changes.

### Connection object

```json
{ "id": "f314cadd-595e-40f3-9c15-86437e0eb747",
  "studentId": "22222222-2222-2222-2222-222222222222", "studentName": "Demo Student",
  "mentorId": "11111111-1111-1111-1111-111111111111", "mentorName": "Demo Mentor",
  "status": "ACTIVE", "message": "Hi!", "notes": "Weekly on Tuesdays",
  "createdAt": "2026-10-17T20:40:12.345Z", "updatedAt": "2026-10-17T20:41:03.210Z" }
```

Names come from Shared Core at read time and are `null` if Shared Core does not know the user.

### `POST /connections`

| Field | Type | Rules |
| - | - | - |
| `mentorId` | UUID string | required; the mentor must have a profile |
| `message` | string | optional note to the mentor; max 2000 chars |

Returns `201` with the new `PENDING` connection.

| Error | When |
| - | - |
| `400` | missing or malformed `mentorId`, unknown field, message too long |
| `403` | caller is not a Student |
| `404` | the caller has no matching profile, or the mentor has no profile |
| `409` | the mentor has no room (`hasCapacity` is false), or this student and mentor already have a `PENDING` or `ACTIVE` connection |

### `PUT /connections/{id}`

Send `status`, `notes`, or both. A request that fails changes nothing.

| Field | Type | Rules |
| - | - | - |
| `status` | `ConnectionStatus` | a move allowed by the lifecycle table for the caller's side |
| `notes` | string | max 2000 chars; only on a connection that is `ACTIVE` before or after this request |

| Error | When |
| - | - |
| `400` | unknown status value, unknown field, notes too long, empty body |
| `403` | the move belongs to the other side (e.g. a student accepting) |
| `404` | no such connection, or the caller is not on it |
| `409` | the move is not allowed from the current status, the mentor is full when accepting, or notes on a connection that is not active |

## Open items before this goes on the wiki

- **Notifications (Subsystem 8)** should hear about a new request and an accept or decline. Not wired yet; needs their contract.
- **Session scheduling** is not built. The SOW's text-based proposal and confirm would sit on an active connection.
- **Staff and admin access.** Today only the two participants can read a connection. Career Services Staff reporting on connections needs a route agreed with them.
- **Confirm the `/connections` prefix with Shared Core** for the gateway, as with `/mentors` and `/students`.
