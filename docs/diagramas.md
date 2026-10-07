# Diagramas técnicos

Cinco diagramas: la base de datos, las clases del dominio, las clases por capas, el flujo de pedir una copa y el de repartir y pagar una mano de blackjack.
Todos salen del código: migraciones Flyway V1 a V7 y paquetes de `com.iulianlounge.backend`. Si el código cambia, este fichero cambia en el mismo commit.
El diseño amplio de la Fase 0 (salas, retos, préstamos, progreso) vive en [architecture.md](architecture.md) y no está implementado.

## 1. Base de datos

Cuatro tablas en PostgreSQL. `flyway_schema_history` es de Flyway y no se dibuja.

```mermaid
erDiagram
    users ||--o| wallet : "wallet.user_id"
    wallet ||--o{ token_transaction : "token_transaction.wallet_id"
    users ||--o{ blackjack_hand : "blackjack_hand.user_id"

    users {
        uuid id PK
        varchar username UK "VARCHAR(50), NOT NULL"
        text email UK "NOT NULL, único sobre lower(email)"
        varchar password_hash "VARCHAR(60), NOT NULL, BCrypt"
        text role "NOT NULL, CHECK USER o ADMIN"
        text locale "NOT NULL, CHECK es o en"
        timestamptz created_at "NOT NULL"
        timestamptz last_seen_at "nullable"
    }

    wallet {
        uuid id PK
        uuid user_id FK,UK "NOT NULL, ON DELETE CASCADE"
        bigint balance "NOT NULL, CHECK >= 0"
        bigint version "NOT NULL, bloqueo optimista"
        timestamptz created_at "NOT NULL"
    }

    token_transaction {
        uuid id PK
        uuid wallet_id FK "NOT NULL, ON DELETE RESTRICT"
        bigint amount "NOT NULL, con signo, CHECK distinto de 0"
        text type "NOT NULL, CHECK de tipos"
        bigint balance_after "NOT NULL, CHECK >= 0"
        text idempotency_key "nullable, máx. 64 caracteres"
        timestamptz created_at "NOT NULL"
    }

    blackjack_hand {
        uuid id PK
        uuid user_id FK "NOT NULL, ON DELETE CASCADE"
        uuid idempotency_key UK "NOT NULL, único con user_id"
        bigint bet "NOT NULL, CHECK 10, 20 o 50"
        text status "NOT NULL, CHECK PLAYER_TURN, FINISHED o VOID"
        text outcome "nullable, CHECK BLACKJACK, WIN, PUSH o LOSE"
        bigint payout "nullable, atado al resultado"
        text deck "NOT NULL, solo servidor"
        text player_cards "NOT NULL"
        text dealer_cards "NOT NULL, solo servidor hasta que acaba"
        bigint version "NOT NULL, bloqueo optimista"
        timestamptz created_at "NOT NULL"
        timestamptz finished_at "nullable"
        timestamptz settled_at "nullable"
    }
```

Relaciones:

- `users` 1 a 1 `wallet`. `wallet.user_id` es `NOT NULL` y `UNIQUE`: cada cartera tiene un dueño y nadie tiene dos. La BD admitiría un usuario sin cartera, pero el código evita que pase: `RegisterService` abre la cartera al registrar y V4 creó una vacía a cada usuario que ya existía.
- `wallet` 1 a N `token_transaction`. El ledger solo crece (ADR-04) y `wallet.balance` es su caché.
- `users` 1 a N `blackjack_hand`, con `ON DELETE CASCADE` como `wallet`: la mano es estado de juego, no ledger. El ledger sigue bloqueando el borrado con `RESTRICT`.
- Las fichas son `BIGINT`, nunca decimales (ADR-09).

Restricciones e índices, con su migración:

