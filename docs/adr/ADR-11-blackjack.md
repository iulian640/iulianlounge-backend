# ADR-11: Blackjack de una mano autoritativo en servidor

## Estado

Aceptada, 2026-10-07 (IUL-75). Sustituye en parte al ADR-03 de `architecture.md` (ver Alternativas). Nota del 2026-10-11: la moneda del producto pasó a llamarse «monedas» (en inglés, «coins»); el nombre del tipo de movimiento y las claves del ledger no cambian.

## Contexto

La Barra (ADR-10) gasta monedas, pero no hay forma de ganarlas: el único ingreso es el fiado diario
de la casa. En móvil la experiencia es 2D y, sin un segundo juego, se queda en «pide una copa y
vete». El blackjack es la mesa de El Salón desde la Fase 0 y le da a las monedas un uso de ida y
vuelta.

El ADR-03 (resumido en `architecture.md`) ya decidió que el servidor es la autoridad: baraja, guarda
el mazo y el cliente solo manda HIT o STAND. Pero también decía que la apuesta se liquida «en la
misma transacción de BD que el cambio de estado». Desde el 2026-09-25 eso no se puede: la regla 6
del ADR-04 obliga a que `credit`, `creditIf` y `debit` de `WalletService` abran su propia
transacción y se nieguen a correr dentro de otra (`requireOwnTransaction`). El movimiento de monedas
y el estado de la mano van, por tanto, en transacciones distintas, y entre las dos el proceso se
puede caer.

La mesa tiene que ser simple de jugar y de defender dentro del plazo de la entrega.

## Decisión

### 1. Reglas del juego

- Una sola mano contra el crupier. Apuestas fijas: enum `Bet` con `TEN` (10), `TWENTY` (20) y
  `FIFTY` (50). El JSON lleva el nombre (`{"bet":"TWENTY"}`). Un número o un nombre que no existe
  es 400 `request.rejected` (los ordinales ya se rechazan con
  `spring.jackson.datatype.enum.fail-on-numbers-for-enums`); sin `bet`, 400 `validation.failed`.
- Cada mano usa una baraja nueva de 52 cartas, barajada por un `Shuffler` inyectable:
  `SecureRandom` en producción y un orden fijo en los tests.
- Reparto: jugador, crupier (vista), jugador, crupier (oculta).
- El crupier mira si tiene blackjack: si lo tiene, la mano se resuelve en el acto.
- Blackjack natural del jugador (y el crupier sin él): paga 3:2 en el acto, es decir, 2,5 veces la
  apuesta. Es entero porque las tres apuestas son pares (un test lo vigila).
- Los dos con natural: empate.
- Acciones: solo `HIT` y `STAND`. Sin doblar, separar, seguro ni rendición. Llegar a 21 no planta
  sola la mano: plantarse lo decides tú.
- Más de 21 es pasarse y pierdes; el crupier no juega. El as vale 11 o 1.
- El crupier pide hasta 17 o más y se planta en todos los 17, blandos incluidos.
- Pagos (`Outcome.payoutFor(bet)`): `BLACKJACK` 2,5×, `WIN` 2×, `PUSH` 1× (te devuelve la
  apuesta), `LOSE` 0.
- El rango sigue saliendo solo del gasto en `BAR_ORDER` (ADR-10). Apostar no sube de rango.

El motor es Java puro, sin Spring ni estado: `Card`, `Face`, `Suit`, `HandTotal`, `Bet`, `Outcome`,
el record inmutable `BlackjackTable` y `BlackjackRules` (`deal`, `hit`, `stand`). Recibe una mesa y
devuelve otra. Ahí viven casi todos los tests de juego.

### 2. Información oculta

- El mazo y la carta oculta del crupier no salen del servidor hasta que termina la mano. Los
  guarda la entidad `BlackjackHand`, que nunca se serializa: los controladores devuelven DTOs.
- `HandView.of(hand)` es el único sitio que convierte una mano en respuesta. Mientras la mano está
  en `PLAYER_TURN`, `dealerCards` lleva solo la carta vista y `dealerTotal` es el valor de esa
  carta. Al terminar, enseña todas las del crupier. El mazo no se enseña nunca, ni al final.
