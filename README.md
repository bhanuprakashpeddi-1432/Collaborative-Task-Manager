# WorkCollab

WorkCollab is a role-based collaborative task management application. It combines a Spring Boot REST API with a React/Vite client for workspace, project, Kanban task, reporting, comments, presence, and task audit-history workflows.

The repository is suitable for local development and demonstration. The Docker Compose setup provides a production-like local stack, but a real production deployment still requires organization-specific secret management, HTTPS, restricted origins, backups, monitoring, and operational policies.

## What the application provides

- Workspace-based access control for administrators, managers, and team members.
- Project and Kanban task management with automatic default columns.
- Task assignment, priorities, due dates, progress, and time tracking.
- Optimistic locking to prevent stale concurrent task edits from silently overwriting changes.
- Task comments and a chronological, access-controlled audit history.
- An Inbox for direct messages with role-limited recipient selection.
- WebSocket/STOMP collaboration events with Redis support for message distribution.
- Project-scoped reports for completion, progress, status, priority, overdue work, and time tracking.
- Workspace Settings for names and member management, subject to role permissions.
- JWT authentication with login, refresh, logout, and registration endpoints.

## Roles and permissions

### Global administrator

An administrator can:

- View all workspaces, projects, tasks, reports, and workspace members.
- Create a workspace and assign it to a manager.
- Rename workspaces and manage workspace memberships.
- View project dashboards and project data.

Project creation, editing, and archiving are restricted to the assigned workspace manager. The administrator can view project data but is not granted project-write operations by the project API.

### Manager

A manager can access only workspaces explicitly assigned to that manager. In an assigned workspace, a manager can:

- Create, edit, and archive projects.
- Create tasks and assign them to workspace team members.
- Set task priority and manage task status, description, progress, dates, and time values.
- Rename the workspace.
- Add or remove members with the `TEAM_MEMBER` workspace role.

Managers cannot add or remove other managers through the current Settings UI.

### Team member

A team member can access only assigned workspaces and receives task results filtered to tasks assigned to that account. Settings membership data is read-only for team members.

### Inbox messaging

- Administrators can message any other registered user.
- Managers can message global administrators and team members assigned to the manager's workspaces.
- Recipients are calculated and checked by the backend; changing or bypassing the frontend cannot send a message to an unauthorized user.
- Messages include a subject, content, sender, recipient, read state, and creation time.
- Team members currently have the Inbox navigation available but do not have permitted recipients under the requested messaging rules.

### Workspace roles

Workspace memberships are separate from global system roles. The application uses roles such as `OWNER`, `ADMIN`, `MANAGER`, `TEAM_MEMBER`, `MEMBER`, and `VIEWER` where applicable. Authorization is enforced by workspace membership and, for administrators, the global `ADMIN` role.

## Main workflow

1. Sign in as an administrator.
2. Create a workspace and select a manager from the manager list.
3. Sign in as that manager.
4. Create a project in the assigned workspace.
5. The project is initialized with `To Do`, `In Progress`, and `Done` task lists.
6. Create tasks, set priorities, and assign them to workspace team members.
7. Move tasks across Kanban lists or edit them in the task details modal.
8. Open a task to view comments and its audit history.
9. Open Reports to review project-level metrics.
10. Use Settings to rename the workspace or manage permitted team-member memberships.
11. Use Inbox to send direct messages to recipients permitted by the signed-in user's role.

## Technology

### Backend

- Java 21
- Spring Boot 3.3.0
- Spring MVC and Bean Validation
- Spring Security with JWT
- Spring Data JPA and Hibernate
- PostgreSQL 16
- Redis 7
- WebSocket/STOMP
- Maven Wrapper

### Frontend

- React 19
- TypeScript 6
- Vite 8
- TanStack React Query
- Zustand
- Axios
- Tailwind CSS
- `@hello-pangea/dnd` for Kanban drag and drop
- SockJS and STOMP client for collaboration updates

### Testing

- JUnit 5
- Spring Boot test support
- Testcontainers
- PostgreSQL and Redis containers for integration tests

## Repository layout

