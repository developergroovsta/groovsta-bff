ALTER TABLE identity_accounts
    ALTER COLUMN email DROP NOT NULL;

CREATE UNIQUE INDEX uq_identity_accounts_phone_number
    ON identity_accounts(phone_number)
    WHERE phone_number IS NOT NULL;
