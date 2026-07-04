# ADR-09: Tokens as `long` integers — `double` banned from the economy

> English version of [`ADR-09-fichas-enteros-long-prohibido-double.md`](ADR-09-fichas-enteros-long-prohibido-double.md).

## Status

Accepted — 2026-07-03

## Context

The entire game economy (bets, rewards, purchases, incremental income,
loans) is expressed in a single currency: tokens (Chikilicuatres/Shrutebucks
in the fiction). Floating-point numbers (`double`, `float`) cannot exactly
represent many decimal values: `0.1 + 0.2 != 0.3`. In a system where every
token matters and the ledger must balance to the cent (ADR-04:
`balance_after` auditable row by row), a single silent rounding corrupts the
whole bookkeeping — and that family of bugs is among the hardest to detect
because the error is tiny until it accumulates.

## Decision

- **Tokens are integers**: `long` in Java, `BIGINT` in PostgreSQL. The
  token is indivisible: there is no such thing as "0.5 tokens".
- **`double` and `float` are banned across the entire economic domain**
  (entities, services, reward and income calculations).
- **Multipliers (prestige, interest) are expressed in basis points as
  integers**: `prestigeMultiplierBps = 10500` means x1.05;
  `interestBps = 0` means the bartender lends without usury. Applying a
  multiplier is integer arithmetic: `amount * bps / 10000`.

## Alternatives considered

- **`double`.** Rejected: the complete family of rounding bugs, sums that
  do not reconcile with the ledger, and treacherous comparisons.
- **`BigDecimal`.** Rejected as overkill: it solves the decimal problem but
  at the cost of verbosity, performance and an awkward API — and we have no
  decimals: the token is indivisible by design. `BigDecimal` is the right
  answer for euros with cents; there are no cents here.
- **Tokens with decimals (allowing 0.5).** Rejected by game design: whole
  numbers are more readable, more "casino-like", and they remove the
  problem at the root.

## Consequences

- (+) The ledger sum always reconciles, exactly. ADR-04's audit is integer
  arithmetic with no surprises.
- (+) Simple, reliable DB constraints (`CHECK balance >= 0` over BIGINT).
- (+) Economy tests compare with `==`, no tolerances or deltas.
- (−) The incremental's cost/production curves must be designed in integers
  (growth in bps); a small mental gymnastics when balancing.
- (−) If the token ever needed subdividing, it would be a migration
  (multiply everything by 100); unlikely and accepted.
