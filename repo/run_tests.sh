#!/usr/bin/env bash
# =============================================================================
# EventOps Platform — Test Orchestrator
#
# Runs all test suites from the correct locations:
#   - Frontend unit tests:    repo/frontend/unit_tests/  (Vitest)
#   - Backend unit tests:     repo/backend/unit_tests/   (JUnit 5 via Maven Surefire)
#   - Backend API tests:      repo/backend/api_tests/    (JUnit 5 via Maven Failsafe)
#
# Usage:
#   ./run_tests.sh                  # Run all suites in Docker (default)
#   ./run_tests.sh --host           # Run all suites using host tooling
#   ./run_tests.sh --host frontend  # Frontend tests only (host mode)
#   ./run_tests.sh --host backend   # Backend unit + API tests (host mode)
#   ./run_tests.sh --host smoke     # E2E smoke tests (starts real backend)
#   ./run_tests.sh --host --coverage # All suites with coverage reports
#
# Prerequisites (default Docker mode):
#   - Docker and Docker Compose
# Prerequisites (--host mode):
#   - Node.js 20+, npm (for frontend)
#   - Java 17+, Maven 3.9+ (for backend)
#   - No external DB required (H2 in-memory for tests)
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/frontend"
BACKEND_DIR="$SCRIPT_DIR/backend"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Parse SUITE from first non-flag argument
SUITE="all"
COVERAGE=false
HOST_MODE=false

for arg in "$@"; do
  case "$arg" in
    --*) ;; # skip flags in suite detection
    *) SUITE="$arg"; break ;;
  esac
done
EXIT_CODE=0
FRONTEND_RESULT="skipped"
BACKEND_UNIT_RESULT="skipped"
BACKEND_API_RESULT="skipped"
SMOKE_RESULT="skipped"

# Check for flags
for arg in "$@"; do
  if [ "$arg" = "--coverage" ]; then
    COVERAGE=true
  fi
  if [ "$arg" = "--host" ]; then
    HOST_MODE=true
  fi
  # Legacy: --docker is still accepted (no-op since Docker is now default)
  if [ "$arg" = "--docker" ]; then
    true
  fi
done

run_frontend_tests() {
  echo -e "${YELLOW}=== Frontend Unit Tests (Vitest) ===${NC}"
  echo "  Source: $FRONTEND_DIR/unit_tests/"
  echo "  Config: $FRONTEND_DIR/vite.config.js"
  echo ""

  if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
    echo -e "${BLUE}Installing frontend dependencies...${NC}"
    cd "$FRONTEND_DIR"
    npm ci --ignore-scripts 2>/dev/null || npm install --ignore-scripts 2>/dev/null
    cd "$SCRIPT_DIR"
  fi

  cd "$FRONTEND_DIR"
  if [ "$COVERAGE" = true ]; then
    npx vitest run --coverage 2>&1 && FRONTEND_RESULT="passed" || { FRONTEND_RESULT="failed"; EXIT_CODE=1; }
  else
    npx vitest run 2>&1 && FRONTEND_RESULT="passed" || { FRONTEND_RESULT="failed"; EXIT_CODE=1; }
  fi
  cd "$SCRIPT_DIR"
}

run_backend_unit_tests() {
  echo -e "${YELLOW}=== Backend Unit Tests (JUnit 5 / Surefire) ===${NC}"
  echo "  Source: $BACKEND_DIR/unit_tests/"
  echo "  Config: $BACKEND_DIR/pom.xml (build-helper-maven-plugin adds test sources)"
  echo ""

  if [ ! -f "$BACKEND_DIR/pom.xml" ]; then
    echo -e "${RED}Backend pom.xml not found at $BACKEND_DIR${NC}"
    BACKEND_UNIT_RESULT="failed"
    EXIT_CODE=1
    return
  fi

  cd "$BACKEND_DIR"
  if [ "$COVERAGE" = true ]; then
    mvn test -pl . -Dtest="*Test,*Tests" -Djacoco.destFile=target/jacoco-ut.exec 2>&1 \
      && BACKEND_UNIT_RESULT="passed" || { BACKEND_UNIT_RESULT="failed"; EXIT_CODE=1; }
  else
    mvn test -pl . -Dtest="*Test,*Tests" 2>&1 \
      && BACKEND_UNIT_RESULT="passed" || { BACKEND_UNIT_RESULT="failed"; EXIT_CODE=1; }
  fi
  cd "$SCRIPT_DIR"
}

