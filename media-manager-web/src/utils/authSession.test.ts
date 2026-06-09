import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';

class MemoryStorage implements Storage {
  private store = new Map<string, string>();

  get length() {
    return this.store.size;
  }

  clear() {
    this.store.clear();
  }

  getItem(key: string) {
    return this.store.has(key) ? this.store.get(key)! : null;
  }

  key(index: number) {
    return [...this.store.keys()][index] ?? null;
  }

  removeItem(key: string) {
    this.store.delete(key);
  }

  setItem(key: string, value: string) {
    this.store.set(key, value);
  }
}

function installStorage() {
  const local = new MemoryStorage();
  const session = new MemoryStorage();

  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: local,
  });
  Object.defineProperty(globalThis, 'sessionStorage', {
    configurable: true,
    value: session,
  });

  return { local, session };
}

async function loadAuthSession() {
  const modulePath = `./authSession.ts?test=${Date.now()}-${Math.random()}`;
  return import(modulePath);
}

describe('authSession remember-me storage', () => {
  beforeEach(() => {
    installStorage();
  });

  it('stores tokens in localStorage when remember is true', async () => {
    const authSession = await loadAuthSession();

    authSession.setSessionTokens(
      { accessToken: 'access-1', refreshToken: 'refresh-1' },
      { remember: true },
    );

    assert.equal(authSession.getAccessToken(), 'access-1');
    assert.equal(authSession.getRefreshToken(), 'refresh-1');
    assert.equal(localStorage.getItem('accessToken'), 'access-1');
    assert.equal(sessionStorage.getItem('accessToken'), null);
    assert.equal(authSession.isRememberLogin(), true);
  });

  it('stores tokens in sessionStorage when remember is false', async () => {
    const authSession = await loadAuthSession();

    authSession.setSessionTokens(
      { accessToken: 'access-2', refreshToken: 'refresh-2' },
      { remember: false },
    );

    assert.equal(authSession.getAccessToken(), 'access-2');
    assert.equal(authSession.getRefreshToken(), 'refresh-2');
    assert.equal(sessionStorage.getItem('accessToken'), 'access-2');
    assert.equal(localStorage.getItem('accessToken'), null);
    assert.equal(authSession.isRememberLogin(), false);
  });

  it('reads tokens from either storage', async () => {
    const authSession = await loadAuthSession();

    sessionStorage.setItem('accessToken', 'session-access');
    localStorage.setItem('accessToken', 'local-access');

    assert.equal(authSession.getAccessToken(), 'local-access');

    localStorage.removeItem('accessToken');
    assert.equal(authSession.getAccessToken(), 'session-access');
  });

  it('remembers and clears username', async () => {
    const authSession = await loadAuthSession();

    authSession.setRememberedUsername('admin');
    assert.equal(authSession.getRememberedUsername(), 'admin');

    authSession.setRememberedUsername(null);
    assert.equal(authSession.getRememberedUsername(), null);
  });

  it('clears tokens from both storages on logout', async () => {
    const authSession = await loadAuthSession();

    authSession.setSessionTokens(
      { accessToken: 'access-3', refreshToken: 'refresh-3' },
      { remember: true },
    );
    authSession.setSessionTokens(
      { accessToken: 'access-4', refreshToken: 'refresh-4' },
      { remember: false },
    );

    authSession.clearSessionTokens();

    assert.equal(authSession.getAccessToken(), null);
    assert.equal(authSession.getRefreshToken(), null);
    assert.equal(localStorage.getItem('accessToken'), null);
    assert.equal(sessionStorage.getItem('accessToken'), null);
  });

  it('defaults remember login to true when preference is missing', async () => {
    const authSession = await loadAuthSession();

    assert.equal(authSession.isRememberLogin(), true);
  });
});
