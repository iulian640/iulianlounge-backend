# ADR-04: Append-only ledger + materialized balance

> English version of [`ADR-04-ledger-append-only-saldo-materializado.md`](ADR-04-ledger-append-only-saldo-materializado.md).

## Status

Accepted — 2026-07-03

## Context

The entire game economy (bets, rewards, purchases) depends on each player's
token balance. The simple option — a `balance` column updated on every
movement — destroys information: every UPDATE overwrites the previous value
and we lose the history, so we cannot audit what happened when facing a bug
or a complaint ("yesterday I had 500 tokens and today I have 200").

The opposite option — storing only the transaction history — forces us to
walk and sum the player's entire history every time the balance is needed,
and that happens on every bet, every purchase and every HUD query.

## Decision

We store both, with different roles:

- **`TokenTransaction` (the ledger)** is the source of truth. Append-only:
  every token movement is an INSERT with a signed amount, type,
  `balance_after` and timestamp. Never UPDATE or DELETE on this table.
- **`Wallet.balance`** is a materialized cache of that truth: the balance
  already computed, readable at the cost of one row.

Rules that keep both in sync:

1. Every balance mutation writes to both places **inside the same database
   transaction** (either both changes commit, or neither does).
2. Only `WalletService` touches these two tables; the rest of the system
   (blackjack, katas, shop, incremental, loans) are its clients.
3. `Wallet` carries `@Version` (optimistic locking): if two concurrent
   writes hit the same wallet, the second one fails instead of overwriting
   (1 retry, then 409).
4. UNIQUE `idempotencyKey` on the ledger: duplicate client retries
   (double-clicking "bet") die at the database.
5. CHECK constraint `balance >= 0`: the last line of defense lives in the
   DB, not in Java.

## Alternatives considered

- **Only a `balance` column (destructive UPDATE).** Rejected: without
  history there is no possible audit, nor any way to diagnose economy bugs.
- **Balance 100% derived (SUM over the ledger on every read).** Rejected:
  every bet would scan the player's entire history, which grows without
  bound with the incremental.
- **Pessimistic locking (`SELECT FOR UPDATE`).** Rejected as the default:
  with a single user per wallet, the real contention is their own double
  click, which the idempotencyKey already solves; optimistic locking
  teaches more and scales better.

## Consequences

- (+) Full audit: any balance can be explained and reconstructed row by
  row (`balance_after` on every transaction).
- (+) O(1) balance reads, independent of history size.
- (+) DB constraints (UNIQUE, CHECK) protect the economy even against
  bugs in the code.
- (−) The same data lives in two places: every write must be atomic and
  go through `WalletService`, no exceptions. Team discipline.
- (−) The ledger grows indefinitely; mitigated because the incremental
  only writes on collection (see R6 in `architecture.md`).
