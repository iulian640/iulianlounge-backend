# ADR-04: Ledger append-only + saldo materializado

## Estado

Aceptada — 2026-07-03

## Contexto

Toda la economía del juego (apuestas, recompensas, compras) depende del saldo
de fichas de cada jugador. La opción simple — una columna `balance` que se
actualiza con cada movimiento — destruye información: cada UPDATE machaca el
valor anterior y perdemos el historial, con lo que no podemos auditar qué pasó
ante un bug o una reclamación ("ayer tenía 500 fichas y hoy tengo 200").

La opción contraria — guardar solo el historial de transacciones — obliga a
recorrer y sumar el historial entero del jugador cada vez que hay que consultar
su saldo, y eso ocurre en cada apuesta, cada compra y cada consulta del HUD.

## Decisión

Guardamos las dos cosas, con papeles distintos:

- **`TokenTransaction` (el ledger)** es la fuente de verdad. Append-only:
  cada movimiento de fichas es un INSERT con importe con signo, tipo,
  `balance_after` y timestamp. Jamás UPDATE ni DELETE sobre esta tabla.
- **`Wallet.balance`** es una caché materializada de esa verdad: el saldo
  ya calculado, para leerlo al coste de una fila.

Reglas que mantienen ambos sincronizados:

1. Toda mutación de saldo escribe en los dos sitios **dentro de la misma
   transacción de base de datos** (o se confirman ambos cambios, o ninguno).
2. Solo `WalletService` toca estas dos tablas; el resto del sistema
   (blackjack, katas, tienda, incremental, préstamos) son clientes suyos.
3. `Wallet` lleva `@Version` (bloqueo optimista): si dos escrituras
   concurrentes pisan la misma cartera, la segunda falla en vez de machacar
   (1 reintento, luego 409).
4. `idempotencyKey` UNIQUE en el ledger: los reintentos duplicados del
   cliente (doble clic en "apostar") mueren en la base de datos.
5. Constraint CHECK `balance >= 0`: la última defensa está en la BD, no en Java.

## Alternativas consideradas

- **Solo columna `balance` (UPDATE destructivo).** Descartada: sin historial
  no hay auditoría posible ni forma de diagnosticar bugs económicos.
- **Saldo 100% derivado (SUM del ledger en cada lectura).** Descartada:
  cada apuesta escanearía el historial completo del jugador, que crece sin
  límite con el incremental.
- **Bloqueo pesimista (`SELECT FOR UPDATE`).** Descartado como defecto: con
  un solo usuario por cartera, la contención real es su propio doble clic,
  que ya resuelve la idempotencyKey; el bloqueo optimista enseña más y
  escala mejor.

## Consecuencias

- (+) Auditoría completa: cualquier saldo se puede explicar y reconstruir
  fila a fila (`balance_after` en cada transacción).
- (+) Lectura de saldo O(1), independiente del tamaño del historial.
- (+) Las constraints de BD (UNIQUE, CHECK) protegen la economía incluso
  ante bugs en el código.
- (−) El mismo dato vive en dos sitios: toda escritura debe ser atómica y
  pasar por `WalletService`, sin excepciones. Disciplina de equipo.
- (−) El ledger crece indefinidamente; mitigado porque el incremental solo
  escribe al recolectar (ver R6 en `architecture.md`).
