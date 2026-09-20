import test from 'node:test';
import assert from 'node:assert/strict';
import { createAuthServer } from './server.mjs';

const response = { access_token: 'ghu_' + 'a'.repeat(40), token_type: 'bearer',
  refresh_token: 'ghr_' + 'b'.repeat(40), expires_in: 28800, refresh_token_expires_in: 15897600 };
async function fixture(t, upstream) {
  const calls = [];
  const server = createAuthServer({ clientId: 'test-client', clientSecret: 'server-secret',
    fetchImpl: async (...args) => { calls.push(args); return upstream ?? Response.json(response); } });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => { server.closeAllConnections(); return new Promise(resolve => server.close(resolve)); });
  const send = (path, body) => fetch(`http://127.0.0.1:${server.address().port}${path}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
  });
  return { send, calls };
}

test('exchange pins client and callback, forwards PKCE and keeps secret server-side', async t => {
  const { send, calls } = await fixture(t);
  const result = await send('/v1/exchange', { code: 'abcdefgh123', code_verifier: 'v'.repeat(64),
    redirect_uri: 'https://attacker.example', client_secret: 'attacker-secret' });
  assert.equal(result.status, 200);
  assert.equal(result.headers.get('cache-control'), 'no-store');
  assert.deepEqual(await result.json(), { ...response, scope: '' });
  assert.equal(calls[0][0], 'https://github.com/login/oauth/access_token');
  const form = calls[0][1].body;
  assert.equal(form.get('client_secret'), 'server-secret');
  assert.equal(form.get('redirect_uri'), 'etoile://oauth');
  assert.equal(form.get('code_verifier'), 'v'.repeat(64));
  assert.equal(calls[0][1].redirect, 'error');
});

test('refresh rotates tokens using configured client credentials', async t => {
  const { send, calls } = await fixture(t);
  const result = await send('/v1/refresh', { refresh_token: response.refresh_token });
  assert.equal(result.status, 200);
  assert.equal(calls[0][1].body.get('grant_type'), 'refresh_token');
  assert.equal(calls[0][1].body.get('refresh_token'), response.refresh_token);
});

test('malformed, oversized and missing PKCE requests never reach GitHub', async t => {
  const { send, calls } = await fixture(t);
  for (const input of [null, [], {}, { code: 'abcdefgh123' }]) {
    assert.equal((await send('/v1/exchange', input)).status, 400);
  }
  assert.equal((await send('/v1/refresh', { refresh_token: 'x'.repeat(5000) })).status, 413);
  assert.equal(calls.length, 0);
});

test('upstream rejection is sanitized', async t => {
  const { send } = await fixture(t, Response.json({ error: 'bad_verification_code', error_description: 'sensitive' }));
  const result = await send('/v1/exchange', { code: 'abcdefgh123', code_verifier: 'v'.repeat(64) });
  assert.equal(result.status, 401);
  assert.deepEqual(await result.json(), { error: 'authorization_rejected' });
});

test('rate limit bounds unauthenticated traffic', async t => {
  const { send } = await fixture(t);
  for (let i = 0; i < 30; i++) assert.equal((await send('/v1/exchange', {})).status, 400);
  assert.equal((await send('/v1/exchange', {})).status, 429);
});