- Ningún DTO tiene un componente para el mazo ni para la carta oculta. Un test recorre los
  componentes del record `HandView` y falla si aparece uno nuevo sin revisar.
- No se registra nada de la mano en los logs, y la entidad no sobrescribe `toString`.
- PostgreSQL mete la fila entera en el texto de error cuando falla un `CHECK` («Failing row
  contains (…)»), y Hibernate lo escribe en el log. Con mazo y carta oculta dentro, una violación
  acabaría en el log. Se apaga para toda la aplicación con
  `spring.datasource.hikari.data-source-properties.logServerErrorDetail=false`: el mensaje
  conserva el nombre de la restricción (lo que leen los tests con `assertViolates`) y pierde los
  valores. De paso deja de salir el email en un `UNIQUE` duplicado de `users`.

### 3. Modelo de datos y migración V7

Una tabla, `blackjack_hand`, y los dos tipos nuevos del ledger. V1 a V6 no se tocan. SQL de V7
(`V7__create_blackjack_hand_and_ledger_types.sql`, sin comentarios, como V5 y V6):

```sql
ALTER TABLE token_transaction DROP CONSTRAINT token_transaction_type_check;
ALTER TABLE token_transaction ADD CONSTRAINT token_transaction_type_check
    CHECK (type IN ('WELCOME_BONUS', 'BAR_ORDER', 'HOUSE_CREDIT', 'BLACKJACK_BET', 'BLACKJACK_PAYOUT'));

ALTER TABLE token_transaction DROP CONSTRAINT token_transaction_amount_sign_check;
ALTER TABLE token_transaction ADD CONSTRAINT token_transaction_amount_sign_check
    CHECK ((type IN ('BAR_ORDER', 'BLACKJACK_BET')) = (amount < 0));

CREATE TABLE blackjack_hand (
    id              UUID        PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    idempotency_key UUID        NOT NULL,
    bet             BIGINT      NOT NULL CONSTRAINT blackjack_hand_bet_check CHECK (bet IN (10, 20, 50)),
    status          TEXT        NOT NULL CONSTRAINT blackjack_hand_status_check
                                CHECK (status IN ('PLAYER_TURN', 'FINISHED', 'VOID')),
    outcome         TEXT        CONSTRAINT blackjack_hand_outcome_check
                                CHECK (outcome IN ('BLACKJACK', 'WIN', 'PUSH', 'LOSE')),
    payout          BIGINT,
    deck            TEXT        NOT NULL,
    player_cards    TEXT        NOT NULL,
    dealer_cards    TEXT        NOT NULL,
    version         BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ,
    settled_at      TIMESTAMPTZ,
    CONSTRAINT blackjack_hand_user_idempotency_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT blackjack_hand_outcome_when_finished CHECK ((status = 'FINISHED') = (outcome IS NOT NULL)),
    CONSTRAINT blackjack_hand_finished_at_when_closed CHECK ((status = 'PLAYER_TURN') = (finished_at IS NULL)),
    CONSTRAINT blackjack_hand_payout_check CHECK (
        (status = 'PLAYER_TURN' AND payout IS NULL)
        OR (status = 'VOID' AND payout = bet)
        OR (status = 'FINISHED' AND payout = CASE outcome
                WHEN 'BLACKJACK' THEN bet * 5 / 2
                WHEN 'WIN' THEN bet * 2
                WHEN 'PUSH' THEN bet
                ELSE 0 END)),
    CONSTRAINT blackjack_hand_settled_only_when_closed CHECK (settled_at IS NULL OR status <> 'PLAYER_TURN')
);

CREATE UNIQUE INDEX blackjack_hand_one_in_progress
    ON blackjack_hand (user_id) WHERE status = 'PLAYER_TURN';

CREATE INDEX blackjack_hand_unsettled
    ON blackjack_hand (user_id) WHERE status <> 'PLAYER_TURN' AND settled_at IS NULL;
```

- `BLACKJACK_BET` siempre resta y `BLACKJACK_PAYOUT` siempre suma: el `CHECK` de signo de V6 se
  sustituye entero, igual que V5 hizo con el de tipos.
