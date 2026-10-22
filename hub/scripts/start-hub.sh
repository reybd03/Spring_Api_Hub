#!/usr/bin/env bash
#
# start-hub.sh - start the Spring API Hub application.
#
# Brings up the MySQL dependency declared in compose.yaml (unless --no-db) and then
# starts the hub itself. Database credentials are never hard-coded here: put them in
# hub/.env (git-ignored) or export them before running.
#
# Usage: scripts/start-hub.sh [options]   (see --help)
#
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
HUB_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$HUB_DIR/compose.yaml"
GRADLEW="$HUB_DIR/gradlew"

# --- Defaults (override with flags or environment) ------------------------------------
PROFILE="${SPRING_PROFILES_ACTIVE:-dev}"
START_DB=true
DETACH=false
RUN_JAR=false
BUILD=true
DRY_RUN=false
DEBUG_JVM="${DEBUG_JVM:-false}"
DEBUG_PORT="${DEBUG_PORT:-5005}"
DEBUG_SUSPEND="${DEBUG_SUSPEND:-n}"
DB_SERVICE="${HUB_DB_SERVICE:-mysql}"
DB_WAIT_TIMEOUT="${HUB_DB_WAIT_TIMEOUT:-120}"
APP_PORT="${SERVER_PORT:-8082}"
LOG_DIR="${HUB_LOG_DIR:-$HUB_DIR/logs}"
PID_FILE="${HUB_PID_FILE:-$HUB_DIR/hub.pid}"

log()  { printf '[hub] %s\n' "$*" >&2; }
warn() { printf '[hub] WARNING: %s\n' "$*" >&2; }
die()  { printf '[hub] ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<EOF
Start the Spring API Hub application.

Usage:
  scripts/start-hub.sh [options]

Options:
  -p, --profile <name>   Spring profile to activate (default: ${PROFILE})
      --no-db            Skip starting the MySQL container (assume it is already up)
      --jar              Build (unless --no-build) and run the packaged jar instead of bootRun
      --no-build         With --jar, reuse an existing jar in build/libs instead of building
  -d, --detach           Run in the background (implies --jar); logs go to ${LOG_DIR}
      --debug-jvm        Start the JVM with JDWP debugging enabled
      --debug-port <port> Debug port to listen on (default: ${DEBUG_PORT}; bootRun uses 5005)
      --debug-suspend    Suspend startup until a debugger attaches (jar mode only)
      --dry-run          Print the resolved configuration and exit without starting anything
  -h, --help             Show this help and exit

Environment:
  MYSQL_PASSWORD             Password for the application DB user (required to start MySQL)
  MYSQL_ROOT_PASSWORD        MySQL root password (required to start MySQL)
  SPRING_DATASOURCE_USERNAME DB user (default: rey, must match the MySQL service)
  SPRING_DATASOURCE_PASSWORD Overrides MYSQL_PASSWORD for the application
  HUB_SHUTDOWN_TOKEN         Enables POST /actuator/shutdown when set (blank disables it)
  DEBUG_JVM                  Enable JVM debugging when set to true
  DEBUG_PORT                 Debug listen port for jar mode (default: 5005)
  DEBUG_SUSPEND              Suspend on startup when set to y (jar mode only)
  SERVER_PORT                HTTP port (default: 8082)

Credentials can also live in ${HUB_DIR}/.env, which this script loads automatically.

Examples:
  scripts/start-hub.sh                       # dev profile, start MySQL, run via bootRun
  scripts/start-hub.sh --no-db               # assume MySQL is already running
  scripts/start-hub.sh --profile prod --jar  # build and run the packaged jar
  scripts/start-hub.sh --detach              # build, run in the background
EOF
}

# --- Argument parsing ------------------------------------------------------------------
while (( $# )); do
  case "$1" in
    -p|--profile)      PROFILE="${2:?--profile requires a value}"; shift 2 ;;
    --no-db)           START_DB=false; shift ;;
    --jar)             RUN_JAR=true; shift ;;
    --no-build)        BUILD=false; shift ;;
    -d|--detach)       DETACH=true; RUN_JAR=true; shift ;;
    --debug-jvm)       DEBUG_JVM=true; shift ;;
    --debug-port)      DEBUG_PORT="${2:?--debug-port requires a value}"; shift 2 ;;
    --debug-suspend)   DEBUG_SUSPEND=y; shift ;;
    --dry-run)         DRY_RUN=true; shift ;;
    -h|--help)         usage; exit 0 ;;
    *)             die "Unknown option: $1 (try --help)" ;;
  esac
