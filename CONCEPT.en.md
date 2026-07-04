# IulianLounge — Concept Document

> English version of [`CONCEPT.md`](CONCEPT.md). The Spanish version is the working original; if they ever disagree, Spanish wins.
> Status: concept locked · 2026-07-02
> Next phase: Phase 0 — technical architecture (JPA entities, endpoints, ADRs)

## What it is

A virtual speakeasy you can walk through in 3D. You come in as a nobody and climb
until you become a partner of the club: you earn tokens, improve the business,
unlock rooms, dress better and better. It is not a website with a decorative 3D
canvas — it is a browser video game with a serious backend behind it.

**Dual purpose:** a memorable playable experience + a professional showcase of the
Java/Spring/JPA stack with an out-of-the-ordinary Three.js frontend.

## Art direction

Elegant, dark speakeasy, 1920s-30s:

- Well-kept wood, leather, bottle green, black.
- Gold/brass accents (the bar, frames, details) — the thing that sells "expensive".
- Warm, low light (2700-3000K), point sources: lamps, bar lights,
  the ember of a cigar. Strong contrast, hard shadows. No flat lighting.
- Background jazz, expensive whisky, smoke, people playing cards.
- Technique: restrained geometry + PBR with varied roughness (matte wood, shiny
  brass, glass) + subtle bloom. The atmosphere comes from the light, not the polycount.
- **The "Iulian's" sign**: the club's name inside the fiction. A lit sign
  inside the lounge, pre-war-Fallout style but with **warm bulbs and brass**
  (retro-American marquee, period typography; no atomic neon, which would clash
  with the 20s-30s) — a hero art piece, candidate for emissive + bloom.
  The project's brand remains IulianLounge.

## Camera and controls

- **Third person**, WASD + mouse.
- **Desktop first** for the 3D experience. No decisions that would block
  adding touch controls in phase 2.
- **Mobile: full 2D experience.** Same app and same features (login,
  blackjack, challenge terminal, shop, bartender, leaderboards) served by the
  same Vue components in a responsive layout, without the walkable lounge. The 3D
  is the desktop wrapper, not a requirement to play. (Meets the assignment
  requirement: responsive frontend for Mobile and Desktop.)

## The progression system (the heart of the design)

Narrative, economy, space and cosmetics are **the same system** seen from
four angles:

### 1. Narrative: your rise
From nobody to partner of the club. The story IS the gameplay: progressing in the
economy is progressing in the story. The bartender marks the milestones.

### 2. Economy: the tokens
A single currency (club tokens) shared by every activity:

| Source | Type |
|---|---|
| Terminal katas | Additive only (paid per solved challenge) |
| Incremental (the business) | Additive only (passive trickle) |
| Blackjack and secondary game | **Real risk: you bet and you can go broke** |

All sources pay at a comparable rate — the player chooses how to make a living.

**Bankruptcy:** if you hit 0, the bartender lends to you. A loan with mockery
included ("again, huh?"). It turns frustration into a character moment.
→ Backend: debt/loan entity, atomic transactions, double-spend protection.

### 3. Space: the rooms
- **Main room**: bar, card table, jazz stage.
- **Back room/office**: the challenge terminal. Unlockable.
- **VIP room**: unlockable with progress/status.

Rising in status = literally gaining access to more space.

### 4. Avatar: your look
- A single base character + **equipment slots** (not a character editor).
- At launch: 2-3 slots (hat, hand accessory — cigar/glass/cane; later
  a jacket). Expandable.
- Each piece in **material/color variants** (felt vs silk, brass vs gold):
  a large catalog without modeling more. More expensive = visually more luxurious.
- You start in humble clothes; you end up in silk and gold.

## The pieces

### Challenge terminal (the back room)
Our own mini-freeCodeCamp: **JavaScript logic katas**.

- User code runs **sandboxed in their browser** (Web Worker),
  never on the server.
- The backend validates the result without trusting the client (result
  verification, reasonable anti-cheat — ADR topic) and persists progress and attempts.
- Every solved challenge pays tokens.

### Card table
- **Blackjack** as the main game, with real token bets.
- The backend models a generic "game table" to add more games without rebuilding.
- **A second simple game** (dice or scratch card) so the casino breathes.
  - Reference for the scratch card: **Scritchy Scratchy**.

### The bartender (AI)
**A sardonic veteran who knows everything.** He has been at the club for decades.

