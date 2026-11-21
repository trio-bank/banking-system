# 07 — Frontend Guide

Rules for the Angular application in `/frontend`. API and DTOs: [05](05-api-contract.md).

## 1. Setup

| Item | Value |
|---|---|
| Framework | Angular 22.x, standalone components (no NgModules) |
| UI library | Angular Material + CDK |
| Language | TypeScript strict mode (`"strict": true`) |
| State | Services with Angular **signals**; no NgRx in MVP |
| Forms | Typed Reactive Forms |
| HTTP | `HttpClient` with functional interceptors |
| Unit tests | Angular CLI default runner |
| E2E | Playwright (owned by QA/DevOps) |
| Lint/format | ESLint (angular-eslint) + Prettier |
| Locale | `en-GB` texts; money formatted as `pl-PL` (`1 250,00 zł`) — see §6 |

## 2. Folder structure

```
src/app
├── app.config.ts            # providers: router, http + interceptors, animations
├── app.routes.ts
├── core
│   ├── api
│   │   ├── models/          # TypeScript interfaces copied from 05 §7 (one file per area)
│   │   └── services/        # AuthApi, AccountsApi, PaymentsApi, AdminApi … (thin HTTP wrappers)
│   ├── auth
│   │   ├── auth.store.ts    # signals: token, currentUser, isLoggedIn, role
│   │   ├── auth.guard.ts    # requires login
│   │   ├── role.guard.ts    # requires role CUSTOMER / ADMIN
│   │   └── session-timer.service.ts
│   ├── theme
│   │   └── theme.service.ts         # light / dark / system, choice kept in localStorage
│   ├── http
│   │   ├── auth.interceptor.ts          # adds Bearer token
│   │   ├── error.interceptor.ts         # 401 → logout; maps ProblemDetail
│   │   └── idempotency-key.ts           # helper to create/keep keys
│   └── errors
│       └── error-messages.ts            # code → user message (single place)
├── shared
│   ├── components/          # page-hero, account-card, page-header, money-display, iban-display, status-chip, confirm-dialog, empty-state
│   ├── pipes/               # money.pipe, iban.pipe, signed-amount.pipe, enum-label.pipe
│   └── validators/          # iban.validator, pesel.validator, money.validator, password.validator
├── layout
│   ├── customer-shell/      # yellow top bar (links on the right), bottom tabs on phones
│   ├── theme-toggle/        # light / dark button in the toolbars
│   └── admin-shell/
└── features
    ├── auth/                # login, register
    ├── dashboard/
    ├── accounts/            # list, details, open-account
    ├── payments/            # transfer (form → confirm → result)
    ├── atm/                 # deposit, withdraw
    ├── history/             # account history, transaction details
    ├── profile/
    └── admin/               # customers, customer-details, transactions, audit, ledger, branch-cash, jobs
```

Features are **lazy-loaded** via `loadComponent` / `loadChildren`.

## 3. Routes

| Path | Component | Guard | Story |
|---|---|---|---|
| `/login` | LoginPage | guest only | US-02 |
| `/register` | RegisterPage | guest only | US-01 |
| `/register/success` | RegisterSuccessPage | guest only | US-01 |
| `/app` | CustomerShell → redirect `/app/dashboard` | auth + CUSTOMER | |
| `/app/dashboard` | DashboardPage | | US-06 |
| `/app/accounts/new` | OpenAccountPage | | US-07 |
| `/app/accounts/:accountId` | AccountDetailsPage (+ history tab) | | US-08, US-09, US-12 |
| `/app/transfer` | TransferPage (`?from=<accountId>&own=true`) | | US-10, US-11 |
| `/app/transactions/:transactionId` | TransactionDetailsPage | | US-13 |
| `/app/atm` | AtmPage (deposit / withdraw tabs) | | US-14, US-15 |
| `/app/profile` | ProfilePage | | US-05 |
| `/admin` | AdminShell → redirect `/admin/customers` | auth + ADMIN | |
| `/admin/customers` | CustomerSearchPage | | US-17 |
| `/admin/customers/:customerId` | CustomerDetailsPage | | US-17…19 |
| `/admin/transactions` | TransactionSearchPage | | US-21 |
| `/admin/transactions/:transactionId` | AdminTransactionDetailsPage | | US-21 |
| `/admin/branch` | BranchCashPage | | US-20 |
| `/admin/audit` | AuditLogPage | | US-22 |
| `/admin/ledger` | LedgerIntegrityPage (+ demo jobs panel) | | US-23, US-24 |
| `**` | NotFoundPage | | |

After login: `CUSTOMER` → `/app/dashboard`, `ADMIN` → `/admin/customers`. A guard redirects a user with the wrong role to their home.

