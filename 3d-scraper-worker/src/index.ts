/**
 * MODULE 1: Scraper & Broadcaster
 * Fetches Myanmar 3D results directly from the official Government Lottery Office (GLO) Thailand API
 * (https://www.glo.or.th/api/lottery/getLatestLottery)
 * and pushes to Firebase (authenticated via service account).
 *
 * Thai 3D winning number = last 3 digits of the official 1st prize (รางวัลที่ 1).
 */

import {
  handleTelegramWebhook,
  sendTelegramMessage,
  getCleanBotToken,
  getAutoApproveConfig,
  getApprovalKeyboard
} from './telegramBot';

export interface Env {
  FIREBASE_DB_URL: string;
  TELEGRAM_BOT_TOKEN: string;
  TELEGRAM_CHAT_ID: string;
  GOOGLE_SERVICE_ACCOUNT_JSON: string;
}

export interface GloLotteryData {
  first?: {
    price?: string;
    number?: Array<{ round?: number; value: string }>;
  };
  last2?: {
    price?: string;
    number?: Array<{ round?: number; value: string }>;
  };
  last3f?: {
    price?: string;
    number?: Array<{ round?: number; value: string }>;
  };
  last3b?: {
    price?: string;
    number?: Array<{ round?: number; value: string }>;
  };
  near1?: {
    price?: string;
    number?: Array<{ round?: number; value: string }>;
  };
}

export interface GloResponse {
  status: boolean;
  statusCode?: number;
  statusMessage?: string;
  response?: {
    date?: string;
    pdf_url?: string;
    youtube_url?: string;
    data?: GloLotteryData;
    n3?: {
      straight3?: { price?: string; number?: Array<{ round?: number; value: string }> };
      straight2?: { price?: string; number?: Array<{ round?: number; value: string }> };
    };
  };
}

export interface LotteryResult {
  threeD: string;
  twoD: string;
  firstPrize: string;
  date: string;
  session: string;
  isFinal: boolean;
}