| Tabla | Regla | Qué hace | Migración |
|---|---|---|---|
| `users` | `UNIQUE (username)` | Nombre de usuario único. Distingue mayúsculas | V1 |
| `users` | `users_email_lower_key`: índice único sobre `lower(email)` | Email único sin distinguir mayúsculas. Sustituye a `users_email_key`, que se borra | V2 |
| `users` | `users_role_check`: `role IN ('USER', 'ADMIN')` | La BD solo acepta los valores del enum `Role` | V3 |
| `users` | `users_locale_check`: `locale IN ('es', 'en')` | La BD solo acepta los códigos del enum `Language` | V3 |
| `wallet` | `UNIQUE (user_id)` | Una cartera por usuario como mucho. Es lo que hace la relación 1 a 0..1 | V4 |
| `wallet` | `user_id` `REFERENCES users (id) ON DELETE CASCADE` | Borrar el usuario borra su cartera | V4 |
| `wallet` | `CHECK (balance >= 0)` | Última defensa contra el saldo negativo, aunque Java fallara | V4 |
| `token_transaction` | `wallet_id` `REFERENCES wallet (id) ON DELETE RESTRICT` | No se puede borrar una cartera con movimientos: el ledger no se pierde. Como la cartera cae en cascada con el usuario, hoy tampoco se puede borrar un usuario con movimientos. El borrado RGPD será una operación explícita (ADR-04) | V4 |
| `token_transaction` | `CHECK (amount <> 0)` | Un movimiento de 0 no es un movimiento | V4 |
| `token_transaction` | `CHECK (balance_after >= 0)` | El saldo tras cada movimiento nunca es negativo | V4 |
| `token_transaction` | `CHECK (char_length(idempotency_key) <= 64)` | Tope a la clave que inventa el cliente | V4 |
| `token_transaction` | `token_transaction_type_check`: `type IN ('WELCOME_BONUS', 'BAR_ORDER', 'HOUSE_CREDIT', 'BLACKJACK_BET', 'BLACKJACK_PAYOUT')` | Tipos de movimiento. V4 solo tenía `WELCOME_BONUS`; V5 lo borra y lo crea con tres; V7 lo borra otra vez y lo crea con cinco | V4, V5, V7 |
| `token_transaction` | `token_transaction_amount_sign_check`: `(type IN ('BAR_ORDER', 'BLACKJACK_BET')) = (amount < 0)` | Un pedido y una apuesta siempre restan. El bono, el crédito de la casa y el pago siempre suman. V7 sustituye entero el `CHECK` de V6 | V6, V7 |
| `token_transaction` | `token_transaction_wallet_idempotency_key`: índice único `(wallet_id, idempotency_key)` `WHERE idempotency_key IS NOT NULL` | Índice parcial. Un reintento con la misma clave en la misma cartera falla. Sin clave no hay límite | V4 |
| `token_transaction` | `token_transaction_wallet_created_at`: índice `(wallet_id, created_at DESC, id DESC)` | Historial paginado del más nuevo al más viejo. `id` desempata | V4 |
| `token_transaction` | `token_transaction_wallet_type`: índice `(wallet_id, type) INCLUDE (amount)` | Suma del gasto por tipo, que da el rango y lo apostado | V6 |
| `blackjack_hand` | `user_id` `REFERENCES users (id) ON DELETE CASCADE` | Borrar el usuario borra sus manos | V7 |
| `blackjack_hand` | `blackjack_hand_user_idempotency_key`: `UNIQUE (user_id, idempotency_key)` | Repetir la clave de un reparto devuelve la misma mano. Otro socio puede usar la misma clave | V7 |
| `blackjack_hand` | `blackjack_hand_bet_check`: `bet IN (10, 20, 50)` | Solo las tres apuestas. Son pares, así que el 3:2 es un entero | V7 |
| `blackjack_hand` | `blackjack_hand_status_check`: `status IN ('PLAYER_TURN', 'FINISHED', 'VOID')` | Estados de la mano. `VOID` es una apuesta cobrada que no pudo ocupar la mesa y se devuelve entera | V7 |
| `blackjack_hand` | `blackjack_hand_outcome_check`: `outcome IN ('BLACKJACK', 'WIN', 'PUSH', 'LOSE')` | Resultados posibles | V7 |
| `blackjack_hand` | `blackjack_hand_outcome_when_finished`: `(status = 'FINISHED') = (outcome IS NOT NULL)` | Solo una mano terminada tiene resultado, y toda mano terminada lo tiene | V7 |
| `blackjack_hand` | `blackjack_hand_finished_at_when_closed`: `(status = 'PLAYER_TURN') = (finished_at IS NULL)` | `finished_at` está puesto justo cuando la mano ya no está en juego | V7 |
| `blackjack_hand` | `blackjack_hand_payout_check` | Sin pago en juego, la apuesta entera en `VOID`, y en `FINISHED` el pago que toca al resultado: 2,5 veces, 2 veces, 1 vez o 0 | V7 |
| `blackjack_hand` | `blackjack_hand_settled_only_when_closed`: `settled_at IS NULL OR status <> 'PLAYER_TURN'` | Una mano en juego no se puede marcar como pagada | V7 |
| `blackjack_hand` | `blackjack_hand_one_in_progress`: índice único `(user_id)` `WHERE status = 'PLAYER_TURN'` | Índice parcial. Una mano en juego por socio como mucho | V7 |
| `blackjack_hand` | `blackjack_hand_unsettled`: índice `(user_id)` `WHERE status <> 'PLAYER_TURN' AND settled_at IS NULL` | Encuentra rápido las manos cerradas sin pagar, que se buscan en cada llamada | V7 |

## 2. Clases del dominio

Paquete `domain`. Se omiten los getters y setters de `User`: hay uno de cada por columna. De `TokenTransaction` se omiten `getId()`, `getWalletId()` y `getCreatedAt()`. De `BlackjackHand` se omiten los getters de columna salvo los que usa `HandView`.

