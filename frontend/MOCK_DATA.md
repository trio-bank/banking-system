# Test data

Data for trying the app by hand against the real backend. Everything here is fictional.

Registrations are saved in the database. To start over, run `docker compose down -v` from the repo root.

> **Quick start:** every test customer uses the same password, `Str0ng!Passw0rd`. Register one from the table below, then log in with its email and this password. Admin: `admin@triobank.local` / `Adm1n!Passw0rd`.

## Register: data to copy

Each customer can be registered once.

Password for all of them: `Str0ng!Passw0rd`

| Email                                           | First name            | Last name              | PESEL         | Phone           | Street                              | Building | Apartment | Postal code | City     | Use it for                   |
| ----------------------------------------------- | --------------------- | ---------------------- | ------------- | --------------- | ----------------------------------- | -------- | --------- | ----------- | -------- | ---------------------------- |
| `anna.demo@example.com`                         | Anna                  | Nowak                  | `90051402869` | `+48501000001`  | Krakowska                           | `12A`    | `4`       | `45-018`    | Opole    | Demo, password `Demo!Passw0rd2026` |
| `jan.demo@example.com`                          | Jan                   | Kowalski               | `85110356782` | `+48501000002`  | Rynek                               | `7`      |           | `50-101`    | Wroclaw  | Demo, password `Demo!Passw0rd2026` |
| `maria.demo@example.com`                        | Maria                 | Wisniewska             | `01222843219` | `+48501000003`  | Dluga                               | `3`      | `10`      | `80-001`    | Gdansk   | Demo, password `Demo!Passw0rd2026` |
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

## Accounts and ATM: getting started

A new customer has one personal account with `0,00 zł`. To have something to look at:

1. Log in and open the personal account.
2. **ATM → Deposit** a few times, e.g. `3000`, `500`, `1000`.
3. **ATM → Withdraw** once or twice, e.g. `200`.
4. **Open account → Trio Savings Account** to get a second account.
5. Do 21 or more ATM operations on one account to see the second page of history (20 per page).

A blocked account can't be made from the UI until the admin panel exists (US-18).

## Accounts and ATM: error cases

| Where          | What to do                                  | Result                              |
| -------------- | ------------------------------------------- | ----------------------------------- |
| Open account   | A 3rd personal or a 3rd savings account     | `ACCOUNT_LIMIT_REACHED` (max 2)     |
| Open account   | Name longer than 50 characters              | `VALIDATION_ERROR`, `TOO_LONG`      |
| Rename         | Name longer than 50 characters or blank     | Error under the field, nothing sent |
| ATM            | Amount not in whole tens, e.g. `15`         | `CASH_AMOUNT_INVALID`               |
| ATM deposit    | More than `20000`                           | `CASH_AMOUNT_INVALID`               |
| ATM withdrawal | More than `5000`                            | `CASH_AMOUNT_INVALID`               |
| ATM withdrawal | More than the balance (after a few `5000`s) | `INSUFFICIENT_FUNDS`                |
| ATM            | A savings account                           | `OPERATION_NOT_ALLOWED_FOR_ACCOUNT` |
