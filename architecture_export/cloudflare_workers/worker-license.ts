/**
 * MODULE 2: Secure Licensing API
 */
export interface Env {
  FIREBASE_DB_URL: string;
  JWT_SECRET: string;
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    // 1. IP Rate Limiting (Pseudocode for CF Rate Limiter binding)
    const ip = request.headers.get('cf-connecting-ip');
    // if (await env.RATE_LIMITER.limit(ip).success === false) return new Response('Too Many Requests', { status: 429 });

    if (request.method !== 'POST') return new Response('Method Not Allowed', { status: 405 });
    
    const body: any = await request.json();
    const { cd_key, device_fingerprint } = body;

    // 2. Format Validation
    const regex = /^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/;
    if (!cd_key || !regex.test(cd_key)) {
      return new Response('Activation failed', { status: 400 }); // Generic error
    }

    // 3. Transactional Validation
    const dbRes = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cd_key}.json`);
    const keyData = await dbRes.json();

    if (!keyData || keyData.status !== 'available') {
      return new Response('Activation failed', { status: 400 });
    }

    // Update Firebase
    await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cd_key}.json`, {
      method: 'PATCH',
      body: JSON.stringify({
        status: 'claimed',
        claimed_by: device_fingerprint,
        activated_at: Date.now()
      })
    });

    // 4. Generate JWT Token
    // (Omitted HMAC-SHA256 signature boiler plate for brevity)
    const token = `SIGNED_JWT_${device_fingerprint}_${Date.now()}`;

    return new Response(JSON.stringify({ token }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    });
  }
};