```mermaid
classDiagram
    direction LR

    class User {
        <<Entity>>
        -UUID id
        -String username
        -String email
        -String passwordHash
        -Role role
        -Language locale
        -Instant createdAt
        -Instant lastSeenAt
        -boolean isNew
        +isNew() boolean
    }

    class Persistable~UUID~ {
        <<interface>>
    }

    class Wallet {
        <<Entity>>
        -UUID id
        -UUID userId
        -long balance
        -Long version
        -Instant createdAt
        +Wallet(UUID, Instant)
        +getId() UUID
        +getUserId() UUID
        +getBalance() long
        +setBalance(long)
        +getCreatedAt() Instant
    }

    class TokenTransaction {
        <<Entity>>
        -UUID id
        -UUID walletId
        -long amount
        -TransactionType type
        -long balanceAfter
        -String idempotencyKey
        -Instant createdAt
        +TokenTransaction(UUID, long, TransactionType, long, String, Instant)
        +getAmount() long
        +getType() TransactionType
        +getBalanceAfter() long
        +getIdempotencyKey() String
    }

    class Role {
        <<enumeration>>
        USER
        ADMIN
    }

    class Language {
        <<enumeration>>
        ES
        EN
        -String code
        +code() String
        +fromCode(String)$ Language
    }

    class LanguageConverter {
        <<Converter>>
        +convertToDatabaseColumn(Language) String
        +convertToEntityAttribute(String) Language
    }

    class TransactionType {
        <<enumeration>>
        WELCOME_BONUS
        BAR_ORDER
        HOUSE_CREDIT
        BLACKJACK_BET
        BLACKJACK_PAYOUT
    }

    class Rank {
        <<enumeration>>
        NADIE
        HABITUAL
        CONFIANZA
        SOCIO
        -long minSpent
        +minSpent() long
        +forSpent(long)$ Rank
    }

    class Drink {
        <<enumeration>>
        BATHTUB_GIN
        BEES_KNEES
        GIN_RICKEY
        SIDECAR
        FRENCH_75
        -long price
        +price() long
        +cheapestPrice()$ long
    }

    class BarmanSituation {
        <<enumeration>>
        GREETING
        SERVE
        PROMOTION
        BROKE
        NO_CREDIT
        HOUSE_CREDIT
        -boolean byRank
        +lineFor(Rank) String
    }

    class BlackjackHand {
        <<Entity>>
        -UUID id
        -UUID userId
        -UUID idempotencyKey
        -Bet bet
        -HandStatus status
        -Outcome outcome
        -Long payout
        -List~Card~ deck
        -List~Card~ playerCards
        -List~Card~ dealerCards
        -Long version
        -Instant createdAt
        -Instant finishedAt
        -Instant settledAt
        +BlackjackHand(UUID, UUID, Bet, BlackjackTable, Instant)
        +voided(UUID, UUID, Bet, Instant)$ BlackjackHand
        +play(BlackjackTable, Instant)
        +settle(Instant)
        +table() BlackjackTable
        +isSettled() boolean
    }

    class BlackjackTable {
        <<record>>
        List~Card~ deck
        List~Card~ playerCards
        List~Card~ dealerCards
        Outcome outcome
        +isFinished() boolean
    }

    class BlackjackRules {
        <<final>>
        +deal(List~Card~)$ BlackjackTable
        +hit(BlackjackTable)$ BlackjackTable
        +stand(BlackjackTable)$ BlackjackTable
    }

    class Shuffler {
        <<interface>>
        +shuffle(List~Card~) List~Card~
    }

    class Card {
        <<record>>
        Face face
        Suit suit
        +deck()$ List~Card~
        +fromCode(String)$ Card
        +code() String
    }

    class Face {
        <<enumeration>>
        ACE
        TWO a NINE
        TEN
        JACK
        QUEEN
        KING
        +points() int
    }

    class Suit {
        <<enumeration>>
        CLUBS
        DIAMONDS
        HEARTS
        SPADES
    }

    class HandTotal {
        <<record>>
        int value
        boolean soft
        int cardCount
        +of(List~Card~)$ HandTotal
        +isBust() boolean
        +isBlackjack() boolean
    }

    class Bet {
        <<enumeration>>
        TEN
        TWENTY
        FIFTY
        -long chips
        +chips() long
        +fromChips(long)$ Bet
    }

    class Outcome {
        <<enumeration>>
        BLACKJACK
        WIN
        PUSH
        LOSE
        +payoutFor(Bet) long
    }

    class HandStatus {
        <<enumeration>>
        PLAYER_TURN
        FINISHED
        VOID
    }

    class BetConverter {
        <<Converter>>
        +convertToDatabaseColumn(Bet) Long
        +convertToEntityAttribute(Long) Bet
    }

    class CardsConverter {
        <<Converter>>
        +convertToDatabaseColumn(List~Card~) String
        +convertToEntityAttribute(String) List~Card~
    }

    Persistable~UUID~ <|.. User
    User --> Role : role
    User --> Language : locale
    LanguageConverter ..> Language : es y en
    Wallet "0..1" ..> "1" User : userId
    TokenTransaction "0..*" ..> "1" Wallet : walletId
    TokenTransaction --> TransactionType : type
    BarmanSituation ..> Rank : lineFor
    BlackjackHand "0..*" ..> "1" User : userId
    BlackjackHand --> Bet : bet
    BlackjackHand --> HandStatus : status
    BlackjackHand --> Outcome : outcome
    BlackjackHand --> Card : deck, playerCards, dealerCards
    BlackjackHand ..> BlackjackTable : table() y play()
    BetConverter ..> Bet : 10, 20 y 50
    CardsConverter ..> Card : AS,TD,9C
    BlackjackRules ..> BlackjackTable : recibe y devuelve
    BlackjackRules ..> HandTotal : totales
    BlackjackTable --> Card
    BlackjackTable --> Outcome
    Card --> Face
    Card --> Suit
    Outcome ..> Bet : payoutFor
    Shuffler ..> Card : baraja
```

