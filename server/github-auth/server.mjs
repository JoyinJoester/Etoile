import http from 'node:http';
import { pathToFileURL } from 'node:url';

const TOKEN_URL = 'https://github.com/login/oauth/access_token';
const safeToken = value => typeof value === 'string' && /^[\x21-\x7e]{20,255}$/.test(value);

export function createAuthServer({ clientId, clientSecret, redirectUri = 'etoile://oauth', fetchImpl = fetch, now = Date.now }) {
  if (!clientId || !clientSecret) throw new Error('GitHub App credentials must be configured');
  if (redirectUri !== 'etoile://oauth') throw new Error('Unsupported redirect URI');
  const buckets = new Map();
  let active = 0;
  function admit(address) {
    const time = now();
    for (const [key, value] of buckets) if (value.reset <= time) buckets.delete(key);
    const bucket = buckets.get(address) ?? { count: 0, reset: time + 60_000 };
    if (!buckets.has(address) && buckets.size >= 10_000) return false;
    buckets.set(address, bucket);
    return ++bucket.count <= 30;
  }
  const server = http.createServer(async (req, res) => {
    const reply = (status, body) => {
      res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store',
        'Pragma': 'no-cache', 'X-Content-Type-Options': 'nosniff' });
      res.end(JSON.stringify(body));
    };
    if (req.method === 'GET' && req.url === '/health') return reply(200, { status: 'ok' });
    if (req.method !== 'POST' || !['/v1/exchange', '/v1/refresh'].includes(req.url)) {
      return reply(404, { error: 'not_found' });
    }
    // Do not trust forwarded IP headers without a configured trusted proxy.
    if (!admit(req.socket.remoteAddress) || active >= 32) return reply(429, { error: 'rate_limited' });
    if (req.headers['content-type']?.split(';')[0].trim() !== 'application/json') {
      return reply(415, { error: 'json_required' });
    }
    active++;
    try {
      let raw = '';
      let size = 0;
      for await (const chunk of req) {
        size += chunk.length;
        if (size > 4096) { reply(413, { error: 'request_too_large' }); return; }
        raw += chunk.toString('utf8');
      }
      let input;
      try { input = JSON.parse(raw); } catch { return reply(400, { error: 'invalid_request' }); }
      if (!input || typeof input !== 'object' || Array.isArray(input)) return reply(400, { error: 'invalid_request' });
      const form = new URLSearchParams({ client_id: clientId, client_secret: clientSecret });
      if (req.url === '/v1/exchange') {
        if (typeof input.code !== 'string' || !/^[A-Za-z0-9_-]{8,255}$/.test(input.code) ||
            typeof input.code_verifier !== 'string' || !/^[A-Za-z0-9._~-]{43,128}$/.test(input.code_verifier)) {
          return reply(400, { error: 'invalid_request' });
        }
        form.set('code', input.code);
        form.set('code_verifier', input.code_verifier);
        form.set('redirect_uri', redirectUri);
      } else {
        if (!safeToken(input.refresh_token)) return reply(400, { error: 'invalid_request' });
        form.set('grant_type', 'refresh_token');
        form.set('refresh_token', input.refresh_token);
      }
      const upstream = await fetchImpl(TOKEN_URL, {
        method: 'POST', redirect: 'error', signal: AbortSignal.timeout(15_000),
        headers: { Accept: 'application/json', 'User-Agent': 'Etoile-Auth-Service' }, body: form
      });
      if (!upstream.ok) return reply(upstream.status === 429 ? 429 : 502, { error: 'github_unavailable' });
      const data = await upstream.json();
      if (data.error) return reply(401, { error: 'authorization_rejected' });
      if (!safeToken(data.access_token) || data.token_type?.toLowerCase() !== 'bearer' ||
          !safeToken(data.refresh_token) || !Number.isSafeInteger(data.expires_in) || data.expires_in <= 0 ||
          !Number.isSafeInteger(data.refresh_token_expires_in) || data.refresh_token_expires_in <= 0) {
        return reply(502, { error: 'invalid_github_response' });
      }
      // Return only the fields the native client needs. Never log credentials or request bodies.
      reply(200, { access_token: data.access_token, token_type: 'bearer', scope: '',
        refresh_token: data.refresh_token, expires_in: data.expires_in,
        refresh_token_expires_in: data.refresh_token_expires_in });
    } catch {
      if (!res.headersSent) reply(502, { error: 'exchange_unavailable' });
    } finally { active--; }
  });
  server.requestTimeout = 20_000;
  server.headersTimeout = 10_000;
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const server = createAuthServer({ clientId: process.env.GITHUB_APP_CLIENT_ID,
    clientSecret: process.env.GITHUB_APP_CLIENT_SECRET });
  server.listen(Number(process.env.PORT || 8787), process.env.HOST || '127.0.0.1', () => {
    console.log('Etoile authorization service ready');
  });
}
