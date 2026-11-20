# ADR-0015: UI visual direction

- **Status:** Accepted
- **Date:** 2026-11-20

## Context

After Sprint 2 the customer app works, but it looks dated: default Roboto, every block is a white box with a 1px border, no icons, and the brand yellow appears only on buttons. Without a shared direction every new screen gets styled ad hoc.

We compared four directions on the dashboard (clean fintech, bold brand, dark premium, dense internet bank), then three levels of yellow inside the bold brand direction.

## Decision

**Customer app: "yellow header".**

- A full-width yellow header holds the toolbar. On the dashboard it is tall (greeting, total balance, main actions) and account cards overlap it. On other pages it is compact (page title and subtitle).
- Accounts are shown as bank cards; "Open account" is the last, see-through card in the row.
- Lists sit directly on the page background as rows, not inside boxes.
- Space Grotesk for headings and amounts, Inter for everything else.
- Material Symbols Rounded icons.
- Light and dark theme; the header stays yellow in both.
- Desktop: top toolbar with links on the right. Below 768px: bottom tabs.
- Minimal motion. No charts on the dashboard.

**Admin app: dense style.** Black toolbar, no yellow header, compact tables, same fonts and tokens.

The rules and tokens are in [07 §13](../07-frontend-guide.md#13-visual-design).

## Alternatives considered

| Option | Pros | Cons |
|---|---|---|
| Yellow header (chosen) | Recognizable brand, strong first screen for the demo | A lot of yellow; needs rules so it does not tire the eye |
| Yellow block (white toolbar, one rounded yellow block) | Calmer | Less "wow", less recognizable |
| Clean fintech (white tiles, side menu) | Easiest migration | Generic, little character |
| Dark premium | Looks expensive | Balance chart needs a new API; harder contrast work |
| Dense internet bank | Easy with `mat-table` | Dry for customers, poor on phones (kept for admin) |

## Consequences

- One yellow surface per screen keeps the header from tiring the eye. The tall header appears only on the dashboard, and the sticky toolbar turns to the surface color on scroll.
- New shared components: `page-hero` and `account-card`.
- Space Grotesk has no Cyrillic. That is fine while the UI is English; a localized UI (PKG-15) would need another heading font.
- The redesign is gradual: foundation, shell, then dashboard. After that, a screen moves to the new style when it is created or next touched.
