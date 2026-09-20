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
  getApprovalKeyboard,
  notifyAllAdmins,
  recordResellerActivationDue,
  calculateTutNumbers,
  broadcastAppUpdate,
  broadcastTextMessage,
  sendTelegramDocument,
  saveAppRelease,
  getAppRelease,
  AppReleaseRecord
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
  source?: string;
  isFinal: boolean;
}

export default {
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(processDraw(env));
  },

  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    const corsHeaders: Record<string, string> = {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    };

    // CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    // Health/ping check
    if (url.pathname === '/health' || url.pathname === '/ping') {
      return new Response(JSON.stringify({ status: 'ok', service: '3d-scraper-worker' }), {
        headers: corsHeaders
      });
    }

    // Direct fetch test for GLO with CORS headers
    if (url.pathname === '/latest-glo') {
      try {
        const result = await fetchFromGloLottery();
        if (!result) {
          return new Response(JSON.stringify({ status: 'error', message: 'Unable to fetch GLO data' }), {
            status: 502,
            headers: corsHeaders
          });
        }
        return new Response(JSON.stringify({ status: 'ok', data: result }), {
          headers: corsHeaders
        });
      } catch (e: any) {
        return new Response(JSON.stringify({ status: 'error', message: e.message }), {
          status: 500,
          headers: corsHeaders
        });
      }
    }

    // Telegram Bot Webhook endpoint
    if (url.pathname === '/webhook' || url.pathname === '/telegram') {
      return handleTelegramWebhook(request, env);
    }

    // Admin Broadcast Announcement Endpoint
    if (url.pathname === '/broadcast' || url.pathname === '/broadcast-update') {
      const msgParam = url.searchParams.get('message') || '';
      const adminChatId = env.TELEGRAM_CHAT_ID;
      const defaultAnnouncement =
        `🚀 <b>3D LEDGER စနစ် အဆင့်မြှင့်တင်မှု အသစ် ထွက်ရှိပါပြီ!</b>\n\n` +
        `✨ <b>အဓိက ပြောင်းလဲမှုများ:</b>\n` +
        `• 📱 Android App: လက်တွေ့ဆန်သော 3D ခလုတ်ဒီဇိုင်း (Physical Tactile Keypad) နှင့် Haptic တုန်ခါမှု တုံ့ပြန်စနစ်\n` +
        `• 🔑 လိုင်စင် စနစ်: ဖုန်းပြောင်းသုံးခွင့် မူဝါဒနှင့် Active ကုတ်များ ချက်ချင်း စစ်ဆေး/ပိတ်သိမ်းနိုင်သော စနစ်\n` +
        `• ⚡ စွမ်းဆောင်ရည်: ပေါက်ဂဏန်းနှင့် တွတ် ၇ ကွက် တွက်ချက်မှု မြန်နှုန်း အဆပေါင်းများစွာ မြှင့်တင်ထားခြင်း\n\n` +
        `ယခုပင် အက်ပ်ကို အဆင့်မြှင့်တင် စမ်းသပ် အသုံးပြုနိုင်ပါပြီ 👇`;

      const sentCount = await broadcastTextMessage(env, adminChatId, msgParam || defaultAnnouncement);
      return new Response(JSON.stringify({ status: 'ok', sent_count: sentCount }), { headers: corsHeaders });
    }

    // Automatically set Telegram Webhook and Register Bot Menu Commands
    if (url.pathname === '/set-webhook' || url.pathname === '/setup-bot') {
      const webhookUrl = url.searchParams.get('url') || `${url.origin}/webhook`;
      const token = getCleanBotToken(env);
      const tgRes = await fetch(
        `https://api.telegram.org/bot${token}/setWebhook?url=${encodeURIComponent(webhookUrl)}&allowed_updates=${encodeURIComponent(JSON.stringify(['message', 'callback_query']))}`
      );
      const tgData = await tgRes.json();

      // Register bot commands list with Telegram
      let cmdData = null;
      try {
        const cmdRes = await fetch(`https://api.telegram.org/bot${token}/setMyCommands`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            commands: [
              { command: 'start', description: '🎁 စတင်ရန် နှင့် ၃ ရက် Trial ရယူရန်' },
              { command: 'menu', description: '📱 ပင်မ မီနူးနှင့် ခလုတ်များ' },
              { command: 'live', description: '🇹🇭 ထိုင်း 3D တိုက်ရိုက် ပေါက်မဲ' },
              { command: 'tut', description: '🔢 တွတ်ဂဏန်းများ တွက်ရန်' },
              { command: 'check', description: '🎯 ပေါက်မဲ စစ်ဆေးရန်' },
              { command: 'buy', description: '🛒 လိုင်စင် ဝယ်ယူရန်' },
              { command: 'admin', description: '👑 Admin စီမံခန့်ခွဲမှု မီနူး' },
              { command: 'id', description: '🆔 သင့် Telegram ID ကြည့်ရန်' }
            ]
          })
        });
        cmdData = await cmdRes.json();
      } catch (_) {}

      // Enable persistent Menu Button
      let menuData = null;
      try {
        const menuRes = await fetch(`https://api.telegram.org/bot${token}/setChatMenuButton`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            menu_button: { type: 'commands' }
          })
        });
        menuData = await menuRes.json();
      } catch (_) {}

      return new Response(JSON.stringify({ status: 'ok', webhookUrl, telegram: tgData, commands: cmdData, menuButton: menuData }, null, 2), {
        headers: corsHeaders
      });
    }

    // Query Telegram Webhook status
    if (url.pathname === '/webhook-info') {
      const token = getCleanBotToken(env);
      const tgRes = await fetch(`https://api.telegram.org/bot${token}/getWebhookInfo`);
      const tgData = await tgRes.json();
      return new Response(JSON.stringify(tgData, null, 2), {
        headers: corsHeaders
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
        headers: corsHeaders
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

    // Public App Release Endpoint (for in-app update checking)
    if (url.pathname === '/api/app/latest' || url.pathname === '/app/latest') {
      const release = await getAppRelease(env);
      return new Response(JSON.stringify({
        status: 'ok',
        release: release || null
      }), { headers: corsHeaders });
    }

    // App Publish Endpoint (for GitHub Actions or CLI release automation)
    if ((url.pathname === '/api/app/publish' || url.pathname === '/app/publish') && request.method === 'POST') {
      try {
        const body = await request.json() as any;
        if (!body || (!body.file_id && !body.download_url)) {
          return new Response(JSON.stringify({ error: 'file_id or download_url is required' }), {
            status: 400, headers: corsHeaders
          });
        }

        let fileId = body.file_id || '';
        const downloadUrl = body.download_url || '';
        const versionName = body.version_name || 'v1.0.100';
        const versionCode = body.version_code || 100;
        const releaseNotes = body.release_notes || 'Official Production Release from GitHub';

        // If file_id is empty but download_url is provided, send to Admin chat to convert into native Telegram file_id
        if (!fileId && downloadUrl) {
          try {
            const tgRes = await sendTelegramDocument(
              env,
              env.TELEGRAM_CHAT_ID,
              downloadUrl,
              `📦 <b>[GitHub Auto-Release Sync]</b>\n\n` +
              `🔖 <b>ဗားရှင်း:</b> <b>${versionName}</b>\n` +
              `📝 <b>မှတ်ချက်:</b> ${releaseNotes}\n\n` +
              `<i>GitHub Releases မှ Telegram Bot သို့ အလိုအလျောက် ရောက်ရှိလာပါသည်</i>`
            );
            if (tgRes?.result?.document?.file_id) {
              fileId = tgRes.result.document.file_id;
              if (tgRes.result.document.file_size && !body.file_size) {
                body.file_size = tgRes.result.document.file_size;
              }
            }
          } catch (_) {}
        }

        const releaseRecord: AppReleaseRecord = {
          file_id: fileId,
          file_name: body.file_name || `3D_Ledger_${versionName}.apk`,
          file_size: body.file_size || 9284449,
          version_name: versionName,
          version_code: versionCode,
          download_url: downloadUrl,
          release_notes: releaseNotes,
          uploaded_by: body.uploaded_by || 'GitHub Actions',
          uploaded_at: Date.now()
        };

        await saveAppRelease(env, releaseRecord);

        // If broadcast requested, notify users
        let broadcastCount = 0;
        if (body.broadcast === true) {
          broadcastCount = await broadcastAppUpdate(env, env.TELEGRAM_CHAT_ID);
        }

        return new Response(JSON.stringify({
          status: 'ok',
          message: 'App release published and synchronized to Telegram',
          release: releaseRecord,
          broadcastCount
        }), { headers: corsHeaders });
      } catch (err: any) {
        return new Response(JSON.stringify({ status: 'error', error: err.message }), {
          status: 500, headers: corsHeaders
        });
      }
    }

    // Sync Current Release APK to Telegram to obtain native file_id
    if ((url.pathname === '/api/app/sync-telegram' || url.pathname === '/app/sync-telegram') && request.method === 'POST') {
      try {
        const release = await getAppRelease(env);
        if (!release || !release.download_url) {
          return new Response(JSON.stringify({ error: 'No release or download_url found in database' }), {
            status: 400, headers: corsHeaders
          });
        }

        const tgRes = await sendTelegramDocument(
          env,
          env.TELEGRAM_CHAT_ID,
          release.download_url,
          `📦 <b>[Official 3D Ledger Production APK]</b>\n\n` +
          `🔖 <b>ဗားရှင်း:</b> <b>${release.version_name}</b>\n` +
          `📝 <b>မှတ်ချက်:</b> ${release.release_notes}\n\n` +
          `<i>ဆာဗာမှ Telegram CDN သို့ အောင်မြင်စွာ တင်သွင်းပြီးဖြစ်ပါသည်</i>`,
          undefined,
          'HTML',
          release.file_name || `3D_Ledger_${release.version_name}.apk`
        );

        if (tgRes?.result?.document?.file_id) {
          release.file_id = tgRes.result.document.file_id;
          await saveAppRelease(env, release);
          return new Response(JSON.stringify({
            status: 'ok',
            message: 'APK successfully uploaded to Telegram servers and file_id saved',
            file_id: release.file_id,
            release
          }), { headers: corsHeaders });
        } else {
          return new Response(JSON.stringify({
            status: 'error',
            message: 'Telegram API did not return document file_id',
            telegram_response: tgRes
          }), { status: 502, headers: corsHeaders });
        }
      } catch (err: any) {
        return new Response(JSON.stringify({ status: 'error', error: err.message }), {
          status: 500, headers: corsHeaders
        });
      }
    }

    // Manual draw processing / Apply GLO trigger
    if (url.pathname === '/process-draw' || url.pathname === '/apply-glo' || (url.pathname === '/' && request.method === 'POST')) {
      try {
        const drawResult = await processDraw(env);
        return new Response(JSON.stringify({ status: 'ok', message: 'Official GLO Draw processed and synced to Firebase', result: drawResult }), {
          headers: corsHeaders
        });
      } catch (e: any) {
        return new Response(JSON.stringify({ status: 'error', message: e.message }), {
          status: 500, headers: corsHeaders
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
  const tut = calculateTutNumbers(result.threeD);
  const updates: Record<string, unknown> = {
    '3d_live_results/winning_number':   result.threeD,
    '3d_live_results/target_draw_date': nextDate,
    '3d_live_results/first_prize':      result.firstPrize,
    '3d_live_results/twod':             result.twoD,
    '3d_live_results/result_date':      result.date,
    '3d_live_results/result_time':      result.session,
    '3d_live_results/is_final':         result.isFinal,
    '3d_live_results/source':           result.source || result.session || 'Official Thai Government Lottery (GLO)',
    '3d_live_results/updated_at':       new Date().toISOString(),
    '3d_lottery_status/state':          'declared',
    '3d_live_results/tut_permutations': tut.permutations,
    '3d_live_results/tut_near_misses':  tut.nearMisses,
    '3d_live_results/tut_all':          tut.allTut,
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
  const sourceName = result.source || result.session || 'Official Thai Lottery';
  await sendTelegramAlert(env,
    `🇹🇭 <b>Thai 3D Lottery Result (${sourceName})</b>\n\n🎯 <b>3D ပေါက်ဂဏန်း: ${result.threeD}</b>\n🥇 1st Prize: ${result.firstPrize}\n🔢 2D: ${result.twoD}\n📅 Draw Date: ${result.date}\n⏭️ Next Draw: ${nextDate}\n\n🔄 တွတ်ဂဏန်းများ: ${tut.allTut.join(', ')}`
  );

  return result;
}

export async function fetchFastThaiLottery(): Promise<LotteryResult | null> {
  // 1. Tier 1: Sanook Live Real-Time Feed (Fastest: published at ~3:15 PM MMT directly from live TV draw)
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);
    const res = await fetch('https://news.sanook.com/lotto/', {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
      },
      signal: controller.signal
    });
    clearTimeout(timeoutId);

    if (res.ok) {
      const html = await res.text();
      let firstPrize: string | null = null;
      let twoD: string | null = null;
      let drawDate: string | null = null;

      // Method A: Check structured JSON-LD articleBody
      const articleMatch = html.match(/"articleBody":\s*"([^"]+)"/);
      if (articleMatch) {
        const decoded = articleMatch[1].replace(/\\r\\n/g, '\n').replace(/\\n/g, '\n');
        const lines = decoded.split('\n').map(l => l.trim()).filter(Boolean);
        for (let i = 0; i < lines.length; i++) {
          if (lines[i].includes('รางวัลที่ 1') && lines[i+1]) {
            const m = lines[i+1].match(/\d{6}/);
            if (m) firstPrize = m[0];
          }
          if (lines[i].includes('รางวัลเลขท้าย 2 ตัว') && lines[i+1]) {
            const m = lines[i+1].match(/\d{2}/);
            if (m) twoD = m[0];
          }
        }
      }

      // Method B: Fallback to HTML classes
      if (!firstPrize) {
        const m1 = html.match(/<strong[^>]*class="[^"]*lotto__number[^"]*"[^>]*>(\d{6})<\/strong>/i);
        if (m1) firstPrize = m1[1];
      }
      if (!twoD) {
        const m2 = html.match(/2 ตัว<\/em>[\s\S]*?<strong[^>]*class="[^"]*lotto__number[^"]*"[^>]*>(\d{2})<\/strong>/i)
          || html.match(/<strong[^>]*class="[^"]*lotto__number[^"]*"[^>]*>(\d{2})<\/strong>[\s\S]*?2 ตัว/i);
        if (m2) twoD = m2[1];
      }

      const datePublished = html.match(/"datePublished":\s*"([^"]+)"/);
      if (datePublished) {
        drawDate = datePublished[1].slice(0, 10);
      }

      if (firstPrize && firstPrize.length >= 3) {
        return {
          threeD: firstPrize.slice(-3),
          twoD: twoD || firstPrize.slice(-2),
          firstPrize,
          date: drawDate || new Date().toISOString().split('T')[0],
          session: 'Sanook Live Realtime (3:15 PM MMT)',
          source: 'Sanook Live Realtime Feed (3:15 PM MMT အမြန်ဆုံး)',
          isFinal: true
        };
      }
    }
  } catch (e) {
    console.error('Sanook live scraper error:', e);
  }

  // 2. Tier 2: Direct Official GLO Thailand API (Updates ~3:30 PM MMT once official certificates are signed)
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

    if (res.ok) {
      const json = await res.json() as GloResponse;
      if (json.status && json.response?.data?.first?.number?.length) {
        const firstPrize = json.response.data.first.number[0].value.trim();
        if (firstPrize.length >= 3) {
          const threeD = firstPrize.slice(-3);
          const twoD = json.response.data.last2?.number?.[0]?.value?.trim() ?? firstPrize.slice(-2);
          const drawDate = json.response.date ?? new Date().toISOString().split('T')[0];

          return {
            threeD,
            twoD,
            firstPrize,
            date: drawDate,
            session: 'GLO Official Draw (3:30 PM MMT)',
            source: 'Official Thai Government Lottery (GLO)',
            isFinal: true
          };
        }
      }
    }
  } catch (e) {
    console.error('Direct GLO API error:', e);
  }

  // 3. Tier 3: Rayriffy Community Lottery API (Fallback mirror)
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);
    const res = await fetch('https://lotto.api.rayriffy.com/latest', {
      headers: { 'Accept': 'application/json' },
      signal: controller.signal
    });
    clearTimeout(timeoutId);

    if (res.ok) {
      const json = await res.json() as any;
      const first = json.response?.data?.first?.number?.[0]?.value?.trim() || '';
      const last2 = json.response?.data?.last2?.number?.[0]?.value?.trim() || '';
      const date = json.response?.date || '';
      if (first && first.length >= 3) {
        return {
          threeD: first.slice(-3),
          twoD: last2 || first.slice(-2),
          firstPrize: first,
          date: date,
          session: 'Rayriffy Mirror Draw',
          source: 'Rayriffy Mirror',
          isFinal: true
        };
      }
    }
  } catch (e) {
    console.error('Rayriffy fallback error:', e);
  }

  return null;
}