## 4. Screens

| Screen | Shows | Actions | API |
|---|---|---|---|
| Login | email, password, error banner, lock countdown | Log in, link to register | A2, then A4 |
| Register | Stepper: 1) account data (email, password + strength meter), 2) personal data (name, PESEL → shows derived date of birth, phone), 3) address, 4) summary | Submit | A1 |
| Register success | customer number, IBAN | Go to login | — |
| Dashboard | Greeting, last login, total balance, account cards (name, type, IBAN, balance, status), last 5 operations of first account | Transfer, ATM, open account, go to details | C1, C5 |
| Open account | Product cards with rules (rate, policy, ATM), name field | Open | P1, C2 |
| Account details | Header (name, IBAN copy, balance, status, product); savings: rate + accrued interest; history tab with filters & paginator | Rename (inline), Transfer from this account | C3, C4, C5 |
| Transfer | Toggle "To my account / To someone else"; source select (shows balance), target (own select or recipient name + IBAN), amount, title (placeholder = default title) | Next → confirmation → Confirm → result (reference, link to details) | C1, T1 |
| Transaction details | Reference, type, status, dates, amount, title, sender, recipient | Back | T2 |
| ATM | Account select (personal only), quick amounts (50/100/200/500), custom amount | Deposit / Withdraw → confirmation → result | C1, T3, T4 |
| Profile | Customer data (read-only) | — | A4 |
| Admin customers | Search box, status filter, table | Open details | D1 |
| Admin customer details | Profile (full PESEL), user status, failed attempts, accounts table | Block/unblock/unlock user, block/unblock account (dialog with reason) | D2–D7 |
| Admin transactions | Filters, table | Open details (entries) | D10, D11 |
| Branch cash | IBAN, amount, title, deposit/withdraw | Confirm | D8, D9 |
| Audit log | Filters, table, JSON details expandable | — | D12 |
| Ledger | Integrity status (OK/FAIL), system accounts; demo jobs: date/month inputs + run buttons | Run jobs | D13–D15 |

Wireframes: low-fidelity sketches in [`docs/diagrams/wireframes/`](diagrams/wireframes/README.md) (SVG). Sprint 1: login, register, register success, toolbars, session warning.

## 5. Models and API services

