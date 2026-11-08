# Wireframes — Sprint 1

Low-fidelity sketches of the Sprint 1 screens: layout and content only, no colors or final design.
They were drawn from the finished screens, so they match what is in `frontend/` now.

| File                                                                   | Screens                                            | Story        |
| ---------------------------------------------------------------------- | -------------------------------------------------- | ------------ |
| [01-login.svg](01-login.svg)                                           | Login                                              | US-02        |
| [02-register-stepper.svg](02-register-stepper.svg)                     | Register: account, personal data, address, summary | US-01        |
| [03-register-success.svg](03-register-success.svg)                     | Register success                                   | US-01        |
| [04-shells-and-session-warning.svg](04-shells-and-session-warning.svg) | Customer and admin toolbars, session warning       | US-03, US-04 |

## Notes

- Guest pages (login, register) have a header with the bank name and one centered card.
- Below 600 px the register steps are shown one under another, and two-field rows become one column.
- Messages on the login page come from `/login?reason=expired` or `inactive`, and from login errors.
- Taken email or PESEL is only known after "Create account": the form goes back to that step.

Test data for these screens: [frontend/MOCK_DATA.md](../../../frontend/MOCK_DATA.md).
