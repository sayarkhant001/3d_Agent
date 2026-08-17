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
  const sa = JSON.parse(serviceAccountJson) as {
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
          'Access-Control-Allow-Methods': 'POST, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type',
        },
      });
    }

    if (request.method !== 'POST') {
      return new Response(JSON.stringify({ error: 'Method Not Allowed' }), { status: 405 });
    }

    const corsHeaders = {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
    };

    try {
      const body = await request.json() as { cd_key?: string; device_fingerprint?: string };
      const { cd_key, device_fingerprint } = body;

      // 1. Validate format (XXXX-XXXX-XXXX-XXXX)
      const keyRegex = /^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/;
      if (!cd_key || !keyRegex.test(cd_key) || !device_fingerprint) {
        return new Response(JSON.stringify({ error: 'Invalid request format' }), {
          status: 400, headers: corsHeaders,
        });
      }

      // 2. Get Firebase access token from service account
      const accessToken = await getFirebaseAccessToken(env.FIREBASE_SERVICE_ACCOUNT);

      // 3. Check Firebase for key
      const fbUrl = `${env.FIREBASE_DB_URL}/3d_licenses/keys/${cd_key}.json?access_token=${accessToken}`;
      const fbRes = await fetch(fbUrl);
      const keyData = await fbRes.json() as {
        status?: string;
        duration?: string | number;
      } | null;

      if (!keyData || keyData.status !== 'available') {
        return new Response(JSON.stringify({ error: 'Activation failed' }), {
          status: 400, headers: corsHeaders,
        });
      }

      // 4. Calculate expiration based on key type from Admin Dashboard
      //    - "trial"    → 3 days from now
      //    - number     → that many days from now (custom duration)
      //    - "lifetime"  → no expiration (no 'exp' field)
      const nowSeconds = Math.floor(Date.now() / 1000);
      const jwtPayload: Record<string, unknown> = {
        cd_key,
        device_fingerprint,
        iat: nowSeconds,
      };

      if (keyData.duration === 'trial') {
        jwtPayload.exp = nowSeconds + (3 * 24 * 60 * 60); // +3 days
        jwtPayload.duration_type = 'trial';
      } else if (typeof keyData.duration === 'number') {
        jwtPayload.exp = nowSeconds + (keyData.duration * 24 * 60 * 60); // +N days
        jwtPayload.duration_type = 'custom';
        jwtPayload.duration_days = keyData.duration;
      } else if (keyData.duration === 'lifetime') {
        jwtPayload.duration_type = 'lifetime';
      }

      // 5. Sign app JWT with HMAC-SHA256
      const token = await signAppJWT(jwtPayload, env.JWT_SECRET);

      // 6. Claim the key in Firebase
      await fetch(fbUrl, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          status: 'claimed',
          claimed_by: device_fingerprint,
          activated_at: Date.now(),
        }),
      });

      // 7. Return token to Android app
      return new Response(JSON.stringify({
        token,
        duration_type: jwtPayload.duration_type,
        expires_at: jwtPayload.exp || null,
      }), { headers: corsHeaders });

    } catch (e: any) {
      return new Response(JSON.stringify({ error: 'Internal Server Error', detail: e.message }), {
        status: 500, headers: corsHeaders,
      });
    }
  },
};