export default {
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(processDraw(env));
  },

  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    // Health/ping check
    if (url.pathname === '/health' || url.pathname === '/ping') {
      return new Response(JSON.stringify({ status: 'ok', service: '3d-scraper-worker' }), {
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Direct fetch test for GLO without needing Firebase credentials
    if (url.pathname === '/latest-glo') {
      try {
        const result = await fetchFromGloLottery();
        if (!result) {
          return new Response(JSON.stringify({ status: 'error', message: 'Unable to fetch GLO data' }), {
            status: 502,
            headers: { 'Content-Type': 'application/json' }
          });
        }
        return new Response(JSON.stringify({ status: 'ok', data: result }), {
          headers: { 'Content-Type': 'application/json' }
        });
      } catch (e: any) {
        return new Response(JSON.stringify({ status: 'error', message: e.message }), {
          status: 500,
          headers: { 'Content-Type': 'application/json' }
        });
      }
    }

    // Telegram Bot Webhook endpoint
    if (url.pathname === '/webhook' || url.pathname === '/telegram') {
      return handleTelegramWebhook(request, env);
    }

    // Automatically set Telegram Webhook
    if (url.pathname === '/set-webhook') {
      const webhookUrl = url.searchParams.get('url') || `${url.origin}/webhook`;
      const token = getCleanBotToken(env);
      const tgRes = await fetch(
        `https://api.telegram.org/bot${token}/setWebhook?url=${encodeURIComponent(webhookUrl)}&allowed_updates=${encodeURIComponent(JSON.stringify(['message', 'callback_query']))}`
      );
      const tgData = await tgRes.json();
      return new Response(JSON.stringify({ status: 'ok', webhookUrl, telegram: tgData }, null, 2), {
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Query Telegram Webhook status
    if (url.pathname === '/webhook-info') {
      const token = getCleanBotToken(env);
      const tgRes = await fetch(`https://api.telegram.org/bot${token}/getWebhookInfo`);
      const tgData = await tgRes.json();
      return new Response(JSON.stringify(tgData, null, 2), {
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Test send message
    if (url.pathname === '/test-telegram') {
      const res = await sendTelegramMessage(
        env,
        env.TELEGRAM_CHAT_ID,
        '🔔 <b>3D Ledger Telegram Bot Test</b>\n\nCloudflare Worker မှ စမ်းသပ် တိုက်ရိုက် ပေးပို့ခြင်း ဖြစ်ပါသည်။'
      );
      return new Response(JSON.stringify(res, null, 2), {
        headers: { 'Content-Type': 'application/json' }
      });
    }

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

    // License Activation endpoint
    if (url.pathname === '/activate' || url.pathname === '/api/license/activate' || (url.pathname === '/' && request.method === 'POST' && request.headers.get('Content-Type')?.includes('application/json'))) {
      const cloned = request.clone();
      try {
        const body = await cloned.json() as any;
        if (body && body.cd_key) {
          return handleLicenseActivate(request, env);
        }
      } catch (_) {}
    }

    // License Status Checking endpoint (for pending polling)
    if (url.pathname === '/check-status' || url.pathname === '/api/license/check-status') {
      return handleLicenseCheckStatus(request, env);
    }

    // License Verification endpoint (for app startup check)
    if (url.pathname === '/verify' || url.pathname === '/api/license/verify') {
      return handleLicenseVerify(request, env);
    }

    // Manual draw processing trigger
    if (url.pathname === '/process-draw' || (url.pathname === '/' && request.method === 'POST')) {
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

    // Default root GET welcome
    if (url.pathname === '/') {
      return new Response(JSON.stringify({
        service: '3d-scraper-worker & telegram-bot & license-api',
        status: 'online',
        endpoints: [
          '/health',
          '/latest-glo',
          '/webhook (POST for Telegram)',
          '/set-webhook',
          '/webhook-info',
          '/activate (POST)',
          '/check-status (POST)',
          '/verify (POST)',
          '/test-telegram',
          '/process-draw'
        ]
      }, null, 2), {
        headers: { 'Content-Type': 'application/json' }
      });
    }

    return new Response('Method not allowed', { status: 405 });
  }
};

// ── Firebase Auth via Service Account ────────────────────────────────────────

export async function getFirebaseToken(env: Env): Promise<string> {
  if (!env.GOOGLE_SERVICE_ACCOUNT_JSON) {
    throw new Error('GOOGLE_SERVICE_ACCOUNT_JSON is missing');
  }
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

export async function processDraw(env: Env) {
  // 1. Fetch official lottery data from Thai GLO API
  const result = await fetchFromGloLottery();
  if (!result) {
    await sendTelegramAlert(env, '⚠️ No official Thai 3D result available from GLO API.');
    return;
  }

  // 2. Get Firebase auth token
  const token = await getFirebaseToken(env);
  const authHeaders = { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' };

  // 3. Check admin mode (non-blocking)
  try {
    const modeRes = await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/mode.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (modeRes.ok) {
      const mode = await modeRes.json();
      if (mode === 'manual') { console.log('Manual mode, skipping.'); return; }
    }
  } catch (_) {}

  // 4. Get current Firebase data for archiving
  let currentResults: Record<string, unknown> | null = null;
  try {
    const cr = await fetch(`${env.FIREBASE_DB_URL}/3d_live_results.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (cr.ok) currentResults = await cr.json() as any;
  } catch (_) {}

  // 5. Build update payload
  const nextDate = calculateNextThaiDrawDate(result.date);
  const updates: Record<string, unknown> = {
    '3d_live_results/winning_number':   result.threeD,
    '3d_live_results/target_draw_date': nextDate,
    '3d_live_results/first_prize':      result.firstPrize,
    '3d_live_results/twod':             result.twoD,
    '3d_live_results/result_date':      result.date,
    '3d_live_results/result_time':      result.session,
    '3d_live_results/is_final':         result.isFinal,
    '3d_live_results/source':           'Official Thai Government Lottery (GLO)',
    '3d_live_results/updated_at':       new Date().toISOString(),
    '3d_lottery_status/state':          'declared',
  };

  const anyRes = currentResults as any;
  if (anyRes?.winning_number && anyRes?.winning_number !== result.threeD) {
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

  // 7. Telegram Alert
  await sendTelegramAlert(env,
    `🇹🇭 Official Thai Government Lottery (GLO)\n\n🎯 3D: ${result.threeD}\n🥇 1st Prize: ${result.firstPrize}\n🔢 2D: ${result.twoD}\n📅 Draw Date: ${result.date}\n⏭️ Next Draw: ${nextDate}`
  );
}

export async function fetchFromGloLottery(): Promise<LotteryResult | null> {
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);
    const res = await fetch('https://www.glo.or.th/api/lottery/getLatestLottery', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
      },
      body: '{}',
      signal: controller.signal
    });
    clearTimeout(timeoutId);

    if (!res.ok) {
      console.error(`GLO API returned HTTP status ${res.status}`);
      return null;
    }

    const json = await res.json() as GloResponse;
    if (!json.status || !json.response?.data?.first?.number?.length) {
      console.error('GLO response missing first prize data', json);
      return null;
    }

    const firstPrize = json.response.data.first.number[0].value.trim();
    if (firstPrize.length < 3) return null;

    const threeD = firstPrize.slice(-3);
    const twoD = json.response.data.last2?.number?.[0]?.value?.trim() ?? firstPrize.slice(-2);
    const drawDate = json.response.date ?? new Date().toISOString().split('T')[0];

    return {
      threeD,
      twoD,
      firstPrize,
      date: drawDate,
      session: 'GLO Official Draw',
      isFinal: true
    };
  } catch (e) {
    console.error('fetchFromGloLottery error:', e);
    return null;
  }
}

async function sendTelegramAlert(env: Env, message: string) {
  const token = getCleanBotToken(env);
  if (!token || !env.TELEGRAM_CHAT_ID) return;
  try {
    await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chat_id: env.TELEGRAM_CHAT_ID, text: message })
    });
  } catch (_) {}
}

export function calculateNextThaiDrawDate(currentDate: string): string {
  const parts = currentDate.split('-');
  let year = parseInt(parts[0], 10);
  let month = parseInt(parts[1], 10); // 1-12
  let day = parseInt(parts[2], 10);

  if (isNaN(year) || isNaN(month) || isNaN(day)) {
    const d = new Date();
    year = d.getFullYear();
    month = d.getMonth() + 1;
    day = d.getDate();
  }

  let nextYear = year;
  let nextMonth = month;
  let nextDay = 16;

  if (day >= 16) {
    nextMonth = month + 1;
    if (nextMonth > 12) {
      nextMonth = 1;
      nextYear += 1;
    }
    nextDay = 1;
  } else {
    nextDay = 16;
  }

  const mm = String(nextMonth).padStart(2, '0');
  const dd = String(nextDay).padStart(2, '0');
  return `${nextYear}-${mm}-${dd}`;
}

// ── Licensing API Handlers ──────────────────────────────────────────────────

function toBase64Url(str: string): string {
  return btoa(str).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}

export async function signLicenseJwt(payload: Record<string, unknown>, secret = '3d-ledger-jwt-secret-2026'): Promise<string> {
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

  const sigBuf = await crypto.subtle.sign('HMAC', key, encoder.encode(`${header}.${encodedPayload}`));
  const sig = btoa(String.fromCharCode(...new Uint8Array(sigBuf)))
    .replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');

  return `${header}.${encodedPayload}.${sig}`;
}

export async function handleLicenseActivate(request: Request, env: Env): Promise<Response> {
  const corsHeaders = {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  };

  try {
    const body = await request.json() as { cd_key?: string; device_fingerprint?: string; device_model?: string };
    const cd_key = (body.cd_key || '').trim().toUpperCase();
    const device_fingerprint = (body.device_fingerprint || '').trim();
    const device_model = (body.device_model || 'Unknown Android Device').trim();

    if (!cd_key || !device_fingerprint) {
      return new Response(JSON.stringify({ error: 'CD-Key နှင့် Device ID ထည့်သွင်းရန် လိုအပ်ပါသည်' }), {
        status: 400, headers: corsHeaders
      });
    }

    const token = await getFirebaseToken(env);
    const keyRes = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cd_key}.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    if (!keyRes.ok) {
      return new Response(JSON.stringify({ error: 'ဆာဗာ ချိတ်ဆက်မှု မအောင်မြင်ပါ' }), {
        status: 500, headers: corsHeaders
      });
    }

    const keyData = await keyRes.json() as any;
    if (!keyData) {
      return new Response(JSON.stringify({ error: 'CD-Key မတွေ့ရှိပါ။ ပြန်လည်စစ်ဆေးပါ' }), {
        status: 404, headers: corsHeaders
      });
    }

    if (keyData.status === 'revoked') {
      return new Response(JSON.stringify({ error: 'ဤလိုင်စင်ကုတ်အား Admin မှ ပိတ်သိမ်းထားပါသည် (Revoked)' }), {
        status: 403, headers: corsHeaders
      });
    }

    // If key is active and already bound to this device, re-issue token
    if (keyData.status === 'active') {
      if (keyData.device_fingerprint === device_fingerprint) {
        const nowSec = Math.floor(Date.now() / 1000);
        let expSec: number | undefined = undefined;
        if (keyData.expires_at) {
          expSec = Math.floor(keyData.expires_at / 1000);
        }
        const jwtPayload: Record<string, unknown> = {
          cd_key,
          device_fingerprint,
          iat: nowSec,
          exp: expSec
        };
        const appToken = await signLicenseJwt(jwtPayload);
        return new Response(JSON.stringify({
          status: 'activated',
          token: appToken,
          expires_at: keyData.expires_at || null
        }), { headers: corsHeaders });
      } else {
        return new Response(JSON.stringify({ error: 'ဤလိုင်စင်ကုတ်အား အခြားဖုန်းတွင် အသုံးပြုထားပြီး ဖြစ်ပါသည်' }), {
          status: 403, headers: corsHeaders
        });
      }
    }

    // Check auto_approve setting
    const autoApprove = await getAutoApproveConfig(env);

    if (autoApprove) {
      // Auto-Approve mode: Activate immediately!
      const now = Date.now();
      let expiresAt: number | null = null;
      let expSec: number | undefined = undefined;
      if (typeof keyData.duration === 'number') {
        expiresAt = now + (keyData.duration * 24 * 60 * 60 * 1000);
        expSec = Math.floor(expiresAt / 1000);
      } else if (keyData.duration === 'trial') {
        expiresAt = now + (3 * 24 * 60 * 60 * 1000);
        expSec = Math.floor(expiresAt / 1000);
      }

      await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cd_key}.json`, {
        method: 'PATCH',
        headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          status: 'active',
          device_fingerprint,
          device_model,
          activated_at: now,
          expires_at: expiresAt,
          approved_by: 'auto'
        })
      });

      const jwtPayload: Record<string, unknown> = {
        cd_key,
        device_fingerprint,
        iat: Math.floor(now / 1000),
        exp: expSec
      };
      const appToken = await signLicenseJwt(jwtPayload);

      // Notify Telegram Admin
      await sendTelegramMessage(env, env.TELEGRAM_CHAT_ID,
        `⚡ <b>[Auto-Approved] ကုတ် အလိုအလျောက် ဖွင့်လှစ်ပြီးပါပြီ</b>\n\n` +
        `🔑 ကုတ်နံပါတ်: <code>${cd_key}</code>\n` +
        `📱 ဖုန်းမော်ဒယ်: <b>${device_model}</b>\n` +
        `⏳ သက်တမ်း: <b>${keyData.duration_label || keyData.duration}</b>\n` +
        `⏰ အချိန်: ${new Date().toLocaleTimeString('my-MM')}`
      );

      return new Response(JSON.stringify({
        status: 'activated',
        token: appToken,
        expires_at: expiresAt
      }), { headers: corsHeaders });
    } else {
      // Manual Telegram Approval mode: Mark pending and notify Admin with inline buttons!
      await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cd_key}.json`, {
        method: 'PATCH',
        headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          status: 'pending_approval',
          device_fingerprint,
          device_model,
          requested_at: Date.now()
        })
      });

      // Send Interactive Prompt to Admin
      await sendTelegramMessage(
        env,
        env.TELEGRAM_CHAT_ID,
        `🔔 <b>ခွင့်ပြုချက် တောင်းခံလွှာ အသစ် (New Activation Request)</b>\n\n` +
        `🔑 ကုတ်နံပါတ်: <code>${cd_key}</code>\n` +
        `📱 ဖုန်းမော်ဒယ်: <b>${device_model}</b>\n` +
        `🆔 ID: <code>${device_fingerprint}</code>\n` +
        `⏳ သက်တမ်း: <b>${keyData.duration_label || keyData.duration}</b>\n` +
        `⏰ အချိန်: ${new Date().toLocaleTimeString('my-MM')}\n\n` +
        `<i>အထက်ပါ ဖုန်းအား ဆော့ဝဲလ် အသုံးပြုခွင့် ပေးမည်လား? 👇</i>`,
        getApprovalKeyboard(cd_key)
      );

      return new Response(JSON.stringify({
        status: 'pending_approval',
        message: 'Admin ၏ အတည်ပြုချက်ကို စောင့်ဆိုင်းနေပါသည် (Waiting for Admin approval via Telegram)'
      }), { headers: corsHeaders });
    }
  } catch (err: any) {
    return new Response(JSON.stringify({ error: 'Activation error', detail: err.message }), {
      status: 500, headers: corsHeaders
    });
  }
}

export async function handleLicenseCheckStatus(request: Request, env: Env): Promise<Response> {
  const corsHeaders = {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
  };

  try {
    const body = await request.json() as { cd_key?: string; device_fingerprint?: string };
    const cd_key = (body.cd_key || '').trim().toUpperCase();
    const device_fingerprint = (body.device_fingerprint || '').trim();

    const token = await getFirebaseToken(env);
    const keyRes = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cd_key}.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    if (!keyRes.ok) {
      return new Response(JSON.stringify({ error: 'Key not found' }), { status: 404, headers: corsHeaders });
    }

    const keyData = await keyRes.json() as any;
    if (!keyData) {
      return new Response(JSON.stringify({ error: 'Key not found' }), { status: 404, headers: corsHeaders });
    }

    if (keyData.status === 'active' && keyData.device_fingerprint === device_fingerprint) {
      let expSec: number | undefined = undefined;
      if (keyData.expires_at) {
        expSec = Math.floor(keyData.expires_at / 1000);
      }
      const jwtPayload: Record<string, unknown> = {
        cd_key,
        device_fingerprint,
        iat: Math.floor(Date.now() / 1000),
        exp: expSec
      };
      const appToken = await signLicenseJwt(jwtPayload);
      return new Response(JSON.stringify({
        status: 'activated',
        token: appToken,
        expires_at: keyData.expires_at || null
      }), { headers: corsHeaders });
    } else if (keyData.status === 'pending_approval') {
      return new Response(JSON.stringify({
        status: 'pending_approval',
        message: 'Admin ၏ အတည်ပြုချက်ကို စောင့်ဆိုင်းနေဆဲ ဖြစ်ပါသည်'
      }), { headers: corsHeaders });
    } else if (keyData.status === 'available') {
      return new Response(JSON.stringify({
        status: 'rejected',
        message: 'Admin မှ အသုံးပြုခွင့် ငြင်းပယ်ခဲ့ပါသည်'
      }), { headers: corsHeaders });
    } else if (keyData.status === 'revoked') {
      return new Response(JSON.stringify({
        status: 'revoked',
        message: 'ဤကုတ်အား ပိတ်သိမ်းထားပါသည်'
      }), { headers: corsHeaders });
    }

    return new Response(JSON.stringify({ status: keyData.status }), { headers: corsHeaders });
  } catch (err: any) {
    return new Response(JSON.stringify({ error: err.message }), { status: 500, headers: corsHeaders });
  }
}

export async function handleLicenseVerify(request: Request, env: Env): Promise<Response> {
  const corsHeaders = {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
  };

  try {
    const body = await request.json() as { cd_key?: string; device_fingerprint?: string };
    const cd_key = (body.cd_key || '').trim().toUpperCase();
    const device_fingerprint = (body.device_fingerprint || '').trim();

    const token = await getFirebaseToken(env);
    const keyRes = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cd_key}.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    if (!keyRes.ok) {
      return new Response(JSON.stringify({ valid: false, reason: 'not_found' }), { headers: corsHeaders });
    }

    const keyData = await keyRes.json() as any;
    if (!keyData || keyData.status === 'revoked') {
      return new Response(JSON.stringify({ valid: false, reason: 'revoked' }), { headers: corsHeaders });
    }

    if (keyData.status === 'active' && keyData.device_fingerprint === device_fingerprint) {
      return new Response(JSON.stringify({ valid: true, expires_at: keyData.expires_at || null }), { headers: corsHeaders });
    }

    return new Response(JSON.stringify({ valid: false, reason: 'mismatch_or_inactive' }), { headers: corsHeaders });
  } catch (_) {
    return new Response(JSON.stringify({ valid: false, reason: 'error' }), { headers: corsHeaders });
  }
}

