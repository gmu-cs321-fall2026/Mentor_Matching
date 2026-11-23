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
| `GET /connections/{id}/sessions` | Either participant | The session log, oldest first |
| `POST /connections/{id}/sessions` | Either participant | Record a session that took place |
| `GET /connections/{id}/schedule` | Either participant | The next session's proposed or confirmed time |
| `POST /connections/{id}/schedule` | Either participant | Propose a time for the next session |
| `PUT /connections/{id}/schedule` | Either participant | Confirm or cancel the proposed time |

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
  "studentEmail": "student@example.com",
  "mentorId": "11111111-1111-1111-1111-111111111111", "mentorName": "Demo Mentor",
  "mentorEmail": "mentor@example.com",
  "status": "ACTIVE", "message": "Hi!", "notes": "Weekly on Tuesdays",
  "schedule": null,
  "createdAt": "2026-10-17T20:40:12.345Z", "updatedAt": "2026-10-17T20:41:03.210Z",
  "sessions": [] }
```

- Names and emails come from Shared Core at read time and are `null` if Shared Core does not know the user.
- `studentEmail` and `mentorEmail` are filled in only while the connection is `ACTIVE`; otherwise they are `null`. Mentor Matching has no messaging, so this is how the two people reach each other. A pending request does not reveal the student's email, and a closed connection stops sharing either email.
- `schedule` is the next session's time (see [Session scheduling](#session-scheduling)), or `null`.
- `sessions` is the session log (see [Session log](#session-log)). It is included only by `GET /connections/{id}` and `PUT /connections/{id}`, not in the `GET /connections` list.

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

## Session log

Either participant records a session after it happens: the date and a short summary. Mentor Matching keeps the log for the participants' own reference; it is not shared with anyone else.

| Method & path | Who | Purpose |
| - | - | - |
| `POST /connections/{id}/sessions` | Either participant | Record a session (connection must be `ACTIVE`) |
| `GET /connections/{id}/sessions` | Either participant | The log, oldest first; still readable after the connection ends |

**`POST` body**

| Field | Type | Rules |
| - | - | - |
| `summary` | string | required; not blank; max 2000 chars |
| `heldOn` | string | optional; `YYYY-MM-DD`; defaults to today in GMU's time zone (`America/New_York`); may not be later than tomorrow (UTC), to allow for time zones |

**Response** (`201` from `POST` with the new entry; `200` from `GET` with an array of entries)

```json
{ "id": "437603a2-6221-47bb-befb-c481cd7a112d", "heldOn": "2026-11-23",
  "summary": "Resume review over Zoom",
  "loggedBy": "22222222-2222-2222-2222-222222222222",
  "loggedAt": "2026-11-23T16:00:08.588Z" }
```

| Error | When |
| - | - |
| `400` | `summary` missing, blank, not text or too long; `heldOn` badly formatted or in the future; unknown field |
| `404` | no such connection, or the caller is not on it |
| `405` | any method other than `GET` or `POST` |
| `409` | the connection is not `ACTIVE`, or its log already has 200 entries |

## Session scheduling

One participant proposes a time for the next session; the other confirms it. Mentor Matching does not book rooms or send invites: `note` says where or how to meet. There is at most one schedule per connection, and a new proposal replaces it (that is how a session is rescheduled). `GET /connections` and `GET /connections/{id}` include it as `schedule`, or `null`.

| Method & path | Who | Purpose |
| - | - | - |
| `POST /connections/{id}/schedule` | Either participant | Propose a time (replaces any current one) |
| `PUT /connections/{id}/schedule` | Either participant | `CONFIRMED` accepts it; `CANCELLED` withdraws, declines or cancels it |
| `GET /connections/{id}/schedule` | Either participant | The current proposal or confirmed time |

**`POST` body**

| Field | Type | Rules |
| - | - | - |
| `startsAt` | string | required; ISO 8601 with an offset or `Z`, e.g. `2026-11-24T18:00:00-05:00`; in the future and within a year |
| `note` | string | optional; max 500 chars |

**`PUT` body**

| Field | Type | Rules |
| - | - | - |
| `proposalId` | UUID | required; the proposal the caller is answering |
| `status` | string | `CONFIRMED` or `CANCELLED` |

**Response** (`201` from `POST`, `200` from `PUT` and `GET`; `PUT` with `CANCELLED` returns `null`)

```json
{
  "id": "6f1c…", "startsAt": "2026-11-24T23:00:00Z", "note": "Zoom",
  "status": "CONFIRMED", "proposedBy": "2222…", "proposedAt": "2026-11-20T15:00:00Z",
  "confirmedBy": "1111…", "confirmedAt": "2026-11-20T16:10:00Z"
}
```

| Error | When |
| - | - |
| `400` | missing or badly formatted `startsAt`, a time in the past or more than a year away, note too long, unknown field, bad `proposalId` or `status` |
| `403` | confirming your own proposal |
| `404` | no such connection, the caller is not on it, or (`GET`) nothing is scheduled |
| `409` | the connection is not `ACTIVE`; nothing proposed; the proposal was replaced by a newer one; already confirmed; or its time has passed |

When the connection leaves `ACTIVE`, an unconfirmed proposal is dropped and a confirmed one is kept read-only as a record.

## Open items before this goes on the wiki

- **Notifications (Subsystem 6)** should hear about a new request and an accept or decline. Not wired yet; needs their contract.
- **Staff and admin access.** Today only the two participants can read a connection. Career Services Staff reporting on connections needs a route agreed with them.
- **Confirm the `/connections` prefix with Shared Core** for the gateway, as with `/mentors` and `/students`.
