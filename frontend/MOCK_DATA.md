# Mock data

Test data for working without a backend (`useMocks: true`). Everything here is fictional.
Existing users live in `src/app/core/api/mocks/mock-data.ts`. If you change them, update this file too.

Nothing is saved: a newly registered user cannot log in, and every registration returns the same customer number and IBAN.

## Register: free data to copy

None of this is taken, so each customer can be registered as many times as you want.

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

Every registration returns customer number `10000100` and IBAN `PL45 9990 0012 1000 0000 0000 0001`.

## Register: error cases

Fill the other fields with free data from above.

| Field    | Value                    | Result                                    | When                     |
| -------- | ------------------------ | ----------------------------------------- | ------------------------ |
| Email    | `anna.nowak@example.com` | Email taken, error under the field        | After **Create account** |
| PESEL    | `90051412343`            | PESEL taken, error under the field        | After **Create account** |
| PESEL    | `10270111119`            | Under 18, cannot go further               | Right away               |
| PESEL    | `90051412344`            | Wrong check digit, cannot go further      | Right away               |
| Password | `NoSpecialChar123`       | Weak password, cannot go further          | Right away               |
| Phone    | `+48 501 234`            | Not 9 digits after +48, cannot go further | Right away               |
| Phone    | `501234567`              | No country code, cannot go further        | Right away               |
| Street   | `12345`                  | No letters, cannot go further             | Right away               |
| Building | `abc`                    | Not a number, cannot go further           | Right away               |

Taken email and PESEL can only be checked by the server, and the API does that when the form is sent.

## Log in: existing users

Password for all of them: `Str0ng!Passw0rd`. Any other email or password gives `INVALID_CREDENTIALS`.

| Email                          | Role     | PESEL (taken) | Use it for                     |
| ------------------------------ | -------- | ------------- | ------------------------------ |
| `anna.nowak@example.com`       | CUSTOMER | `90051412343` | Default customer               |
| `admin@triobank.example`       | ADMIN    |               | Default admin, no profile      |
| `locked@example.com`           | CUSTOMER | `82031710413` | `USER_LOCKED` error, countdown |
| `blocked@example.com`          | CUSTOMER | `75090821123` | `USER_BLOCKED` error           |
| `michal.kaczmarek@example.com` | CUSTOMER | `96100517797` | First login, no "Last login"   |