- Estados: `PLAYER_TURN` (en juego), `FINISHED` (con `outcome` y `payout`) y `VOID` (la apuesta se
  cobró pero la mano no pudo ocupar la mesa; se devuelve entera). `VOID` no sale nunca por la API.
- `settled_at` marca que el pago ya está en el ledger (o que no había nada que pagar).
- Una mano en juego por socio: el índice único parcial `blackjack_hand_one_in_progress`.
- Bloqueo optimista: `@Version` en `BlackjackHand`, como en `Wallet`.
- Las cartas se guardan como texto (`AS,TD,9C`) con un `AttributeConverter`; `bet` como `BIGINT`
  con otro, para poder sumarlo. `user_id` cae en cascada con el usuario, como `wallet`: la mano es
  estado de juego, no ledger, y el ledger ya bloquea ese borrado con `RESTRICT`.

### 4. Idempotencia

- `POST /api/v1/blackjack/hands` exige la cabecera `Idempotency-Key` (UUID), igual que los
  pedidos de la barra. La mano guarda esa clave (`UNIQUE (user_id, idempotency_key)`).
- La apuesta se cobra con la clave de ledger `blackjack-bet:<Idempotency-Key>`.
- El pago se abona con `blackjack-payout:<id de la mano>`: una mano se paga como mucho una vez. La
  devolución de una mano `VOID` usa la misma clave.
- Repetir la misma `Idempotency-Key` devuelve la misma mano sin cobrar otra vez. Si la apuesta no
  coincide, 409 `wallet.idempotency_mismatch`. Si aquella mano quedó `VOID`, la repetición vuelve
  a dar 409 `blackjack.hand_in_progress`, que es lo que respondió la primera vez.
- Ambas claves caben en los 64 caracteres del ledger (50 y 53).
- `HIT` y `STAND` no llevan clave: cada uno cambia la versión de la mano y el frontend bloquea el
  botón mientras espera. Si dos acciones chocan en la versión, la perdedora relee la mano y
  devuelve el estado actual.

### 5. Coherencia sin transacción compartida

`BlackjackService` no lleva `@Transactional` (ADR-04, regla 6; `BarService` es el modelo). Cada
escritura de la mano y cada movimiento de monedas van en su propia transacción. El orden fija qué
puede quedar a medias:

**Repartir:** reconciliar → buscar la mano por clave (repetición) → si ya hay una en juego, 409
sin cobrar → `debit` de la apuesta → insertar la mano ya repartida → si acabó por un natural,
pagarla.

**Jugar:** reconciliar → cargar la mano por `id` y `user_id` → aplicar `HIT` o `STAND` con el motor
→ guardar con `@Version` → si terminó, pagarla.

**Pagar** (`settle`): `credit(payout, BLACKJACK_PAYOUT, "blackjack-payout:<id>")` si el pago es
mayor que 0 → poner `settled_at`. Si otra petición ya la marcó, se relee.

La invariante que lo sostiene: **una fila de `blackjack_hand` solo se inserta cuando su
`BLACKJACK_BET` ya existe**. Por tanto, las manos son un subconjunto de las apuestas, y si la suma
de `bet` de las manos de un socio es igual a lo apostado según el ledger (`spentOn(userId,
BLACKJACK_BET)`), no hay apuestas huérfanas. Son dos sumas indexadas (la de V6 y la de
`blackjack_hand`).

**Reconciliar** (al principio de cada llamada, también en el `GET`):

1. Si las dos sumas no cuadran, se listan los `BLACKJACK_BET` del socio
   (`WalletService.movementsOf`, método nuevo y de solo lectura) y cada apuesta sin mano se
   **adopta**: se reparte su mano con la clave sacada de `blackjack-bet:<clave>`. Si la mesa está
   ocupada por otra mano, se inserta como `VOID` para devolverla.
2. Cada mano cerrada sin `settled_at` se paga con `settle`. El abono es idempotente, así que da
   igual si ya se había hecho.

