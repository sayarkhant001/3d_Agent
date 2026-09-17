/**
 * MODULE 2: Secure Licensing API
 * Supports: 3-Day Trial, Lifetime, and Custom Duration keys
 * Auth: Google Service Account → OAuth2 Access Token → Firebase REST API
 */
export interface Env {
  FIREBASE_DB_URL: string;
  FIREBASE_SERVICE_ACCOUNT: string; // JSON string of service account
  JWT_SECRET: string;
}

// ===== Google Service Account Auth =====

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const pemContents = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, '')
    .replace(/-----END PRIVATE KEY-----/g, '')
    .replace(/\s/g, '');

  const binaryDer = Uint8Array.from(atob(pemContents), c => c.charCodeAt(0));

  return crypto.subtle.importKey(
    'pkcs8',
    binaryDer.buffer,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  );
}

function toBase64Url(str: string): string {
  return btoa(str).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

async function getFirebaseAccessToken(serviceAccountJson: string): Promise<string> {
  // Strip BOM and whitespace that may have been saved with the secret
  const cleanJson = serviceAccountJson.replace(/^\uFEFF/, '').trim();
  const sa = JSON.parse(cleanJson) as {
    client_email: string;
    private_key: string;
  };

  const now = Math.floor(Date.now() / 1000);
  const header = toBase64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const payload = toBase64Url(JSON.stringify({
    iss: sa.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.database https://www.googleapis.com/auth/userinfo.email',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  }));

  const key = await importPrivateKey(sa.private_key);
  const encoder = new TextEncoder();
  const signatureBuffer = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    encoder.encode(`${header}.${payload}`)
  );
  const signature = btoa(String.fromCharCode(...new Uint8Array(signatureBuffer)))
    .replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');

  const jwt = `${header}.${payload}.${signature}`;

  // Exchange JWT for Google OAuth2 access token
  const tokenRes = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });

  const tokenData = await tokenRes.json() as { access_token?: string; error?: string };
  if (!tokenData.access_token) {
    throw new Error(`OAuth token exchange failed: ${tokenData.error || 'unknown'}`);
  }
  return tokenData.access_token;
}

// ===== App JWT Signing (HMAC-SHA256) =====

async function signAppJWT(payload: Record<string, unknown>, secret: string): Promise<string> {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );

  const header = toBase64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const encodedPayload = toBase64Url(JSON.stringify(payload));

  const signatureBuffer = await crypto.subtle.sign(
    'HMAC', key, encoder.encode(`${header}.${encodedPayload}`)
  );
  const signature = btoa(String.fromCharCode(...new Uint8Array(signatureBuffer)))
    .replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');

  return `${header}.${encodedPayload}.${signature}`;
}

// ===== Main Worker =====

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    // CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type, Authorization',
        },
      });
    }

    if (request.method !== 'POST') {
      return new Response(JSON.stringify({ error: 'Method Not Allowed' }), {
        status: 405,
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*',
        },
      });
    }

    const url = new URL(request.url);
    const targetPath = url.pathname === '/' ? '/activate' : url.pathname;
    const targetUrl = `https://3d-scraper-worker.khaingkhantkyaw001.workers.dev${targetPath}${url.search}`;

    try {
      const headers = new Headers(request.headers);
      headers.set('Host', '3d-scraper-worker.khaingkhantkyaw001.workers.dev');

      const bodyBuffer = await request.arrayBuffer();

      const res = await fetch(targetUrl, {
        method: 'POST',
        headers: headers,
        body: bodyBuffer,
      });

      const responseBody = await res.arrayBuffer();
      const newHeaders = new Headers(res.headers);
      newHeaders.set('Access-Control-Allow-Origin', '*');

      return new Response(responseBody, {
        status: res.status,
        headers: newHeaders,
      });
    } catch (e: any) {
      return new Response(JSON.stringify({ error: 'License Gateway Error', detail: e.message }), {
        status: 502,
        headers: {
          'Content-Type': 'application/json',
          'Access-Control-Allow-Origin': '*',
        },
      });
    }
  },
};