```text
.
├── frontend/
│   ├── src/
│   │   ├── components/       # Board, task modal, reports, presence UI
│   │   ├── hooks/            # React Query, drag, WebSocket, presence hooks
│   │   ├── lib/              # Axios API client
│   │   ├── pages/            # Workspace page and application views
│   │   └── store/            # Authentication state
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── src/main/java/com/workcollab/
│   ├── config/               # Startup seed and application configuration
│   ├── controller/           # REST endpoints
│   ├── dto/                  # Request and response models
│   ├── entity/               # JPA entities
│   ├── repository/           # Spring Data repositories and specifications
│   ├── security/             # JWT and workspace authorization
│   └── service/              # Application services
├── src/main/resources/
│   ├── application.yml       # Local defaults
│   └── schema.sql            # PostgreSQL schema reference/init script
├── src/test/                 # Testcontainers integration tests
├── Dockerfile                # Backend image
├── docker-compose.yml        # PostgreSQL, Redis, backend, and frontend
├── .env.example              # Docker environment template
└── pom.xml
```

## Prerequisites

For local development:

- Java 21 JDK
- Node.js and npm
- PostgreSQL 16
- Redis 7

For Docker Compose and integration tests:

- Docker Desktop with a working Docker daemon
- Docker Compose v2

## Local development

### 1. Start PostgreSQL and Redis

The default local configuration expects:

- PostgreSQL: `localhost:5432`
- Database: `workcollab`
- Database user: `workcollab`
- Database password: `changeme`
- Redis: `localhost:6379`

You can start both dependencies with Docker:

```powershell
docker run -d --name workcollab-postgres -p 5432:5432 `
  -e POSTGRES_DB=workcollab `
  -e POSTGRES_USER=workcollab `
  -e POSTGRES_PASSWORD=changeme `
  postgres:16-alpine

docker run -d --name workcollab-redis -p 6379:6379 redis:7-alpine
```

If these container names already exist, start them with:

```powershell
docker start workcollab-postgres workcollab-redis
```

### 2. Start the backend

From the repository root:

```powershell
$env:JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"
.\mvnw.cmd spring-boot:run
```

The backend listens on `http://localhost:8081`.

The UTC setting is useful on Windows systems where the JVM reports the legacy `Asia/Calcutta` timezone and PostgreSQL rejects it. It is not required on every machine.

Backend health:

```text
http://localhost:8081/actuator/health
```

The local configuration uses `spring.jpa.hibernate.ddl-auto=update` and runs the supplied `schema.sql`. This is convenient for development; use managed migrations and an explicit production database policy before deploying to production.

After adding or updating the Inbox feature, restart the backend so Hibernate can create or update the `direct_messages` table:

```powershell
Ctrl+C
$env:JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"
.\mvnw.cmd spring-boot:run
```

The timezone setting is a Windows development workaround for environments where the JVM reports the legacy `Asia/Calcutta` timezone to PostgreSQL. If the application starts without that error, the backend can be run with `.\mvnw.cmd spring-boot:run` alone.

### 3. Start the frontend

In a second terminal:

```powershell
Set-Location .\frontend
npm install
npm run dev
```

Open the Vite URL, normally:

```text
http://localhost:5173
```

The Vite development server proxies `/api` and `/ws` to the backend on port `8081`.

## Demo accounts

The startup seeder creates or repairs the following local demonstration accounts. These credentials are for development only:

| Account | Email | Password | Global role |
|---|---|---|---|
| Administrator | `admin@demo.com` | `admin123` | `ADMIN` |
| Manager 1 | `manager1@demo.com` | `admin123` | `MANAGER` |
| Manager 2 | `manager2@demo.com` | `admin123` | `MANAGER` |
| Team member | `mem1@demo.com` | `admin123` | `TEAM_MEMBER` |

Do not use these passwords or the checked-in development JWT defaults in a production environment.

## Important API areas

The backend is rooted at `/api`.

| Area | Representative endpoints |
|---|---|
| Authentication | `POST /api/auth/login`, `/refresh`, `/logout`, `/register` |
| Workspaces | `GET /api/workspaces`, `POST /api/workspaces` |
| Managers | `GET /api/admin/managers` |
| Workspace members | `GET/POST /api/workspaces/{workspaceId}/members` |
| Workspace settings | `PUT /api/workspaces/{workspaceId}` |
| Projects | `GET /api/workspaces/{workspaceId}/projects`, `PUT/DELETE /api/workspaces/{workspaceId}/projects/{projectId}` |
| Project details | `GET /api/projects/{projectId}` |
| Tasks | `GET/POST /api/projects/{projectId}/tasks` |
| Task changes | `PUT/DELETE /api/projects/{projectId}/tasks/{taskId}` |
| Task movement | `PATCH /api/projects/{projectId}/tasks/{taskId}/move` |
| Comments | `GET/POST /api/tasks/{taskId}/comments` |
| Audit history | `GET /api/tasks/{taskId}/activity` |
| Inbox | `GET /api/inbox`, `GET /api/inbox/contacts`, `POST /api/inbox` |
| Mark message read | `PATCH /api/inbox/{messageId}/read` |

