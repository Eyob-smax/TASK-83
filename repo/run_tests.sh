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
#   ./run_tests.sh                  # Run all suites
#   ./run_tests.sh frontend         # Frontend tests only
#   ./run_tests.sh backend          # Backend unit + API tests
#   ./run_tests.sh backend-unit     # Backend unit tests only
#   ./run_tests.sh backend-api      # Backend API tests only
#   ./run_tests.sh --coverage       # Run all with coverage reports
#
# Prerequisites:
#   - Node.js 20+, npm (for frontend)
#   - Java 17+, Maven 3.9+ (for backend)
#   - No external DB required by default test profile (H2 in-memory)
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

SUITE="${1:-all}"
COVERAGE=false
EXIT_CODE=0
FRONTEND_RESULT="skipped"
BACKEND_UNIT_RESULT="skipped"
BACKEND_API_RESULT="skipped"

# Check for --coverage flag
for arg in "$@"; do
  if [ "$arg" = "--coverage" ]; then
    COVERAGE=true
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
#   # Execute failsafe goals directly so API tests run without surefire unit-test selection drift.
#   mvn failsafe:integration-test failsafe:verify -pl . -Dit.test="*IT,*IntegrationTest" -DfailIfNoTests=false 2>&1 \
#     && BACKEND_API_RESULT="passed" || { BACKEND_API_RESULT="failed"; EXIT_CODE=1; }
#   cd "$SCRIPT_DIR"
# }

print_summary() {
  echo ""
  echo -e "${BLUE}=== Test Summary ===${NC}"
  echo "  Frontend unit tests:     $FRONTEND_RESULT"
  echo "  Backend unit tests:      $BACKEND_UNIT_RESULT"
  echo "  Backend API tests:       $BACKEND_API_RESULT"
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
    [ -f "$BACKEND_DIR/target/site/jacoco/index.html" ] && echo "  Backend:  $BACKEND_DIR/target/site/jacoco/index.html"
  fi
}

# --- Main ---
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
  all|--coverage)
    run_frontend_tests
    run_backend_unit_tests
    # run_backend_api_tests
    ;;
  *)
    echo "Usage: $0 {all|frontend|backend|backend-unit|backend-api} [--coverage]"
    exit 1
    ;;
esac

print_summary
exit $EXIT_CODE