| Dónde se corta | Qué queda | Quién lo arregla |
|---|---|---|
| Tras cobrar la apuesta, antes de insertar la mano | `BLACKJACK_BET` sin mano | El reintento con la misma clave, o la siguiente llamada: las sumas no cuadran y la apuesta se adopta |
| La mesa la ocupó otra clave entre la comprobación y el insert | Insert rechazado por `blackjack_hand_one_in_progress` | La misma petición inserta la mano `VOID`, devuelve la apuesta y responde 409 `blackjack.hand_in_progress` |
| Tras insertar la `VOID`, antes de devolver | `VOID` sin `settled_at` | La siguiente llamada: `settle` devuelve la apuesta |
| Mano terminada y guardada, antes del abono | `FINISHED` sin `settled_at` | La siguiente llamada la paga |
| Abono hecho, antes de `settled_at` | `FINISHED` sin `settled_at` y con su `BLACKJACK_PAYOUT` | `credit` devuelve el movimiento previo y se marca `settled_at` |
| Dos peticiones con la misma clave a la vez | Choque en `wallet.version` o en `blackjack_hand_user_idempotency_key` | El débito reintenta y encuentra el movimiento previo; el insert perdedor relee la mano por clave |
| La reconciliación adopta mientras la petición original sigue viva | Dos inserts para la misma clave | Gana uno por el `UNIQUE`; el otro relee y devuelve esa mano. Sus cartas no se han enseñado a nadie |

Una mano que acaba en `LOSE` se marca `settled_at` sin movimiento. Un 409 `wallet.conflict` en el
abono deja la mano `FINISHED` sin pagar, y la siguiente llamada la paga.

### 6. Códigos de error

Nuevos en `ErrorCode`, con su excepción de dominio (`ApiException`) y sin tocar
`GlobalExceptionHandler`:

| Código | Estado | Cuándo |
|---|---|---|
| `blackjack.hand_in_progress` | 409 | Repartir con una mano en juego (o repetir la clave de una mano `VOID`) |
| `blackjack.hand_not_found` | 404 | `HIT` o `STAND` sobre una mano que no existe o es de otro socio |
| `blackjack.hand_finished` | 409 | `HIT` o `STAND` sobre una mano terminada |
| `blackjack.too_many_requests` | 429 | Límite por socio de la mesa (punto 7) |

Se reutilizan `wallet.insufficient_funds` (422), `wallet.idempotency_mismatch` (409),
`wallet.conflict` (409), `wallet.not_found` (404), `request.rejected` (400) y `validation.failed`
(400). La mano de otro socio da 404, no 403: no se confirma que exista.

### 7. Límite de peticiones

`BarRateLimitFilter` pasó a ser `MemberRateLimitFilter`, con una lista de áreas: prefijo de ruta,
máximo por minuto y `ErrorCode` del 429. Cada área tiene su `FixedWindowCounter` y cuenta por
`userId`, solo los `POST`, después del filtro JWT y dejando pasar a quien no tiene sesión (para que
conteste el 401), como hoy.

| Área | Prefijo | Por defecto | Propiedad | 429 |
|---|---|---|---|---|
| Barra | `/api/v1/bar/` | 30 | `bar.rate-limit.max-per-minute` | `bar.too_many_requests` |
| Mesa | `/api/v1/blackjack/` | 60 | `blackjack.rate-limit.max-per-minute` | `blackjack.too_many_requests` |

- Presupuestos separados: jugar no te cierra la barra. Una mano son de 2 a 8 `POST`, por eso la
  mesa tiene 60.
- `GET /api/v1/blackjack` no cuenta, como la carta de la barra. Su reconciliación solo escribe
  cuando algo quedó a medias, y siempre con claves idempotentes.
- Desde ADR-05 el filtro tiene una tercera área, exacta, para `POST /api/v1/bar/talk`: 10 por minuto
  (`barman.talk.max-per-minute`), con el 429 de la barra.

## API

Base `/api/v1`, con `Authorization: Bearer`. El `userId` sale del token, nunca del body.

