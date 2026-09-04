/**
 * MODULE 1: Scraper & Broadcaster
 * Fetches Myanmar 3D results from Thai Stock Exchange data
 * via thaistock2d.com API and pushes to Firebase (authenticated via service account).
 *
 * Cron: weekdays at 05:35 UTC (12:05 PM MMT) and 11:05 UTC (5:35 PM MMT)
 */

export interface Env {
  FIREBASE_DB_URL: string;
  TELEGRAM_BOT_TOKEN: string;
  TELEGRAM_CHAT_ID: string;
  GOOGLE_SERVICE_ACCOUNT_JSON: string;
}

interface LiveResult {
  set: string;
  value: string;
  open_time: string;
  twod: string;
  stock_date: string;
  stock_datetime: string;
  history_id: string | null;
}

interface LiveResponse {
  server_time: string;
  live: { set: string; value: string; time: string; twod: string; date: string; };
  result: LiveResult[];
  holiday: { status: string; date: string; name: string; };
}

export default {
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(processDraw(env));
  },

  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    if (request.method === 'GET') {
      try {
        await processDraw(env);
        return new Response(JSON.stringify({ status: 'ok', message: 'Draw processed' }), {
          headers: { 'Content-Type': 'application/json' }
        });
      } catch (e: any) {
        return new Response(JSON.stringify({ status: 'error', message: e.message }), {
          status: 500, headers: { 'Content-Type': 'application/json' }
        });
      }
    }
    return new Response('Method not allowed', { status: 405 });
  }
};

// ── Firebase Auth via Service Account ────────────────────────────────────────

async function getFirebaseToken(env: Env): Promise<string> {
  // Strip UTF-8 BOM if present; replace literal \n sequences with real newlines
  const rawJson = env.GOOGLE_SERVICE_ACCOUNT_JSON.replace(/^\uFEFF/, '').trim();
  const sa = JSON.parse(rawJson);
  // Cloudflare secrets may store private_key with literal \n instead of real newlines
  const privateKey: string = sa.private_key.replace(/\\n/g, '\n');
  const now = Math.floor(Date.now() / 1000);

  // Build JWT header + payload
  const header  = { alg: 'RS256', typ: 'JWT' };
  const payload = {
    iss: sa.client_email,
    sub: sa.client_email,
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
    scope: 'https://www.googleapis.com/auth/firebase.database https://www.googleapis.com/auth/userinfo.email'
  };

  const enc = (obj: object) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

  const sigInput = `${enc(header)}.${enc(payload)}`;

  // Import the RSA private key
  const pemBody = privateKey
    .replace('-----BEGIN PRIVATE KEY-----', '')
    .replace('-----END PRIVATE KEY-----', '')
    .replace(/\s/g, '');
  const keyDer = Uint8Array.from(atob(pemBody), c => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    'pkcs8', keyDer.buffer,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false, ['sign']
  );

  // Sign
  const sigBuf  = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(sigInput));
  const sig     = btoa(String.fromCharCode(...new Uint8Array(sigBuf)))
                    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const jwt     = `${sigInput}.${sig}`;

  // Exchange JWT for access token
  const tokenRes = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`
  });
  const tokenData = await tokenRes.json() as { access_token: string };
  return tokenData.access_token;
}

// ── Main Logic ────────────────────────────────────────────────────────────────

async function processDraw(env: Env) {
  // 1. Get Firebase auth token
  const token = await getFirebaseToken(env);
  const authHeaders = { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' };

  // 2. Check admin mode (non-blocking)
  try {
    const modeRes = await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/mode.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (modeRes.ok) {
      const mode = await modeRes.json();
      if (mode === 'manual') { console.log('Manual mode, skipping.'); return; }
    }
  } catch (_) {}

  // 3. Fetch from thaistock2d.com
  const result = await fetchFromThaiStock();
  if (!result) {
    await sendTelegramAlert(env, '⚠️ No 3D result available. Market may still be open or holiday.');
    return;
  }

  // 4. Get current Firebase data for archiving
  let currentResults: Record<string, unknown> | null = null;
  try {
    const cr = await fetch(`${env.FIREBASE_DB_URL}/3d_live_results.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (cr.ok) currentResults = await cr.json() as any;
  } catch (_) {}

  // 5. Build update payload
  const nextDate = calculateNextDrawDate(result.date);
  const updates: Record<string, unknown> = {
    '3d_live_results/winning_number':   result.threeD,
    '3d_live_results/target_draw_date': nextDate,
    '3d_live_results/set_value':        result.setValue,
    '3d_live_results/trade_value':      result.tradeValue,
    '3d_live_results/twod':             result.twoD,
    '3d_live_results/result_date':      result.date,
    '3d_live_results/result_time':      result.session,
    '3d_live_results/is_final':         result.isFinal,
    '3d_live_results/updated_at':       new Date().toISOString(),
    '3d_lottery_status/state':          result.isFinal ? 'declared' : 'interim',
  };

  const anyRes = currentResults as any;
  if (anyRes?.winning_number && anyRes?.is_final === true) {
    updates['3d_live_results/previous_winning_number'] = anyRes.winning_number;
    updates['3d_live_results/previous_draw_date']      = anyRes.result_date || anyRes.target_draw_date;
  }

  // 6. Write to Firebase (authenticated)
  const patchRes = await fetch(`${env.FIREBASE_DB_URL}/.json`, {
    method: 'PATCH',
    headers: authHeaders,
    body: JSON.stringify(updates)
  });

  if (!patchRes.ok) {
    const errText = await patchRes.text();
    throw new Error(`Firebase PATCH failed: ${patchRes.status} ${errText}`);
  }

  // 7. Telegram
  const label = result.isFinal ? '✅ FINAL' : '⏳ Interim';
  await sendTelegramAlert(env,
    `${label} 3D — ${result.date} (${result.session})\n\n🎯 3D: ${result.threeD}\n📊 SET: ${result.setValue}\n💰 Value: ${result.tradeValue}\n🔢 2D: ${result.twoD}`
  );
}

