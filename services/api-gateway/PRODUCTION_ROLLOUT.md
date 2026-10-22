# API Gateway - Production Rollout Plan

## Overview
This document describes the production rollout procedure for the `api-gateway` service, including deployment strategy, rollback plan, monitoring, and post-deployment verification.

## Release Information
- **Service**: api-gateway
- **Version**: 1.0.0
- **Release Date**: 2026-10-22
- **Git Tag**: `api-gateway/v1.0.0`
- **Docker Image**: `ghcr.io/<org>/api-gateway:v1.0.0`

## Pre-Deployment Checklist

### Code & Build
- [ ] All tests pass: `./gradlew test`
- [ ] Build successful: `./gradlew build`
- [ ] Docker image built and pushed to registry
- [ ] Image scanned for vulnerabilities (Trivy/Snyk)
- [ ] SBOM generated and stored

### Configuration
- [ ] Production `application-prod.yml` reviewed
- [ ] No hardcoded secrets in config
- [ ] Externalized secrets (Vault/SealedSecrets)
- [ ] Resource limits configured (CPU/Memory)
- [ ] Health checks configured (liveness/readiness)

### Infrastructure
- [ ] Redis cluster provisioned (HA mode)
- [ ] Service discovery (Consul/Eureka) configured
- [ ] TLS certificates provisioned
- [ ] Network policies applied
- [ ] Ingress/controller configured
- [ ] Prometheus/Grafana dashboards imported

### Documentation
- [ ] Runbook updated
- [ ] API contracts published
- [ ] Changelog updated
- [ ] Rollback procedure documented

## Deployment Strategy: Blue-Green

### Architecture
```
                    ┌─────────────┐
                    │  Ingress    │
                    │  Controller │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
        ┌─────────────┐           ┌─────────────┐
        │ Blue (v1.0) │           │ Green (v1.0)│  ← New version
        │  3 replicas │           │  3 replicas │
        └─────────────┘           └─────────────┘
```

### Steps

#### 1. Deploy Green Environment
```bash
# Deploy new version alongside existing
kubectl apply -f k8s/green-deployment.yaml
kubectl apply -f k8s/green-service.yaml

# Wait for pods to be ready
kubectl rollout status deployment/api-gateway-green -n production --timeout=300s

# Run smoke tests against green
./staging/smoke-test.sh https://api-gateway-green.internal.example.com
```

#### 2. Validate Green
- [ ] All smoke tests pass
- [ ] Health endpoints return UP
- [ ] Metrics flowing to Prometheus
- [ ] No error spikes in logs
- [ ] Circuit breakers in CLOSED state
- [ ] Rate limiting functioning

#### 3. Switch Traffic
```bash
# Update ingress to point to green service
kubectl patch ingress api-gateway -n production \
  -p '{"spec":{"rules":[{"http":{"paths":[{"backend":{"service":{"name":"api-gateway-green"}}}]}]}}'

# Verify traffic routing
curl -H "Host: api.example.com" https://ingress-controller/actuator/health
```

#### 4. Monitor (15 minutes)
- [ ] Request latency p95 < 200ms
- [ ] Error rate < 0.1%
- [ ] CPU/Memory within limits
- [ ] No circuit breaker openings
- [ ] Redis connections stable

#### 5. Decommission Blue
```bash
# After validation period
kubectl delete deployment api-gateway-blue -n production
kubectl delete service api-gateway-blue -n production
```

## Rollback Plan

### Trigger Conditions
- Error rate > 1% for 5 minutes
- p99 latency > 2s for 5 minutes
- Circuit breaker OPEN for > 2 minutes
- Critical business functionality broken

### Rollback Procedure (< 3 minutes)
```bash
# 1. Immediately switch ingress back to blue
kubectl patch ingress api-gateway -n production \
  -p '{"spec":{"rules":[{"http":{"paths":[{"backend":{"service":{"name":"api-gateway-blue"}}}]}]}}'

# 2. Verify rollback
curl -H "Host: api.example.com" https://ingress-controller/actuator/health

# 3. Investigate green environment
kubectl logs -l app=api-gateway,version=green -n production --tail=100

# 4. Scale down green (keep for debugging)
kubectl scale deployment api-gateway-green --replicas=0 -n production
```

### Rollback Verification
- [ ] Traffic routed to blue
- [ ] Health endpoints UP on blue
- [ ] Error rate returns to baseline
- [ ] Latency returns to baseline

## Production Configuration

