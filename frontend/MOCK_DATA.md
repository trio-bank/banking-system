# Test data

Data for trying the app by hand against the real backend. Everything here is fictional.

Registrations are saved in the database. To start over, run `docker compose down -v` from the repo root.

## Register: data to copy

Each customer can be registered once.

Password for all of them: `Str0ng!Passw0rd`

| Email                                           | First name            | Last name              | PESEL         | Phone           | Street                              | Building | Apartment | Postal code | City     | Use it for                   |
| ----------------------------------------------- | --------------------- | ---------------------- | ------------- | --------------- | ----------------------------------- | -------- | --------- | ----------- | -------- | ---------------------------- |
| `jan.kowalski@example.com`                      | Jan                   | Kowalski               | `85110356782` | `+48601234567`  | Polna                               | `5`      | `2`       | `45-018`    | Opole    | Ordinary customer            |
| `maria.zielinska@example.com`                   | Maria                 | Zielińska              | `01222843219` | `+48701234567`  | Leśna                               | `10B`    |           | `30-001`    | Kraków   | No apartment                 |
| `tomasz.wisniewski@example.com`                 | Tomasz                | Wiśniewski             | `88062331836` | `+48502000011`  | Świętokrzyska                       | `7/9`    | `7`       | `00-116`    | Warszawa | Polish letters, `/` building |
| `sean.oconnor@example.com`                      | Seán                  | O'Connor               | `79043053258` | `+48502000012`  | 3 Maja                              | `82`     | `3`       | `80-244`    | Gdańsk   | Apostrophe, digit in street  |
| `aleksandra.wierzbicka-szczepanska@example.com` | Aleksandra Konstancja | Wierzbicka-Szczepańska | `91071996000` | `+48502000013`  | Aleja Marszałka Józefa Piłsudskiego | `123B`   | `1045`    | `50-001`    | Wrocław  | Long values                  |
| `oliwia.mazur@example.com`                      | Oliwia                | Mazur                  | `08310163967` | `+48502000014`  | Kwiatowa                            | `5`      |           | `40-001`    | Katowice | Just turned 18               |
| `olena.shevchenko@example.com`                  | Olena                 | Shevchenko             | `98030951542` | `+380501234567` | Mickiewicza                         | `27`     | `5`       | `45-367`    | Opole    | Foreign phone number         |

## Register: error cases

Fill the other fields with free data from above.

| Field    | Value                      | Result                                    | When                     |
| -------- | -------------------------- | ----------------------------------------- | ------------------------ |
| Email    | one you already registered | Email taken, error under the field        | After **Create account** |
| PESEL    | one you already registered | PESEL taken, error under the field        | After **Create account** |
| PESEL    | `10270111119`              | Under 18, cannot go further               | Right away               |
| PESEL    | `90051412344`              | Wrong check digit, cannot go further      | Right away               |
| Password | `NoSpecialChar123`         | Weak password, cannot go further          | Right away               |
| Phone    | `+48 501 234`              | Not 9 digits after +48, cannot go further | Right away               |
| Phone    | `501234567`                | No country code, cannot go further        | Right away               |
| Street   | `12345`                    | No letters, cannot go further             | Right away               |
| Building | `abc`                      | Not a number, cannot go further           | Right away               |

Taken email and PESEL can only be checked by the server, and the API does that when the form is sent.

## Log in: existing users

Customers exist only after you register them. The admin is created when the backend starts (`local` profile):

| Email                  | Password         | Role  |
| ---------------------- | ---------------- | ----- |
| `admin@triobank.local` | `Adm1n!Passw0rd` | ADMIN |

To see the lockout, enter a wrong password 5 times. The 6th attempt shows the countdown, and the lock lasts 15 minutes.

A blocked user can't be made from the UI until the admin panel exists (US-18).

## Accounts and ATM: mocked data

The backend doesn't have these endpoints yet, so with `useMocks: true` (`src/environments/environment.ts`) they are answered by `src/app/core/api/mocks`: P1, C1–C5, T3, T4. Log in with any real user first. Every user sees the same three accounts below. Changes (new account, rename, ATM) last until the page is reloaded.

| Account               | IBAN                                 | Type     | Status  | Balance      | Use it for                              |
| --------------------- | ------------------------------------ | -------- | ------- | ------------ | --------------------------------------- |
| Trio Personal Account | `PL45 9990 0012 1000 0000 0000 0001` | PERSONAL | ACTIVE  | 10 420,51 zł | ATM, 25 history entries (2 pages of 20) |
| Holiday savings       | `PL18 9990 0012 1000 0000 0000 0002` | SAVINGS  | ACTIVE  | 3 324,97 zł  | Interest and tax entries, no ATM        |
| Old personal account  | `PL88 9990 0012 1000 0000 0000 0003` | PERSONAL | BLOCKED | 120,00 zł    | Blocked account                         |

## Accounts and ATM: error cases

| Where           | What to do                                  | Result                              |
| --------------- | ------------------------------------------- | ----------------------------------- |
| Open account    | Choose the personal account product         | `ACCOUNT_LIMIT_REACHED` (max 2)     |
| Open / rename   | Name longer than 50 characters              | `VALIDATION_ERROR`, `TOO_LONG`      |
| ATM             | Amount not in whole tens, e.g. `15`         | `CASH_AMOUNT_INVALID`               |
| ATM deposit     | More than `20000`                           | `CASH_AMOUNT_INVALID`               |
| ATM withdrawal  | More than `5000`                            | `CASH_AMOUNT_INVALID`               |
| ATM withdrawal  | More than the balance (after a few `5000`s) | `INSUFFICIENT_FUNDS`                |
| ATM             | Holiday savings                             | `OPERATION_NOT_ALLOWED_FOR_ACCOUNT` |
| ATM             | Old personal account                        | `*_ACCOUNT_UNAVAILABLE`             |
| History filters | "From" after "To"                           | `VALIDATION_ERROR`, `INVALID_DATE`  |
