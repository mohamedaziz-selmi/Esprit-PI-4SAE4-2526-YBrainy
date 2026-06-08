export const environment = {
  production: false,
  // All requests go through the Angular dev-server proxy → API Gateway (8088).
  apiBaseUrl: '',
  courseApiBaseUrl: '',
  partnerApiBaseUrl: '',
  apiUrl: '/api',
  cartApiUrl: '/api',
  financeApiUrl: '/api/finance',
  forumApiUrl: '',
  forumWsUrl: 'http://localhost:8088',
  googleIdpHint: 'google',
  keycloakUrl: 'http://localhost:9190',
  keycloakRealm: 'microservices',
  keycloakClientId: 'angular-client',
  twelveDataApiKey: 'REPLACE_WITH_YOUR_TWELVE_DATA_API_KEY',
};