- Las entidades no se enlazan con `@ManyToOne` ni `@OneToOne`. Cada una guarda el `UUID` de la otra (`Wallet.userId`, `TokenTransaction.walletId`), y por eso esas flechas son de puntos.
- `TokenTransaction` no tiene setters y lleva `@Immutable`: el ledger no se edita (ADR-04).
- `Wallet.version` lleva `@Version`. Si hay dos escrituras a la vez, la segunda falla en vez de machacar a la primera.
- `User` implementa `Persistable<UUID>` porque el id lo pone el servicio, no la BD. `isNew()` le dice a Spring Data si toca un `INSERT`. En `Wallet` y `TokenTransaction` el id lo genera Hibernate (`@GeneratedValue(strategy = GenerationType.UUID)`). El SQL no tiene `DEFAULT`.
- `LanguageConverter` (`@Converter(autoApply = true)`, implementa `AttributeConverter<Language, String>`) guarda `Language` como `es` o `en`. `Role` y `TransactionType` se guardan por nombre (`@Enumerated(EnumType.STRING)`).
- Valores fijos en el código: las copas de `Drink` cuestan 5, 10, 15, 25 y 40 fichas. Los rangos de `Rank` empiezan en 0, 25, 100 y 300 fichas gastadas en la barra.
- `BarmanSituation.lineFor(rank)` devuelve una clave i18n (`barman.serve.habitual`, `barman.no_credit`) y no un texto (ADR-06).
- El motor del blackjack es Java puro y sin estado: `BlackjackRules` recibe un `BlackjackTable` inmutable y devuelve otro. `HandTotal` cuenta el as como 11 mientras no se pase y después como 1. Ahí viven casi todos los tests de juego.
- `BlackjackHand` guarda el mazo y la carta oculta del crupier y nunca se serializa: los controladores devuelven `HandView`. No sobrescribe `toString`, para que nada de la mano acabe en un log. Lleva `@Version` como `Wallet`.
- `BetConverter` (`autoApply`) guarda la apuesta como `BIGINT` para poder sumarla. `CardsConverter` guarda las cartas como texto (`AS,TD,9C`) y una lista vacía como cadena vacía. `Outcome` y `HandStatus` se guardan por nombre.
- Las apuestas son 10, 20 y 50 fichas, y `Outcome.payoutFor` paga 2,5 veces, 2, 1 y 0. Con apuestas pares el 3:2 sale entero.

## 3. Clases por capas

### 3.1 Controladores, servicios y repositorios

Cada flecha es una dependencia que entra por el constructor. Para no ensuciar el dibujo faltan `Clock`, `PasswordEncoder`, `TransactionOperations` y el secreto JWT.

```mermaid
classDiagram
    direction TB

    class AuthController {
        +register(RegisterRequest) RegisterResponse
        +login(LoginRequest) ResponseEntity~AccessTokenResponse~
        +refresh(String) ResponseEntity~AccessTokenResponse~
        +logout() ResponseEntity~Void~
    }
    class MeController {
        +me(AccessTokenClaims) MeResponse
    }
    class WalletController {
        +wallet(AccessTokenClaims) WalletResponse
        +transactions(AccessTokenClaims, int, int) PageResponse~TransactionResponse~
    }
    class BarController {
        +bar(AccessTokenClaims) BarResponse
        +order(AccessTokenClaims, UUID, OrderRequest) OrderResponse
        +houseCredit(AccessTokenClaims) HouseCreditResponse
    }
    class BlackjackController {
        +table(AccessTokenClaims) BlackjackResponse
        +deal(AccessTokenClaims, UUID, DealRequest) HandResponse
        +hit(AccessTokenClaims, UUID) HandResponse
        +stand(AccessTokenClaims, UUID) HandResponse
    }

    class RegisterService {
        +register(RegisterRequest) UUID
    }
    class AuthService {
        +login(LoginRequest) IssuedTokens
        +refresh(String) IssuedTokens
    }
    class UserService {
        +getProfile(UUID) MeResponse
    }
    class BarService {
        +menu(UUID) BarResponse
        +order(UUID, Drink, UUID) OrderResponse
        +houseCredit(UUID) HouseCreditResponse
        +rankOf(UUID) Rank
    }
    class BlackjackService {
        +table(UUID) BlackjackResponse
        +deal(UUID, Bet, UUID) HandResponse
        +hit(UUID, UUID) HandResponse
        +stand(UUID, UUID) HandResponse
    }
    class WalletService {
        +openWallet(UUID) TokenTransaction
        +credit(UUID, long, TransactionType, String) TokenTransaction
        +creditIf(UUID, long, TransactionType, String, LongPredicate) Optional~TokenTransaction~
        +debit(UUID, long, TransactionType, String) TokenTransaction
        +getBalance(UUID) long
        +spentOn(UUID, TransactionType) long
        +movementsOf(UUID, TransactionType) List~TokenTransaction~
        +hasMovement(UUID, String) boolean
        +getTransactions(UUID, Pageable) Page~TokenTransaction~
    }
    class JwtService {
        +generateAccessToken(User) String
        +generateRefreshToken(User, Instant) String
        +validateAccessToken(String) AccessTokenClaims
        +validateRefreshToken(String) RefreshTokenClaims
    }

    class UserRepository {
        <<interface>>
        +findByUsername(String) Optional~User~
        +existsByUsername(String) boolean
        +existsByEmail(String) boolean
    }
    class WalletRepository {
        <<interface>>
        +findByUserId(UUID) Optional~Wallet~
    }
    class TokenTransactionRepository {
        <<interface>>
        +saveAndFlush(TokenTransaction) TokenTransaction
        +findByWalletIdAndIdempotencyKey(UUID, String) Optional~TokenTransaction~
        +findByWalletIdOrderByCreatedAtDescIdDesc(UUID, Pageable) Page~TokenTransaction~
        +sumAmountByWalletIdAndType(UUID, TransactionType) long
        +findByWalletIdAndTypeOrderByCreatedAtAscIdAsc(UUID, TransactionType) List~TokenTransaction~
    }
    class BlackjackHandRepository {
        <<interface>>
        +saveAndFlush(BlackjackHand) BlackjackHand
        +findByIdAndUserId(UUID, UUID) Optional~BlackjackHand~
        +findByUserIdAndIdempotencyKey(UUID, UUID) Optional~BlackjackHand~
        +findByUserIdAndStatus(UUID, HandStatus) Optional~BlackjackHand~
        +sumBetByUserId(UUID) long
        +findUnsettledByUserId(UUID) List~BlackjackHand~
    }
    class Shuffler {
        <<interface>>
        +shuffle(List~Card~) List~Card~
    }
    class BlackjackConfig {
        <<Configuration>>
        +shuffler() Shuffler
    }

    AuthController --> RegisterService
    AuthController --> AuthService
    MeController --> UserService
    WalletController --> WalletService
    BarController --> BarService
    BlackjackController --> BlackjackService

    RegisterService --> UserRepository
    RegisterService --> WalletService
    AuthService --> UserRepository
    AuthService --> JwtService
    UserService --> UserRepository
    UserService --> BarService
    BarService --> WalletService
    BlackjackService --> WalletService
    BlackjackService --> BlackjackHandRepository
    BlackjackService --> Shuffler
    BlackjackConfig ..> Shuffler : SecureRandom
    WalletService --> WalletRepository
    WalletService --> TokenTransactionRepository
```

