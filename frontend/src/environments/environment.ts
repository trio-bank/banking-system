// Relative URL: in local dev `ng serve` proxies /api to the backend (proxy.conf.json),
// in Docker nginx does the same (docs/10-devops.md §7, API_URL).
export const environment = {
  apiUrl: '/api/v1',
};
