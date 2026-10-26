# WorkCollab — Collaborative Task Management

WorkCollab is an enterprise-grade collaborative task management application designed for real-time team coordination. It features a Spring Boot backend and a React frontend, with WebSocket-powered live updates, optimistic locking for concurrent modifications, and Redis Pub/Sub support.

## 🚀 Features

- **Real-Time Collaboration**: Live updates across all connected clients via WebSocket (STOMP) backed by a Redis Pub/Sub cluster.
- **Concurrent Editing Protection**: Optimistic locking ensures data integrity when multiple users edit the same task simultaneously.
- **JWT-Based Security**: Secure authentication and Role-Based Access Control (RBAC) down to the workspace level.
- **Role-Based Workspace Management**: Administrators create workspaces and assign each workspace to a manager.
- **Project and Task Management**: Assigned managers create projects, initialize Kanban columns, create tasks, assign tasks to team members, and manage task priorities.
- **Personal Task Views**: Managers can view workspace tasks; team members see only tasks assigned to them.
- **Task Activity & Audit Trail**: Each task records a chronological, access-controlled history of status, description, priority, creation, movement, and other changes with the acting user and timestamp.
- **Project Reports**: Project-scoped reports show completion, progress, status, priority, overdue, and time-tracking metrics.
- **Production-Ready Docker Stack**: Fully containerized with multi-stage Dockerfiles, NGINX reverse proxy, and Docker Compose orchestration.
- **Integration Testing Infrastructure**: Testcontainers-backed integration tests cover task lifecycle operations and optimistic-locking behavior against real PostgreSQL and Redis instances.

## 🛠️ Tech Stack

**Backend**:
- Java 21 & Spring Boot 3.3.0
- Spring Security (JWT) & Spring WebSockets
- Spring Data JPA / Hibernate (Optimistic Locking)
- PostgreSQL 16
- Redis 7 (Pub/Sub for WebSocket scaling)
- JUnit 5 & Testcontainers (Integration Testing)

**Frontend**:
- React 18 & TypeScript
- Vite (Build Tool)
- NGINX (Production Reverse Proxy)

---

## 💻 Prerequisites

Before running the project locally, ensure you have the following installed:
- [Java 21 JDK](https://adoptium.net/)
- [Node.js 22](https://nodejs.org/) & npm
- [Docker & Docker Compose](https://www.docker.com/) (Required for the containerized stack; optional if PostgreSQL and Redis are installed locally)

---

## 🏃 Local Development Setup

To run the application locally for active development, you'll run the backend and frontend separately.

### 1. Start Infrastructure (Databases)
The backend requires PostgreSQL on port `5432` and Redis on port `6379`. You can quickly spin these up using Docker:
```bash
docker run -d --name workcollab-postgres -p 5432:5432 -e POSTGRES_DB=workcollab -e POSTGRES_USER=workcollab -e POSTGRES_PASSWORD=changeme postgres:16-alpine
docker run -d --name workcollab-redis -p 6379:6379 redis:7-alpine
```

### 2. Start the Backend
The backend runs on port `8081` by default. The application configuration is in `src/main/resources/application.yml`; database values can be overridden with `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD`.

On Windows, use UTC to avoid PostgreSQL timezone errors from an `Asia/Calcutta` Java timezone:
```powershell
$env:JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"
```

```bash
# Using the included Maven wrapper
./mvnw spring-boot:run
```
*Note: On Windows, use `mvnw.cmd spring-boot:run`.*

Health check:
```text
http://localhost:8081/actuator/health
```

### 3. Start the Frontend
The frontend uses Vite and runs on a local dev server (typically port `5173`).
```bash
cd frontend
npm install
npm run dev
```

The application will now be accessible at the URL provided by the Vite dev server (e.g., `http://localhost:5173`).

The Vite development server proxies API and WebSocket requests to the backend on port `8081`.

### Demo accounts

The startup seeder creates or repairs these accounts with the password `admin123`:

| Account | Email | Role |
|---|---|---|
| Administrator | `admin@demo.com` | Global `ADMIN` |
| Manager 1 | `manager1@demo.com` | `MANAGER` |
| Manager 2 | `manager2@demo.com` | `MANAGER` |
| Team member | `mem1@demo.com` | `TEAM_MEMBER` |

### Access rules

- **Administrators** can view every workspace, project, task, member list, and report. They can create workspaces and assign them to managers, but cannot create, edit, or archive projects.
- **Managers** can access only workspaces assigned to them. They can create, edit, and archive projects in those workspaces, create tasks, set priorities, and assign tasks to workspace team members.
- **Team members** can access only their assigned workspaces and see tasks assigned to their own account.
- Every new project receives `To Do`, `In Progress`, and `Done` columns automatically.

---

## 🐳 Dockerized Production Setup

For a full production-like environment, you can run the entire stack (Frontend, Backend, PostgreSQL, Redis) using Docker Compose.

1. **Configure Environment Variables**:
   Copy the example environment file and configure any necessary secrets:
   ```bash
   cp .env.example .env
   ```
   *(Ensure you update the `JWT_SECRET` and `DB_PASSWORD` in the `.env` file for real production deployments).*

2. **Build and Run the Stack**:
   ```bash
   docker compose up -d --build
   ```

3. **Access the Application**:
   Once the containers are healthy, the NGINX reverse proxy will serve the application at:
   **http://localhost**

4. **View Logs or Stop**:
   ```bash
   # View logs of all services
   docker compose logs -f

   # Stop the stack
   docker compose down
   ```

---

## 🧪 Testing

The backend includes a comprehensive integration test suite that automatically spins up real PostgreSQL and Redis containers using **Testcontainers**. 

To run the tests (Docker must be running):
```bash
./mvnw verify
```

On Windows:
```powershell
.\mvnw.cmd verify
```

Integration tests require a working Docker environment for Testcontainers.

---

## 📁 Project Structure

```
WorkCollab/
├── frontend/            # React + Vite frontend source code
│   ├── src/             # UI Components, Hooks, API services
│   ├── Dockerfile       # Multi-stage NGINX image
│   └── nginx.conf       # Reverse proxy config for API & WebSockets
├── src/                 # Spring Boot backend source code
│   ├── main/java/...    # Controllers, Services, Security, Entities
│   └── test/java/...    # Testcontainers integration tests
├── Dockerfile           # Multi-stage JRE-alpine image for backend
├── docker-compose.yml   # Production orchestration
└── pom.xml              # Maven configuration
```