- El saldo solo se toca a través de `WalletService`. `RegisterService` (bono de bienvenida), `BarService` (pedidos y crédito de la casa) y `BlackjackService` (apuestas y pagos) pasan por él. Ninguno de los tres toca los repositorios de cartera. `creditIf` comprueba una condición sobre el saldo dentro de la transacción del crédito, y la vuelve a comprobar si reintenta: así el fiado no se cuela si el saldo cambió entre la consulta y el cobro. Devuelve un `Optional` vacío si la condición no se cumple, y `BarService` lo convierte en 422 con `orElseThrow`.
- `BlackjackService` tampoco lleva `@Transactional`, igual que `BarService`: cada cobro, cada pago y cada escritura de la mano van en su propia transacción. Lo que queda a medias lo arregla la reconciliación del inicio de cada llamada (apartado 5). El único método nuevo de `WalletService` es `movementsOf`, de solo lectura.
- `UserService` usa `BarService` solo para sacar el rango que devuelve `GET /api/v1/me`.
- `UserRepository` y `WalletRepository` extienden `JpaRepository`, y de ahí salen `findById` y `saveAndFlush`. `TokenTransactionRepository` y `BlackjackHandRepository` extienden `Repository` a secas, así que no exponen `delete` ni el `save` genérico.
- `AuthService` devuelve el record `IssuedTokens`. `AuthController` monta y borra la cookie `refresh_token` con los métodos estáticos de `RefreshCookies` (ADR-08).
- `JwtService` está en el paquete `security`, no en `service`.

### 3.2 Cadena de seguridad y errores

Flecha continua: dependencia de constructor o herencia. Flecha de puntos: `SecurityConfig` crea el objeto con `new`, o una clase usa a otra sin guardarla.

