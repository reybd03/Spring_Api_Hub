#!/usr/bin/env bash
# Staging Verification & Smoke Test Script for API Gateway
# Usage: ./staging-smoke-test.sh <staging-url>
# Example: ./staging-smoke-test.sh https://api-gateway-staging.example.com

set -euo pipefail

STAGING_URL="${1:-http://localhost:8080}"
TIMEOUT=30
RETRIES=3

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

check_endpoint() {
    local name="$1"
    local url="$2"
    local expected_status="${3:-200}"
    local description="$4"
    
    log_info "Checking $name: $url"
    
    local response
    local http_code
    response=$(curl -s -w "\n%{http_code}" --max-time "$TIMEOUT" "$url" 2>/dev/null || true)
    http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | head -n-1)
    
    if [[ "$http_code" == "$expected_status" ]]; then
        log_info "✓ $name - HTTP $http_code"
        return 0
    else
        log_error "✗ $name - Expected HTTP $expected_status, got $http_code"
        log_error "  Response: $body"
        return 1
    fi
}

check_json_field() {
    local name="$1"
    local url="$2"
    local field="$3"
    local expected_value="$4"
    
    log_info "Validating $name JSON field: $field"
    
    local response
    response=$(curl -s --max-time "$TIMEOUT" -H "Accept: application/json" "$url" 2>/dev/null || true)
    
    if echo "$response" | jq -e "$field" >/dev/null 2>&1; then
        local actual_value=$(echo "$response" | jq -r "$field")
        if [[ "$actual_value" == "$expected_value" ]]; then
            log_info "✓ $name - $field = $actual_value"
            return 0
        else
            log_error "✗ $name - $field expected '$expected_value', got '$actual_value'"
            return 1
        fi
    else
        log_error "✗ $name - Field $field not found in response"
        return 1
    fi
}

check_rate_limit() {
    local url="$1"
    local limit=100
    
    log_info "Testing rate limiting (will make $((limit + 10)) requests)..."
    
    local blocked=0
    for i in $(seq 1 $((limit + 10))); do
        http_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$url" 2>/dev/null || echo "000")
        if [[ "$http_code" == "429" ]]; then
            blocked=1
            log_info "Rate limit triggered at request $i (HTTP 429)"
            break
        fi
    done
    
    if [[ "$blocked" == "1" ]]; then
        log_info "✓ Rate limiting is working"
        return 0
    else
        log_warn "Rate limit not triggered (may be disabled in staging)"
        return 0
    fi
}

check_circuit_breaker() {
    local url="$1"
    
    log_info "Checking circuit breaker endpoint..."
    
    local response
    response=$(curl -s --max-time "$TIMEOUT" -H "Accept: application/json" "$url" 2>/dev/null || true)
    
    if echo "$response" | jq -e '.circuitBreakers' >/dev/null 2>&1; then
        log_info "✓ Circuit breaker endpoint accessible"
        return 0
    else
        log_warn "Circuit breaker endpoint not returning expected structure"
        return 0
    fi
}

main() {
    log_info "=== API Gateway Staging Verification ==="
    log_info "Target: $STAGING_URL"
    log_info "Timeout: ${TIMEOUT}s, Retries: $RETRIES"
    echo
    
    local failed=0
    
    # 1. Health endpoint
    check_endpoint "Health" "$STAGING_URL/actuator/health" 200 "Service health check" || ((failed++))
    check_json_field "Health" "$STAGING_URL/actuator/health" ".status" "UP" || ((failed++))
    
    # 2. Info endpoint
    check_endpoint "Info" "$STAGING_URL/actuator/info" 200 "Service info" || ((failed++))
    
    # 3. Metrics endpoint
    check_endpoint "Metrics" "$STAGING_URL/actuator/metrics" 200 "Prometheus metrics" || ((failed++))
    
    # 4. Circuit breakers endpoint
    check_endpoint "CircuitBreakers" "$STAGING_URL/actuator/circuitbreakers" 200 "Circuit breaker status" || ((failed++))
    check_circuit_breaker "$STAGING_URL/actuator/circuitbreakers" || true
    
    # 5. Fallback endpoints
    check_endpoint "Automation Fallback" "$STAGING_URL/fallback/automation" 503 "Fallback for automation service" || ((failed++))
    check_json_field "Automation Fallback" "$STAGING_URL/fallback/automation" ".error" "Service Unavailable" || ((failed++))
    
    check_endpoint "Default Fallback" "$STAGING_URL/fallback/default" 503 "Default fallback" || ((failed++))
    check_json_field "Default Fallback" "$STAGING_URL/fallback/default" ".error" "Service Unavailable" || ((failed++))
    
    # 6. Routing to automation-service (will 404/503 if downstream not available)
    check_endpoint "Automation Route (list)" "$STAGING_URL/products/automation" 404 "Route to automation-service" || true
    check_endpoint "Automation Route (jenkins)" "$STAGING_URL/products/automation/jenkins" 404 "Route to Jenkins endpoint" || true
    
    # 7. Unknown route should 404
    check_endpoint "Unknown Route" "$STAGING_URL/unknown/path" 404 "Unknown route returns 404" || ((failed++))
    
    # 8. CORS headers
    log_info "Checking CORS headers..."
    cors_headers=$(curl -s -I -X OPTIONS --max-time "$TIMEOUT" \
        -H "Origin: https://example.com" \
        -H "Access-Control-Request-Method: GET" \
        -H "Access-Control-Request-Headers: Content-Type" \
        "$STAGING_URL/products/automation" 2>/dev/null || true)
    
    if echo "$cors_headers" | grep -qi "access-control-allow-origin"; then
        log_info "✓ CORS headers present"
    else
        log_warn "CORS headers not found (may be configured differently)"
    fi
    
    # 9. Rate limiting test (optional, may be disabled in staging)
    check_rate_limit "$STAGING_URL/actuator/health" || true
    
    # 10. Graceful shutdown endpoint (if enabled)
    check_endpoint "Shutdown Endpoint" "$STAGING_URL/actuator/shutdown" 405 "Shutdown endpoint exists (POST only)" || true
    
    echo
    log_info "=== Summary ==="
    if [[ "$failed" -eq 0 ]]; then
        log_info "All critical checks passed! ✓"
        exit 0
    else
        log_error "$failed critical check(s) failed"
        exit 1
    fi
}

main "$@"