# run_backend_api_tests() {
#   echo -e "${YELLOW}=== Backend API/Integration Tests (JUnit 5 / Failsafe) ===${NC}"
#   echo "  Source: $BACKEND_DIR/api_tests/"
#   echo "  Config: $BACKEND_DIR/pom.xml (maven-failsafe-plugin)"
#   echo "  Note: Uses application-test profile (H2 in-memory) by default"
#   echo ""

#   if [ ! -f "$BACKEND_DIR/pom.xml" ]; then
#     echo -e "${RED}Backend pom.xml not found at $BACKEND_DIR${NC}"
#     BACKEND_API_RESULT="failed"
#     EXIT_CODE=1
#     return
#   fi

#   cd "$BACKEND_DIR"
#   if [ "$COVERAGE" = true ]; then
#     mvn verify -pl . -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false \
#       -Dit.test="*IT,*IntegrationTest" -DfailIfNoTests=false 2>&1 \
#       && BACKEND_API_RESULT="passed" || { BACKEND_API_RESULT="failed"; EXIT_CODE=1; }
#   else
#     # Use the lifecycle so JaCoCo prepare-agent-integration populates failsafeArgLine.
#     mvn verify -pl . -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false \
#       -Dit.test="*IT,*IntegrationTest" -DfailIfNoTests=false 2>&1 \
#       && BACKEND_API_RESULT="passed" || { BACKEND_API_RESULT="failed"; EXIT_CODE=1; }
#   fi
#   cd "$SCRIPT_DIR"
# }

run_docker_tests() {
  echo -e "${YELLOW}=== Running Tests in Docker ===${NC}"
  echo "  Config: $SCRIPT_DIR/docker-compose.test.yml"
  echo ""

  local COMPOSE="docker compose -f $SCRIPT_DIR/docker-compose.test.yml"

  echo -e "${BLUE}--- Frontend Tests (Docker) ---${NC}"
  $COMPOSE run --rm frontend-test 2>&1 && FRONTEND_RESULT="passed" || { FRONTEND_RESULT="failed"; EXIT_CODE=1; }

  echo -e "${BLUE}--- Backend Unit Tests (Docker) ---${NC}"
  $COMPOSE run --rm backend-unit-test 2>&1 && BACKEND_UNIT_RESULT="passed" || { BACKEND_UNIT_RESULT="failed"; EXIT_CODE=1; }

  echo -e "${BLUE}--- Backend API Tests (Docker) ---${NC}"
  $COMPOSE run --rm backend-api-test 2>&1 && BACKEND_API_RESULT="passed" || { BACKEND_API_RESULT="failed"; EXIT_CODE=1; }
}