done

# --- Preflight -------------------------------------------------------------------------
command -v java >/dev/null 2>&1 || die "java not found on PATH; install a JDK (21+) first."
[[ -x "$GRADLEW" ]] || die "Gradle wrapper missing or not executable: $GRADLEW"

# Load local, git-ignored credentials if present.
if [[ -f "$HUB_DIR/.env" ]]; then
  # shellcheck disable=SC1091
  set -a; . "$HUB_DIR/.env"; set +a
fi

# Reconcile the two naming schemes: MySQL container (MYSQL_*) vs Spring (SPRING_DATASOURCE_*).
# A password supplied under either name is used for both. Nothing is exported unless it was
# actually provided, so credentials in application-*.properties are never masked by empty vars.
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}"
if [[ -z "${SPRING_DATASOURCE_PASSWORD:-}" && -n "$MYSQL_PASSWORD" ]]; then
  SPRING_DATASOURCE_PASSWORD="$MYSQL_PASSWORD"
fi
[[ -n "${SPRING_DATASOURCE_USERNAME:-}" ]] && export SPRING_DATASOURCE_USERNAME
[[ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]] && export SPRING_DATASOURCE_PASSWORD

# --- MySQL -----------------------------------------------------------------------------
db_ready() {
  docker compose -f "$COMPOSE_FILE" exec -T "$DB_SERVICE" \
    sh -c 'mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' >/dev/null 2>&1
}

wait_for_db() {
  local deadline=$(( SECONDS + DB_WAIT_TIMEOUT ))
  log "Waiting for MySQL to accept connections (timeout ${DB_WAIT_TIMEOUT}s) ..."
  until db_ready; do
    (( SECONDS < deadline )) || die "MySQL did not become ready within ${DB_WAIT_TIMEOUT}s."
    sleep 2
  done
  log "MySQL is ready."
}

start_db() {
  command -v docker >/dev/null 2>&1 || die "docker not found; needed to start MySQL (use --no-db to skip)."
  docker compose version >/dev/null 2>&1 || die "the docker compose plugin is not available."
  [[ -n "$MYSQL_PASSWORD" ]] \
    || die "MYSQL_PASSWORD is not set. Put it in $HUB_DIR/.env or export it (use --no-db to skip MySQL)."
  [[ -n "${MYSQL_ROOT_PASSWORD:-}" ]] \
    || die "MYSQL_ROOT_PASSWORD is not set. Put it in $HUB_DIR/.env or export it (use --no-db to skip MySQL)."
  export MYSQL_PASSWORD MYSQL_ROOT_PASSWORD
  # compose.yaml derives the container's MYSQL_USER from SPRING_DATASOURCE_USERNAME.
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-rey}"
  log "Starting MySQL service '$DB_SERVICE' ..."
  docker compose -f "$COMPOSE_FILE" up -d "$DB_SERVICE"
  wait_for_db
}

# --- Application -----------------------------------------------------------------------
check_port_free() {
  command -v ss >/dev/null 2>&1 || return 0
  if ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE ":${APP_PORT}\$"; then
    die "Port ${APP_PORT} is already in use; stop the existing process or set SERVER_PORT."
  fi
}

find_jar() {
  local jar="${HUB_JAR:-}"
  if [[ -z "$jar" ]]; then
    jar="$(ls -t "$HUB_DIR"/build/libs/*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -n1 || true)"
  fi
  printf '%s' "$jar"
}

build_jar() {
  log "Building boot jar ..."
  ( cd "$HUB_DIR" && "$GRADLEW" bootJar -x test )
}

run_bootrun() {
  check_port_free
  if [[ "${DEBUG_JVM}" == "true" ]]; then
    log "Starting hub via Gradle bootRun (profile: ${PROFILE}, port: ${APP_PORT}, debug: 5005, suspended) ..."
  else
    log "Starting hub via Gradle bootRun (profile: ${PROFILE}, port: ${APP_PORT}) ..."
  fi
  cd "$HUB_DIR"
  if [[ "${DEBUG_JVM}" == "true" ]]; then
    # --debug-jvm is a Gradle JavaExec option, not an application arg.
    exec "$GRADLEW" bootRun --debug-jvm --args="--spring.profiles.active=${PROFILE}"
  else
    exec "$GRADLEW" bootRun --args="--spring.profiles.active=${PROFILE}"
  fi
}

