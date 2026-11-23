import { newIdempotencyKey } from './idempotency-key';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('newIdempotencyKey', () => {
  it('gives a new UUID v4 every time', () => {
    const a = newIdempotencyKey();
    expect(a).toMatch(UUID_V4);
    expect(newIdempotencyKey()).not.toBe(a);
  });

  it('still works without crypto.randomUUID (http on a LAN address)', () => {
    const own = Object.getOwnPropertyDescriptor(crypto, 'randomUUID');
    Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true });
    try {
      expect(newIdempotencyKey()).toMatch(UUID_V4);
    } finally {
      delete (crypto as { randomUUID?: unknown }).randomUUID;
      if (own) Object.defineProperty(crypto, 'randomUUID', own);
    }
  });
});