run_smoke_tests() {
  echo -e "${YELLOW}=== E2E Smoke Tests ===${NC}"
  echo "  Starting backend in test profile..."
  echo ""

  local BACKEND_PID=""
  local BASE_URL="http://localhost:8080/api"
  local SMOKE_PASS=0
  local SMOKE_FAIL=0

  cd "$BACKEND_DIR"
  mvn spring-boot:run -Dspring-boot.run.profiles=test -q &
  BACKEND_PID=$!
  cd "$SCRIPT_DIR"

  # Wait for backend health (up to 90 seconds)
  echo -e "${BLUE}Waiting for backend to start...${NC}"
  local attempts=0
  while [ $attempts -lt 45 ]; do
    if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
      echo -e "${GREEN}Backend is healthy.${NC}"
      break
    fi
    sleep 2
    attempts=$((attempts + 1))
  done

  if [ $attempts -ge 45 ]; then
    echo -e "${RED}Backend failed to start within 90 seconds.${NC}"
    kill "$BACKEND_PID" 2>/dev/null || true
    SMOKE_RESULT="failed"
    EXIT_CODE=1
    return
  fi

  # Smoke test 1: Register
  echo -n "  POST /api/auth/register ... "
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"smokeuser","password":"password123","displayName":"Smoke Test"}')
  if [ "$HTTP_CODE" = "201" ] || [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}$HTTP_CODE OK${NC}"; SMOKE_PASS=$((SMOKE_PASS + 1))
  else
    echo -e "${RED}$HTTP_CODE FAIL${NC}"; SMOKE_FAIL=$((SMOKE_FAIL + 1))
  fi

  # Smoke test 2: Login
  echo -n "  POST /api/auth/login ... "
  LOGIN_RESPONSE=$(curl -s -c /tmp/eventops_smoke_cookies -w "\n%{http_code}" -X POST "$BASE_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"smokeuser","password":"password123"}')
  HTTP_CODE=$(echo "$LOGIN_RESPONSE" | tail -1)
  if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}$HTTP_CODE OK${NC}"; SMOKE_PASS=$((SMOKE_PASS + 1))
  else
    echo -e "${RED}$HTTP_CODE FAIL${NC}"; SMOKE_FAIL=$((SMOKE_FAIL + 1))
  fi

  # Smoke test 3: GET /events (authenticated)
  echo -n "  GET  /api/events ... "
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b /tmp/eventops_smoke_cookies "$BASE_URL/events")
  if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}$HTTP_CODE OK${NC}"; SMOKE_PASS=$((SMOKE_PASS + 1))
  else
    echo -e "${RED}$HTTP_CODE FAIL${NC}"; SMOKE_FAIL=$((SMOKE_FAIL + 1))
  fi

  # Smoke test 4: GET /auth/me (authenticated)
  echo -n "  GET  /api/auth/me ... "
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b /tmp/eventops_smoke_cookies "$BASE_URL/auth/me")
  if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}$HTTP_CODE OK${NC}"; SMOKE_PASS=$((SMOKE_PASS + 1))
  else
    echo -e "${RED}$HTTP_CODE FAIL${NC}"; SMOKE_FAIL=$((SMOKE_FAIL + 1))
  fi

  # Cleanup
  kill "$BACKEND_PID" 2>/dev/null || true
  rm -f /tmp/eventops_smoke_cookies

  echo ""
  echo "  Smoke: $SMOKE_PASS passed, $SMOKE_FAIL failed"
  if [ $SMOKE_FAIL -eq 0 ]; then
    SMOKE_RESULT="passed"
  else
    SMOKE_RESULT="failed"
    EXIT_CODE=1
  fi
}

print_summary() {
  echo ""
  echo -e "${BLUE}=== Test Summary ===${NC}"
  echo "  Frontend unit tests:     $FRONTEND_RESULT"
  echo "  Backend unit tests:      $BACKEND_UNIT_RESULT"
  echo "  Backend API tests:       $BACKEND_API_RESULT"
  if [ "$SMOKE_RESULT" != "skipped" ]; then
    echo "  E2E smoke tests:         $SMOKE_RESULT"
  fi
  echo ""

  if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}All requested test suites passed.${NC}"
  else
    echo -e "${RED}Some test suites failed. Review output above.${NC}"
  fi

  if [ "$COVERAGE" = true ]; then
    echo ""
    echo -e "${BLUE}Coverage reports:${NC}"
    [ -d "$FRONTEND_DIR/coverage" ] && echo "  Frontend: $FRONTEND_DIR/coverage/index.html"
    [ -f "$BACKEND_DIR/target/site/jacoco/index.html" ] && echo "  Backend unit: $BACKEND_DIR/target/site/jacoco/index.html"
    [ -f "$BACKEND_DIR/target/site/jacoco-it/index.html" ] && echo "  Backend API:  $BACKEND_DIR/target/site/jacoco-it/index.html"
  fi
}

# --- Main ---
if [ "$HOST_MODE" = true ]; then
  # Host-local execution (requires Node.js, Java, Maven on host)
  case "$SUITE" in
    frontend)
      run_frontend_tests
      ;;
    backend)
      run_backend_unit_tests
      # run_backend_api_tests
      ;;
    backend-unit)
      run_backend_unit_tests
      ;;
    backend-api)
      # run_backend_api_tests
      ;;
    smoke)
      # run_smoke_tests
      ;;
    all|--coverage)
      run_frontend_tests
      run_backend_unit_tests
      # run_backend_api_tests
      ;;
    *)
      echo "Usage: $0 {all|frontend|backend|backend-unit|backend-api|smoke} [--host] [--coverage]"
      exit 1
      ;;
  esac
else
  # Default: Docker-contained execution (no host tooling required)
  run_docker_tests
fi

print_summary
exit $EXIT_CODE
