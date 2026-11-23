-- Demo data for the demo/local profiles (NOT loaded in production or tests).
-- Loaded by adding classpath:db/demo to spring.flyway.locations in application-demo.yml.
-- Repeatable migration: safe to run again, customers whose email or PESEL already exists are skipped.
--
-- Password of every demo user: Demo!Passw0rd2026
-- Everything goes through the same tables and rules as the application, so the ledger stays balanced:
-- every money movement has a debit and a credit entry, balance_after matches, BANK_CASH goes negative.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Posts one transaction the way LedgerService does: two balances, one transaction, two entries.
CREATE OR REPLACE FUNCTION pg_temp.demo_post(p_type TEXT, p_channel TEXT, p_source UUID, p_target UUID,
                                             p_amount NUMERIC, p_title TEXT, p_sender TEXT, p_recipient TEXT,
                                             p_user UUID, p_at TIMESTAMPTZ)
    RETURNS VOID AS
$fn$
DECLARE
    v_tx          UUID := gen_random_uuid();
    v_source_bal  NUMERIC;
    v_target_bal  NUMERIC;
BEGIN
    UPDATE accounts SET balance = balance - p_amount, updated_at = p_at
    WHERE id = p_source RETURNING balance INTO v_source_bal;
    UPDATE accounts SET balance = balance + p_amount, updated_at = p_at
    WHERE id = p_target RETURNING balance INTO v_target_bal;

    INSERT INTO transactions (id, reference, type, status, channel, amount, currency, source_account_id,
                              target_account_id, sender_name, recipient_name, title, initiated_by_user_id,
                              created_at, booked_at)
    VALUES (v_tx,
            'TB' || to_char(p_at AT TIME ZONE 'Europe/Warsaw', 'YYYYMMDD')
                || lpad(nextval('transaction_reference_seq')::TEXT, 10, '0'),
            p_type, 'COMPLETED', p_channel, p_amount, 'PLN', p_source, p_target, p_sender, p_recipient,
            p_title, p_user, p_at, p_at);

    INSERT INTO ledger_entries (id, transaction_id, account_id, direction, amount, balance_after, created_at)
    VALUES (gen_random_uuid(), v_tx, p_source, 'DEBIT', p_amount, v_source_bal, p_at),
           (gen_random_uuid(), v_tx, p_target, 'CREDIT', p_amount, v_target_bal, p_at);
END
$fn$ LANGUAGE plpgsql;

DO
$seed$
DECLARE
    demo          RECORD;
    v_cash        UUID;
    v_personal_pr UUID;
    v_savings_pr  UUID;
    v_user        UUID;
    v_customer    UUID;
    v_personal    UUID;
    v_savings     UUID;
    v_full_name   TEXT;
    v_bban        TEXT;
    v_at          TIMESTAMPTZ;
BEGIN
    SELECT id INTO v_cash FROM accounts WHERE system_code = 'BANK_CASH';
    SELECT id INTO v_personal_pr FROM account_products WHERE code = 'PERSONAL_STANDARD';
    SELECT id INTO v_savings_pr FROM account_products WHERE code = 'SAVINGS_STANDARD';

    FOR demo IN
        SELECT *
        FROM (VALUES
                  ('anna.demo@example.com', 'Anna', 'Nowak', '90051402869', DATE '1990-05-14', '+48501000001',
                   'Krakowska', '12A', '4', '45-018', 'Opole', 5000.00, 2000.00, 3),
                  ('jan.demo@example.com', 'Jan', 'Kowalski', '85110356782', DATE '1985-11-03', '+48501000002',
                   'Rynek', '7', NULL, '50-101', 'Wroclaw', 1200.00, 0.00, 2),
                  ('maria.demo@example.com', 'Maria', 'Wisniewska', '01222843219', DATE '2001-02-28', '+48501000003',
                   'Dluga', '3', '10', '80-001', 'Gdansk', 300.00, 0.00, 1)
             ) AS t(email, first_name, last_name, pesel, birth, phone, street, building, apartment, postal, city,
                    personal_deposit, savings_transfer, days_ago)
    LOOP
        -- skip if the email or PESEL is taken, otherwise the unique constraint kills the whole app startup
        IF EXISTS (SELECT 1 FROM users WHERE email = demo.email)
            OR EXISTS (SELECT 1 FROM customers WHERE pesel = demo.pesel) THEN
            RAISE NOTICE 'demo seed: % skipped, email or PESEL already exists', demo.email;
            CONTINUE;
        END IF;

        v_user := gen_random_uuid();
        v_customer := gen_random_uuid();
        v_full_name := demo.first_name || ' ' || demo.last_name;
        v_at := date_trunc('milliseconds', now() - make_interval(days => demo.days_ago));

        INSERT INTO users (id, email, password_hash, role, status, created_at, updated_at)
        VALUES (v_user, demo.email, crypt('Demo!Passw0rd2026', gen_salt('bf', 12)), 'CUSTOMER', 'ACTIVE', v_at, v_at);

        INSERT INTO customers (id, user_id, customer_number, first_name, last_name, pesel, date_of_birth,
                               phone_number, address_street, address_building_number, address_apartment_number,
                               address_postal_code, address_city, address_country_code, created_at, updated_at)
        VALUES (v_customer, v_user, nextval('customer_number_seq')::TEXT, demo.first_name, demo.last_name,
                demo.pesel, demo.birth, demo.phone, demo.street, demo.building, demo.apartment, demo.postal,
                demo.city, 'PL', v_at, v_at);

        -- personal account; IBAN = PL + mod-97 check digits + sort code + account number (same rule as IbanGenerator)
        v_personal := gen_random_uuid();
        v_bban := '99900012' || nextval('account_number_seq')::TEXT;
        INSERT INTO accounts (id, iban, category, customer_id, product_id, name, currency, opened_at, created_at, updated_at)
        VALUES (v_personal,
                'PL' || lpad((98 - ((v_bban || '252100')::NUMERIC % 97))::TEXT, 2, '0') || v_bban,
                'CUSTOMER', v_customer, v_personal_pr, 'Trio Personal Account', 'PLN', v_at, v_at, v_at);

        PERFORM pg_temp.demo_post('CASH_DEPOSIT', 'ATM', v_cash, v_personal, demo.personal_deposit,
                                  'ATM cash deposit', 'Trio Bank', v_full_name, v_user, v_at);

        IF demo.savings_transfer > 0 THEN
            v_savings := gen_random_uuid();
            v_bban := '99900012' || nextval('account_number_seq')::TEXT;
            INSERT INTO accounts (id, iban, category, customer_id, product_id, name, currency, opened_at, created_at, updated_at)
            VALUES (v_savings,
                    'PL' || lpad((98 - ((v_bban || '252100')::NUMERIC % 97))::TEXT, 2, '0') || v_bban,
                    'CUSTOMER', v_customer, v_savings_pr, 'Holiday savings', 'PLN', v_at, v_at, v_at);

            PERFORM pg_temp.demo_post('OWN_TRANSFER', 'WEB', v_personal, v_savings, demo.savings_transfer,
                                      'Own transfer', v_full_name, v_full_name, v_user,
                                      v_at + INTERVAL '1 minute');
        END IF;
    END LOOP;
END
$seed$;
