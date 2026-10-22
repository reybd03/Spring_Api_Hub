# API Gateway - Staging Verification & Smoke Test Plan

## Overview
This document describes the staging verification process for the `api-gateway` service, including smoke tests, acceptance criteria validation, and deployment verification steps.

## Prerequisites
- API Gateway deployed to staging environment
- Automation Service deployed and accessible via service discovery
- Redis instance available for rate limiting
- Network connectivity between gateway and downstream services
- DNS/service discovery configured for `automation-service`

## Environment Variables
```bash
# Required for staging deployment
SPRING_PROFILES_ACTIVE=staging
SPRING_CLOUD_GATEWAY_ROUTES_0_URI=lb://automation-service
SPRING_REDIS_HOST=staging-redis
SPRING_REDIS_PORT=6379
RESILIENCE4J_CIRCUITBREAKER_INSTANCES_AUTOMATION_SERVICE_WAITDURATIONINOPENSTATE=30s
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics,circuitbreakers
```

## Smoke Test Checklist

### 1. Service Health & Availability
| Test | Endpoint | Expected | Description |
|------|----------|----------|-------------|
| Health Check | `GET /actuator/health` | 200, `{"status":"UP"}` | Overall service health |
| Liveness Probe | `GET /actuator/health/liveness` | 200 | Kubernetes liveness |
| Readiness Probe | `GET /actuator/health/readiness` | 200 | Kubernetes readiness |
| Info Endpoint | `GET /actuator/info` | 200 | Build/deployment info |

### 2. Actuator Endpoints
| Test | Endpoint | Expected | Description |
|------|----------|----------|-------------|
| Metrics | `GET /actuator/metrics` | 200, JSON array | Prometheus metrics available |
| Circuit Breakers | `GET /actuator/circuitbreakers` | 200 | Resilience4j status exposed |
| Health Components | `GET /actuator/health` | `redis:UP`, `circuitBreakers:UP` | Downstream dependencies healthy |

### 3. Fallback Endpoints
| Test | Endpoint | Expected | Description |
|------|----------|----------|-------------|
| Automation Fallback | `GET /fallback/automation` | 503, JSON error | Fallback when automation-service down |
| Default Fallback | `GET /fallback/default` | 503, JSON error | Generic fallback |

### 4. Dynamic Routing
| Test | Endpoint | Expected | Description |
|------|----------|----------|-------------|
| Automation List | `GET /products/automation` | 200/404/503 | Routes to automation-service |
| Jenkins Config | `GET /products/automation/jenkins/config` | 200/404/503 | Routes to Jenkins config |
| Jenkins Actions | `POST /products/automation/jenkins/actions` | 200/404/503 | Routes to Jenkins actions |
| Path Stripping | Verify `/products/automation/**` → `/**` | StripPrefix=2 | Path correctly rewritten |

### 5. Rate Limiting
| Test | Scenario | Expected | Description |
|------|----------|----------|-------------|
| Normal Load | <100 req/s | 200 | Requests under limit succeed |
| Burst Load | >200 req/s burst | 429 after burst | Token bucket burst capacity |
| Sustained Load | >100 req/s sustained | 429 | Replenish rate enforced |
| Per-IP Isolation | Multiple IPs | Independent limits | Key resolver works |

### 6. Circuit Breaker
| Test | Scenario | Expected | Description |
|------|----------|----------|-------------|
| Normal Operation | Downstream healthy | Closed, requests pass | CB closed state |
| Failure Threshold | 50% failures in 10 calls | Open | CB opens after threshold |
| Half-Open | After 30s wait | Half-open, test requests | Automatic transition |
| Recovery | Downstream recovers | Closed | Successful calls close CB |
| Fallback | CB open | 503 from fallback | Fallback invoked |

### 7. Security & Headers
| Test | Check | Expected | Description |
|------|-------|----------|-------------|
| CORS | `OPTIONS /products/automation` | Allow headers | Cross-origin configured |
| Security Headers | Response headers | No sensitive info | No server version leakage |
| X-Forwarded | Request headers | Preserved | Proxy headers passed through |

### 8. Observability
| Test | Check | Expected | Description |
|------|-------|----------|-------------|
| Structured Logs | Application logs | JSON format | Log aggregation ready |
| Trace Context | `traceparent` header | Propagated | Distributed tracing |
| Metrics Labels | Prometheus labels | service=api-gateway | Service identification |
| Access Logs | Request/response | Logged | Audit trail |

## Automated Smoke Test Execution

### Using the Shell Script
```bash
# Make executable
chmod +x staging/smoke-test.sh

# Run against staging
./staging/smoke-test.sh https://api-gateway-staging.example.com

# Run against local
./staging/smoke-test.sh http://localhost:8080
```

