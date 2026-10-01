# Instant Message

One-to-one real-time chat: a Spring Boot backend that talks to the browser over REST and a raw WebSocket (no STOMP), and a React single-page app.

You can sign up, log in, find people by account name, add them as contacts, and exchange messages live. Messages are stored before they are delivered, so someone who is offline sees them the next time they open the chat.

## Stack

| Part | Uses |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1 (Web MVC, WebSocket, Security, Data JPA), Flyway, PostgreSQL |
| Frontend | React 19, TypeScript, Vite, React Router |
| Local database | Docker Compose (PostgreSQL 17) |

## Run it locally

You need Java 21 or newer, Node.js 20.19+ or 22.12+, and Docker.

```bash
# 1. Database (port 5432)
docker compose up -d

# 2. Backend (port 8085); Flyway creates the tables on first start
cd backend
./mvnw spring-boot:run

# 3. Frontend (port 5173), in a second terminal
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and sign up. Sign-up accepts `@gmail.com` addresses only, and the address is not verified.

To try a conversation, open the app in two browser tabs and sign up a different user in each. The login is kept per tab, so the tabs stay separate users.

The Vite dev server proxies `/api` and `/ws` to the backend, so the browser only ever talks to port 5173.

## Tests

```bash
cd backend
./mvnw test
```

The integration test starts its own PostgreSQL through Testcontainers, so Docker must be running. It does not use the Compose database.

```bash
cd frontend
npm run lint
npm run build   # type-checks, then builds
```

## Configuration

Backend settings live in `backend/src/main/resources/application.properties`.

| Setting | Default | Notes |
| --- | --- | --- |
| `server.port` | `8085` | |
| `spring.datasource.*` | `localhost:5432`, database, user and password all `im` | Matches `docker-compose.yml` |
| `app.jwt.secret` | a local-development value | Override with the `JWT_SECRET` environment variable; at least 32 bytes |
| `app.jwt.ttl` | `12h` | Access token lifetime; there is no refresh token |
| `app.frontend-origin` | `http://localhost:5173` | The only origin allowed to open the WebSocket |

The default secret and database password are public in this repository. Replace both before running anywhere other than your own machine.

## REST API

All paths are under `/api/v1`. Everything except `/auth/*` needs an `Authorization: Bearer <token>` header. Errors are returned as RFC 9457 problem details.

| Endpoint | Purpose |
| --- | --- |
| `POST /auth/signup` | Body: `email`, `accountName`, `displayName`, `password`. Returns 201 |
| `POST /auth/login` | Body: `identifier` (account name or email), `password`. Returns `accessToken`, `expiresIn`, `accountName`, `displayName` |
| `GET /users/search?q=` | Account-name prefix search, at most 20 results, excludes you |
| `GET /contacts` | Your contacts |
| `POST /contacts` | Body: `accountName`. Adds a contact; adding the same person again is harmless |
| `GET /conversations` | Everyone you have exchanged messages with |
| `GET /conversations/{accountName}/messages?before=&limit=` | History with one person, newest first. `before` is an ISO timestamp for paging back; `limit` defaults to 50, maximum 100 |

## WebSocket protocol

Connect to `/ws?token=<access token>`. An invalid or missing token is rejected with 401 before the connection is upgraded.

Frames are JSON text:

| Direction | Frame |
| --- | --- |
| Client to server | `{"type":"send","clientMessageId":"<uuid>","to":"<accountName>","body":"..."}` |
| Server to sender | `{"type":"ack","clientMessageId":"<uuid>","sentAt":"..."}` |
| Server to recipient | `{"type":"message","id":"<uuid>","from":"...","to":"...","body":"...","sentAt":"..."}` |
| Server to sender | `{"type":"error","clientMessageId":"<uuid>","code":"..."}` |

- The sender is always taken from the token; there is no `from` field to send.
- `clientMessageId` becomes the message id. Sending the same id again is acked but not stored or delivered twice, so a client can retry safely.
- A message body can be up to 2000 characters.
- Error codes: `bad_request`, `body_too_long`, `recipient_not_found`, `duplicate_id`, `server_error`.

## Project layout

```
backend/src/main/java/dev/sithutun/im/
  auth/      sign-up, login, JWT and security configuration
  user/      user entity and search
  contact/   contacts
  chat/      messages, history, WebSocket handler and session registry
backend/src/main/resources/db/migration/   Flyway migrations
frontend/src/
  api.ts               fetch wrapper
  features/auth/       login and sign-up pages, auth provider
  features/chat/       chat page and the WebSocket hook
docker-compose.yml     local PostgreSQL
ProjectDoc.md          the full MVP plan and design decisions
```

## Not built yet

`ProjectDoc.md` describes the full MVP. Still to do from it: email verification codes, refresh tokens and logout on the server, removing a contact, the account-name availability check, rate limiting, and a "load older messages" control in the UI.

The backend keeps open WebSocket sessions in memory, so it runs as a single instance only.
