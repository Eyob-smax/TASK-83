#!/usr/bin/env bash
# =============================================================================
# EventOps — Demo User Provisioning Script
#
# Registers four demo users (one per role) via the REST API and promotes
# three of them to their target roles via a single SQL update.
#
# Usage:
#   ./seed-demo-users.sh
#
# Prerequisites:
#   - Docker containers running (docker compose up --build)
#   - Backend healthy on https://localhost:8443
# =============================================================================

set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

BASE_URL="https://localhost:8443/api"
MAX_WAIT=90

echo -e "${BLUE}=== EventOps Demo User Provisioning ===${NC}"

# --- Wait for backend health ---
echo -n "Waiting for backend to be healthy..."
attempts=0
while [ $attempts -lt $((MAX_WAIT / 2)) ]; do
  if curl -skf "${BASE_URL}/auth/login" -X POST -H "Content-Type: application/json" \
    -d '{"username":"__healthcheck__","password":"x"}' -o /dev/null 2>/dev/null; then
    break
  fi
  # Even a 401 means the server is up
  status=$(curl -sk -o /dev/null -w "%{http_code}" "${BASE_URL}/auth/login" -X POST \
    -H "Content-Type: application/json" -d '{"username":"__hc__","password":"x"}' 2>/dev/null || echo "000")
  if [ "$status" != "000" ]; then
    break
  fi
  sleep 2
  attempts=$((attempts + 1))
  echo -n "."
done
echo ""

if [ $attempts -ge $((MAX_WAIT / 2)) ]; then
  echo -e "${RED}Backend did not become healthy within ${MAX_WAIT}s. Aborting.${NC}"
  exit 1
fi

echo -e "${GREEN}Backend is up.${NC}"

# --- Register four users ---
register_user() {
  local username=$1
  local password=$2
  local display=$3

  local code
  code=$(curl -sk -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${username}\",\"password\":\"${password}\",\"displayName\":\"${display}\"}")

  if [ "$code" = "201" ] || [ "$code" = "200" ]; then
    echo -e "  ${GREEN}Registered ${username} (${code})${NC}"
  elif [ "$code" = "409" ]; then
    echo -e "  ${BLUE}${username} already exists (${code})${NC}"
  else
    echo -e "  ${RED}Failed to register ${username} (${code})${NC}"
  fi
}

echo ""
echo "Registering demo users..."
register_user "admin"    "admin123"    "Demo Admin"
register_user "staff"    "staff123"    "Demo Staff"
register_user "finance"  "finance123"  "Demo Finance Manager"
register_user "attendee" "attendee123" "Demo Attendee"

# --- Promote users to target roles ---
echo ""
echo "Promoting users to target roles..."
docker exec -i eventops-mysql mysql -u eventops -peventops_dev eventops <<'SQL'
UPDATE users SET role_type = 'SYSTEM_ADMIN'    WHERE username = 'admin'    AND role_type != 'SYSTEM_ADMIN';
UPDATE users SET role_type = 'EVENT_STAFF'     WHERE username = 'staff'    AND role_type != 'EVENT_STAFF';
UPDATE users SET role_type = 'FINANCE_MANAGER' WHERE username = 'finance'  AND role_type != 'FINANCE_MANAGER';
SQL

echo -e "${GREEN}Done.${NC}"
echo ""
echo "Demo credentials:"
echo "  admin    / admin123    (SYSTEM_ADMIN)"
echo "  staff    / staff123    (EVENT_STAFF)"
echo "  finance  / finance123  (FINANCE_MANAGER)"
echo "  attendee / attendee123 (ATTENDEE)"
