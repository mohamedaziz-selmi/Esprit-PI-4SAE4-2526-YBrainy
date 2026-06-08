import Keycloak from 'keycloak-js';
import { environment } from '../../environments/environment';

interface StoredAuthSession {
  accessToken: string;
  refreshToken?: string | null;
  expiresAt?: number | null;
}

interface BackendAuthResponse {
  userId?: number;
  username?: string;
  email?: string;
  role?: string;
  accessToken?: string;
  refreshToken?: string;
  expiresIn?: number | string;
}

const AUTH_STORAGE_KEY = 'bb_keycloak_tokens_v1';
const USER_SESSION_STORAGE_KEY = 'bb_user_session_v1';

const keycloak = new Keycloak({
  url: environment.keycloakUrl?.trim() || 'http://localhost:9190',
  realm: environment.keycloakRealm?.trim() || 'microservices',
  clientId: environment.keycloakClientId?.trim() || 'angular-client',
});

const GOOGLE_IDP_HINT = environment.googleIdpHint?.trim() || 'google';

let manualSessionActive = false;
let manualRefreshPromise: Promise<void> | null = null;

function browserAvailable(): boolean {
  return typeof window !== 'undefined' && typeof localStorage !== 'undefined';
}

function getKeycloakAny(): any {
  return keycloak as any;
}

function normalizeBase64Url(value: string): string {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const padding = (4 - (base64.length % 4)) % 4;
  return base64 + '='.repeat(padding);
}

