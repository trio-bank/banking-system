# Mock data

Test data for working without a backend (`useMocks: true`). Everything here is fictional.
The source is `src/app/core/api/mocks/mock-data.ts`. If you change it, update this file too.

Nothing is saved: a newly registered user cannot log in, and every registration returns the same customer number and IBAN.

## Log in

Password for all 20 users: `Str0ng!Passw0rd`

Any other email or password gives `INVALID_CREDENTIALS`.

### Admins

Admins have no customer profile.

| Email                     | Last login       |
| ------------------------- | ---------------- |
| `admin@triobank.example`  | never            |
| `teller@triobank.example` | 05.11.2026 08:30 |

### Customers

All of them log in normally, unless the "Use it for" column says otherwise.

| Email                                           | Name                                         | PESEL         | Date of birth | Use it for                     |
| ----------------------------------------------- | -------------------------------------------- | ------------- | ------------- | ------------------------------ |
| `anna.nowak@example.com`                        | Anna Nowak                                   | `90051412343` | 14.05.1990    | Default customer               |
| `locked@example.com`                            | Piotr Lis                                    | `82031710413` | 17.03.1982    | `USER_LOCKED` error, countdown |
| `blocked@example.com`                           | Ewa Wrona                                    | `75090821123` | 08.09.1975    | `USER_BLOCKED` error           |
| `tomasz.wisniewski@example.com`                 | Tomasz Wiśniewski                            | `88062331836` | 23.06.1988    | Polish letters                 |
| `katarzyna.nowak-lewandowska@example.com`       | Katarzyna Nowak-Lewandowska                  | `93120142541` | 01.12.1993    | Double last name               |
| `sean.oconnor@example.com`                      | Seán O'Connor                                | `79043053258` | 30.04.1979    | Apostrophe in the name         |
| `aleksandra.wierzbicka-szczepanska@example.com` | Aleksandra Konstancja Wierzbicka-Szczepańska | `91071996000` | 19.07.1991    | Long values, layout on a phone |
| `oliwia.mazur@example.com`                      | Oliwia Mazur                                 | `08310163967` | 01.11.2008    | Just turned 18                 |
| `stanislaw.dabrowski@example.com`               | Stanisław Dąbrowski                          | `38021174679` | 11.02.1938    | The oldest customer            |
| `zofia.kaminska@example.com`                    | Zofia Kamińska                               | `00210185386` | 01.01.2000    | First day of the 2000s         |
| `adam.pawlak@example.com`                       | Adam Pawlak                                  | `00222939113` | 29.02.2000    | Leap day                       |
| `michal.kaczmarek@example.com`                  | Michał Kaczmarek                             | `96100517797` | 05.10.1996    | First login, no "Last login"   |
| `agnieszka.krol@example.com`                    | Agnieszka Król                               | `84052728426` | 27.05.1984    | No apartment number            |
| `magdalena.wojcik@example.com`                  | Magdalena Wójcik                             | `87081440862` | 14.08.1987    | Different last login date      |
| `olena.shevchenko@example.com`                  | Olena Shevchenko                             | `98030951542` | 09.03.1998    | Foreign phone number           |
| `ab@example.com`                                | Ala Bąk                                      | `02292162288` | 21.09.2002    | Very short email               |
| `grzegorz.zajac@example.com`                    | Grzegorz Zając                               | `69113073934` | 30.11.1969    | Building number with a slash   |
| `natalia.sikora@example.com`                    | Natalia Sikora                               | `94011684627` | 16.01.1994    | Ordinary customer              |

Phones and addresses of all customers are in `mock-data.ts`.

## Register

Emails and PESEL numbers from the tables above are already taken, so use the data below.

### Valid customers

| Field       | Customer 1                 | Customer 2                    |
| ----------- | -------------------------- | ----------------------------- |
| Email       | `jan.kowalski@example.com` | `maria.zielinska@example.com` |
| Password    | `Str0ng!Passw0rd`          | `Str0ng!Passw0rd`             |
| First name  | Jan                        | Maria                         |
| Last name   | Kowalski                   | Zielińska                     |
| PESEL       | `85110356782`              | `01222843219`                 |
| Birth date  | 03.11.1985                 | 28.02.2001                    |
| Phone       | `+48601234567`             | `+48701234567`                |
| Street      | Polna                      | Leśna                         |
| Building    | `5`                        | `10B`                         |
| Apartment   | `2`                        | leave empty                   |
| Postal code | `45-018`                   | `30-001`                      |
| City        | Opole                      | Kraków                        |

Every registration returns customer number `10000100` and IBAN `PL45 9990 0012 1000 0000 0000 0001`.

### Error cases

Fill the other fields with a valid customer's data.

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
