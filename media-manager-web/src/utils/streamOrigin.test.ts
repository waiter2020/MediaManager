import assert from 'node:assert/strict';
import { afterEach, beforeEach, describe, it } from 'node:test';
import {
  listOrderedOriginsForFile,
  listStreamRequestOrigins,
  resolveMediaPlaybackUrl,
  resolveStreamRequestOrigin,
  setStreamAuxPortsForTest,
} from './streamOrigin';

describe('streamOrigin', () => {
  let originalLocation: Location;

  beforeEach(() => {
    originalLocation = globalThis.location;
    (globalThis as typeof globalThis & { window: typeof globalThis }).window = globalThis;
    const memoryStorage = {
      getItem: () => null,
      setItem: () => {},
      removeItem: () => {},
      clear: () => {},
      key: () => null,
      length: 0,
    };
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: memoryStorage,
    });
    Object.defineProperty(globalThis, 'sessionStorage', {
      configurable: true,
      value: memoryStorage,
    });
    Object.defineProperty(globalThis, 'location', {
      configurable: true,
      value: {
        protocol: 'http:',
        hostname: 'localhost',
        port: '',
        origin: 'http://localhost',
      },
    });
    setStreamAuxPortsForTest([50000, 50001, 50002]);
  });

  afterEach(() => {
    Object.defineProperty(globalThis, 'location', {
      configurable: true,
      value: originalLocation,
    });
    delete globalThis.__MM_STREAM_AUX_PORTS__;
  });

  it('includes page origin and configured auxiliary ports', () => {
    const origins = listStreamRequestOrigins();
    assert.ok(origins.includes('http://localhost'));
    assert.ok(origins.includes('http://localhost:50000'));
    assert.ok(origins.includes('http://localhost:50001'));
    assert.ok(origins.includes('http://localhost:50002'));
    assert.equal(origins.length, 4);
  });

  it('shards file ids across stream origins', () => {
    const origins = new Set([1, 2, 3, 4, 5].map((id) => resolveStreamRequestOrigin(id)));
    assert.ok(origins.size > 1);
  });

  it('orders preferred origin first for playback fallback', () => {
    const ordered = listOrderedOriginsForFile(2);
    assert.equal(ordered[0], resolveStreamRequestOrigin(2));
    assert.equal(ordered.length, listStreamRequestOrigins().length);
  });

  it('builds playback urls per origin attempt index', () => {
    const path = '/api/v1/stream/9';
    const first = resolveMediaPlaybackUrl(path, 2, 0);
    const second = resolveMediaPlaybackUrl(path, 2, 1);
    assert.notEqual(first, second);
    assert.match(first, /^http:\/\/localhost(?::\d+)?\/api\/v1\/stream\/9/);
  });

  it('appends auth token for video element playback', () => {
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: {
        getItem: (key: string) => (key === 'accessToken' ? 'preview-token' : null),
        setItem: () => {},
        removeItem: () => {},
        clear: () => {},
        key: () => null,
        length: 0,
      },
    });
    const url = resolveMediaPlaybackUrl('/api/v1/stream/42', 1, 0);
    assert.match(url, /\/api\/v1\/stream\/42\?token=preview-token$/);
  });
});
