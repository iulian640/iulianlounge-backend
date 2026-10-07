CREATE INDEX token_transaction_wallet_type ON token_transaction (wallet_id, type) INCLUDE (amount);

ALTER TABLE token_transaction ADD CONSTRAINT token_transaction_amount_sign_check
    CHECK ((type = 'BAR_ORDER') = (amount < 0));