### application-prod.yml
```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: automation-service
          uri: lb://automation-service
          predicates:
            - Path=/products/automation/**
          filters:
            - StripPrefix=2
            - name: CircuitBreaker
              args:
                name: automation-service
                fallbackUri: forward:/fallback/automation
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 1000
                redis-rate-limiter.burstCapacity: 2000
                key-resolver: "#{@userKeyResolver}"
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "https://app.example.com"
            allowedMethods: GET,POST,PUT,DELETE,OPTIONS
            allowedHeaders: "*"
            allowCredentials: true
            maxAge: 3600
  redis:
    cluster:
      nodes:
        - redis-1.prod:6379
        - redis-2.prod:6379
        - redis-3.prod:6379
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 50
        max-idle: 20
        min-idle: 10

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,circuitbreakers,prometheus
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  health:
    circuitbreakers:
      enabled: true
    redis:
      enabled: true

resilience4j:
  circuitbreaker:
    instances:
      automation-service:
        registerHealthIndicator: true
        slidingWindowSize: 20
        minimumNumberOfCalls: 10
        permittedNumberOfCallsInHalfOpenState: 5
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 60s
        failureRateThreshold: 30
        slowCallRateThreshold: 50
        slowCallDurationThreshold: 5s
  timelimiter:
    instances:
      automation-service:
        timeoutDuration: 10s
        cancelRunningFuture: true

logging:
  level:
    org.springframework.cloud.gateway: INFO
    io.github.resilience4j: INFO
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
  structured:
    format:
      console: json
```

### Kubernetes Deployment
```yaml
# k8s/production-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: production
  labels:
    app: api-gateway
    version: v1.0.0
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 25%
      maxUnavailable: 0
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
        version: v1.0.0
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
        - name: api-gateway
          image: ghcr.io/<org>/api-gateway:v1.0.0
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
              name: http
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: JAVA_OPTS
              value: "-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "1000m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 3
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 30
```

## Monitoring & Alerting

### Key Dashboards
1. **API Gateway Overview** - Request rate, latency, errors
2. **Circuit Breaker Status** - Per downstream service
3. **Rate Limiting** - Rejections, limits hit
4. **JVM Metrics** - Heap, GC, threads
5. **Redis Health** - Connections, latency, memory

### Critical Alerts
| Alert | Condition | Severity | Runbook |
|-------|-----------|----------|---------|
| GatewayDown | `up{job="api-gateway"} == 0` | Critical | [Runbook](runbooks/gateway-down.md) |
| HighErrorRate | `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.01` | Critical | [Runbook](runbooks/high-error-rate.md) |
| HighLatency | `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m])) > 2` | Warning | [Runbook](runbooks/high-latency.md) |
| CircuitBreakerOpen | `resilience4j_circuitbreaker_state > 0` | Critical | [Runbook](runbooks/circuit-breaker-open.md) |
| RateLimitExceeded | `rate(gateway_ratelimit_rejected_total[5m]) > 100` | Warning | [Runbook](runbooks/rate-limit-exceeded.md) |
| RedisDown | `redis_up == 0` | Critical | [Runbook](runbooks/redis-down.md) |
| HighMemory | `jvm_memory_used_bytes / jvm_memory_max_bytes > 0.85` | Warning | [Runbook](runbooks/high-memory.md) |

### SLOs
| SLI | Target | Window |
|-----|--------|--------|
| Availability | 99.9% | 30 days |
| Latency p95 | < 200ms | 30 days |
| Latency p99 | < 500ms | 30 days |
| Error Rate | < 0.1% | 30 days |

## Post-Deployment Verification

### Automated Checks (First 30 minutes)
- [ ] Synthetic monitoring passing
- [ ] Real user monitoring (RUM) normal
- [ ] Business metrics stable
- [ ] No new error patterns in logs
- [ ] Dependency health normal

### Manual Verification (First hour)
- [ ] End-to-end flow: UI → Gateway → Automation Service
- [ ] Jenkins actions execute correctly
- [ ] SSE streams work (sys-stats, config)
- [ ] Rate limiting not impacting legitimate users
- [ ] Circuit breakers remain closed

### 24-Hour Monitoring
- [ ] SLOs within targets
- [ ] No alert fatigue
- [ ] Capacity headroom > 30%
- [ ] Cost within budget

## Communication Plan

### Pre-Deployment (T-1 hour)
- Slack #deployments: "API Gateway v1.0.0 deployment starting"
- Email stakeholders: Deployment schedule

### During Deployment
- Slack #deployments: Real-time updates
- Incident channel if issues arise

### Post-Deployment (T+30 min)
- Slack #deployments: "API Gateway v1.0.0 deployed successfully"
- Update deployment board
- Share dashboard links

### Rollback Communication
- Slack #incidents: "ROLLBACK: API Gateway rolled back to v0.9.x"
- PagerDuty/OnCall notification
- Post-incident review scheduled

## Runbooks Reference
- `runbooks/gateway-down.md`
- `runbooks/high-error-rate.md`
- `runbooks/high-latency.md`
- `runbooks/circuit-breaker-open.md`
- `runbooks/rate-limit-exceeded.md`
- `runbooks/redis-down.md`
- `runbooks/high-memory.md`

## Sign-Off

| Role | Name | Approved | Date |
|------|------|----------|------|
| Tech Lead | | ☐ | |
| SRE Lead | | ☐ | |
| Product Owner | | ☐ | |
| Security | | ☐ | |

---

## Changelog

### v1.0.0 - 2026-10-22
- Initial production release
- Spring Cloud Gateway WebFlux
- Dynamic routing to automation-service
- Redis rate limiting (1000 req/s, burst 2000)
- Resilience4j circuit breakers
- Fallback endpoints
- Full observability stack