```mermaid
classDiagram
    direction LR

    class SecurityConfig {
        <<Configuration>>
        +passwordEncoder() PasswordEncoder
        +securityFilterChain(HttpSecurity, JwtService, int, int, int, ObjectProvider~Clock~) SecurityFilterChain
    }
    class FixedWindowCounter {
        -int maxRequestsPerWindow
        -Clock clock
        -int maxTrackedKeys
        ~tryAcquire(String) boolean
        ~rejectTooManyRequests(HttpServletResponse, ErrorCode)$
    }
    class AuthRateLimitFilter {
        -FixedWindowCounter counter
        #shouldNotFilter(HttpServletRequest) boolean
        #doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)
    }
    class MemberRateLimitFilter {
        -List~LimitedArea~ areas
        #shouldNotFilter(HttpServletRequest) boolean
        #doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)
    }
    class Area {
        <<record>>
        String pathPrefix
        int maxRequestsPerMinute
        ErrorCode tooManyRequests
    }
    class JwtAuthenticationFilter {
        -JwtService jwtService
        #doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)
    }
    class ProblemDetailAuthenticationEntryPoint {
        +commence(HttpServletRequest, HttpServletResponse, AuthenticationException)
    }
    class JwtService {
        +validateAccessToken(String) AccessTokenClaims
    }
    class AccessTokenClaims {
        <<record>>
        UUID userId
        String role
    }
    class ResponseEntityExceptionHandler
    class GlobalExceptionHandler {
        <<RestControllerAdvice>>
        +handleApiException(ApiException) ProblemDetail
        +handleInvalidToken(InvalidTokenException) ResponseEntity~ProblemDetail~
        +handleDataIntegrityViolation(DataIntegrityViolationException) ProblemDetail
        +handleUnexpected(Exception) ProblemDetail
        #handleMethodArgumentNotValid(MethodArgumentNotValidException, HttpHeaders, HttpStatusCode, WebRequest) ResponseEntity~Object~
        #handleExceptionInternal(Exception, Object, HttpHeaders, HttpStatusCode, WebRequest) ResponseEntity~Object~
    }
    class ApiException {
        <<abstract>>
        -ErrorCode errorCode
        +getErrorCode() ErrorCode
    }
    class ErrorCode {
        <<enumeration>>
        AUTH_REQUIRED
        AUTH_TOO_MANY_REQUESTS
        WALLET_INSUFFICIENT_FUNDS
        BAR_TOO_MANY_REQUESTS
        BLACKJACK_HAND_IN_PROGRESS
        BLACKJACK_HAND_NOT_FOUND
        BLACKJACK_HAND_FINISHED
        BLACKJACK_TOO_MANY_REQUESTS
        -String key
        -HttpStatus status
        -String detail
        +key() String
        +status() HttpStatus
        +detail() String
        +toProblemDetail() ProblemDetail
    }

    SecurityConfig ..> AuthRateLimitFilter : new, primero
    SecurityConfig ..> JwtAuthenticationFilter : new, después
    SecurityConfig ..> MemberRateLimitFilter : new, tras el JWT
    SecurityConfig ..> Area : una por barra y una por mesa
    AuthRateLimitFilter --> FixedWindowCounter
    MemberRateLimitFilter --> FixedWindowCounter : uno por área
    MemberRateLimitFilter --> Area
    MemberRateLimitFilter ..> AccessTokenClaims : userId del SecurityContext
    SecurityConfig ..> ProblemDetailAuthenticationEntryPoint : new, 401 auth.required
    JwtAuthenticationFilter --> JwtService
    JwtService ..> AccessTokenClaims : devuelve
    FixedWindowCounter ..> ErrorCode : 429 a mano
    ProblemDetailAuthenticationEntryPoint ..> ErrorCode : 401 a mano
    ResponseEntityExceptionHandler <|-- GlobalExceptionHandler
    GlobalExceptionHandler ..> ApiException : la traduce
    ApiException --> ErrorCode
    ErrorCode ..> ProblemDetail : RFC 7807
```

- `FixedWindowCounter` es la ventana de un minuto que comparten los dos filtros de límite. `MemberRateLimitFilter` va después del filtro JWT y cuenta por `userId` los `POST` de cada área, y cada área tiene su propio contador y su propio 429:

  | Área | Prefijo | Por defecto | Propiedad | 429 |
  |---|---|---|---|---|
  | Barra | `/api/v1/bar/` | 30 | `bar.rate-limit.max-per-minute` | `bar.too_many_requests` |
  | Mesa | `/api/v1/blackjack/` | 60 | `blackjack.rate-limit.max-per-minute` | `blackjack.too_many_requests` |

  En la barra cuentan juntos el pedido y el fiado. En la mesa, el reparto, `hit` y `stand` (una mano son de 2 a 8 `POST`). Los presupuestos son separados: jugar no cierra la barra. La carta (`GET /api/v1/bar`) y la mesa (`GET /api/v1/blackjack`) no tienen límite. Sin sesión deja pasar la petición para que la seguridad conteste el 401. Si el contador se llena (10.000 socios distintos en un minuto), deja pasar a los nuevos en vez de cerrar el bar a todos: el saldo ya acota lo que puede gastar cada uno.
- `AuthRateLimitFilter` solo actúa en `POST /api/v1/auth/login` y `POST /api/v1/auth/register`. Por defecto deja 20 peticiones por minuto por IP y ruta. A partir de ahí responde 429 `auth.too_many_requests` con la cabecera `Retry-After: 60`. También rechaza con 429 las claves nuevas si, después de barrer las caducadas, sigue habiendo 10.000 claves (IP y ruta) vivas.
- `JwtAuthenticationFilter` valida el `Bearer`, mete `AccessTokenClaims` en el `SecurityContext` con la autoridad `ROLE_` + rol y deja seguir. Si el token no vale, limpia el contexto y se traga la `InvalidTokenException`. Luego la regla `anyRequest().authenticated()` responde 401 con `ProblemDetailAuthenticationEntryPoint` (ADR-08). Por eso no llevar token y llevar uno caducado o inválido dan el mismo 401 `auth.required`, no `auth.invalid_token`.
- Los errores de los filtros no pasan por `GlobalExceptionHandler`, porque los filtros corren antes del `DispatcherServlet`. El 429 y el 401 escriben el JSON a mano con `key()` y `detail()` de su `ErrorCode`.
- `MeController`, `WalletController`, `BarController` y `BlackjackController` reciben las claims con `@AuthenticationPrincipal(errorOnInvalidType = true)`. El `userId` sale del token, nunca del body. Los endpoints de `AuthController` son públicos y no reciben claims.
- Cada excepción de negocio extiende `ApiException` y lleva uno de los 22 `ErrorCode`. `GlobalExceptionHandler` la convierte en `ProblemDetail` con un `code` estable (`wallet.insufficient_funds`). El texto lo pone el frontend (ADR-06).
- `GlobalExceptionHandler` extiende `ResponseEntityExceptionHandler`. Un body inválido da 400 `validation.failed` con el mapa `errors`. Al resto de errores de Spring MVC sin `code` les pone `request.rejected`. El 401 de un refresh inválido borra además la cookie `refresh_token`.

