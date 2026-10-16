#!/usr/bin/env bash
set -euo pipefail

AUTH_URL="${AUTH_URL:-http://localhost:8080}"
NOTIFICATION_URL="${NOTIFICATION_URL:-http://localhost:8082}"
MAILHOG_URL="${MAILHOG_URL:-http://localhost:8025}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-90}"

EMAIL="e2e-$(date +%s)@sophia.local"
PASSWORD='E2e-passw0rd!'

fail() { echo "FAIL: $*" >&2; exit 1; }
ok()   { echo "ok: $*"; }

wait_for() {
  local description="$1" command="$2" deadline=$((SECONDS + TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    if eval "$command" >/dev/null 2>&1; then ok "$description"; return 0; fi
    sleep 2
  done
  fail "timed out waiting for $description"
}

echo "== registering $EMAIL through the authentication API"
curl -fsS -X POST "$AUTH_URL/api/v1/authentication/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"firstName\":\"E2E\",\"lastName\":\"Probe\"}" \
  >/dev/null || fail "registration rejected"

wait_for "the verification email reached the mail server" \
  "curl -fsS '$MAILHOG_URL/api/v2/search?kind=to&query=$EMAIL' | grep -q 'Verify your email'"

echo "== signing in"
TOKEN=$(curl -fsS -X POST "$AUTH_URL/api/v1/authentication/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
[[ -n "$TOKEN" ]] || fail "no access token in the login response"
ok "authenticated"

echo "== the contact projection reached the notification service"
wait_for "the inbox answers for a user this service was never told about directly" \
  "curl -fsS -H 'Authorization: Bearer $TOKEN' '$NOTIFICATION_URL/api/v1/notifications' | grep -q '\"success\"'"

echo "== preferences round trip"
curl -fsS -X PUT "$NOTIFICATION_URL/api/v1/notification-preferences" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"channels":{"EMAIL":false}}' | grep -q '"EMAIL":false' \
  || fail "the preference update did not take"
ok "preferences updated"

echo "== a mandatory category ignores the preference"
curl -fsS -X POST "$AUTH_URL/api/v1/authentication/forgot-password" \
  -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\"}" >/dev/null || true

wait_for "the password reset email was sent despite email being switched off" \
  "curl -fsS '$MAILHOG_URL/api/v2/search?kind=to&query=$EMAIL' | grep -q 'Reset your password'"

echo
echo "E2E passed"