### Using curl Directly
```bash
# Health check
curl -f https://api-gateway-staging.example.com/actuator/health | jq '.status'

# Circuit breaker status
curl -s https://api-gateway-staging.example.com/actuator/circuitbreakers | jq .

# Rate limit test
for i in {1..110}; do curl -s -o /dev/null -w "%{http_code}\n" https://api-gateway-staging.example.com/actuator/health; done | sort | uniq -c

# Routing test
curl -s https://api-gateway-staging.example.com/products/automation
```

## Acceptance Criteria Validation

### Functional Requirements
- [ ] Gateway starts on configured port (8080/443)
- [ ] Health endpoint returns UP with all components UP
- [ ] Routes `/products/automation/**` to automation-service
- [ ] Path stripping works (StripPrefix=2)
- [ ] Rate limiting enforces 100 req/s per IP
- [ ] Circuit breaker opens at 50% failure rate
- [ ] Fallback returns 503 with meaningful error
- [ ] CORS configured for frontend origins
- [ ] Actuator endpoints exposed (health, info, metrics, circuitbreakers)

### Non-Functional Requirements
- [ ] Startup time < 30 seconds
- [ ] Memory usage < 512MB heap
- [ ] CPU usage < 50% under normal load
- [ ] Latency overhead < 10ms per request
- [ ] Graceful shutdown completes in < 20s
- [ ] Zero-downtime deployment supported

### Integration Requirements
- [ ] Service discovery resolves `automation-service`
- [ ] Redis connection pool healthy
- [ ] Resilience4j metrics in Prometheus
- [ ] Distributed tracing context propagated
- [ ] Log correlation IDs present

## Deployment Verification Steps

### 1. Pre-Deployment
- [ ] Docker image built and pushed to registry
- [ ] Image scanned for vulnerabilities
- [ ] Configuration validated (no hardcoded secrets)
- [ ] Database migrations (if any) reviewed

### 2. Deployment
- [ ] Rolling update strategy configured
- [ ] Health checks passing before traffic switch
- [ ] Old version drained gracefully
- [ ] New version receiving traffic

### 3. Post-Deployment
- [ ] Smoke test suite passes
- [ ] Monitoring alerts configured
- [ ] Dashboard showing gateway metrics
- [ ] Log aggregation working
- [ ] Trace sampling configured

### 4. Rollback Plan
- [ ] Previous version tagged and available
- [ ] Rollback procedure documented
- [ ] Rollback tested in staging
- [ ] RTO < 5 minutes, RPO = 0

## Test Data Requirements

### Automation Service Mock (for isolated gateway testing)
```yaml
# docker-compose.mock.yml for staging
services:
  automation-service-mock:
    image: wiremock/wiremock:3.5.0
    ports:
      - "8081:8080"
    volumes:
      - ./mappings:/home/wiremock/mappings
    command: --verbose --global-response-templating
```

### Mock Mappings
```json
// mappings/automation-list.json
{
  "request": { "method": "GET", "urlPath": "/api/v1/automation" },
  "response": { "status": 200, "body": "[{\"id\":1,\"productName\":\"Jenkins\"}]" }
}
```

## Monitoring Validation

### Key Metrics to Verify
| Metric | Expected | Alert Threshold |
|--------|----------|-----------------|
| `http_server_requests_seconds_count` | Increasing | - |
| `http_server_requests_seconds_max` | < 1s | > 5s |
| `resilience4j_circuitbreaker_state` | 0 (closed) | 1 (open) |
| `gateway_route_errors_total` | 0 | > 10/min |
| `redis_commands_total` | Increasing | - |
| `jvm_memory_used_bytes` | < 400MB | > 450MB |

### Dashboards to Validate
- [ ] Gateway Overview (request rate, latency, errors)
- [ ] Circuit Breaker State (per service)
- [ ] Rate Limiting (hits, rejections)
- [ ] JVM Metrics (heap, GC, threads)
- [ ] Downstream Service Health

## Sign-Off Checklist

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Developer | | | |
| QA Engineer | | | |
| DevOps/SRE | | | |
| Product Owner | | | |

## Troubleshooting Common Issues

### Health Check Failing
```bash
# Check Redis connectivity
redis-cli -h staging-redis ping

# Check service discovery
curl http://consul:8500/v1/catalog/service/automation-service

# Check gateway logs
kubectl logs -l app=api-gateway -n staging --tail=100
```

### Routing Not Working
```bash
# Verify route configuration
curl http://api-gateway-staging:8080/actuator/gateway/routes

# Check service discovery
curl http://api-gateway-staging:8080/actuator/servicediscovery
```

### Rate Limiting Not Working
```bash
# Verify Redis connection
curl http://api-gateway-staging:8080/actuator/health | jq '.components.redis'

# Check rate limiter config
curl http://api-gateway-staging:8080/actuator/gateway/globalfilters | grep -i ratelimit
```

---

## Version History
| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2026-10-22 | | Initial staging verification plan |