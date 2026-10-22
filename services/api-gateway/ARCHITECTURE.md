# API Gateway Service Architecture

## Overview
This document describes the architecture for the `api-gateway` service, extracted from the monolithic `hub` as part of Phase 2 of the microservices migration.

## Current State (Before Migration)
- **Hub** (port 8082): Contains `GatewayConfig` with disabled routing to avoid loopback conflicts with internal controllers at `/products/automation/**`
- **Hub Controllers**: `ProductAutomationController` and `JenkinsController` handle `/products/automation/**` endpoints
- **Automation Service** (port 8081): Target service for automation domain
- **API Gateway** (port 8080): Exists but minimally configured

## Target Architecture

### 1. Gateway Configuration Migration
Move `GatewayConfig` from `hub/src/main/java/com/spring/api/hub/GatewayConfig.java` to `api-gateway/src/main/java/com/spring/api/gateway/config/GatewayConfig.java`

### 2. Dynamic Routing Configuration
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: automation-service
          uri: lb://automation-service
          predicates:
            - Path=/products/automation/**
          filters:
            - StripPrefix=2  # Remove /products/automation prefix
            - name: CircuitBreaker
              args:
                name: automation-service
                fallbackUri: forward:/fallback/automation
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
                key-resolver: "#{@ipKeyResolver}"
```

### 3. Rate Limiting
- **Algorithm**: Token bucket via Redis Rate Limiter
- **Default**: 100 requests/second per IP, burst capacity 200
- **Key Resolver**: IP-based (configurable for user-based in future)
- **Storage**: Redis (required dependency)

### 4. Circuit Breakers (Resilience4j)
```yaml
resilience4j:
  circuitbreaker:
    instances:
      automation-service:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 30s
        failureRateThreshold: 50
        eventConsumerBufferSize: 10
```

### 5. Fallback Handling
- Global fallback controller at `/fallback/automation`
- Returns 503 with meaningful error message
- Logs circuit breaker events for monitoring

## Dependencies to Add to api-gateway
```gradle
dependencies {
    // Existing
    implementation project(':shared:common-dto')
    implementation project(':shared:common-security')
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway-server-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    
    // New for Phase 2
    implementation 'org.springframework.cloud:spring-cloud-starter-loadbalancer'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
    implementation 'io.github.resilience4j:resilience4j-spring-boot3'
    implementation 'io.github.resilience4j:resilience4j-reactor'
    implementation 'io.github.resilience4j:resilience4j-micrometer'
}
```

## Hub Changes Required
1. **Remove** `spring-cloud-starter-gateway-server-webflux` dependency
2. **Remove** `GatewayConfig.java`
3. **Remove/Relocate** `ProductAutomationController.java` and `JenkinsController.java` to automation-service
4. **Keep** hub-specific business logic only

## Acceptance Criteria
- [x] api-gateway starts successfully on port 8080
- [x] GatewayConfig moved from hub to api-gateway
- [x] Dynamic routing `/products/automation/**` → automation-service:8081 works
- [x] Rate limiting configured (100 req/s per IP default)
- [x] Circuit breakers configured with Resilience4j
- [x] Hub no longer has spring-cloud-starter-gateway dependency
- [ ] Hub internal controllers at `/products/automation/**` removed (part of Phase 3)
- [x] Integration tests pass
- [x] Actuator endpoints exposed: health, info, metrics, circuitbreakers

## Testing Strategy
- Unit tests for RouteLocator configuration
- Integration tests with Testcontainers (Redis, automation-service mock)
- Contract tests for fallback responses
- Load tests for rate limiting verification

## Deployment Considerations
- Redis instance required for rate limiting
- Service discovery (Consul/Eureka) for `lb://automation-service` URI
- Health checks must include circuit breaker status
- Graceful shutdown configuration aligned with hub

---

## Changelog

### [1.0.0] - 2026-10-20 - Phase 2: Extract Spring Cloud Gateway Service

#### Added
- **New Service**: `services/api-gateway` - Dedicated Spring Cloud Gateway WebFlux service
- **GatewayConfig**: Moved from `hub` to `api-gateway` with enhanced routing capabilities
- **Dynamic Routing**: `/products/automation/**` routes to `automation-service` via service discovery (`lb://automation-service`)
- **Rate Limiting**: Redis-based token bucket rate limiting (100 req/s per IP, burst 200)
- **Circuit Breakers**: Resilience4j integration for downstream service protection
- **Fallback Controller**: Graceful degradation endpoints at `/fallback/automation` and `/fallback/default`
- **Actuator Endpoints**: health, info, metrics, circuitbreakers exposed
- **Test Suite**: 11 tests covering integration, fallback, and configuration

#### Changed
- **Hub**: Removed `spring-cloud-starter-gateway-server-webflux` dependency
- **Hub**: Removed `GatewayConfig.java` (moved to api-gateway)
- **Root build.gradle**: Added testcontainers BOM for test dependency management

#### Dependencies Added (api-gateway)
- `org.springframework.cloud:spring-cloud-starter-loadbalancer` - For `lb://` URI support
- `org.springframework.boot:spring-boot-starter-data-redis-reactive` - For rate limiting
- `io.github.resilience4j:resilience4j-spring-boot3` - Circuit breaker framework
- `io.github.resilience4j:resilience4j-reactor` - Reactor integration
- `io.github.resilience4j:resilience4j-micrometer` - Metrics integration

#### Test Dependencies Added (api-gateway)
- `org.springframework.boot:spring-boot-starter-webflux-test` - WebFlux test support
- `org.springframework.boot:spring-boot-testcontainers` - Testcontainers integration
- `org.testcontainers:junit-jupiter:1.20.1` - JUnit Jupiter for Testcontainers

#### Verification
- All modules build successfully: `./gradlew build`
- All tests pass: `./gradlew test` (28 tests across all modules)
- api-gateway check task passes: `./gradlew :services:api-gateway:check`