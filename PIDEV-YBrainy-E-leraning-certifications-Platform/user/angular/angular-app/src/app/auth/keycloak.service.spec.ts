import { getDisplayName, getKeycloak, getRealmRoles, isAuthenticated, signInWithPassword, updateToken } from './keycloak.service';

function createJwt(payload: Record<string, unknown>): string {
  const header = { alg: 'none', typ: 'JWT' };
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
  return `${encode(header)}.${encode(payload)}.signature`;
}

describe('keycloak.service signInWithPassword', () => {
  beforeEach(() => {
    localStorage.clear();
    const kc: any = getKeycloak();
    kc.token = undefined;
    kc.refreshToken = undefined;
    kc.tokenParsed = undefined;
    kc.refreshTokenParsed = undefined;
    kc.authenticated = false;
    kc.subject = undefined;
  });

  afterEach(() => {
    localStorage.clear();
    const kc: any = getKeycloak();
    kc.token = undefined;
    kc.refreshToken = undefined;
    kc.tokenParsed = undefined;
    kc.refreshTokenParsed = undefined;
    kc.authenticated = false;
    kc.subject = undefined;
  });

  it('surfaces backend auth-client configuration errors', async () => {
    spyOn(window, 'fetch').and.resolveTo({
      ok: false,
      text: async () =>
        JSON.stringify({ message: 'Keycloak auth client configuration is invalid (client id/secret)' }),
    } as Response);

    await expectAsync(signInWithPassword('user@example.com', 'secret')).toBeRejectedWithError(
      'Keycloak auth client configuration is invalid (client id/secret)'
    );
  });

  it('stores token session and exposes parsed auth helpers after successful login', async () => {
    const token = createJwt({
      sub: 'kc-user-123',
      exp: Math.floor(Date.now() / 1000) + 300,
      email: 'user@example.com',
      preferred_username: 'user1',
      realm_access: { roles: ['ADMIN'] },
    });

    spyOn(window, 'fetch').and.resolveTo({
      ok: true,
      text: async () =>
        JSON.stringify({
          accessToken: token,
          refreshToken: 'refresh-123',
          expiresIn: 300,
          role: 'ADMIN',
          email: 'user@example.com',
          username: 'user1',
        }),
    } as Response);

    const response = await signInWithPassword('user@example.com', 'secret');

    expect(response.role).toBe('ADMIN');
    expect(isAuthenticated()).toBeTrue();
    expect(getRealmRoles()).toEqual(['ADMIN']);
    expect(getDisplayName()).toBe('user1');

    const stored = localStorage.getItem('bb_keycloak_tokens_v1');
    expect(stored).toContain('refresh-123');
  });

  it('refreshes an expired manual session when a refresh token is available', async () => {
    const expiredToken = createJwt({
      sub: 'kc-user-123',
      exp: Math.floor(Date.now() / 1000) - 30,
      email: 'user@example.com',
      preferred_username: 'user1',
      realm_access: { roles: ['ADMIN'] },
    });
    const refreshedToken = createJwt({
      sub: 'kc-user-123',
      exp: Math.floor(Date.now() / 1000) + 300,
      email: 'user@example.com',
      preferred_username: 'user1',
      realm_access: { roles: ['ADMIN'] },
    });

    localStorage.setItem(
      'bb_keycloak_tokens_v1',
      JSON.stringify({
        accessToken: expiredToken,
        refreshToken: 'refresh-123',
      })
    );

    spyOn(window, 'fetch').and.resolveTo({
      ok: true,
      text: async () =>
        JSON.stringify({
          accessToken: refreshedToken,
          refreshToken: 'refresh-456',
          expiresIn: 300,
          role: 'ADMIN',
          email: 'user@example.com',
          username: 'user1',
        }),
    } as Response);

    await updateToken();

    expect(isAuthenticated()).toBeTrue();
    expect(getRealmRoles()).toEqual(['ADMIN']);

    const stored = localStorage.getItem('bb_keycloak_tokens_v1');
    expect(stored).toContain('refresh-456');
  });
});