export const fetchFromGloLottery = fetchFastThaiLottery;

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

    // Expiration check: If key has expired, reject
    if (keyData.expires_at && Date.now() >= keyData.expires_at) {
      return new Response(JSON.stringify({ error: 'ဤလိုင်စင်ကုတ် သက်တမ်း ကုန်ဆုံးသွားပါပြီ။ ဆက်လက်အသုံးပြုရန် လိုင်စင် အသစ် ဝယ်ယူပါ' }), {
        status: 403, headers: corsHeaders
      });
    }

    // If key is active:
    if (keyData.status === 'active') {
      if (keyData.device_fingerprint === device_fingerprint) {
        // Re-issue token to current bound device
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
        // A different device is trying to activate with this active key!
        const isDeviceChangeable = keyData.device_changeable === true;
        if (!isDeviceChangeable) {
          return new Response(JSON.stringify({
            error: 'ဤလိုင်စင်ကုတ်အား အခြားဖုန်းတွင် အသုံးပြုထားပြီး ဖြစ်ပါသည်။ ဤအစီအစဉ်သည် စက်ပြောင်းလဲအသုံးပြုခွင့် မရှိပါ (Non-device changeable)'
          }), { status: 403, headers: corsHeaders });
        }

        // It is device changeable (e.g. 1-Year Plan)!
        // Seamlessly migrate remaining days to the new device without admin permission, and remove old device!
        const now = Date.now();
        const oldFingerprint = keyData.device_fingerprint;
        const oldModel = keyData.device_model;
        const remainingDays = keyData.expires_at ? Math.max(1, Math.ceil((keyData.expires_at - now) / (1000 * 60 * 60 * 24))) : 365;

        const migrationUpdates = {
          device_fingerprint,
          device_model,
          previous_device_fingerprint: oldFingerprint,
          previous_device_model: oldModel,
          last_migrated_at: now,
          migration_count: (keyData.migration_count || 0) + 1
        };

        await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cd_key}.json`, {
          method: 'PATCH',
          headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
          body: JSON.stringify(migrationUpdates)
        });

        // Notify Admins on Telegram about device change
        await notifyAllAdmins(env,
          `🔄 <b>[စက် ပြောင်းလဲ အသုံးပြုခြင်း] Device Migrated</b>\n\n` +
          `🔑 ကုတ်နံပါတ်: <code>${cd_key}</code>\n` +
          `⏳ သက်တမ်း: <b>${keyData.duration_label || '၁ နှစ် (Device Changeable)'}</b>\n` +
          `📅 ကျန်ရှိသော သက်တမ်း: <b>${remainingDays} ရက်</b>\n\n` +
          `📱 <b>စက်အဟောင်း (ဖယ်ရှားပြီး):</b> ${oldModel || 'မသိ'} (<code>${oldFingerprint}</code>)\n` +
          `📱 <b>စက်အသစ် (ခွင့်ပြုပြီး):</b> ${device_model} (<code>${device_fingerprint}</code>)\n` +
          `⏰ ပြောင်းလဲချိန်: ${new Date().toLocaleTimeString('my-MM')}\n\n` +
          `<i>မှတ်ချက်: ၁ နှစ် သက်တမ်း ကုတ်ဖြစ်သဖြင့် စက်အသစ်သို့ လက်ကျန်ရက်များနှင့်အတူ အလိုအလျောက် လွှဲပြောင်းပေးလိုက်ပါသည်။ စက်အဟောင်းတွင် အသုံးပြုခွင့် ရပ်ဆိုင်းသွားပါမည်။</i>`
        );

        if (keyData.generated_by_reseller_id) {
          await sendTelegramMessage(env, keyData.generated_by_reseller_id,
            `🔄 <b>[စက် ပြောင်းလဲ အသုံးပြုခြင်း]</b>\n\n` +
            `🔑 ကုတ်နံပါတ်: <code>${cd_key}</code>\n` +
            `📱 စက်အဟောင်း: ${oldModel || 'မသိ'}\n` +
            `📱 စက်အသစ်: ${device_model}\n` +
            `📅 ကျန်ရှိသော သက်တမ်း: <b>${remainingDays} ရက်</b>`
          );
        }

        const expSec = keyData.expires_at ? Math.floor(keyData.expires_at / 1000) : undefined;
        const jwtPayload: Record<string, unknown> = {
          cd_key,
          device_fingerprint,
          iat: Math.floor(now / 1000),
          exp: expSec
        };
        const appToken = await signLicenseJwt(jwtPayload);

        return new Response(JSON.stringify({
          status: 'activated',
          token: appToken,
          expires_at: keyData.expires_at || null,
          device_migrated: true,
          remaining_days: remainingDays,
          message: `စက်အသစ်သို့ အောင်မြင်စွာ ပြောင်းလဲလိုက်ပါပြီ။ လက်ကျန်သက်တမ်း ${remainingDays} ရက် ရရှိပါသည်။`
        }), { headers: corsHeaders });
      }
    }

    // Check auto_approve setting or Free 3-Day Trial
    const autoApprove = await getAutoApproveConfig(env);
    const isTrial = keyData.duration === 'trial' || keyData.plan_id === 'trial_3d';

    if (autoApprove || isTrial) {
      // Auto-Approve mode or Free Trial: Activate immediately!
      const now = Date.now();
      let expiresAt: number | null = null;
      let expSec: number | undefined = undefined;
      if (isTrial) {
        expiresAt = now + (72 * 60 * 60 * 1000); // exactly 72 hours (3 days)
        expSec = Math.floor(expiresAt / 1000);
      } else if (typeof keyData.duration === 'number') {
        expiresAt = now + (keyData.duration * 24 * 60 * 60 * 1000);
        expSec = Math.floor(expiresAt / 1000);
      }

      const isChangeable = keyData.device_changeable ?? (keyData.duration === 365 || keyData.plan_id === 'one_year');

      await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cd_key}.json`, {
        method: 'PATCH',
        headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          status: 'active',
          device_fingerprint,
          device_model,
          activated_at: now,
          expires_at: expiresAt,
          device_changeable: isChangeable,
          approved_by: isTrial ? 'free_trial' : 'auto'
        })
      });

      const jwtPayload: Record<string, unknown> = {
        cd_key,
        device_fingerprint,
        iat: Math.floor(now / 1000),
        exp: expSec
      };
      const appToken = await signLicenseJwt(jwtPayload);

      // Record reseller activation due accounting if generated by reseller
      if (keyData.generated_by_reseller_id) {
        await recordResellerActivationDue(env, {
          ...keyData,
          cd_key,
          status: 'active',
          activated_at: now,
          expires_at: expiresAt,
          device_fingerprint,
          device_model
        });
      }

      // Notify Telegram Admins
      await notifyAllAdmins(env,
        `⚡ <b>[${isTrial ? 'Trial-Activated' : 'Auto-Approved'}] ကုတ် စတင် အသုံးပြုပါပြီ</b>\n\n` +
        `🔑 ကုတ်နံပါတ်: <code>${cd_key}</code>\n` +
        `📱 ဖုန်းမော်ဒယ်: <b>${device_model}</b>\n` +
        `⏳ သက်တမ်း: <b>${keyData.duration_label || keyData.duration}</b>\n` +
        (keyData.generated_by_reseller_id ? `👤 အရောင်းကိုယ်စားလှယ်: <b>${keyData.reseller_name || keyData.generated_by_reseller_id}</b>\n` : '') +
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

      const resellerInfo = (keyData.reseller_name || keyData.generated_by_reseller_id)
        ? `\n👤 <b>အရောင်းကိုယ်စားလှယ်:</b> ${keyData.reseller_name || keyData.generated_by_reseller_id} (<code>${keyData.generated_by_reseller_id}</code>)`
        : '';

      const approvalPrompt = `🔔 <b>ခွင့်ပြုချက် တောင်းခံလွှာ အသစ် (New Activation Request)</b>\n\n` +
        `🔑 ကုတ်နံပါတ်: <code>${cd_key}</code>\n` +
        `📱 ဖုန်းမော်ဒယ်: <b>${device_model}</b>\n` +
        `🆔 ID: <code>${device_fingerprint}</code>\n` +
        `⏳ သက်တမ်း: <b>${keyData.duration_label || keyData.duration}</b>` +
        resellerInfo + `\n` +
        `⏰ အချိန်: ${new Date().toLocaleTimeString('my-MM')}\n\n` +
        `<i>အထက်ပါ ဖုန်းအား ဆော့ဝဲလ် အသုံးပြုခွင့် ပေးမည်လား? 👇</i>`;

      // Send Interactive Prompt to all Admins
      await notifyAllAdmins(
        env,
        approvalPrompt,
        getApprovalKeyboard(cd_key)
      );

      // If generated by reseller, also send approval prompt to reseller
      if (keyData.generated_by_reseller_id) {
        const resellerPrompt = `🔔 <b>သင်ထုတ်ယူထားသော ကုတ်အတွက် ခွင့်ပြုချက် တောင်းခံလွှာ</b>\n\n` +
          `🔑 ကုတ်နံပါတ်: <code>${cd_key}</code>\n` +
          `📱 ဖုန်းမော်ဒယ်: <b>${device_model}</b>\n` +
          `⏳ သက်တမ်း: <b>${keyData.duration_label || keyData.duration}</b>\n` +
          `⏰ အချိန်: ${new Date().toLocaleTimeString('my-MM')}\n\n` +
          `<i>ဝယ်ယူသူ ဖုန်းအား ဆော့ဝဲလ် အသုံးပြုခွင့် ဖွင့်ပေးမည်လား? 👇</i>`;
        await sendTelegramMessage(
          env,
          keyData.generated_by_reseller_id,
          resellerPrompt,
          getApprovalKeyboard(cd_key)
        );
      }

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
      return new Response(JSON.stringify({
        valid: false,
        reason: 'revoked',
        message: 'ဤလိုင်စင်ကုတ်အား ပိတ်သိမ်းထားပါသည် (Revoked)'
      }), { headers: corsHeaders });
    }

    // Expiration Check
    if (keyData.expires_at && Date.now() >= keyData.expires_at) {
      return new Response(JSON.stringify({
        valid: false,
        reason: 'expired',
        message: 'လိုင်စင် သက်တမ်း ကုန်ဆုံးသွားပါပြီ။ ဆက်လက်အသုံးပြုရန် လိုင်စင် အသစ် ဝယ်ယူပါ'
      }), { headers: corsHeaders });
    }

    // Bound Device Check
    if (keyData.status === 'active' && keyData.device_fingerprint === device_fingerprint) {
      return new Response(JSON.stringify({
        valid: true,
        expires_at: keyData.expires_at || null
      }), { headers: corsHeaders });
    }

    // If transferred to another device
    if (keyData.previous_device_fingerprint === device_fingerprint) {
      return new Response(JSON.stringify({
        valid: false,
        reason: 'device_transferred',
        message: 'ဤလိုင်စင်ကုတ်အား အခြားဖုန်းသို့ ပြောင်းရွှေ့အသုံးပြုလိုက်ပါပြီ။ ဆက်လက်အသုံးပြုရန် လိုင်စင် အသစ် ဝယ်ယူပါ'
      }), { headers: corsHeaders });
    }

    return new Response(JSON.stringify({
      valid: false,
      reason: 'mismatch_or_inactive',
      message: 'လိုင်စင် မကိုက်ညီပါ သို့မဟုတ် အသုံးပြုခွင့် မရှိပါ'
    }), { headers: corsHeaders });
  } catch (_) {
    return new Response(JSON.stringify({ valid: false, reason: 'error' }), { headers: corsHeaders });
  }
}