function decodeJwtClaims(token: string): any | null {
  try {
    const parts = token.split('.');
    if (parts.length < 2) return null;
    const payload = normalizeBase64Url(parts[1]);
    const json = decodeURIComponent(
      Array.from(window.atob(payload))
        .map((c) => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .join('')
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

function isTokenExpired(parsed: any): boolean {
  return isTokenExpiringSoon(parsed, 5000);
}

function isTokenExpiringSoon(parsed: any, withinMs: number): boolean {
  const exp = Number(parsed?.exp);
  if (!Number.isFinite(exp) || exp <= 0) return false;
  return exp * 1000 <= Date.now() + withinMs;
}

function readStoredManualSession(): StoredAuthSession | null {
  if (!browserAvailable()) return null;
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as StoredAuthSession;
  } catch {
    clearStoredSessions();
    return null;
  }
}

function clearKeycloakState(): void {
  const kc = getKeycloakAny();
  kc.token = undefined;
  kc.refreshToken = undefined;
  kc.tokenParsed = undefined;
  kc.refreshTokenParsed = undefined;
  kc.authenticated = false;
  kc.subject = undefined;
}

function persistManualSession(session: StoredAuthSession): void {
  if (!browserAvailable()) return;
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
}

function clearStoredSessions(): void {
  if (!browserAvailable()) return;
  localStorage.removeItem(AUTH_STORAGE_KEY);
  localStorage.removeItem(USER_SESSION_STORAGE_KEY);
  localStorage.removeItem('forum_auth_user');
}

function applyManualSession(session: StoredAuthSession, persist = true): boolean {
  if (!session?.accessToken) return false;

  const parsed = decodeJwtClaims(session.accessToken);
  const accessTokenExpired = isTokenExpired(parsed);
  if (!parsed || (accessTokenExpired && !session.refreshToken)) {
    manualSessionActive = false;
    clearStoredSessions();
    clearKeycloakState();
    return false;
  }

  const kc = getKeycloakAny();
  kc.token = session.accessToken;
  kc.refreshToken = session.refreshToken ?? undefined;
  kc.tokenParsed = parsed;
  kc.authenticated = true;
  kc.subject = parsed?.sub ? String(parsed.sub) : undefined;

  manualSessionActive = true;
  if (persist) persistManualSession(session);
  return true;
}

function loadManualSessionFromStorage(): boolean {
  try {
    const session = readStoredManualSession();
    if (!session) return false;
    return applyManualSession(session, false);
  } catch {
    clearStoredSessions();
    clearKeycloakState();
    manualSessionActive = false;
    return false;
  }
}

function ensureAuthStateLoaded(): void {
  const kc = getKeycloakAny();
  if (kc.authenticated && kc.token) {
    if (manualSessionActive && isTokenExpired(kc.tokenParsed) && !kc.refreshToken) {
      clearStoredSessions();
      clearKeycloakState();
      manualSessionActive = false;
    }
    return;
  }
  loadManualSessionFromStorage();
}

function parseExpiresIn(expiresIn: BackendAuthResponse['expiresIn']): number {
  return typeof expiresIn === 'number'
    ? expiresIn
    : Number.parseInt(String(expiresIn ?? ''), 10);
}

async function requestAuthResponse(path: string, payload: Record<string, unknown>): Promise<BackendAuthResponse> {
  const response = await fetch(`${environment.apiBaseUrl}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  const text = await response.text();
  let body: any = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = null;
  }

  if (!response.ok) {
    throw new Error(parseErrorMessage(body));
  }

  if (!body?.accessToken || typeof body.accessToken !== 'string') {
    throw new Error('Authentication succeeded but no access token was returned.');
  }

  return body as BackendAuthResponse;
}

function toStoredSession(
  authResponse: BackendAuthResponse,
  fallbackRefreshToken?: string | null
): StoredAuthSession {
  const expiresInSeconds = parseExpiresIn(authResponse.expiresIn);
  const refreshToken =
    typeof authResponse.refreshToken === 'string' && authResponse.refreshToken.trim()
      ? authResponse.refreshToken
      : fallbackRefreshToken ?? null;

  return {
    accessToken: authResponse.accessToken as string,
    refreshToken,
    expiresAt: Number.isFinite(expiresInSeconds) ? Date.now() + expiresInSeconds * 1000 : null,
  };
}

async function refreshManualSession(): Promise<void> {
  const session = readStoredManualSession();
  if (!session?.refreshToken) {
    clearStoredSessions();
    clearKeycloakState();
    manualSessionActive = false;
    throw new Error('Session expired. Please sign in again.');
  }

  try {
    const refreshed = await requestAuthResponse('/api/auth/refresh', {
      refreshToken: session.refreshToken,
    });

    if (!applyManualSession(toStoredSession(refreshed, session.refreshToken))) {
      throw new Error('Session refresh failed.');
    }
  } catch (error) {
    clearStoredSessions();
    clearKeycloakState();
    manualSessionActive = false;
    throw error;
  }
}

function parseErrorMessage(body: any): string {
  if (typeof body === 'string' && body.trim()) return body.trim();
  if (typeof body?.message === 'string' && body.message.trim()) return body.message.trim();
  if (typeof body?.error === 'string' && body.error.trim()) return body.error.trim();
  return 'Login failed. Please check your credentials and try again.';
}

export function getKeycloak() {
  ensureAuthStateLoaded();
  return keycloak;
}

export async function initKeycloak() {
  try {
    const silentSsoUri =
      typeof window !== 'undefined'
        ? `${window.location.origin}/assets/silent-check-sso.html`
        : undefined;

    const authenticated = await keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      checkLoginIframe: false,
      silentCheckSsoRedirectUri: silentSsoUri,
      silentCheckSsoFallback: false,
    });
    if (authenticated) {
      manualSessionActive = false;
      const kc = getKeycloakAny();
      kc.subject = kc.subject ?? kc.tokenParsed?.sub;
      return true;
    }
  } catch {
    // If Keycloak UI/SSO bootstrap is unavailable, fall back to stored API login tokens.
  }

  return loadManualSessionFromStorage();
}

export async function signInWithPassword(email: string, password: string): Promise<BackendAuthResponse> {
  const authResponse = await requestAuthResponse('/api/auth/signin', { email, password });
  manualRefreshPromise = null;
  applyManualSession(toStoredSession(authResponse));
  return authResponse;
}

export async function signInWithFaceBiometric(
  image: Blob,
  redirectUri: string = window.location.origin
): Promise<void> {
  const formData = new FormData();
  formData.append('image', image, 'face-login.png');

  const response = await fetch(`${environment.apiBaseUrl}/api/auth/face/signin`, {
    method: 'POST',
    body: formData,
    credentials: 'include',
  });

  const text = await response.text();
  let body: any = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = text;
  }

  if (!response.ok) {
    throw new Error(parseErrorMessage(body));
  }

  manualRefreshPromise = null;
  clearStoredSessions();
  clearKeycloakState();
  manualSessionActive = false;

  await login(redirectUri);
}

export function login(
  redirectUri: string = window.location.origin,
  options?: { idpHint?: string }
) {
  const idpHint = options?.idpHint?.trim();
  return keycloak.login({
    redirectUri,
    ...(idpHint ? { idpHint } : {}),
  });
}

export function loginWithGoogle(redirectUri: string = window.location.origin) {
  return login(redirectUri, { idpHint: GOOGLE_IDP_HINT });
}

export function redirectToAppLogin(redirectUri: string = window.location.href): void {
  if (typeof window === 'undefined') return;
  const target = new URL('/login', window.location.origin);
  if (redirectUri) {
    target.searchParams.set('redirect', redirectUri);
  }
  window.location.assign(target.toString());
}

export function isAuthenticated(): boolean {
  ensureAuthStateLoaded();
  if (manualSessionActive) {
    const kc = getKeycloakAny();
    return Boolean(kc.token && (!isTokenExpired(kc.tokenParsed) || kc.refreshToken));
  }
  return Boolean(keycloak.authenticated && keycloak.token);
}

export function getRealmRoles(): string[] {
  ensureAuthStateLoaded();
  const parsed: any = getKeycloakAny().tokenParsed;
  const roles: string[] = parsed?.realm_access?.roles ?? [];
  return roles.map((r) => String(r));
}

export function getDisplayName(): string | null {
  ensureAuthStateLoaded();
  const parsed: any = getKeycloakAny().tokenParsed;
  const name =
    parsed?.name ??
    parsed?.preferred_username ??
    parsed?.given_name ??
    parsed?.email ??
    null;
  return name ? String(name) : null;
}

export function getToken() {
  ensureAuthStateLoaded();
  return getKeycloakAny().token;
}

export async function updateToken() {
  ensureAuthStateLoaded();
  if (!keycloak.authenticated) return;

  if (manualSessionActive) {
    if (!isTokenExpiringSoon(getKeycloakAny().tokenParsed, 30000)) return;

    if (!manualRefreshPromise) {
      manualRefreshPromise = refreshManualSession().finally(() => {
        manualRefreshPromise = null;
      });
    }

    return manualRefreshPromise;
  }

  // Some flows (first load, before login redirect completes) won't have a refresh token.
  if (!keycloak.refreshToken) return;

  await keycloak.updateToken(30);
}

export function logout() {
  ensureAuthStateLoaded();
  const hadInteractiveSession = Boolean(keycloak.authenticated) && !manualSessionActive;

  clearStoredSessions();
  clearKeycloakState();
  manualSessionActive = false;

  if (hadInteractiveSession) {
    return keycloak.logout({ redirectUri: window.location.origin });
  }

  if (typeof window !== 'undefined') {
    window.location.assign(window.location.origin);
  }
  return Promise.resolve();
}
