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
