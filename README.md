# WorkCollab — Collaborative Task Management

WorkCollab is an enterprise-grade collaborative task management application designed for real-time team coordination. It features a robust Spring Boot backend and a modern React frontend, with WebSocket-powered live updates, optimistic locking for concurrent modifications, and a scalable Redis Pub/Sub architecture.

## 🚀 Features

- **Real-Time Collaboration**: Live updates across all connected clients via WebSocket (STOMP) backed by a Redis Pub/Sub cluster.
- **Concurrent Editing Protection**: Optimistic locking ensures data integrity when multiple users edit the same task simultaneously.
- **JWT-Based Security**: Secure authentication and Role-Based Access Control (RBAC) down to the workspace level.
- **Production-Ready Docker Stack**: Fully containerized with multi-stage Dockerfiles, NGINX reverse proxy, and Docker Compose orchestration.
- **Automated Testing Infrastructure**: Comprehensive integration tests leveraging real PostgreSQL and Redis instances via Testcontainers.

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
- [Docker & Docker Compose](https://www.docker.com/) (Required for local DBs and production stack)

---

## 🏃 Local Development Setup

To run the application locally for active development, you'll run the backend and frontend separately.

### 1. Start Infrastructure (Databases)
The backend requires PostgreSQL and Redis. You can quickly spin these up using Docker:
```bash
docker run -d --name workcollab-postgres -p 5432:5432 -e POSTGRES_DB=workcollab -e POSTGRES_USER=workcollab -e POSTGRES_PASSWORD=changeme postgres:16-alpine
docker run -d --name workcollab-redis -p 6379:6379 redis:7-alpine
```

### 2. Start the Backend
The backend runs on port `8081` by default.
```bash
# Using the included Maven wrapper
./mvnw spring-boot:run
```
*Note: On Windows, use `mvnw.cmd spring-boot:run`.*

### 3. Start the Frontend
The frontend uses Vite and runs on a local dev server (typically port `5173`).
```bash
cd frontend
npm install
npm run dev
```

The application will now be accessible at the URL provided by the Vite dev server (e.g., `http://localhost:5173`).

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