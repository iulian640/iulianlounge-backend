# ADR-08: Stateless JWT with refresh token

> English version of [`ADR-08-jwt-stateless-con-refresh-token.md`](ADR-08-jwt-stateless-con-refresh-token.md).

## Status

Accepted — 2026-07-03. Amended on 2026-09-23 (see History). Refresh cookie
and logout implemented on 2026-09-25.

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

Frontend and backend are deployed on the same origin: Caddy serves the
frontend at `iulianlounge.com` and forwards `/api` to the backend. First-party
cookies work without the cross-site problems.

The frontend is a 3D scene with a lot of first- and third-party JavaScript,
so XSS can't be ruled out. Anything the page's JavaScript can read, an XSS
payload can read too.

## Decision

**JWTs signed by the backend, no server-side state, with two tokens:**

- **Access token (15 min)**: travels in the `Authorization` header of every
  authenticated request. It carries the user id, the role and the token type.
  The frontend keeps it in memory only.
- **Refresh token (7 days)**: travels in a
  `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` cookie. JavaScript
  can't read it, so an XSS payload can use the access token while the page is
  open but can't walk away with the session.

Flow: login returns the access token in the body and sets the cookie. When
the access token expires, the backend replies 401, the frontend calls
`POST /auth/refresh` (the browser attaches the cookie), gets a new access
token and retries. `POST /auth/logout` clears the cookie.

Session cap: 7 days from login. Each refresh issues a new refresh token that
inherits the previous one's expiry, so playing every day doesn't extend the
session: on day 8 the user logs in again.

Implementation details that harden the decision (IUL-19, IUL-20 and IUL-21):

- HS256 is pinned in code. It doesn't depend on the key length.
- Own `iss` and `aud` claims: a token signed with the same key by another
  service or environment is not accepted here.
- `type` claim: a refresh token can't be used as an access token, or the
  other way round.
- Only roles from a closed list become `ROLE_*`.
- Every rejected token gets the same message, which doesn't reveal why.

The signing key lives in the `JWT_SECRET` environment variable, base64 and
32 bytes or more (`openssl rand -base64 32`), never in the repo, which is
public. If it leaked it would allow
forging tokens for any user with any role: immediate rotation, which
invalidates every issued token.

CSRF protection stays off. The cookie is only sent with same-site requests
(`SameSite=Strict`) and only to `/api/v1/auth` routes. Every other route
authenticates with the `Authorization` header, which a form on another site
can't set.

Two accepted limits. `SameSite` looks at the site (`iulianlounge.com`), not
the origin: any subdomain counts as same-site and could plant its own
`refresh_token` cookie, so there will be no third-party subdomains. And
`/auth/logout` is public, so another site can force a logout; annoying, but
it steals nothing.

There is no CORS: production serves everything from one origin and dev goes
through the Vite proxy. If it is ever needed, never with `allowCredentials`:
the allowed origin could call `/auth/refresh` with the cookie and read the
access token.

## Alternatives considered

- **Refresh token in the JSON body, stored by the frontend.** This was the
  initial version of this ADR. Rejected in the amendment: any XSS reads
  `localStorage` and walks away with a 7-day session that can't be revoked.
- **Server sessions (cookie + state in the DB).** Rejected: a session lookup
  on every request and shared state that JWT avoids.
- **Inactivity expiry (7 days since last use).** Rejected: with daily use
  the session never expires, and neither does a stolen token.
- **Token blacklist in the DB (immediate revocation).** Postponed: it
  reintroduces the state JWT removes. Only if a real need appears.
- **A single long-lived token.** Rejected: a weeks-long theft window with
  no possible mitigation since there is no revocation.

## Consequences

- (+) The server verifies identity with one cryptographic operation,
  without touching the DB.
- (+) A stolen access token is good for 15 minutes at most, and the refresh
  token can't be read from JavaScript.
- (+) No session lasts longer than 7 days.
- (−) **No immediate revocation.** Two concrete cases: when an account is
  deleted, its access token stays valid for up to 15 minutes on routes that
  don't reload the user (`/me` does reload it and answers 401); and changing
  the password doesn't close open sessions.
- (−) Users log in again every 7 days even if they play daily.
- (−) In development the frontend must also go through the same origin:
  a Vite proxy (`/api` → `localhost:8080`). With that, CORS is no longer
  needed.
- (−) The frontend implements the silent refresh (401 interceptor + retry)
  and sends requests to `/auth` with credentials.

## History

- 2026-07-03: initial decision. Refresh token in the JSON body; frontend and
  backend on different domains.
- 2026-09-23: frontend and backend on the same origin behind Caddy. The
  refresh token moves to an `HttpOnly` cookie, sessions are capped at 7 days
  from login, and the hardening details from the security review are
  documented.
