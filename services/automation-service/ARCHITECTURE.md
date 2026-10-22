# Automation Service - Domain Decomposition Architecture

## Overview
This document describes the architecture for decomposing the automation domain from the monolithic `hub` into a dedicated `automation-service` with its own database (database-per-service pattern).

## Current State (in Hub)
The automation domain in `hub` consists of:

### Entities
- **AutomationEntity** (JPA) - Single table `automation` storing Jenkins configuration and status

### Repository
- **AutomationRepository** - JPA repository with custom query for duplicate deletion

### Services
- **AutomationService** - Core business logic:
  - CRUD operations for AutomationEntity
  - System metrics collection (CPU, memory, swap, uptime)
  - Server-Sent Events (SSE) streaming for real-time metrics
  - Reactive Mono wrappers for blocking DB calls

- **AutomationActions** - Process management:
  - PID-based process checking
  - Docker Compose status checking
  - Process execution with output capture

- **Jenkins** - Jenkins-specific logic:
  - Entity verification and creation
  - Discovery via file system (JenkinsDiscovery)
  - Configuration updates
  - WebClient calls to Jenkins REST API (toggle start/stop)

- **JenkinsDiscovery** - File system scanning:
  - Default Jenkins home paths (Linux, Windows, macOS)
  - Config.xml detection
  - Docker/Docker Compose detection

- **JenkinsExecutorService** - Docker Compose lifecycle:
  - Start/stop/restart via `docker compose`
  - PID tracking and status updates

### Controllers
- **ProductAutomationController** - `GET /products/automation` (list all)
- **JenkinsController** - `GET/POST /products/automation/jenkins/*`:
  - Page rendering (Thymeleaf)
  - Config updates
  - Action execution (start/stop/restart)
  - SSE streams for sys stats and config

## Target Architecture

### 1. Database-per-Service
- **automation-service** owns the `automation` table
- **Hub** no longer accesses automation data directly
- Migration: Move `automation` table to automation-service database
- Hub's `automation` table can be dropped after migration

### 2. Service Boundaries
```
automation-service (port 8081, own DB)
├── AutomationEntity (JPA)
├── AutomationRepository
├── AutomationService (metrics, CRUD, SSE)
├── AutomationActions (process mgmt)
├── Jenkins (Jenkins-specific logic)
├── JenkinsDiscovery (file system)
├── JenkinsExecutorService (docker compose)
├── Controllers:
│   ├── AutomationController (REST API)
│   ├── JenkinsController (REST API)
│   └── JenkinsSseController (SSE endpoints)
└── Health/Actuator endpoints
```

### 3. API Contracts

#### AutomationController
```
GET    /api/v1/automation           - List all automation entities
GET    /api/v1/automation/{id}      - Get by ID
POST   /api/v1/automation           - Create new
PUT    /api/v1/automation/{id}      - Update
DELETE /api/v1/automation/{id}      - Delete
```

#### JenkinsController
```
GET    /api/v1/jenkins              - Get Jenkins details
GET    /api/v1/jenkins/config       - Get Jenkins configuration
POST   /api/v1/jenkins/config       - Update Jenkins configuration
POST   /api/v1/jenkins/actions      - Execute action (start/stop/restart)
POST   /api/v1/jenkins/discover     - Trigger Jenkins discovery
```

#### JenkinsSseController
```
GET    /api/v1/jenkins/stream/sys-stats  - SSE: System metrics (1s interval)
GET    /api/v1/jenkins/stream/config     - SSE: Config changes (1s interval)
```

#### Health/Actuator
```
GET    /actuator/health
GET    /actuator/info
GET    /actuator/metrics
GET    /actuator/health/readiness
GET    /actuator/health/liveness
```

### 4. Data Model
**AutomationEntity** (unchanged, moves to automation-service):
- id (PK)
- product_name (unique)
- product_discovery_status
- product_discovered
- product_base_path
- product_user_name
- product_password
- product_api_key
- product_url
- product_port
- product_process_id
- product_status
- product_functions
- product_actions

### 5. Gateway Routing (from api-gateway)
```
/products/automation/**           → automation-service:8081 (StripPrefix=2)
  /products/automation            → GET /api/v1/automation
  /products/automation/jenkins/** → /api/v1/jenkins/**
```

### 6. Dependencies (automation-service)
```gradle
dependencies {
    implementation project(':shared:common-dto')
    implementation project(':shared:common-security')
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'  // If keeping server-side rendering
    implementation 'com.mysql:mysql-connector-j'
    implementation 'org.projectlombok:lombok'
}
```

### 7. Hub Changes Required
1. **Remove** automation packages: `com.spring.api.hub.products.automation.*`
2. **Remove** controllers: `ProductAutomationController`, `JenkinsController`
3. **Remove** automation DB table from hub schema
4. **Add** Feign/WebClient client for automation-service calls (if hub needs automation data)
5. **Update** gateway routing to point to automation-service

### 8. Acceptance Criteria
- [ ] automation-service starts on port 8081 with own database
- [ ] All automation entities migrated to automation-service DB
- [ ] REST APIs match specified contracts
- [ ] SSE endpoints work for real-time streaming
- [ ] Jenkins discovery works in isolated service
- [ ] Docker Compose lifecycle management works
- [ ] Hub no longer has automation domain code
- [ ] Gateway routes `/products/automation/**` to automation-service
- [ ] Integration tests pass
- [ ] Actuator endpoints exposed

### 9. Migration Strategy
1. **Phase 3a**: Create automation-service with all domain code
2. **Phase 3b**: Run both hub and automation-service with separate DBs
3. **Phase 3c**: Migrate data from hub.automation to automation-service.automation
3. **Phase 3d**: Remove automation code from hub
4. **Phase 3e**: Update gateway routing, verify end-to-end

### 10. Testing Strategy
- Unit tests for AutomationService, AutomationActions, JenkinsDiscovery
- Integration tests with Testcontainers (MySQL, Docker)
- Contract tests for REST APIs and SSE endpoints
- End-to-end tests via gateway

### 11. Deployment Considerations
- Separate MySQL database for automation-service
- Docker daemon access required for `docker compose` commands
- File system access for Jenkins discovery (may need volume mounts)
- Health checks include DB connectivity and Docker daemon access
- Graceful shutdown for SSE streams (reuse hub's DrainSignal pattern)

---

## Changelog

### [1.0.0] - 2026-10-20 - Phase 3: Domain Decomposition Design

#### Designed
- Database-per-service pattern for automation domain
- REST API contracts for automation and Jenkins management
- SSE endpoint contracts for real-time streaming
- Service boundaries and dependency mapping
- Migration strategy from hub to automation-service
- Deployment requirements (Docker daemon, file system access)