- Dry humor: he teases you about your blackjack losses, acknowledges your
  achievements in the challenges, comments on your rise.
- **Function calling against the backend**: he knows your real data (balance,
  streak, progress, debts). An NPC who truly knows who you are.
- He is the gateway to everything: context, challenges ("think you can handle
  that terminal?"), narrative milestones, and the loan when you go broke.
- LLM via external API **from the Java backend** (the key never touches the
  client). Rule-based fallback if needed.
- Conversation history persisted.

### The incremental (the bar's business)
**The 3D lounge IS the incremental.** Upgrades appear physically:

- You buy tables → tables with NPC customers appear.
- You hire musicians → they get up on stage.
- You upgrade the cellar → barrels and bottles in the back room.
- The bar fills up and visibly prospers with your progress.

Mechanics (all three, layered as the project advances):
1. Automatic generators (tables/musicians produce tokens on their own).
2. Per-"facility" level upgrades (level up a table, a musician).
3. **Prestige**: sell the bar and open a more luxurious one with a permanent
   bonus (the 3D scene transforms).

Dense numeric management lives in a 2D panel (the ledger book); the visual part,
in the scene. Asset-cost mitigation: upgrades are **instances** of a few models
with variations, not unique assets.

## Social presence (asynchronous)

No real-time multiplayer (phase 2). Instead, **traces of others**:

- Avatars of other users sitting in the lounge with their outfit and last activity.
- Leaderboards with avatars.
- (Possibly) notes left on the bar.

It justifies the cosmetics (you are seen) without paying the cost of live
synchronization.

## Language

**i18n from the start: Spanish and English.** All text in dictionaries,
bartender prompt per language, challenges in both languages.

## Stack

| Layer | Technology |
|---|---|
| 3D | Vanilla Three.js (threejs-* skills installed) |
| 2D UI overlay | Vue (login, HUD, panels, ledger book, chat) |
| Build | Vite |
| Backend | Java + Spring Boot + Spring Data JPA + Spring Security (JWT) |
| DB | PostgreSQL, versioned migrations (Flyway) |
| Bartender AI | LLM via external API, orchestrated by the backend |

## Quality bar (a serious project, not an exercise)

- TDD, 80%+ coverage on the backend.
- ADRs for big decisions.
- Security: JWT, rate limiting, exhaustive validation, secrets in the environment,
  the client is never trusted (bets and kata results are verified).
- CI/CD (GitHub Actions): tests + build + lint on every push.
- OpenAPI/Swagger, structured logging, explicit errors at every layer.
- Review with java-reviewer / security-reviewer when closing each piece.

## Overall plan (3 months, daily work)

| Weeks | Block |
|---|---|
| 1-2 | Base backend: JWT auth, core entities, wallet/transactions, tested end-to-end |
| 3-5 | Walkable third-person 3D lounge + base avatar + integrated login |
| 6-7 | Challenge terminal (JS katas + validation + progress) |
| 8-9 | Blackjack + secondary game + leaderboard |
| 10-11 | AI bartender (function calling, loans, narrative) |
| 12 | Visual incremental, clothing shop, async presence, polish, demo |

*(Fine-grained distribution to be decided in Phase 0; the incremental may be
brought forward in layers.)*

Extras for spare days: bartender chat streaming (WebSocket), positional
sound, more katas, more clothing variants, a second room.

**Phase 2 (post-3 months):** real-time multiplayer, touch controls,
poker.

## Flagged risks

1. **The 3D eating the backend's time.** Hard time budget for art; the
   backend vertical slice goes first.
2. **Asset cost of the visual incremental + rooms.** Instancing and variants,
   not unique assets.
3. **Trusting the client** (katas and bets). The backend verifies everything;
   anti-cheat design in an ADR.
4. **Bugs in the blackjack logic with the game's real money.**
   Atomic transactions and exhaustive tests of the betting logic.

## Pending (does not block Phase 0)

- ~~Domain extension~~ → **DECIDED (2026-07-04): `iulianlounge.com`, purchased.**
- ~~Proper name for the currency/tokens~~ → **DECIDED (2026-07-03): Chikilicuatres (ES) / Shrutebucks (EN), see `docs/ficcion.md`.**
- Choose the secondary game (dice vs scratch card — reference: Scritchy Scratchy).
- The club's backstory (who founded it, why it is hidden) — developed
  alongside the bartender's prompt.
