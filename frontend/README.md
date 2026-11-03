# Trio Bank — Frontend

Angular 22 single-page application (standalone components, Angular Material). Code structure and conventions: [docs/07-frontend-guide.md](../docs/07-frontend-guide.md).

## Requirements

- **Node.js** `^22.22.3`, `^24.15.0` or `>=26` (what Angular 22 supports)
- npm 11+

## How to run

```bash
npm ci            # install exact versions from package-lock.json
npm start         # dev server on http://localhost:4200
```

`npm start` proxies every `/api` request to the backend at `http://localhost:8080` ([`proxy.conf.json`](proxy.conf.json)), so the app calls the API with relative URLs (`/api/v1/...`) and needs no CORS setup. Start the backend first, or the API calls will fail. The pages themselves still load without it.

> On Windows, stop `npm start` before running `npm ci`: the running dev server locks files in `node_modules` and the install fails with `EBUSY`.

## Commands

| Command                                   | What it does                                                               |
| ----------------------------------------- | -------------------------------------------------------------------------- |
| `npm start`                               | Dev server with live reload and the `/api` proxy                           |
| `npm run lint`                            | ESLint (angular-eslint) on `.ts` and `.html` files                         |
| `npm run format` / `npm run format:check` | Format / check formatting with Prettier                                    |
| `npm test -- --watch=false`               | Unit tests once (Vitest, Angular CLI runner); omit the flag for watch mode |
| `npm run build`                           | Production build into `dist/trio-bank`                                     |

CI (`frontend-ci.yml`) runs `npm ci → lint → test → build`, so run these before opening a PR.
