#!/bin/sh
# Smoke test for a running Mentor Matching service in stub mode (no SHARED_CORE_URL).
#
#   scripts/smoke-test.sh [base-url]        default http://localhost:8080
#
# Checks that every page and script is served, that the API still requires a
# token, and that one connection goes PENDING -> ACTIVE -> COMPLETED with the
# mentor's activeMentees following it. Needs only sh, curl and grep.
# Run it against a fresh service: it creates the demo profiles.

BASE="${1:-http://localhost:8080}"
MENTOR="Authorization: Bearer mentor-demo"
STUDENT="Authorization: Bearer student-demo"
JSON="Content-Type: application/json"
MENTOR_ID="11111111-1111-1111-1111-111111111111"
BODY="$(mktemp)"
FAILED=0

# expect <status> <description> <curl args...>
expect() {
  want="$1"; what="$2"; shift 2
  got="$(curl -s -o "$BODY" -w '%{http_code}' "$@")"
  if [ "$got" = "$want" ]; then
    echo "ok    $want  $what"
  else
    echo "FAIL  $what: expected $want, got $got"
    head -c 300 "$BODY"; echo
    FAILED=1
  fi
}

# body_has <description> <text>
body_has() {
  if grep -q "$2" "$BODY"; then
    echo "ok          $1"
  else
    echo "FAIL  $1: response did not contain $2"
    head -c 300 "$BODY"; echo
    FAILED=1
  fi
}

echo "Smoke testing $BASE"

# Pages and scripts. /connections.* share a prefix with the API route, which
# once made them answer 401.
expect 200 "GET /health"               "$BASE/health"
for page in / index.html mentorprofile.html studentprofile.html connections.html \
            app.js connections.js styles.css; do
  expect 200 "GET /${page#/}"          "$BASE/${page#/}"
done
expect 200 "connections.html loads connections.js" "$BASE/connections.html"
body_has "script tag points at connections.js" 'src="connections.js"'

# The API still requires a token.
expect 401 "GET /connections without a token" "$BASE/connections"
expect 404 "GET /connectionsfoo is not a route" -H "$MENTOR" "$BASE/connectionsfoo"

# One connection through its lifecycle.
expect 201 "create mentor profile" -X POST "$BASE/mentors/profile" -H "$MENTOR" -H "$JSON" \
  -d '{"industry":"TECHNOLOGY","guidanceAreas":["RESUME_REVIEW"],"hoursPerMonth":4,"contactMethod":"EMAIL","maxMentees":2}'
expect 201 "create student profile" -X POST "$BASE/students/match-profile" -H "$STUDENT" -H "$JSON" \
  -d '{"targetIndustries":["TECHNOLOGY"],"guidanceWanted":["RESUME_REVIEW"]}'
expect 200 "student gets recommendations" -H "$STUDENT" "$BASE/mentors/recommendations"
body_has "demo mentor is recommended" "$MENTOR_ID"

expect 201 "student sends a request" -X POST "$BASE/connections" -H "$STUDENT" -H "$JSON" \
  -d "{\"mentorId\":\"$MENTOR_ID\",\"message\":\"smoke test\"}"
ID="$(grep -o '"id" *: *"[^"]*"' "$BODY" | head -n 1 | sed 's/.*"\([^"]*\)"$/\1/')"
if [ -z "$ID" ]; then
  echo "FAIL  no connection id in the response; skipping the lifecycle checks"
  rm -f "$BODY"
  exit 1
fi

expect 403 "student cannot accept their own request" -X PUT "$BASE/connections/$ID" \
  -H "$STUDENT" -H "$JSON" -d '{"status":"ACTIVE"}'
expect 200 "mentor accepts" -X PUT "$BASE/connections/$ID" -H "$MENTOR" -H "$JSON" -d '{"status":"ACTIVE"}'
expect 200 "mentor profile after accept" -H "$MENTOR" "$BASE/mentors/profile"
body_has "activeMentees is 1" '"activeMentees" *: *1'
expect 200 "student saves shared notes" -X PUT "$BASE/connections/$ID" -H "$STUDENT" -H "$JSON" \
  -d '{"notes":"meet friday"}'
expect 200 "mentor completes" -X PUT "$BASE/connections/$ID" -H "$MENTOR" -H "$JSON" -d '{"status":"COMPLETED"}'
expect 200 "mentor profile after complete" -H "$MENTOR" "$BASE/mentors/profile"
body_has "activeMentees is back to 0" '"activeMentees" *: *0'

rm -f "$BODY"
if [ "$FAILED" -ne 0 ]; then
  echo "Smoke test FAILED"
  exit 1
fi
echo "Smoke test passed"
