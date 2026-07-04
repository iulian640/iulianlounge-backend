# ADR-08: Stateless JWT with refresh token

> English version of [`ADR-08-jwt-stateless-con-refresh-token.md`](ADR-08-jwt-stateless-con-refresh-token.md).

## Status

Accepted — 2026-07-03

## Context

HTTP is stateless: the server remembers nothing between requests, so after
login it needs some way to know who makes each request without asking for
username and password every time. Whatever credential is handed out must be
impossible to forge: the client is never trusted, and anyone can send
requests straight to the API.

There are two tensions:

1. **Verification**: either the server remembers every issued credential
   (sessions in the DB, one query per request), or it issues signed
   credentials it can verify without memory.
2. **Expiry**: a long-lived credential is convenient but, if stolen, the
   damage lasts weeks; a short-lived one limits the theft but would force
   the user to re-log constantly (unacceptable in the middle of a blackjack
   hand).

Additionally, frontend and backend live in different repos and domains,
which complicates cross-site session cookies.

## Decision

**JWTs signed by the backend, no server-side state, with two tokens:**

- **Access token (15 min)**: travels in the `Authorization` header of every
  authenticated request. It contains identity and role, signed with the
  server's secret key: tampering with its content invalidates the signature.
- **Refresh token (7 days)**: only good for requesting a new access token
  at `POST /auth/refresh`. Minimal exposure: it only travels to that
  endpoint.

Flow: when the access token expires, the backend replies 401, the frontend
silently calls `/auth/refresh`, gets a new access token and retries. The
user only re-logs after 7 days of inactivity.

The signing key lives in an **environment variable**, never in the repo
(which is public). If it leaked, it would allow forging tokens for any user
with any role: immediate rotation (invalidates every issued token).

## Alternatives considered

- **Server sessions (cookie + state in the DB).** Rejected: a session
  lookup on every request, requires shared state, CSRF protection, and
  fights cross-site cookies with frontend and backend on different domains.
- **Token blacklist in the DB (immediate revocation).** Postponed: it
  reintroduces the state JWT removes. Only if a real need appears.
- **A single long-lived token.** Rejected: a weeks-long theft window with
  no possible mitigation since there is no revocation.

## Consequences

- (+) The server verifies identity with one cryptographic operation,
  without touching the DB: it scales and simplifies.
- (+) Damage window for a stolen access token bounded to 15 minutes.
- (+) Fits the frontend/backend separation across different domains.
- (−) **No immediate revocation**: a stolen access token stays valid until
  it expires. Trade-off accepted and documented; the short TTL bounds the
  window.
- (−) The frontend must implement the silent refresh flow (401 interceptor
  + retry).