`hand` es `{id, bet, status, playerCards, playerTotal, dealerCards, dealerTotal, outcome, payout}`.
Cada carta es `{face, suit}` (`{"face":"QUEEN","suit":"HEARTS"}`). `status` es `PLAYER_TURN` o
`FINISHED`. `outcome` y `payout` son `null` mientras se juega.

| Endpoint | Respuesta 200 | Errores |
|---|---|---|
| `GET /blackjack` | `{hand, balance, bets}`; `hand` es la mano en juego o `null`; `bets` es `[{code, chips}]` | 401, 404 `wallet.not_found` |
| `POST /blackjack/hands` + `Idempotency-Key` · `{bet}` | `{hand, balance}` (si sale un natural, ya viene terminada y pagada) | 400, 401, 409 `blackjack.hand_in_progress` / `wallet.idempotency_mismatch` / `wallet.conflict`, 422 `wallet.insufficient_funds`, 429 |
| `POST /blackjack/hands/{id}/hit` | `{hand, balance}` | 400 (id que no es UUID), 401, 404 `blackjack.hand_not_found`, 409 `blackjack.hand_finished` / `wallet.conflict`, 429 |
| `POST /blackjack/hands/{id}/stand` | `{hand, balance}` | igual que `hit` |

`balance` es el saldo actual de la cartera al responder, ya con la apuesta y el pago aplicados.

## Alternativas consideradas

- **Liquidar en la misma transacción que la mano (ADR-03).** Descartada: choca con la regla 6 del
  ADR-04 y obligaría a abrir `WalletService` a transacciones externas, justo lo que impide el
  reintento limpio del bloqueo optimista. Esta decisión sustituye esa frase del ADR-03; el resto
  (servidor con autoridad, `SecureRandom`, estado que sobrevive a una recarga) se mantiene.
- **`GameSession` genérica con `stateJson` y `BlackjackHand` aparte (diseño de Fase 0).**
  Descartada por ahora: con un solo juego y una sola mano, una tabla basta. El segundo juego
  tendrá su propia tabla.
- **Insertar la mano antes de cobrar (reserva).** Descartada: si la reconciliación anula una
  reserva sin cobro mientras la petición original sigue viva y entra un abono entre medias, la
  apuesta se cobra después de anular la mano. Cobrar primero hace que toda mano tenga su apuesta, y
  eso se comprueba con dos sumas.
- **Reconciliar con una tarea programada.** Descartada: otra pieza que desplegar y probar, y el
  socio solo ve sus manos cuando llama a la API. Reconciliar en la propia llamada cuesta dos sumas
  y una consulta con índice parcial.
- **Bloqueo pesimista sobre la mano.** Descartado por lo mismo que en el ADR-04: el débito corre en
  su propia transacción y el bloqueo no lo cubriría.
- **Apuesta libre.** Descartada: tres apuestas pares garantizan que el 3:2 da un entero (ADR-09)
  y la BD puede comprobarlo con un `CHECK`.
- **Contar mesa y barra en el mismo presupuesto del límite.** Descartado: una mesa activa dejaría
  sin copas al socio.

## Consecuencias

- Las monedas van y vuelven: se ganan en la mesa y se gastan en la barra, en escritorio y en móvil,
  porque la API no da nada por hecho sobre el cliente.
- Perder en la mesa puede dejar al socio sin saldo para la copa más barata, y entonces la barra le
  ofrece el fiado del día (ADR-10).
- Cada llamada a la mesa hace de dos a cuatro consultas extra de reconciliación. Con índices, es
  poco.
- Todo cobro y todo pago queda en el ledger con su clave: cualquier mano se explica fila a fila, y
  una mano `VOID` deja a la vista la apuesta y su devolución.
- La invariante «mano solo con apuesta» es una disciplina: quien inserte en `blackjack_hand` por
  otro camino rompe la detección de huérfanas. Solo `BlackjackService` escribe en esa tabla.
- `logServerErrorDetail=false` quita los valores de todos los errores de PostgreSQL. Depurar una
  violación pide reproducirla, pero ni el mazo ni un email acaban en el log.
- `HIT` sin clave de idempotencia: un reintento de red puede pedir una carta de más. El frontend
  bloquea el botón y el estado devuelto es siempre la verdad del servidor.
