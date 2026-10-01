# Real-Time Messaging MVP Plan

Oct 1, 2026 · @Si Thu Tun

## Current build (minimal cut)

What is implemented in `backend/` and `frontend/` today. The rest of this document describes the full MVP; anything not listed here is still to do.

- **Built:** sign-up (no verification), login with one access JWT (HS256, 12 hours), user search, conversation list, message history, and one-to-one messaging over a raw WebSocket at `/ws`.
- **Deferred:** contacts, refresh tokens and logout endpoint, verification codes, account-name availability check, mobile number, rate limiting, `conversations` table (messages carry `sender_id` and `recipient_id` directly).
- **Run:** `docker compose up -d`, then `./mvnw spring-boot:run` in `backend/` (port 8085) and `npm run dev` in `frontend/` (port 5173, proxies `/api` and `/ws`).
- **Test:** `./mvnw test` in `backend/` (needs Docker for Testcontainers).

WebSocket frames are JSON text:

| Direction | Frame |
| --- | --- |
| Client to server | `{"type":"send","clientMessageId":"<uuid>","to":"<accountName>","body":"..."}` |
| Server to sender | `{"type":"ack","clientMessageId":"<uuid>","sentAt":"..."}` |
| Server to recipient | `{"type":"message","id":"<uuid>","from":"...","to":"...","body":"...","sentAt":"..."}` |
| Server to sender | `{"type":"error","clientMessageId":"<uuid>","code":"..."}` |

## Scope and gaps

The MVP is a Spring Boot REST + raw WebSocket backend with a React single-page app: sign up,  log in, find people by account name, add them as contacts, and exchange one-to-one messages in real time. 

- **login API with JWT access and refresh tokens** ( `/auth/login`, `/auth/token`, `/auth/refresh`).
- **sign-up API and no verification code yet** ( `/auth/signup`).
- **send message one-to-one session over raw  WebSocket, no STOMP yet** ( `/ws`).
- **Contacts API.** The contact list needs search by account name, add contact and list contacts endpoints.
- **Message history API and storage.** Messages must be saved, and the chat page needs history on open. Without this, an offline recipient loses messages.
- **Account-name availability API.** Needed for the real-time check on the sign-up page.
- **Logout.** Revokes the refresh token on the server.
- **Resend code.** `sendCode` doubles as this, with a cooldown.
- **Chat page.** Your page list ends at the contact list; tapping a contact needs a conversation screen.

Out of scope for the MVP: deployment beyond localhost, group chat, media and file messages, push notifications, read receipts, typing indicators, mobile number verification (the number is stored only), and horizontal scaling of WebSocket servers.

## Tech stack

Use Java 21 LTS with the current Spring Boot 3.5.x or 4.x line, and PostgreSQL for all persistence. Versions are from memory and approximate; confirm on start.spring.io before the first commit.

| Layer | Choice                                                                                          | Why |
| --- |-------------------------------------------------------------------------------------------------| --- |
| Language / build | Java 21, Maven, Spring Boot                                                                     | Virtual threads, records, and long-term support |
| Web and security | spring-boot-starter-web, spring-boot-starter-security, spring-boot-starter-oauth2-resource-server | Validates JWTs with built-in Nimbus support, so no custom filter is needed |
| Real-time | spring-boot-starter-websocket with raw WebSocket                                                | One `TextWebSocketHandler` and a small JSON frame protocol; no broker or sub-protocol to learn |
| Persistence | Spring Data JPA, PostgreSQL, Flyway                                                             | Relational data, tracked migrations |
| Validation and mapping | Bean Validation, MapStruct                                                                      | Standard, concise DTO handling |
| Tests | JUnit 5, Mockito, Testcontainers, Spring Security Test                                          | Real PostgreSQL in integration tests |
| Frontend | React 19, TypeScript, Vite, React Router, TanStack Query                                        | Fast build, typed API layer, cached server state |
| Frontend real-time | Websocket                                                                                       | The browser's built-in `WebSocket`; no client library |
| Forms | React Hook Form, Zod                                                                            | Validation shared with API rules |
| Local environment | Docker Compose (PostgreSQL)                                                              | One command to run everything |

Coding conventions: package by feature (`auth`, `user`, `contact`, `chat`), constructor injection, DTO records separate from entities, `@RestControllerAdvice` for errors using RFC 9457 problem details, Google Java Style via Spotless, and Checkstyle in CI.

## Architecture

&#91;embedded content: architecture · 4 components\]

The browser calls REST over HTTPS for sign-up, login and contacts and keeps WebSocket open for live messages. The backend saves everything in PostgreSQL before delivering a message.

## Data model

Six PostgreSQL tables cover the MVP. Flyway owns the schema; JPA is set to `validate` only.

| Table | Key fields | Notes |
| --- | --- | --- |
| users | id (UUID), email (unique, normalized @gmail.com address), account\_name (unique, lowercased, 3-20 chars), display\_name, mobile, password\_hash (bcrypt), email\_verified, status, created\_at | Account name is the public handle and is immutable after sign-up |
| refresh\_tokens | id, user\_id, token\_hash (SHA-256), device\_info, issued\_at, expires\_at, revoked\_at, replaced\_by | Persistent token store; supports rotation and per-device logout |
| contacts | id, owner\_id, contact\_id, created\_at, unique (owner\_id, contact\_id) | One-way add for the MVP; mutual approval is a later feature |
| conversations | id, user\_a\_id, user\_b\_id, unique pair (ordered ids) | One row per pair; makes history queries cheap |
| messages | id (UUID set by client for dedupe), conversation\_id, sender\_id, body, sent\_at, delivered\_at | Index on (conversation\_id, sent\_at desc) |

