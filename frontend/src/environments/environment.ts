// Relative URL: in local dev `ng serve` proxies /api to the backend (proxy.conf.json),
// in Docker nginx does the same (docs/10-devops.md §7, API_URL).
export const environment = {
  apiUrl: '/api/v1',
  // true = endpoints listed in core/api/mocks answer without a backend, the rest go to the
  // real API. Login is real, so the backend must run. Set false to use only the real API.
  useMocks: true,
};