// Session priority order
const SESSION_PRIORITY = ['16:30:00', '15:00:00', '12:00:00', '11:00:00'];
const FINAL_SESSIONS   = new Set(['16:30:00', '15:00:00']);

async function fetchFromThaiStock(): Promise<{
  threeD: string; twoD: string; setValue: string; tradeValue: string;
  date: string; session: string; isFinal: boolean;
} | null> {
  try {
    const res  = await fetch('https://api.thaistock2d.com/live', { headers: { 'User-Agent': 'Mozilla/5.0' } });
    const data = await res.json() as LiveResponse;

    if (data.holiday?.status === '1') return null;

    // Best session with real data
    let best: LiveResult | null = null;
    for (const t of SESSION_PRIORITY) {
      const r = data.result.find(r => r.open_time === t);
      if (r?.history_id && r.set !== '--' && r.value !== '--') { best = r; break; }
    }
    if (!best) {
      best = data.result.filter(r => r.history_id && r.set !== '--' && r.value !== '--').pop() ?? null;
    }
    if (!best) return null;

    return {
      threeD:     extract3D(best.value),
      twoD:       best.twod,
      setValue:   best.set,
      tradeValue: best.value,
      date:       best.stock_date,
      session:    best.open_time.substring(0, 5),
      isFinal:    FINAL_SESSIONS.has(best.open_time),
    };
  } catch (e) {
    console.error('fetchFromThaiStock error:', e);
    return null;
  }
}

function extract3D(value: string): string {
  const clean = value.replace(/,/g, '');
  return clean.split('.')[0].slice(-3).padStart(3, '0');
}

async function sendTelegramAlert(env: Env, message: string) {
  if (!env.TELEGRAM_BOT_TOKEN) return;
  try {
    await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chat_id: env.TELEGRAM_CHAT_ID, text: message })
    });
  } catch (_) {}
}

function calculateNextDrawDate(currentDate: string): string {
  const d = new Date(currentDate);
  d.setDate(d.getDate() + 1);
  while (d.getDay() === 0 || d.getDay() === 6) d.setDate(d.getDate() + 1);
  return d.toISOString().split('T')[0];
}