APIs expose account names; internal joins use UUIDs so a future rename is possible.

## Backend API

All REST paths sit under `/api/v1`. Only the rows marked Public skip the JWT check. Your six requested APIs are included; the rest are the gaps from the scope section.

| Endpoint | Auth | Request and response |
| --- | --- | --- |
| POST /auth/signup | Public | email (@gmail.com only), accountName, displayName, mobile, password. Creates an unverified user and sends a code. Returns 201 |
| GET /auth/account-name/available?name= | Public | Returns available true or false. Rate limited by IP |
| POST /auth/login | Public | identifier, password. Rejects unverified users. Returns access token (15 min) and refresh token (7-30 days) |
| POST /auth/token | Public | Your token API: exchanges a valid refresh token for a new access token |
| POST /auth/refresh | Public | Rotates the refresh token: the old one is revoked, a new pair is returned. Reuse of a revoked token revokes the whole chain |
| POST /auth/logout | JWT | Revokes the presented refresh token |
| GET /users/search?q= | JWT | Prefix search on account name, max 20, excludes self |
| GET /contacts | JWT | List of contacts with account name and display name |
| POST /contacts | JWT | accountName. Adds a contact; idempotent |
| GET /conversations/{accountName}/messages?before=&limit= | JWT | Cursor-paged history, newest first |
| WS /ws?token= | JWT on handshake | `send` frame with to, body, clientMessageId. There is no from field; the sender is the token subject |


## Key design decisions

**Password in transit.** Do not encrypt the password in JavaScript. Anything the browser can compute, an attacker on the page can replay, so it adds no real protection. Send it over HTTPS (TLS) in the request body, set HSTS, and let the backend hash it with bcrypt (cost 10-12, `BCryptPasswordEncoder`). Never log request bodies. If you still want a client-side step, it can only be an extra layer on top of TLS, never a replacement.

**Tokens.** The access token is a short-lived (15 minute) signed JWT carrying subject (user id), account name and expiry. Sign with RS256 or ES256 so keys can rotate; HS256 with a 256-bit secret is acceptable for a single-service MVP. The refresh token is a long random opaque string (not a JWT): the database stores only its SHA-256 hash, so a database leak does not leak sessions. Rotate it on every use, and treat reuse of a rotated token as theft by revoking the chain. This satisfies the requirement that tokens be persisted and supports multiple devices, one row per device.

**Token storage in the browser.** Keep the access token in memory. Send the refresh token as an `HttpOnly`, `Secure`, `SameSite=Strict` cookie scoped to `/api/v1/auth`; this removes it from reach of XSS. If you prefer to return it in the JSON body for simplicity, store it in memory too and accept that a page reload forces a login.

**WebSocket auth.** Browsers cannot set headers on the WebSocket handshake, so the client passes the access token as a `token` query parameter. A `HandshakeInterceptor` validates it before the upgrade, answers 401 if it is invalid, and stores the user id in the session attributes. The server always takes the sender from those attributes. The token can appear in access logs, which is acceptable on localhost; keep it short-lived or move it to a first frame before deploying.

**Message flow.** The server validates and saves the message first, then acknowledges to the sender and pushes it to every open session of the recipient, found in an in-memory session registry. Clients send a UUID with each message so retries do not create duplicates. Recipients who are offline fetch history over REST on next open. The in-memory registry is enough for one instance; Redis pub/sub between instances is the step when you run two. Raw sessions do not allow concurrent sends, so each is wrapped in `ConcurrentWebSocketSessionDecorator`.

**Verification codes.** Generate six characters with `SecureRandom` from an alphabet without look-alikes (no 0/O, 1/I/L), for example 32 symbols, giving about one billion combinations. Hash it, expire it after 10 minutes, allow 5 attempts, cap sends to 5 per hour per account, and invalidate older codes when a new one is issued. Password reset uses the same mechanism and returns a one-time reset token after `verifyCode`, so the final reset call does not accept the raw code again.

**Other safeguards.** CORS limited to the frontend origin; rate limiting on auth endpoints (Bucket4j or a gateway); lowercase and trim email and account name before comparing; generic login errors; account-name rules `^[a-z0-9_]{3,20}$` with a reserved-word list; CSRF disabled only because the API is stateless and cookies are scoped to the refresh path with SameSite=Strict.

## Frontend

Seven routes; the first four match your list, the last three complete the loop. Protected routes redirect to login when no valid session exists.

| Route | Page | Behaviour |
| --- | --- | --- |
| /signup | Sign-up | Gmail address (@gmail.com only, checked on the form and again on the server), account name, display name, mobile, password. Account name is auto-suggested from the email prefix and checked against the backend as the user types (debounced 400 ms, with available / taken state). On success, go to /verify |
| /login | Login | Account name or email plus password; link to forgot password. On success, redirect to /contacts |
| /contacts | Contact list | Search bar queries `/users/search`; results have an Add button. Below it, the list of added contacts with a last-message preview |
| /chat/:accountName | Chat | Message history, send box, live incoming messages over the WebSocket, connection status banner |


State and structure: TanStack Query for REST data, a small auth context for the access token, and one websocket client created after login and closed on logout. An Axios or fetch wrapper adds the bearer token and on a 401 calls the refresh endpoint once, then retries. Folder layout by feature (`features/auth`, `features/contacts`, `features/chat`). Tests with Vitest and React Testing Library, plus one Playwright flow: sign up, verify, add a contact, send a message between two browser sessions.
