# ADR-06: i18n — keys in the backend, texts in the frontend (vue-i18n)

> English version of [`ADR-06-i18n-claves-backend-textos-frontend.md`](ADR-06-i18n-claves-backend-textos-frontend.md).

## Status

Accepted — 2026-07-03

## Context

The game is bilingual (ES/EN) from day one, and its personality lives in the
texts: the currency is called **Chikilicuatres** in Spanish and
**Shrutebucks** in English, the ranks are "Pejilgero"/"Riffraff", the rooms
"La Eterna"/"The Long Game", and even the errors speak with the club's voice
(see `docs/ficcion.md`). Someone has to own those texts, and there are two
candidates: the backend (which serves the data) or the frontend (which
displays it). If the backend sent localized texts, every new name or line
fix would require deploying the backend, would duplicate catalogs and would
complicate caching.

## Decision

- **The backend returns keys and codes, never user-facing text**:
  `item.jacket.name`, `error.wallet.insufficient`, `room.vip.name`.
- **The frontend resolves the keys with vue-i18n** and its ES/EN
  dictionaries. All texts with personality (currency, ranks, rooms, the
  bartender's catalog lines, UI texts) live in the dictionaries.
- **`User.locale` is stored in the backend** but is only used for what the
  backend generates by itself: the language of the bartender's system
  prompt (Cursaito replies in the user's language) and future emails.
- Practical consequence already verified: renaming the currency or a room
  is **one line in a dictionary**, not a migration nor a backend deploy.

## Alternatives considered

- **Localized texts from the backend.** Rejected: duplicates catalogs
  (DB + frontend), couples the deploys of the two repos, complicates
  response caching and turns every typo into a redeploy.
- **i18n "later on".** Rejected: retrofitting i18n with dozens of strings
  hardcoded in components is guaranteed pain. The dictionaries are set up
  in the frontend skeleton (ticket IUL-26), before the first text.

## Consequences

- (+) Personality and translations are edited without touching the backend.
- (+) The backend stays free of presentation concerns: stable, testable,
  cacheable codes.
- (+) The fiction's texts have a single source (`docs/ficcion.md` →
  dictionaries).
- (−) Discipline required: the temptation to return a "quick" text message
  from Java will appear; the rule admits no exceptions.
- (−) Orphan keys (the backend emits a key the frontend lacks) fail
  silently by showing the raw key; mitigation: a component test that
  verifies dictionaries are complete.
