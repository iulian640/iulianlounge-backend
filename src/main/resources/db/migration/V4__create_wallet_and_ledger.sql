-- ADR-04: el ledger (token_transaction) es la verdad y solo crece; wallet.balance es su caché.
-- ADR-09: fichas en BIGINT, nunca decimales

CREATE TABLE wallet (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    -- Última defensa: aunque Java fallara, la BD no deja un saldo negativo
    balance    BIGINT      NOT NULL CHECK (balance >= 0),
    -- Bloqueo optimista (@Version): dos escrituras a la vez → la segunda falla en vez de machacar
    version    BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE token_transaction (
    id              UUID PRIMARY KEY,
    -- RESTRICT, no CASCADE: el ledger es append-only y borrar la cartera no puede llevárselo.
    -- El borrado de cuenta (RGPD) será una operación explícita que decida qué hacer con él (ADR-04)
    wallet_id       UUID        NOT NULL REFERENCES wallet (id) ON DELETE RESTRICT,
    -- Con signo: positivo entra, negativo sale. Un movimiento de 0 no es un movimiento
    amount          BIGINT      NOT NULL CHECK (amount <> 0),
    type            TEXT        NOT NULL CHECK (type IN ('WELCOME_BONUS')),
    balance_after   BIGINT      NOT NULL CHECK (balance_after >= 0),
    -- La inventa el cliente: con tope, para que nadie guarde megas en un índice único
    idempotency_key TEXT        CHECK (char_length(idempotency_key) <= 64),
    created_at      TIMESTAMPTZ NOT NULL
);

-- Un reintento del cliente (doble clic) con la misma clave muere aquí. Por cartera: las claves
-- las inventa cada cliente y no tienen por qué ser únicas en todo el sistema
CREATE UNIQUE INDEX token_transaction_wallet_idempotency_key
    ON token_transaction (wallet_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- El historial se lee por cartera y del más nuevo al más viejo; id desempata dos movimientos
-- del mismo instante, para que una fila no salte de una página a otra
CREATE INDEX token_transaction_wallet_created_at
    ON token_transaction (wallet_id, created_at DESC, id DESC);

-- Los usuarios que ya existían (antes de la cartera) reciben una vacía: nadie se queda sin wallet.
-- Sin bono: el de bienvenida es para los registros nuevos
INSERT INTO wallet (id, user_id, balance, version, created_at)
SELECT gen_random_uuid(), id, 0, 0, now()
FROM users;