## 4. Pedir una copa

`POST /api/v1/bar/orders`. Lleva la cabecera `Idempotency-Key` con un UUID y el body `{"drink": "SIDECAR"}`.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant F as JwtAuthenticationFilter
    participant J as JwtService
    participant BC as BarController
    participant BS as BarService
    participant WS as WalletService
    participant DB as PostgreSQL
    participant H as GlobalExceptionHandler

    C->>F: POST /api/v1/bar/orders (Bearer, Idempotency-Key, drink)
    F->>J: validateAccessToken(token)
    J-->>F: AccessTokenClaims(userId, role)
    F->>BC: petición autenticada
    BC->>BS: order(userId, drink, idempotencyKey)

    BS->>WS: debit(userId, drink.price(), BAR_ORDER, "order:" + idempotencyKey)
    Note over WS,DB: transacción propia. Si choca @Version, un reintento
    WS->>DB: cartera por user_id y movimiento previo con esa clave

    alt clave nueva con saldo suficiente, o clave ya usada
        alt clave nueva
            WS->>DB: UPDATE wallet (balance, version)
            WS->>DB: INSERT token_transaction (amount negativo, balance_after)
            WS->>DB: COMMIT
            WS-->>BS: TokenTransaction nueva
        else clave ya usada, mismo importe y tipo
            WS-->>BS: TokenTransaction previa, no cobra otra vez
        end
        Note over BS,DB: desde aquí, lecturas fuera de la transacción del cobro
        Note over BS: saldo = balance_after del movimiento
        BS->>WS: spentOn(userId, BAR_ORDER)
        WS->>DB: cartera por user_id y SUM(amount) de BAR_ORDER
        WS-->>BS: gastado (ya incluye este pedido)
        Note over BS: rango = forSpent(gastado), promoted si forSpent(gastado - precio) era otro
        opt saldo menor que la copa más barata (5)
            BS->>WS: hasMovement(userId, "house-credit:" + fecha)
            WS->>DB: movimiento con esa clave
            WS-->>BS: crédito de la casa ya usado hoy o no
        end
        Note over BS: situación PROMOTION, SERVE, BROKE o NO_CREDIT y lineFor(rango)
        BS-->>BC: OrderResponse
        BC-->>C: 200 {drink, price, balance, rank, promoted, creditAvailable, line}
    else clave nueva y saldo menor que el precio
        WS--xBS: InsufficientFundsException (ROLLBACK, no se escribe nada)
        BS--xBC: se propaga
        BC--xH: InsufficientFundsException
        H-->>C: 422 ProblemDetail, code wallet.insufficient_funds
    end