Task endpoints require a `workspaceId` query parameter where indicated by the frontend. The authenticated JWT identifies the current user; workspace access is checked on the server.

### Inbox API behavior

`GET /api/inbox/contacts` returns the allowed recipients for the authenticated user:

- `ADMIN`: all other registered users.
- `MANAGER`: all administrators and team members belonging to the manager's assigned workspaces.
- `TEAM_MEMBER`: no recipients under the current messaging policy.

`POST /api/inbox` accepts a recipient ID, subject, and content. The backend checks the recipient permission again before saving the message; the frontend recipient list is not a security boundary. `GET /api/inbox` returns messages sent or received by the current user, and `PATCH /api/inbox/{messageId}/read` can be used only by the message recipient.

## Docker Compose

The Compose stack starts PostgreSQL, Redis, the Spring Boot backend, and the NGINX-served frontend.

1. Copy the environment template:

   ```powershell
   Copy-Item .env.example .env
   ```

2. Replace `DB_PASSWORD`, `JWT_SECRET`, and `WEBSOCKET_ALLOWED_ORIGINS` with environment-appropriate values.

3. Build and start:

   ```powershell
   docker compose up -d --build
   ```

4. Open:

   ```text
   http://localhost
   ```

Useful commands:

```powershell
docker compose ps
docker compose logs -f backend
docker compose logs -f frontend
docker compose down
```

The Compose file publishes PostgreSQL on `5432`, Redis on `6379`, the backend on `8081`, and the frontend on port `80`. Named volumes preserve PostgreSQL and Redis data when containers are recreated.

## Testing and verification

Run the backend package build without integration tests:

```powershell
.\mvnw.cmd -DskipTests package
```

Run the frontend type-check and production build:

```powershell
Set-Location .\frontend
npm run build
```

Run backend tests:

```powershell
.\mvnw.cmd verify
```

The integration tests use Testcontainers and therefore require Docker. The current integration coverage includes:

- Task creation and persistence.
- Paginated task retrieval.
- Task updates and version increments.
- Task movement between lists.
- Task deletion.
- Full task lifecycle flow.
- Stale-version conflicts.
- Concurrent updates and moves, verifying optimistic locking.

The repository does not currently claim complete end-to-end coverage for every role, settings, reporting, WebSocket, or audit-history scenario. Those areas should be covered with additional tests before treating the project as production-certified.

## Configuration

Local defaults are defined in `src/main/resources/application.yml`. Docker Compose supplies container hostnames and environment overrides through the Docker Spring profile.

Common environment variables:

| Variable | Purpose |
|---|---|
| `DB_HOST` | PostgreSQL host |
| `DB_PORT` | PostgreSQL port |
| `DB_NAME` | Database name |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `REDIS_HOST` | Redis host |
| `REDIS_PORT` | Redis port |
| `JWT_SECRET` | Base64 JWT signing secret |
| `JWT_EXPIRATION_MS` | JWT lifetime in milliseconds |
| `WEBSOCKET_ALLOWED_ORIGINS` | Allowed WebSocket origins |

Use a strong, unique JWT secret with at least 256 bits of entropy. Never commit `.env` or production secrets.

## Current operational considerations

- The default development configuration exposes detailed actuator health information; review actuator exposure and authentication before production use.
- WebSocket origins default to `*` in development and must be restricted in production.
- The default JWT secret and demo passwords are development values and must be replaced.
- `ddl-auto=update` is intended for local development, not controlled production schema migration.
- Docker Compose is a single-host deployment definition, not a high-availability or autoscaling configuration.
- Integration tests require Docker and were designed around PostgreSQL and Redis containers.
- The frontend production build reports a bundle-size warning; the build still succeeds.

## License

No license file is currently included in this repository. Add a project license before distributing the code outside the intended organization.