run_jar_foreground() {
  check_port_free
  $BUILD && build_jar
  local jar; jar="$(find_jar)"
  [[ -n "$jar" && -f "$jar" ]] || die "No boot jar found in build/libs; run without --no-build."
  local java_opts=()
  if [[ "${DEBUG_JVM}" == "true" ]]; then
    java_opts+=("-agentlib:jdwp=transport=dt_socket,server=y,suspend=${DEBUG_SUSPEND},address=*:${DEBUG_PORT}")
    log "Starting hub from $(basename "$jar") (profile: ${PROFILE}, port: ${APP_PORT}, debug: ${DEBUG_PORT}, suspend: ${DEBUG_SUSPEND}) ..."
  else
    log "Starting hub from $(basename "$jar") (profile: ${PROFILE}, port: ${APP_PORT}) ..."
  fi
  # exec keeps the JVM as the script's process so SIGTERM triggers Spring's graceful shutdown.
  exec java "${java_opts[@]}" -jar "$jar" "--spring.profiles.active=${PROFILE}"
}

wait_for_app() {
  command -v curl >/dev/null 2>&1 || return 0
  local deadline=$(( SECONDS + 60 ))
  while (( SECONDS < deadline )); do
    if curl -fsS "http://127.0.0.1:${APP_PORT}/actuator/health" >/dev/null 2>&1; then
      log "Hub is up: http://127.0.0.1:${APP_PORT}"
      return 0
    fi
    sleep 1
  done
  warn "Hub did not report healthy within 60s; check the log."
}

run_jar_detached() {
  check_port_free
  $BUILD && build_jar
  local jar; jar="$(find_jar)"
  [[ -n "$jar" && -f "$jar" ]] || die "No boot jar found in build/libs; run without --no-build."

  mkdir -p "$LOG_DIR"
  local log_file="$LOG_DIR/hub-$(date +%Y%m%d-%H%M%S).log"
  local java_opts=()
  if [[ "${DEBUG_JVM}" == "true" ]]; then
    java_opts+=("-agentlib:jdwp=transport=dt_socket,server=y,suspend=${DEBUG_SUSPEND},address=*:${DEBUG_PORT}")
    log "Starting hub detached (profile: ${PROFILE}, port: ${APP_PORT}, debug: ${DEBUG_PORT}, suspend: ${DEBUG_SUSPEND}); logs -> ${log_file}"
  else
    log "Starting hub detached (profile: ${PROFILE}, port: ${APP_PORT}); logs -> ${log_file}"
  fi
  nohup java "${java_opts[@]}" -jar "$jar" "--spring.profiles.active=${PROFILE}" >"$log_file" 2>&1 &
  local pid=$!
  printf '%s\n' "$pid" > "$PID_FILE"
  wait_for_app
  log "Started with PID ${pid}. Stop gracefully with: kill -TERM ${pid}"
}

# --- Main ------------------------------------------------------------------------------
if $DRY_RUN; then
  log "Dry run - no services started."
  log "  profile        = ${PROFILE}"
  log "  start mysql    = ${START_DB} (service: ${DB_SERVICE})"
  log "  run mode       = $([[ $RUN_JAR == true ]] && echo jar || echo bootRun)"
  log "  build first    = ${BUILD}"
  log "  detach         = ${DETACH}"
  log "  app port       = ${APP_PORT}"
  log "  debug jvm      = ${DEBUG_JVM} (port ${DEBUG_PORT}, suspend ${DEBUG_SUSPEND})"
  log "  db user        = ${SPRING_DATASOURCE_USERNAME:-<from properties>}"
  log "  shutdown token = $([[ -n "${HUB_SHUTDOWN_TOKEN:-}" ]] && echo set || echo 'not set (endpoint disabled)')"
  exit 0
fi

$START_DB && start_db

if [[ -n "${HUB_SHUTDOWN_TOKEN:-}" ]]; then
  log "Shutdown endpoint enabled for this instance."
else
  warn "HUB_SHUTDOWN_TOKEN not set; POST /actuator/shutdown is disabled."
fi

if $RUN_JAR; then
  if $DETACH; then run_jar_detached; else run_jar_foreground; fi
else
  run_bootrun
fi