```

- El cobro va en su propia transacción. `WalletService.debit` lanza `IllegalStateException` si ya hay una abierta, y `BarService.order` no lleva `@Transactional`. Solo el cobro es atómico. El saldo de la respuesta sale del propio movimiento, y el rango de antes se deduce restando el precio a lo gastado. Si repites el pedido enseguida con la misma clave, recibes la misma respuesta, ascenso incluido. Si entre medias hubo otros pedidos o se pidió el fiado, el precio y el saldo (el que dejó aquel pedido) no cambian, pero el rango y `promoted` se recalculan con lo gastado ahora, y `creditAvailable` con si hoy ya se usó el fiado. Por ejemplo, tras aceptar el fiado, repetir el pedido que te dejó a 0 devuelve saldo 0 y `creditAvailable` falso, aunque la cartera ya tenga 50. Y como `spentOn` se lee después del cobro y fuera de su transacción, dos pedidos simultáneos del mismo socio pueden perder o repetir un aviso de ascenso. El cobro nunca se pierde ni se duplica.
- `spentOn` devuelve la suma cambiada de signo. Los `BAR_ORDER` se guardan en negativo y el gasto sale en positivo.
- `hasMovement` solo se llama si el saldo tras el pedido no llega para la copa más barata (`Drink.cheapestPrice()`, 5 fichas). Si llega, `creditAvailable` es `false` y no se hace ninguna consulta.
- La clave `order:<uuid>` hace el pedido idempotente. Si el cliente repite la petición con la misma clave, `WalletService` devuelve el movimiento que ya existe antes de mirar el saldo, y no cobra otra vez. Si la clave ya se usó con otro importe o tipo, responde 409 `wallet.idempotency_mismatch`.
- Si dos escrituras chocan dos veces seguidas, responde 409 `wallet.conflict`. Si el usuario no tiene cartera, 404 `wallet.not_found`.
- Antes de llegar al servicio: sin `Idempotency-Key`, o si no es un UUID, 400 `request.rejected`. Con `drink` nulo, 400 `validation.failed`. Con un `drink` que no existe, 400 `request.rejected`.
- Sin token válido no se llega al controlador: 401 `auth.required`.

## 5. Repartir y pagar una mano de blackjack

`POST /api/v1/blackjack/hands`. Lleva la cabecera `Idempotency-Key` con un UUID y el body `{"bet": "TWENTY"}`. El diagrama sigue un reparto en el que el jugador no tiene natural y luego un `stand` que gana. `BlackjackService` no lleva `@Transactional`: cada flecha hacia `PostgreSQL` es su propia transacción.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant BC as BlackjackController
    participant BS as BlackjackService
    participant R as BlackjackHandRepository
    participant WS as WalletService
    participant DB as PostgreSQL

    C->>BC: POST /api/v1/blackjack/hands (Bearer, Idempotency-Key, bet)
    BC->>BS: deal(userId, bet, idempotencyKey)

    BS->>WS: spentOn(userId, BLACKJACK_BET)
    BS->>R: sumBetByUserId(userId)
    Note over BS: si las dos sumas no cuadran, hay una apuesta sin mano.<br/>Se listan con movementsOf y cada una se adopta como mano repartida<br/>(o como VOID si la mesa está ocupada)
    BS->>R: findUnsettledByUserId(userId)
    R-->>BS: manos cerradas sin pagar, cada una se paga con settle

    BS->>R: findByUserIdAndIdempotencyKey(userId, key)
    alt la clave ya tiene mano
        R-->>BS: la mano
        BS-->>BC: la misma mano, sin cobrar. VOID da 409 blackjack.hand_in_progress
    else clave nueva
        BS->>R: findByUserIdAndStatus(userId, PLAYER_TURN)
        opt ya hay una mano en juego
            BS--xBC: HandInProgressException, 409, sin cobrar
        end
        BS->>WS: debit(userId, bet, BLACKJACK_BET, "blackjack-bet:" + key)
        WS->>DB: UPDATE wallet y INSERT token_transaction (negativo), COMMIT
        Note over BS,DB: desde aquí la apuesta existe en el ledger.<br/>Si el proceso cae ahora, la siguiente llamada adopta la apuesta
        BS->>BS: BlackjackRules.deal(shuffler.shuffle(Card.deck()))
        BS->>R: saveAndFlush(mano repartida)
        R->>DB: INSERT blackjack_hand
        alt el insert choca con la clave
            R--xBS: DataIntegrityViolationException
            BS->>R: findByUserIdAndIdempotencyKey(userId, key)
            R-->>BS: la mano de la otra petición
        else el insert choca con blackjack_hand_one_in_progress
            R--xBS: DataIntegrityViolationException
            BS->>R: saveAndFlush(mano VOID)
            BS->>WS: credit(userId, bet, BLACKJACK_PAYOUT, "blackjack-payout:" + id)
            BS--xBC: HandInProgressException, 409
        end
        opt la mano acabó con un natural
            BS->>WS: credit(userId, pago, BLACKJACK_PAYOUT, "blackjack-payout:" + id)
            BS->>R: saveAndFlush(mano con settled_at)
        end
        BS-->>BC: HandResponse(HandView, balance)
        BC-->>C: 200 {hand, balance}
    end

    C->>BC: POST /api/v1/blackjack/hands/{id}/stand
    BC->>BS: stand(userId, id)
    Note over BS: reconcilia igual que arriba y carga la mano con findByIdAndUserId
    BS->>BS: BlackjackRules.stand(mesa): el crupier pide hasta 17 y se planta en todos los 17
    BS->>R: saveAndFlush(mano FINISHED con outcome y payout)
    R->>DB: UPDATE blackjack_hand (version + 1)
    BS->>WS: credit(userId, payout, BLACKJACK_PAYOUT, "blackjack-payout:" + id)
    WS->>DB: UPDATE wallet y INSERT token_transaction (positivo), COMMIT
    BS->>R: saveAndFlush(mano con settled_at)
    BS-->>BC: HandResponse con el crupier descubierto
    BC-->>C: 200 {hand, balance}
```

- Una fila de `blackjack_hand` solo se inserta cuando su `BLACKJACK_BET` ya existe. Por eso, si lo apostado según el ledger (`spentOn(BLACKJACK_BET)`) es igual a la suma de `bet` de las manos, no hay apuestas huérfanas. Son dos sumas indexadas.
- Dónde se corta y quién lo arregla: tras cobrar y antes de insertar, la apuesta queda sin mano y se adopta al reintentar o en la siguiente llamada. Con la mano terminada y guardada pero sin pagar, la siguiente llamada la paga. Con el pago hecho y sin `settled_at`, `credit` devuelve el movimiento previo y solo se marca la mano. Un 409 `wallet.conflict` en el pago deja la mano `FINISHED` sin pagar y la siguiente llamada la paga.
- La clave `blackjack-payout:<id de la mano>` hace que una mano se pague como mucho una vez, y la devolución de una mano `VOID` usa la misma. La clave de la apuesta es `blackjack-bet:<Idempotency-Key>`. Las dos caben en los 64 caracteres del ledger.
- `HIT` y `STAND` no llevan clave. Si dos acciones chocan en `version`, la que pierde relee la mano y devuelve el estado actual en lugar de fallar.
- Mientras la mano está en `PLAYER_TURN`, `HandView.of` enseña una sola carta del crupier y su valor. El mazo no sale nunca del servidor, ni al final.
- Con una mano en juego, repartir da 409 `blackjack.hand_in_progress` sin cobrar. Sin saldo, 422 `wallet.insufficient_funds`. Una mano ajena o inexistente, 404 `blackjack.hand_not_found`. `hit` o `stand` sobre una mano terminada, 409 `blackjack.hand_finished`.
- Antes de llegar al servicio: sin `Idempotency-Key` o con una que no es UUID, 400 `request.rejected`. Con `bet` nulo, 400 `validation.failed`. Con una apuesta que no existe o escrita como número, 400 `request.rejected`. Un `id` que no es UUID, 400 `request.rejected`. Sin token, 401 `auth.required`.
