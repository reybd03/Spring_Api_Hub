```
microservices/spring/api/
├── settings.gradle
├── build.gradle (common plugins & dependencies)
├── compose.yaml (multi-container dev environment)
├── services/
│   ├── api-gateway/           # Standalone routing, auth, rate limiting
│   ├── web-portal/            # Thymeleaf UI or frontend BFF
│   ├── automation-service/    # Automation & Jenkins domain API
│   └── runner-agent/          # Worker executing Docker/host commands
└── shared/
    ├── common-dto/            # Shared payloads & event contracts
    └── common-security/       # Shared JWT/auth verification filters
```
