// Production build for the dockerized stack: nginx proxies /api,/uploads,/ws
// to the API gateway, so all URLs are same-origin (relative).
// If ever deploying behind a real domain, replace these strings with the FQDN.
export const environment = {
  production: true,
  apiBaseUrl: '',
  courseApiBaseUrl: '',
  partnerApiBaseUrl: '',
  apiUrl: '/api',
  cartApiUrl: '/api',
  financeApiUrl: '/api/finance',
  forumApiUrl: '',
  forumWsUrl: '',
  googleIdpHint: 'google',
  keycloakUrl: 'http://localhost:9190',
  keycloakRealm: 'microservices',
  keycloakClientId: 'angular-client',
  twelveDataApiKey: 'REPLACE_WITH_YOUR_TWELVE_DATA_API_KEY',
};
