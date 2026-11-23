# Sprint 2 Report (Release v0.2.0)

| | |
|---|---|
| **Sprint** | Sprint 2 — Accounts, ledger core, ATM, history |
| **Release** | `v0.2.0` (#153) |
| **Period** | TODO: 2026-11-09 – 2026-11-23 |
| **Team** | Marko Zhenchuk, Hryhorii Martynkian, Kateryna Valentyuk |
| **Status** | Completed |

---

## 1. Summary & Metrics

- **Delivered Epics & Stories:** 4 Epics (`E3`, `E4`, `E6`, `E10`), 8 User Stories (`US-05`–`US-09`, `US-12`, `US-14`, `US-15`)
- **Issues Completed:** 36 / 37 closed (97%; remaining `#153` is the release merge/tag task itself)
- **Backend Test Coverage:** 95% instructions / 83% branches (JaCoCo)
- **QA / Test Cases Covered:** `TC-14`–`TC-19`, `TC-30`, `TC-32`, `TC-34`–`TC-38` + Playwright E2E journeys 1 & 2

---

## 2. Completed Scope

### E4 — Ledger Core (#87)
- **Backend:**
  - `#107` Migration `V005`: `transactions` and `ledger_entries` schema
  - `#108` `LedgerService.post`: double-entry bookkeeping, balance updates, and accounting invariants
  - `#109` Pessimistic/optimistic account locking and idempotency key handling for money operations
- **QA:**
  - `#141` Ledger invariant JUnit extension (`TC-38`)
  - `#142` `TestDataFactory` for bills and transactions
  - `#143` Concurrency test for simultaneous withdrawals (`TC-30`)

### E3 — Customer & Accounts (#86)
- **US-05 View profile (#89):** `#99` Profile page (`FE`)
- **US-06 View my accounts (#90):** `#100` Dashboard with accounts, total balance, and recent operations (`FE`), `#110` `C1 GET /accounts` (`BE`)
- **US-07 Open a new account (#91):** `#101` Open account page (`FE`), `#111` `P1 GET /account-products` & `C2 POST /accounts` (`BE`)
- **US-08 Account details (#92):** `#102` Account details page (`FE`), `#112` `C3 GET /accounts/{accountId}` (`BE`)
- **US-09 Rename an account (#93):** `#103` Inline account rename (`FE`), `#113` `C4 PATCH /accounts/{accountId}` (`BE`)
- **Shared Frontend & QA:**
  - `#98` Frontend models, API services, and mocks for `P1`, `C1`–`C5`, `T3`/`T4`
  - `#145` Integration tests for profile, accounts, and renaming (`TC-14`, `TC-15`–`TC-19`)

### E6 — ATM & Transaction History (#88)
- **US-12 Transaction history (BASE) (#94):** `#104` History tab with list and pagination (`FE`), `#114` `C5 GET /accounts/{accountId}/history` (`BE`), `#146` Integration tests for history, pagination, and `balanceAfter` (`TC-32`)
- **US-14 ATM deposit (#95):** `#105` ATM deposit page and confirmation (`FE`), `#115` `T3 POST /atm/deposits` (`BE`)
- **US-15 ATM withdrawal (#96):** `#106` ATM withdraw tab (`FE`), `#116` `T4 POST /atm/withdrawals` (`BE`)
- **QA:**
  - `#147` Integration tests for ATM deposit, withdrawal, limits, savings, and balance (`TC-34`–`TC-37`)

### E10 — Quality & Release (#97)
- **DevOps & Demo Data:**
  - `#129`, `#152` Repeatable Flyway demo seed migration (`db/demo/R__demo_seed.sql`)
  - `#149` Playwright test skeleton and `e2e.yml` CI workflow
- **QA (E2E):**
  - `#150` E2E journey 1: `register → login → IBAN`
  - `#151` E2E journey 2: `ATM deposit → open savings → history`
- **Release:**
  - `#153` Release `v0.2.0`: `develop → main` and git tag

---

## 3. Retrospective

### What Went Well
- **Contract-first development:** Defining endpoints and DTOs in [05 API contract](../05-api-contract.md) and mocks (`#98`) early enabled parallel frontend and backend work across all customer and ATM stories without integration blockers.
- **Double-entry integrity:** Enforcing ledger invariants via a dedicated JUnit extension (`TC-38`) and concurrency tests (`TC-30`) ensured zero balance discrepancies across ATM and transfer flows.
- **End-to-end automation:** Setting up Playwright in CI (`#149`) alongside repeatable demo seed data (`#129`, `#152`) gave the team reproducible full-stack verification before tagging `v0.2.0`.

### What Could Be Improved
- **PR & documentation sync:** Ensuring PR checklists (test case IDs and documentation updates) are consistently verified before merging into `develop`.
- **Release buffering:** Leaving dedicated buffer time at the end of the sprint for full E2E stabilization and release documentation prior to `develop → main` promotion.
```[cite: 5, 6, 7]