- Interfaces in `core/api/models` **copy exactly** [05 §7](05-api-contract.md#7-dto-catalog). Do not rename fields.
- API services return `Observable<T>` and contain no UI logic:

```ts
@Injectable({ providedIn: 'root' })
export class PaymentsApi {
  private http = inject(HttpClient);
  transfer(req: TransferRequest, idempotencyKey: string) {
    return this.http.post<TransactionDto>('/api/v1/transfers', req, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }
}
```

- Base URL from `environment.apiUrl`; in local dev an Angular proxy (`proxy.conf.json`) forwards `/api` to `http://localhost:8080`.

## 6. Money, IBAN and dates

| Topic | Rule |
|---|---|
| Money from API | String. **Do not do arithmetic** with JS `number`. For display use `MoneyPipe` → `Intl.NumberFormat('pl-PL', { style: 'currency', currency })` on `Number(value)` (display only). Totals on the dashboard: sum with integer grosze (`Math.round(Number(v) * 100)`) or a small decimal helper. |
| Money input | Text input with `inputmode="decimal"`; accept `,` or `.`; normalize to `"123.45"` before sending; validator: > 0, max 2 decimals, limits from BR-10/BR-19. |
| Signed amounts | History: `INCOMING` → `+150,00 zł` (green), `OUTGOING` → `−150,00 zł` (default text color). Don't rely on color only. |
| IBAN display | `IbanPipe` → groups of 4: `PL45 9990 0012 1000 0000 0000 0001`; copy button copies without spaces. |
| IBAN input | Accept spaces and lowercase; live checksum validation (`ibanValidator`); auto-prefix `PL` if user types 26 digits. |
| Dates | Display in user's local time, format `dd.MM.yyyy HH:mm`. Date filters send `IsoDate` (`yyyy-MM-dd`). |
| Enums | `EnumLabelPipe` maps values to labels (`INTERNAL_TRANSFER` → "Transfer"); unknown → raw value. |

## 7. Forms and validation

- Frontend validators mirror backend rules for UX; the backend remains the authority.
- Show field errors under the field after touch/submit; map `errors[].field` from 400 responses to form controls (`address.postalCode` → nested control).
- Disable submit while a request is in flight; show a spinner.
- Shared validators: `ibanValidator`, `peselValidator` (+ derive birth date), `passwordValidator` (returns which rules failed), `moneyValidator(min, max, step?)`.

## 8. Error handling

- `error.interceptor` converts `HttpErrorResponse` with `application/problem+json` into a typed `ProblemDetail`.
- `401` (except on `/auth/login`) → clear session, redirect to `/login?reason=expired`.
- Messages come from **one table** `core/errors/error-messages.ts`:

```ts
export const ERROR_MESSAGES: Record<string, string> = {
  INSUFFICIENT_FUNDS: 'You do not have enough money on this account.',
  RECIPIENT_ACCOUNT_NOT_FOUND: 'No Trio Bank account exists with this number.',
  EXTERNAL_TRANSFER_NOT_SUPPORTED: 'Transfers to other banks are not available yet.',
  // … every code from 05 §4
};
export const FALLBACK_MESSAGE = 'Something went wrong. Please try again.';
```

- Business errors (422) shown in a banner on the form; unexpected errors (500, network) in a snackbar with `traceId` ("Error ID: 4f1c…") to help debugging.

## 9. Authentication and session

- Token stored in **`sessionStorage`** (cleared when the tab closes — closer to banking behaviour than `localStorage`); `AuthStore` keeps it in a signal.
- `SessionTimerService`: tracks user activity (click, keydown, router navigation). At 9 minutes of inactivity → dialog with 60 s countdown; at 10 minutes or at `expiresAt` → logout with `reason=inactive`/`expired`.
- On app start: if a token exists and is not expired → call A4 to restore `currentUser`.

## 10. Idempotency in the UI

- When the **confirmation** step of a transfer/ATM/branch operation opens, generate `crypto.randomUUID()` and keep it in the component.
- Retrying (e.g. after a network error) reuses the same key; going back to edit the form generates a new key.
- The confirm button is disabled after the first click until a response arrives.

## 11. UX rules

- Responsive from 360 px (NFR-USE-01): cards stack on mobile; tables become lists.
- Every page has a clear title, empty states ("No operations in this period") and loading states (skeletons).
- Money operations always have a confirmation step and a result screen with the reference.
- Accessible: labels for all inputs, focus management after navigation and dialogs, contrast AA.
- All user-facing texts are kept in templates/constants grouped by feature (prepared for i18n, PKG-15).

## 12. Checklist for a new screen

1. Route and guard added.
2. Models match [05](05-api-contract.md); API service method created (with mock if backend not ready).
3. Loading, empty and error states handled; error codes have messages.
4. Responsive check at 360 px.
5. Unit tests for validators/logic; e2e scenario added to the QA backlog.
6. Follows the visual rules in §13 (header type, yellow rule, tokens instead of hex values).

## 13. Visual design

Direction and alternatives: [ADR-0015](adr/0015-ui-visual-direction.md). Customer app = "yellow header"; admin app = dense style.

### 13.1 Header

| Page | Header |
|---|---|
| Dashboard | Tall: greeting, last login, total balance. Account cards overlap it by 48px. |
| Other customer pages | Compact: page title + one-line subtitle. A form card overlaps it by 28px. |
| Admin pages | None. Black toolbar. |

- The toolbar and the header share the `hero-*` colors, so they read as one block.
  - **Light:** solid yellow, black text. The only yellow surface on the screen; below it yellow appears only on the card chip and the active bottom tab.
  - **Dark:** warm charcoal (not black: it looked gloomy) with a yellow glow rising from behind the cards. Yellow becomes the accent: total balance, logo mark, active line, avatar, main button, main account card.
- On yellow, text and icons are always black. Yellow is never a text color on light backgrounds; on dark backgrounds it is (12.9:1).
- A hairline and a soft shadow (`hero-edge`) under the toolbar keep it a separate strip above the header.
- Dashboard header: a small line "Hello, <name> · total of active accounts" with the last login on the right, and the total in very large type under it. No buttons: ATM is in the navigation, Open account is the last card.
- The toolbar is sticky. Once the header scrolls away, it switches to `surface` with a shadow.

### 13.2 Navigation

- **≥ 768px:** logo on the left; links with icons on the right; a divider; theme toggle, avatar (opens Profile), "Log out" (outlined pill with an icon, in the bar's text color). The active link is bold, has a filled icon, `aria-current="page"` and a 3px line across its full width on the bar's bottom edge.
- Logo mark, active line and avatar use `hero-accent`: black on the yellow bar, yellow on the dark one. On the black guest and admin bars the logo mark is yellow.
- **< 768px:** the bar keeps only the logo and the avatar. The avatar opens a menu: name and email, My profile, light/dark theme, Log out. The links move to bottom tabs; the active tab gets a yellow pill and a filled icon.
- Links today: **Home** (also active on account details) and **ATM**. Tabs: Home, ATM, Profile. There is no separate accounts page: accounts are the cards on the dashboard. **Transfer** (`/app/transfer`) is added to the links and tabs in `customer-shell.ts` with its page in Sprint 3.
- The theme toggle switches between light and dark and remembers the choice. Until it is pressed, the app follows the system.
- Icons: `MAT_ICON_DEFAULT_OPTIONS` is provided in the shell, so `<mat-icon>home</mat-icon>` is enough inside it.

### 13.3 Components

| Component | Rules |
|---|---|
| Buttons | On the header: `hero-button` pill (black with yellow text in light, yellow with black text in dark). Elsewhere: yellow pill with black text. Secondary: outlined in the text color. Links to more items: text + `chevron_right` ("Show all ›"). |
| Account card | Aspect ratio 1.586 (a real bank card). `ACTIVE` is `card` (black in light, yellow in dark), further active accounts are `card-alt` (graphite / off-white), `BLOCKED` is `card-off` with a lock icon, `CLOSED` is `card-off` with a dashed outline. Text on `card-off` is `ink`: `muted` is below AA there (4.2:1), and so is anything faded with opacity. Shows name, status, type · currency, the full formatted IBAN and the balance (US-06); a long name gets an ellipsis. |
| Open account card | The last card in the row: see-through, dashed outline, `add` icon. |
| Card row | On phones the row scrolls sideways. |
| Lists (operations, history) | Rows directly on the page background, no box: icon in a circle, title + date, amount on the right, `line` divider. |
| Forms | On a `surface` card with 16px radius. |
| Loading | Skeletons, not spinners. |

Only account cards and form cards have a shadow.

### 13.4 Typography and icons

- **Space Grotesk 700:** headings, balance, amounts. **Inter:** everything else. All amounts use `font-variant-numeric: tabular-nums`.
- Space Grotesk has no Cyrillic; for a localized UI (PKG-15) the heading font must change.
- **Icons:** Material Symbols Rounded through `mat-icon`. The font set (`MAT_ICON_DEFAULT_OPTIONS`) is provided in both shells, not in `app.config`, to keep MatIcon out of the first-load bundle. Weight 400, outlined; add `.filled` for active navigation only.
- **Motion:** 150–200 ms for hover, appearance and skeletons. Nothing animates with `prefers-reduced-motion`.

### 13.5 Tokens

Defined once in `styles.scss` (`mat.theme-overrides`, `theme-type: color-scheme`). Components use the tokens, never hex values.

| Token | Use | Light | Dark |
|---|---|---|---|
| `brand` | Active tab, yellow buttons | `#ffd100` | `#ffd100` |
| `on-brand` | Text and icons on yellow | `#141414` | `#141414` |
| `hero-bg` | Customer toolbar and header | `#ffd100` | `#2a2824` |
| `hero-glow` | Glow behind the cards | `#ffd100` (flat) | `#4d4214` |
| `hero-fg` | Text on the header | `#141414` | `#f2f1ea` |
| `hero-accent` | Total, logo mark, active line, avatar | `#141414` | `#ffd100` |
| `hero-button` / `on-hero-button` | Main button on the header | `#141414` / `#ffd100` | `#ffd100` / `#141414` |
| `hero-edge` | Line and soft shadow under the customer toolbar | black 12% | yellow 16% |
| `ink` | Main text | `#141414` | `#f2f1ea` |
| `muted` | Captions, dates, inactive items | `#6b6a62` | `#b3b2a8` |
| `background` | Page below the header | `#f6f5f0` | `#1d1c19` |
| `surface` | Forms, scrolled toolbar, bottom tabs | `#ffffff` | `#272622` |
| `line` | Row dividers, field borders | `#e3e2da` | `#3a3934` |
| `chip` | Icon circles, segmented controls | `#ecebe4` | `#34332e` |
| `card` / `on-card` | Main active account card, its text | `#141414` / `#ffffff` | `#ffd100` / `#141414` |
| `card-alt` | Further active accounts | `#3b3a35` | `#e9e7df` |
| `card-chip` | Chip on active cards | `#ffd100` | `#141414` |
| `card-shadow` | Shadow under active cards | black 60% | yellow 22% (glow) |
| `card-off` | Blocked / closed account | `#e4e3dc` | `#302f2a` |
| `income` | Incoming amounts (§6) | `#12804a` | `#6fd69a` |

The theme follows the system by default; the toolbar toggle overrides it and the choice is saved.

### 13.6 Rollout

1. Foundation: tokens, fonts, icons, dark theme.
2. Shell: yellow toolbar, bottom tabs, theme toggle.
3. `shared/page-hero`, `shared/account-card`, dashboard.
4. After that: new screens are built in this style; existing ones move to it the next time they are touched.
