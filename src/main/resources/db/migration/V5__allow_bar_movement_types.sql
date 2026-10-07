ALTER TABLE token_transaction DROP CONSTRAINT token_transaction_type_check;

ALTER TABLE token_transaction ADD CONSTRAINT token_transaction_type_check
    CHECK (type IN ('WELCOME_BONUS', 'BAR_ORDER', 'HOUSE_CREDIT'));
