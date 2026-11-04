// Relative URL: in local dev `ng serve` proxies /api to the backend (proxy.conf.json),
// in Docker nginx does the same (docs/10-devops.md §7, API_URL).
export const environment = {
  apiUrl: '/api/v1',
  // true = mocked endpoints answer without a backend (core/api/mocks). Set false to use the real API.
  useMocks: true,
};
