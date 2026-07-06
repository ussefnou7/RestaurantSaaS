# RestaurantSaaS

Multi-tenant restaurant SaaS backend (Spring Boot 4 / Java 21 / PostgreSQL).

## Prerequisites

| Tool | Version |
|---|---|
| Java | **21** (Microsoft OpenJDK or Temurin) |
| Docker Desktop | For local PostgreSQL |
| Maven | Use included `./mvnw` wrapper |

## Quick start

### 1. Start PostgreSQL

Start Docker Desktop, then:

```powershell
docker compose up -d
```

This creates database `restaurant-saas` on `localhost:5432` with user/password `postgres/postgres` (matches `application.yml` defaults).

### 2. Run the API

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
.\mvnw.cmd spring-boot:run
```

Or use **Run and Debug → RestaurantSaasApplication** in Cursor/VS Code (`.vscode/launch.json`).

The server listens on **port 2020** (not 8080).

- Swagger UI: http://localhost:2020/swagger-ui.html
- API docs: http://localhost:2020/api-docs

Flyway runs migrations on startup and seeds a bootstrap sysadmin user:

| Field | Value |
|---|---|
| Username | `nou7` |
| Password | `secret123` |
| Tenant | System (`tenant_id = 0`) |

### 3. Environment overrides

Copy `.env.example` to `.env` if you need custom values. Spring reads these env vars:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SERVER_PORT`
- `APP_JWT_SECRET` → maps to `app.jwt.secret` if you add a profile; default is in `application.yml`

## Frontend

The admin web app (`restaurant-saas-web`) expects the API at `http://localhost:2020`. CORS allows `http://localhost:5180` and `http://localhost:5188`.

## Tests

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
.\mvnw.cmd test
```

Integration tests require a running PostgreSQL instance with the same connection settings.
