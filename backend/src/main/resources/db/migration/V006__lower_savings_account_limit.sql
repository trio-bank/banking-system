-- A customer may hold at most 2 savings accounts (was 5), the same as personal accounts.
UPDATE account_products
SET max_accounts_per_customer = 2,
    updated_at                = NOW()
WHERE code = 'SAVINGS_STANDARD';
