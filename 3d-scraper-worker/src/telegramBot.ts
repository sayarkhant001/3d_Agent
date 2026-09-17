/**
 * Admin-Only Telegram Bot Engine for 3D Ledger & License Control
 * Strict Admin Access: Only TELEGRAM_CHAT_ID has management authorization.
 * Features: Key Generation, Monitoring, Revocation, Sales Reporting,
 * Dual Authorization (Manual Telegram Approval vs Auto-Approve),
 * and Manual Winning Number & Tut Declaration.
 */

import { Env, fetchFromGloLottery, getFirebaseToken } from './index';

// ── Telegram Update Data Types ────────────────────────────────────────────────

export interface TelegramUser {
  id: number;
  is_bot: boolean;
  first_name: string;
  last_name?: string;
  username?: string;
}

export interface TelegramChat {
  id: number;
  type: string;
  title?: string;
  username?: string;
}

export interface InlineKeyboardButton {
  text: string;
  callback_data?: string;
  url?: string;
}

export interface InlineKeyboardMarkup {
  inline_keyboard: InlineKeyboardButton[][];
}

export interface TelegramMessage {
  message_id: number;
  from?: TelegramUser;
  chat: TelegramChat;
  date: number;
  text?: string;
}

export interface TelegramCallbackQuery {
  id: string;
  from: TelegramUser;
  message?: TelegramMessage;
  data?: string;
}

export interface TelegramUpdate {
  update_id: number;
  message?: TelegramMessage;
  callback_query?: TelegramCallbackQuery;
}

// ── License Key Record ────────────────────────────────────────────────────────

export interface LicenseKeyRecord {
  cd_key: string;
  duration: 'trial' | number | 'lifetime';
  duration_label: string;
  price: number;
  status: 'available' | 'pending_approval' | 'active' | 'revoked';
  created_at: number;
  device_fingerprint?: string;
  device_model?: string;
  requested_at?: number;
  activated_at?: number;
  expires_at?: number | null;
  approved_by?: string;
  revoked_at?: number;
}

// ── တွတ် (Tut) Calculation Engine ─────────────────────────────────────────────

export interface TutResult {
  winningNumber: string;
  permutations: string[];
  nearMisses: string[];
  allTut: string[];
}

export function calculateTutNumbers(winningNumber: string): TutResult {
  if (!/^\d{3}$/.test(winningNumber)) {
    return { winningNumber, permutations: [], nearMisses: [], allTut: [] };
  }

  // 1. Permutations (အပြန်များ - anagrams excluding self)
  const digits = winningNumber.split('');
  const perms = new Set<string>();
  const permute = (arr: string[], m: string[] = []) => {
    if (arr.length === 0) {
      perms.add(m.join(''));
    } else {
      for (let i = 0; i < arr.length; i++) {
        const curr = arr.slice();
        const next = curr.splice(i, 1);
        permute(curr.slice(), m.concat(next));
      }
    }
  };
  permute(digits);
  perms.delete(winningNumber);
  const permutations = Array.from(perms).sort();

  // 2. Near-misses (ကပ်သီး +1, -1 with 000-999 cyclic boundary)
  const numInt = parseInt(winningNumber, 10);
  const minus1 = String(numInt === 0 ? 999 : numInt - 1).padStart(3, '0');
  const plus1 = String(numInt === 999 ? 0 : numInt + 1).padStart(3, '0');
  const nearMisses = [minus1, plus1].filter(n => n !== winningNumber);

  // 3. Combined Tut (တွတ်)
  const allTut = Array.from(new Set([...permutations, ...nearMisses])).sort();

  return { winningNumber, permutations, nearMisses, allTut };
}

// ── CD Key Generator (32 hex/alphanumeric chars in 8 blocks) ──────────────────

export function generateCdKey(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // omit ambiguous 0, O, 1, I
  const randomBytes = new Uint8Array(32);
  crypto.getRandomValues(randomBytes);
  const blocks: string[] = [];
  let byteIdx = 0;
  for (let b = 0; b < 8; b++) {
    let block = '';
    for (let i = 0; i < 4; i++) {
      block += chars[randomBytes[byteIdx++] % chars.length];
    }
    blocks.push(block);
  }
  return blocks.join('-');
}

// ── Telegram API Helpers ──────────────────────────────────────────────────────

export function getCleanBotToken(env: Env): string {
  const t = (env.TELEGRAM_BOT_TOKEN || '').trim();
  return t.startsWith('bot') ? t.substring(3) : t;
}

export function isAdmin(senderId: number | string, env: Env): boolean {
  return String(senderId).trim() === String(env.TELEGRAM_CHAT_ID).trim();
}

export async function sendTelegramMessage(
  env: Env,
  chatId: number | string,
  text: string,
  replyMarkup?: InlineKeyboardMarkup,
  parseMode: 'HTML' | 'MarkdownV2' | 'Markdown' = 'HTML'
) {
  const token = getCleanBotToken(env);
  if (!token) return null;
  const payload: Record<string, unknown> = {
    chat_id: chatId,
    text: text,
    parse_mode: parseMode,
    disable_web_page_preview: true,
  };
  if (replyMarkup) {
    payload.reply_markup = replyMarkup;
  }

  const res = await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return res.json();
}

export async function editTelegramMessage(
  env: Env,
  chatId: number | string,
  messageId: number,
  text: string,
  replyMarkup?: InlineKeyboardMarkup,
  parseMode: 'HTML' | 'MarkdownV2' | 'Markdown' = 'HTML'
) {
  const token = getCleanBotToken(env);
  if (!token) return null;
  const payload: Record<string, unknown> = {
    chat_id: chatId,
    message_id: messageId,
    text: text,
    parse_mode: parseMode,
    disable_web_page_preview: true,
  };
  if (replyMarkup) {
    payload.reply_markup = replyMarkup;
  }

  const res = await fetch(`https://api.telegram.org/bot${token}/editMessageText`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return res.json();
}

export async function answerCallbackQuery(env: Env, callbackQueryId: string, text?: string, showAlert = false) {
  const token = getCleanBotToken(env);
  if (!token) return;
  await fetch(`https://api.telegram.org/bot${token}/answerCallbackQuery`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ callback_query_id: callbackQueryId, text, show_alert: showAlert }),
  });
}

// ── Firebase Helpers for Licensing ────────────────────────────────────────────

export async function getAutoApproveConfig(env: Env): Promise<boolean> {
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/config/auto_approve.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (res.ok) {
      const val = await res.json();
      return val === true;
    }
  } catch (_) {}
  return false;
}

export async function setAutoApproveConfig(env: Env, enabled: boolean): Promise<boolean> {
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/config/auto_approve.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(enabled)
    });
    return res.ok;
  } catch (_) {
    return false;
  }
}

export async function getAllLicenseKeys(env: Env): Promise<Record<string, LicenseKeyRecord>> {
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json();
      return (data && typeof data === 'object') ? data : {};
    }
  } catch (_) {}
  return {};
}

export async function saveLicenseKey(env: Env, keyRecord: LicenseKeyRecord): Promise<boolean> {
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${keyRecord.cd_key}.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(keyRecord)
    });
    return res.ok;
  } catch (_) {
    return false;
  }
}

export async function approveLicenseKey(env: Env, cdKey: string): Promise<{ ok: boolean; record?: LicenseKeyRecord }> {
  try {
    const token = await getFirebaseToken(env);
    const getRes = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cdKey}.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (!getRes.ok) return { ok: false };
    const record = await getRes.json() as LicenseKeyRecord | null;
    if (!record) return { ok: false };

    const now = Date.now();
    let expiresAt: number | null = null;
    if (typeof record.duration === 'number') {
      expiresAt = now + (record.duration * 24 * 60 * 60 * 1000);
    } else if (record.duration === 'trial') {
      expiresAt = now + (3 * 24 * 60 * 60 * 1000);
    }

    const updates: Partial<LicenseKeyRecord> = {
      status: 'active',
      approved_by: 'telegram_admin',
      activated_at: now,
      expires_at: expiresAt,
      device_fingerprint: record.device_fingerprint || 'approved_by_admin'
    };

    const patchRes = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cdKey}.json`, {
      method: 'PATCH',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(updates)
    });

    return { ok: patchRes.ok, record: { ...record, ...updates } };
  } catch (_) {
    return { ok: false };
  }
}

export async function rejectLicenseKey(env: Env, cdKey: string): Promise<boolean> {
  try {
    const token = await getFirebaseToken(env);
    const patchRes = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cdKey}.json`, {
      method: 'PATCH',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        status: 'available',
        device_fingerprint: null,
        device_model: null,
        requested_at: null
      })
    });
    return patchRes.ok;
  } catch (_) {
    return false;
  }
}

export async function revokeLicenseKey(env: Env, cdKey: string): Promise<boolean> {
  try {
    const token = await getFirebaseToken(env);
    const patchRes = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cdKey}.json`, {
      method: 'PATCH',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        status: 'revoked',
        revoked_at: Date.now()
      })
    });
    return patchRes.ok;
  } catch (_) {
    return false;
  }
}

// ── Admin Keyboards ───────────────────────────────────────────────────────────

export function getAdminMainMenuKeyboard(autoApprove: boolean, pendingCount = 0): InlineKeyboardMarkup {
  const pendingLabel = pendingCount > 0 ? `⏳ စောင့်ဆိုင်းဆဲ (${pendingCount}) 🔔` : `⏳ စောင့်ဆိုင်းဆဲ (၀)`;
  const autoLabel = autoApprove ? '⚡ Auto-Approve: ဖွင့်ထားသည် ✅' : '⚡ Auto-Approve: ပိတ်ထားသည် ❌';

  return {
    inline_keyboard: [
      [
        { text: '➕ ကုတ်အသစ် ထုတ်ရန်', callback_data: 'm_gen_menu' },
        { text: '📋 အသုံးပြုမှု စောင့်ကြည့်', callback_data: 'm_keys_active' }
      ],
      [
        { text: autoLabel, callback_data: 'm_toggle_auto' },
        { text: pendingLabel, callback_data: 'm_keys_pending' }
      ],
      [
        { text: '📊 အရောင်း အစီရင်ခံစာ', callback_data: 'm_report' },
        { text: '🚫 ကုတ် ပိတ်သိမ်းရန်', callback_data: 'm_revoke_list' }
      ],
      [
        { text: '🎯 ပေါက်မဲ လက်ဖြင့် သတ်မှတ်', callback_data: 'm_declare_menu' },
        { text: '📦 အပတ်စဉ် ကြည့်ရှု', callback_data: 'm_batch' }
      ],
      [
        { text: '🔄 မီနူး အသစ်ပြန်ဖွင့် (Refresh)', callback_data: 'm_refresh' }
      ]
    ]
  };
}

export function getKeyGenKeyboard(): InlineKeyboardMarkup {
  return {
    inline_keyboard: [
      [
        { text: '⏱️ ၇ ရက် (7 Days) - 5,000 ကျပ်', callback_data: 'gen:7:5000:7_ရက်' }
      ],
      [
        { text: '📅 ၃၀ ရက် (1 Month) - 15,000 ကျပ်', callback_data: 'gen:30:15000:30_ရက်' }
      ],
      [
        { text: '🗓️ ၉၀ ရက် (3 Months) - 35,000 ကျပ်', callback_data: 'gen:90:35000:90_ရက်' }
      ],
      [
        { text: '📆 ၁ နှစ် (1 Year) - 100,000 ကျပ်', callback_data: 'gen:365:100000:1_နှစ်' }
      ],
      [
        { text: '♾️ တစ်သက်တာ (Lifetime) - 250,000 ကျပ်', callback_data: 'gen:lifetime:250000:တစ်သက်တာ' }
      ],
      [
        { text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }
      ]
    ]
  };
}

export function getApprovalKeyboard(cdKey: string): InlineKeyboardMarkup {
  return {
    inline_keyboard: [
      [
        { text: '✅ ခွင့်ပြုမည် (Approve)', callback_data: `act_app:${cdKey}` },
        { text: '❌ ငြင်းပယ်မည် (Reject)', callback_data: `act_rej:${cdKey}` }
      ]
    ]
  };
}

// ── Admin Message Builders ────────────────────────────────────────────────────

export async function buildAdminDashboardMessage(env: Env): Promise<{ text: string; keyboard: InlineKeyboardMarkup }> {
  const autoApprove = await getAutoApproveConfig(env);
  const keys = await getAllLicenseKeys(env);
  const list = Object.values(keys);

  const activeCount = list.filter(k => k.status === 'active').length;
  const pendingCount = list.filter(k => k.status === 'pending_approval').length;
  const availableCount = list.filter(k => k.status === 'available').length;
  const revokedCount = list.filter(k => k.status === 'revoked').length;

  const text = `👑 <b>3D LEDGER စီမံခန့်ခွဲမှု စင်တာ (ADMIN CONTROL)</b>\n\n` +
    `👋 မင်္ဂလာပါ <b>ဒိုင်ချုပ် / စီမံခန့်ခွဲသူ (Admin)</b>\n` +
    `ဆော့ဝဲလ် အသုံးပြုခွင့်ကုတ်များ၊ အတည်ပြုချက်များနှင့် ပေါက်မဲများကို ဤနေရာမှ တိုက်ရိုက် စီမံခန့်ခွဲနိုင်ပါသည်။\n\n` +
    `⚡ <b>အလိုအလျောက် ခွင့်ပြုစနစ်:</b> ${autoApprove ? '🟢 ဖွင့်ထားသည် (AUTO-ON)' : '🔴 ပိတ်ထားသည် (MANUAL-OFF)'}\n` +
    `<i>(Auto-Approve ဖွင့်ထားပါက ကုတ်မှန်သည်နှင့် စနစ်မှ အလိုအလျောက် ပွင့်သွားမည် ဖြစ်ပြီး ပိတ်ထားပါက Telegram တွင် တစ်ခုချင်း အတည်ပြုပေးရပါမည်)</i>\n\n` +
    `📊 <b>ကုတ်နံပါတ် အကျဉ်းချုပ်:</b>\n` +
    `• 🟢 အသုံးပြုဆဲ: <b>${activeCount}</b> ခု\n` +
    `• ⏳ ခွင့်ပြုချက် စောင့်ဆိုင်းဆဲ: <b>${pendingCount}</b> ခု\n` +
    `• ⚪ ရောင်းရန် အသင့်ရှိ: <b>${availableCount}</b> ခု\n` +
    `• 🔴 ပိတ်သိမ်းထား: <b>${revokedCount}</b> ခု\n\n` +
    `<i>အောက်ပါ ခလုတ်များမှ ရွေးချယ်ဆောင်ရွက်နိုင်ပါသည် 👇</i>`;

  return { text, keyboard: getAdminMainMenuKeyboard(autoApprove, pendingCount) };
}

export async function buildSalesReportMessage(env: Env): Promise<string> {
  const keys = await getAllLicenseKeys(env);
  const list = Object.values(keys);

  let totalSales = 0;
  let activeCount = 0;
  let availableCount = 0;
  let pendingCount = 0;
  let revokedCount = 0;

  const durationStats: Record<string, { count: number; sales: number }> = {};

  for (const k of list) {
    const dur = String(k.duration_label || k.duration || 'အခြား');
    if (!durationStats[dur]) durationStats[dur] = { count: 0, sales: 0 };
    durationStats[dur].count += 1;

    if (k.status === 'active') {
      activeCount += 1;
      const price = Number(k.price || 0);
      totalSales += price;
      durationStats[dur].sales += price;
    } else if (k.status === 'available') {
      availableCount += 1;
    } else if (k.status === 'pending_approval') {
      pendingCount += 1;
    } else if (k.status === 'revoked') {
      revokedCount += 1;
    }
  }

  let breakdownStr = '';
  for (const [dur, stat] of Object.entries(durationStats)) {
    breakdownStr += `• <b>${dur}:</b> ${stat.count} ခု (ရောင်းရ: ${stat.sales.toLocaleString()} ကျပ်)\n`;
  }

  return `📊 <b>3D LEDGER အရောင်းနှင့် အသုံးပြုမှု အစီရင်ခံစာ</b>\n\n` +
    `💰 <b>စုစုပေါင်း ရောင်းရငွေ (ခန့်မှန်း):</b> <code>${totalSales.toLocaleString()}</code> ကျပ်\n\n` +
    `📌 <b>ကုတ်နံပါတ် အခြေအနေများ:</b>\n` +
    `• 🟢 လက်ရှိ အသုံးပြုနေသော ဖုန်းများ: <b>${activeCount}</b> လုံး\n` +
    `• ⚪ ရောင်းရန် လက်ကျန် ကုတ်များ: <b>${availableCount}</b> ခု\n` +
    `• ⏳ စောင့်ဆိုင်းနေသော တောင်းခံချက်များ: <b>${pendingCount}</b> ခု\n` +
    `• 🔴 ပိတ်သိမ်းထားသော ကုတ်များ: <b>${revokedCount}</b> ခု\n` +
    `• 🔢 စုစုပေါင်း ထုတ်ခဲ့သော ကုတ်: <b>${list.length}</b> ခု\n\n` +
    `📈 <b>သက်တမ်းအလိုက် ခွဲခြမ်းစိတ်ဖြာချက်:</b>\n` +
    `${breakdownStr || '• မရှိသေးပါ'}\n` +
    `<i>မှတ်ချက်: စာရင်းများသည် Firebase ဒေတာဘေ့စ်မှ တိုက်ရိုက်ရယူထားခြင်း ဖြစ်ပါသည်။</i>`;
}

export async function buildActiveKeysMessage(env: Env): Promise<{ text: string; keyboard: InlineKeyboardMarkup }> {
  const keys = await getAllLicenseKeys(env);
  const activeList = Object.values(keys).filter(k => k.status === 'active');

  if (activeList.length === 0) {
    return {
      text: `📋 <b>အသုံးပြုဆဲ ကုတ်များ စာရင်း</b>\n\nလက်ရှိတွင် အသုံးပြုနေသော ကုတ်နံပါတ် မရှိသေးပါ။`,
      keyboard: { inline_keyboard: [[{ text: '⬅️ ပင်မ မီနူး', callback_data: 'm_main' }]] }
    };
  }

  let text = `📋 <b>အသုံးပြုဆဲ ကုတ်များ စာရင်း (${activeList.length} ခု)</b>\n\n`;
  const keyboard: InlineKeyboardButton[][] = [];

  const now = Date.now();
  for (let i = 0; i < Math.min(activeList.length, 10); i++) {
    const k = activeList[i];
    let daysLeft = 'မကန့်သတ် (Lifetime)';
    if (k.expires_at) {
      const diff = Math.max(0, Math.ceil((k.expires_at - now) / (1000 * 60 * 60 * 24)));
      daysLeft = `${diff} ရက် ကျန်`;
    }

    const shortKey = k.cd_key.length > 14 ? `${k.cd_key.substring(0, 9)}...${k.cd_key.substring(k.cd_key.length - 4)}` : k.cd_key;
    text += `<b>${i + 1}.</b> <code>${k.cd_key}</code>\n` +
      `   📱 ဖုန်း: ${k.device_model || 'အမည်မသိ'}\n` +
      `   ⏳ သက်တမ်း: ${k.duration_label || k.duration} (${daysLeft})\n\n`;

    keyboard.push([
      { text: `🚫 ပိတ်သိမ်းမည်: ${shortKey}`, callback_data: `rev_conf:${k.cd_key}` }
    ]);
  }

  keyboard.push([{ text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }]);
  return { text, keyboard: { inline_keyboard: keyboard } };
}

export async function buildPendingApprovalsMessage(env: Env): Promise<{ text: string; keyboard: InlineKeyboardMarkup }> {
  const keys = await getAllLicenseKeys(env);
  const pendingList = Object.values(keys).filter(k => k.status === 'pending_approval');

  if (pendingList.length === 0) {
    return {
      text: `🎉 <b>စောင့်ဆိုင်းနေသော ခွင့်ပြုချက် မရှိပါ</b>\n\nလက်ရှိတွင် အတည်ပြုရန် စောင့်ဆိုင်းနေသော ဖုန်း မရှိပါ။`,
      keyboard: { inline_keyboard: [[{ text: '⬅️ ပင်မ မီနူး', callback_data: 'm_main' }]] }
    };
  }

  let text = `⏳ <b>ခွင့်ပြုချက် စောင့်ဆိုင်းဆဲ စာရင်း (${pendingList.length} ခု)</b>\n\n` +
    `<i>အောက်ပါ ဖုန်းများသည် ကုတ်မှန်ကန်စွာ ရိုက်ထည့်ထားပြီး Admin ၏ ခွင့်ပြုချက်ကို စောင့်ဆိုင်းနေပါသည် 👇</i>\n\n`;

  const keyboard: InlineKeyboardButton[][] = [];

  for (let i = 0; i < Math.min(pendingList.length, 5); i++) {
    const k = pendingList[i];
    const timeStr = k.requested_at ? new Date(k.requested_at).toLocaleTimeString('my-MM') : 'လတ်တလော';
    text += `<b>${i + 1}.</b> 🔑 <code>${k.cd_key}</code>\n` +
      `   📱 ဖုန်းမော်ဒယ်: <b>${k.device_model || 'အမည်မသိ မိုဘိုင်း'}</b>\n` +
      `   🆔 Fingerprint: <code>${k.device_fingerprint || '-'}</code>\n` +
      `   ⏳ သက်တမ်း: <b>${k.duration_label || k.duration}</b>\n` +
      `   ⏰ တောင်းဆိုချိန်: ${timeStr}\n\n`;

    keyboard.push([
      { text: `✅ ခွင့်ပြုမည် (${i + 1})`, callback_data: `act_app:${k.cd_key}` },
      { text: `❌ ငြင်းပယ်မည် (${i + 1})`, callback_data: `act_rej:${k.cd_key}` }
    ]);
  }

  keyboard.push([{ text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }]);
  return { text, keyboard: { inline_keyboard: keyboard } };
}

// ── Webhook Handler ───────────────────────────────────────────────────────────

export async function handleTelegramWebhook(request: Request, env: Env): Promise<Response> {
  if (request.method !== 'POST') {
    return new Response('Method Not Allowed', { status: 405 });
  }

  let update: TelegramUpdate;
  try {
    update = await request.json() as TelegramUpdate;
  } catch (_) {
    return new Response('Invalid JSON', { status: 400 });
  }

  // 1. Handle Callback Queries (Button Clicks)
  if (update.callback_query) {
    const cq = update.callback_query;
    const data = cq.data || '';
    const chatId = cq.message?.chat.id || cq.from.id;
    const messageId = cq.message?.message_id;

    // Strict Admin Check for callback queries
    if (!isAdmin(chatId, env)) {
      await answerCallbackQuery(env, cq.id, '⛔ ခွင့်ပြုချက် မရှိပါ (Unauthorized)', true);
      return new Response(JSON.stringify({ status: 'unauthorized' }), { headers: { 'Content-Type': 'application/json' } });
    }

    await answerCallbackQuery(env, cq.id);

    // Main Menu
    if (data === 'm_main' || data === 'm_refresh') {
      const dash = await buildAdminDashboardMessage(env);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, dash.text, dash.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, dash.text, dash.keyboard);
      }
    }

    // Key Generation Menu
    else if (data === 'm_gen_menu') {
      const text = `➕ <b>ကုတ်အသစ် ထုတ်ယူရန် သက်တမ်း ရွေးချယ်ပါ</b>\n\n` +
        `အသုံးပြုသူထံ ရောင်းချလိုသော သက်တမ်းနှင့် ဈေးနှုန်းကို အောက်ပါ ခလုတ်များမှ ရွေးချယ်ပါ 👇`;
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, getKeyGenKeyboard());
      } else {
        await sendTelegramMessage(env, chatId, text, getKeyGenKeyboard());
      }
    }

    // Generate Key Execution: gen:<days>:<price>:<label>
    else if (data.startsWith('gen:')) {
      const [, daysStr, priceStr, label] = data.split(':');
      const newKey = generateCdKey();
      const price = parseInt(priceStr, 10) || 0;
      const duration = daysStr === 'lifetime' ? 'lifetime' : (daysStr === 'trial' ? 'trial' : (parseInt(daysStr, 10) || 30));
      const durLabel = decodeURIComponent(label || daysStr).replace(/_/g, ' ');

      const record: LicenseKeyRecord = {
        cd_key: newKey,
        duration: duration,
        duration_label: durLabel,
        price: price,
        status: 'available',
        created_at: Date.now()
      };

      const saved = await saveLicenseKey(env, record);
      if (saved) {
        const text = `✅ <b>လိုင်စင်ကုတ် အသစ် ထုတ်ယူပြီးပါပြီ</b>\n\n` +
          `🔑 <b>ကုတ်နံပါတ် (CD-Key):</b>\n` +
          `<code>${newKey}</code>\n\n` +
          `⏳ <b>သက်တမ်း:</b> <b>${durLabel}</b>\n` +
          `💰 <b>ဈေးနှုန်း:</b> <b>${price.toLocaleString()} ကျပ်</b>\n` +
          `📌 <b>အခြေအနေ:</b> ⚪ ရောင်းရန် အသင့်ရှိသည် (Available)\n` +
          `⏰ <b>ထုတ်သည့်အချိန်:</b> ${new Date().toLocaleTimeString('my-MM')}\n\n` +
          `<i>အသုံးပြုသူထံ ပေးပို့ရန် အထက်ပါ ကုတ်နံပါတ်ကို ကူးယူ (Copy) ၍ အသုံးပြုနိုင်ပါသည်။</i>`;

        const kb: InlineKeyboardMarkup = {
          inline_keyboard: [
            [{ text: '➕ နောက်ထပ် ကုတ်ထုတ်မည်', callback_data: 'm_gen_menu' }],
            [{ text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }]
          ]
        };

        if (messageId) {
          await editTelegramMessage(env, chatId, messageId, text, kb);
        } else {
          await sendTelegramMessage(env, chatId, text, kb);
        }
      } else {
        await sendTelegramMessage(env, chatId, '❌ <i>ကုတ် သိမ်းဆည်းခြင်း မအောင်မြင်ပါ</i>');
      }
    }

    // Toggle Auto-Approve
    else if (data === 'm_toggle_auto') {
      const current = await getAutoApproveConfig(env);
      const updated = !current;
      await setAutoApproveConfig(env, updated);

      const alertMsg = updated
        ? '⚡ Auto-Approve ဖွင့်လိုက်ပါပြီ (အလိုအလျောက် အတည်ပြုမည်)'
        : '⚡ Auto-Approve ပိတ်လိုက်ပါပြီ (Telegram မှ တစ်ခုချင်း ခွင့်ပြုရပါမည်)';
      await answerCallbackQuery(env, cq.id, alertMsg, true);

      const dash = await buildAdminDashboardMessage(env);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, dash.text, dash.keyboard);
      }
    }

    // Active Keys Monitor
    else if (data === 'm_keys_active') {
      const res = await buildActiveKeysMessage(env);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, res.text, res.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      }
    }

    // Pending Keys Monitor
    else if (data === 'm_keys_pending') {
      const res = await buildPendingApprovalsMessage(env);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, res.text, res.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      }
    }

    // Approve Activation Request: act_app:<key>
    else if (data.startsWith('act_app:')) {
      const keyToApprove = data.split(':')[1];
      const result = await approveLicenseKey(env, keyToApprove);
      if (result.ok) {
        await answerCallbackQuery(env, cq.id, '✅ အသုံးပြုခွင့် ပေးလိုက်ပါပြီ!', true);
        const text = `✅ <b>အသုံးပြုခွင့် အတည်ပြုပြီးပါပြီ (Approved)</b>\n\n` +
          `🔑 ကုတ်နံပါတ်: <code>${keyToApprove}</code>\n` +
          `📱 ဖုန်း: <b>${result.record?.device_model || 'အသုံးပြုသူ မိုဘိုင်း'}</b>\n` +
          `⏳ သက်တမ်း: <b>${result.record?.duration_label || result.record?.duration}</b>\n` +
          `စနစ်မှ အသုံးပြုခွင့် ဖွင့်ပေးလိုက်ပါပြီ။ ဖုန်းတွင် ချက်ချင်း ပွင့်သွားပါမည်။`;
        if (messageId) {
          await editTelegramMessage(env, chatId, messageId, text, {
            inline_keyboard: [[{ text: '⬅️ ပင်မ မီနူး', callback_data: 'm_main' }]]
          });
        }
      } else {
        await answerCallbackQuery(env, cq.id, '❌ ခွင့်ပြုခြင်း မအောင်မြင်ပါ', true);
      }
    }

    // Reject Activation Request: act_rej:<key>
    else if (data.startsWith('act_rej:')) {
      const keyToReject = data.split(':')[1];
      await rejectLicenseKey(env, keyToReject);
      await answerCallbackQuery(env, cq.id, '❌ ငြင်းပယ်လိုက်ပါပြီ', true);
      const text = `❌ <b>အသုံးပြုခွင့် ငြင်းပယ်လိုက်ပါသည် (Rejected)</b>\n\n` +
        `🔑 ကုတ်နံပါတ်: <code>${keyToReject}</code>\n` +
        `အဆိုပါ တောင်းဆိုမှုကို ပယ်ဖျက်လိုက်ပါပြီ။ ကုတ်အား အခြားဖုန်းတွင် ပြန်လည်အသုံးပြုနိုင်ပါသည်။`;
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, {
          inline_keyboard: [[{ text: '⬅️ ပင်မ မီနူး', callback_data: 'm_main' }]]
        });
      }
    }

    // Confirm Revoke Key: rev_conf:<key>
    else if (data.startsWith('rev_conf:')) {
      const keyToRevoke = data.split(':')[1];
      const text = `⚠️ <b>ကုတ် ပိတ်သိမ်းရန် သေချာပါသလား?</b>\n\n` +
        `🔑 <code>${keyToRevoke}</code>\n\n` +
        `ဤကုတ်ကို ပိတ်သိမ်းလိုက်ပါက အဆိုပါ ဖုန်းတွင် ဆော့ဝဲလ် အသုံးပြုခွင့် ချက်ချင်း ရပ်ဆိုင်းသွားပါမည်!`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [
            { text: '🔴 ဟုတ်ကဲ့၊ ပိတ်သိမ်းမည်', callback_data: `rev_do:${keyToRevoke}` },
            { text: '❌ မလုပ်တော့ပါ', callback_data: 'm_keys_active' }
          ]
        ]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      }
    }

    // Execute Revoke Key: rev_do:<key>
    else if (data.startsWith('rev_do:')) {
      const keyToRevoke = data.split(':')[1];
      await revokeLicenseKey(env, keyToRevoke);
      await answerCallbackQuery(env, cq.id, '🔴 ကုတ် ပိတ်သိမ်းပြီးပါပြီ', true);
      const text = `🔴 <b>လိုင်စင်ကုတ် ပိတ်သိမ်းပြီးပါပြီ (Revoked)</b>\n\n` +
        `🔑 <code>${keyToRevoke}</code>\n` +
        `အဆိုပါ ဖုန်းတွင် ဆော့ဝဲလ် အသုံးပြုခွင့်ကို အပြီးအပိုင် ရပ်ဆိုင်းလိုက်ပါပြီ။`;
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, {
          inline_keyboard: [[{ text: '⬅️ ပင်မ မီနူး', callback_data: 'm_main' }]]
        });
      }
    }

    // Sales Report
    else if (data === 'm_report') {
      const rep = await buildSalesReportMessage(env);
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [[{ text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }]]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, rep, kb);
      } else {
        await sendTelegramMessage(env, chatId, rep, kb);
      }
    }

    // Revoke List View
    else if (data === 'm_revoke_list') {
      const res = await buildActiveKeysMessage(env);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, res.text, res.keyboard);
      }
    }

    // Declare Winner Menu
    else if (data === 'm_declare_menu') {
      const text = `🎯 <b>ပေါက်မဲ လက်ဖြင့် ကြေညာရန် (Manual Winner Declaration)</b>\n\n` +
        `အမိန့်ဖြင့် ပေါက်ဂဏန်း သတ်မှတ်လိုပါက အောက်ပါအတိုင်း စာပို့ပေးပါ:\n` +
        `<code>/setwinner 108</code>\n\n` +
        `<i>မှတ်ချက်: စနစ်မှ ပေါက်သီးနှင့် တွတ်ဂဏန်းများ (အပြန် ၅ ကွက် + ကပ်သီး ၂ ကွက်) ကို အလိုအလျောက် တွက်ချက်ကာ ဆာဗာတွင် Manual Mode အဖြစ် သတ်မှတ်ပေးမည် ဖြစ်ပါသည်။</i>`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [[{ text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }]]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Batch View
    else if (data === 'm_batch') {
      let b = '1';
      try {
        const res = await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/current_batch.json`);
        if (res.ok) b = String(await res.json() || '1');
      } catch (_) {}
      const text = `📦 <b>လက်ရှိ ဖွင့်လှစ်ထားသော အကြိမ်:</b> #${b}\n\nအကြိမ် ပြောင်းလဲလိုပါက <code>/setbatch 16</code> စသည်ဖြင့် စာပို့ပေးပါ။`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [[{ text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }]]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      }
    }

    return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
  }

  // 2. Handle Text Messages & Commands
  if (update.message && update.message.text) {
    const msg = update.message;
    const text = msg.text.trim();
    const chatId = msg.chat.id;
    const senderId = msg.from?.id ? String(msg.from.id) : String(chatId);

    // STRICT ADMIN AUTHORIZATION CHECK
    if (!isAdmin(senderId, env)) {
      await sendTelegramMessage(
        env,
        chatId,
        `⛔ <b>ခွင့်ပြုချက် မရှိပါ (Access Denied)</b>\n\n` +
        `ဤ Telegram Bot သည် <b>3D Ledger စနစ် စီမံခန့်ခွဲသူ (Admin)</b> သီးသန့် အသုံးပြုရန် ဖြစ်ပါသည်။\n\n` +
        `သင့် Telegram ID (<code>${senderId}</code>) တွင် အသုံးပြုခွင့် မရှိပါ။`
      );
      return new Response('unauthorized', { status: 200 });
    }

    // Command: /start or /menu or /admin
    if (text === '/start' || text === '/menu' || text === '/admin') {
      const dash = await buildAdminDashboardMessage(env);
      await sendTelegramMessage(env, chatId, dash.text, dash.keyboard);
      return new Response('ok');
    }

    // Command: /autoapprove (Toggle)
    if (text.startsWith('/autoapprove') || text.startsWith('/auto')) {
      const current = await getAutoApproveConfig(env);
      const updated = !current;
      await setAutoApproveConfig(env, updated);
      const alertMsg = updated
        ? '⚡ <b>Auto-Approve: ဖွင့်လိုက်ပါပြီ (ON)</b>\nကုတ်မှန်ကန်စွာ ရိုက်ထည့်သည်နှင့် စနစ်မှ အလိုအလျောက် ချက်ချင်း အတည်ပြုပေးပါမည်။'
        : '⚡ <b>Auto-Approve: ပိတ်လိုက်ပါပြီ (OFF)</b>\nအသုံးပြုသူ ကုတ်ရိုက်ထည့်ပါက Telegram သို့ ခွင့်ပြုချက် တောင်းခံလွှာ ရောက်ရှိမည်ဖြစ်ပြီး Admin ခွင့်ပြုမှသာ ပွင့်ပါမည်။';
      await sendTelegramMessage(env, chatId, alertMsg);
      const dash = await buildAdminDashboardMessage(env);
      await sendTelegramMessage(env, chatId, dash.text, dash.keyboard);
      return new Response('ok');
    }

    // Command: /gen [days] [price]
    if (text.startsWith('/gen')) {
      const parts = text.split(/\s+/);
      const daysStr = parts.length > 1 ? parts[1] : '30';
      const priceStr = parts.length > 2 ? parts[2] : '15000';
      const newKey = generateCdKey();
      const price = parseInt(priceStr, 10) || 15000;
      const duration = daysStr === 'lifetime' ? 'lifetime' : (parseInt(daysStr, 10) || 30);
      const durLabel = daysStr === 'lifetime' ? 'တစ်သက်တာ (Lifetime)' : `${duration} ရက်`;

      const record: LicenseKeyRecord = {
        cd_key: newKey,
        duration: duration,
        duration_label: durLabel,
        price: price,
        status: 'available',
        created_at: Date.now()
      };

      const saved = await saveLicenseKey(env, record);
      if (saved) {
        await sendTelegramMessage(
          env,
          chatId,
          `✅ <b>လိုင်စင်ကုတ် အသစ် ထုတ်ယူပြီးပါပြီ</b>\n\n` +
          `🔑 <b>ကုတ်နံပါတ်:</b>\n<code>${newKey}</code>\n\n` +
          `⏳ သက်တမ်း: <b>${durLabel}</b>\n` +
          `💰 ဈေးနှုန်း: <b>${price.toLocaleString()} ကျပ်</b>\n` +
          `📌 အခြေအနေ: ⚪ အသင့်ရှိသည် (Available)`
        );
      } else {
        await sendTelegramMessage(env, chatId, '❌ ကုတ် သိမ်းဆည်းခြင်း မအောင်မြင်ပါ');
      }
      return new Response('ok');
    }

    // Command: /keys
    if (text.startsWith('/keys')) {
      const res = await buildActiveKeysMessage(env);
      await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      return new Response('ok');
    }

    // Command: /pending
    if (text.startsWith('/pending')) {
      const res = await buildPendingApprovalsMessage(env);
      await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      return new Response('ok');
    }

    // Command: /report
    if (text.startsWith('/report')) {
      const rep = await buildSalesReportMessage(env);
      await sendTelegramMessage(env, chatId, rep);
      return new Response('ok');
    }

    // Command: /revoke <key>
    if (text.startsWith('/revoke')) {
      const parts = text.split(/\s+/);
      const targetKey = parts.length > 1 ? parts[1].trim() : '';
      if (!targetKey) {
        await sendTelegramMessage(env, chatId, '⚠️ ပိတ်သိမ်းမည့် ကုတ်နံပါတ် ထည့်ပေးပါ။ ဥပမာ: <code>/revoke XXXX-XXXX...</code>');
        return new Response('ok');
      }
      const ok = await revokeLicenseKey(env, targetKey);
      if (ok) {
        await sendTelegramMessage(env, chatId, `🔴 <b>ကုတ်နံပါတ် <code>${targetKey}</code> အား ပိတ်သိမ်း (Revoke) လိုက်ပါပြီ။</b>`);
      } else {
        await sendTelegramMessage(env, chatId, '❌ ပိတ်သိမ်းမှု မအောင်မြင်ပါ');
      }
      return new Response('ok');
    }

    // Command: /setwinner <3-digit number>
    if (text.startsWith('/setwinner')) {
      const parts = text.split(/\s+/);
      const num = parts.length > 1 ? parts[1].trim() : '';
      if (!/^\d{3}$/.test(num)) {
        await sendTelegramMessage(env, chatId, '⚠️ <i>၃ လုံးဂဏန်း ရိုက်ထည့်ပေးပါ။ ဥပမာ: /setwinner 108</i>');
        return new Response('ok');
      }

      try {
        const token = await getFirebaseToken(env);
        const dateStr = new Date().toISOString().split('T')[0];
        const tut = calculateTutNumbers(num);

        const updates: Record<string, unknown> = {
          '3d_live_results/winning_number': num,
          '3d_live_results/first_prize': `000${num}`,
          '3d_live_results/twod': num.slice(-2),
          '3d_live_results/tuwt_set': tut.allTut,
          '3d_live_results/result_date': dateStr,
          '3d_live_results/is_final': true,
          '3d_live_results/updated_at': new Date().toISOString(),
          '3d_lottery_status/state': 'declared',
          '3d_lottery_config/mode': 'manual'
        };

        await fetch(`${env.FIREBASE_DB_URL}/.json`, {
          method: 'PATCH',
          headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
          body: JSON.stringify(updates)
        });

        await sendTelegramMessage(
          env,
          chatId,
          `🎯 <b>3D ပေါက်ဂဏန်း အောင်မြင်စွာ ကြေညာပြီးပါပြီ</b>\n\n` +
          `✨ <b>ပေါက်သီး (ဒဲ့):</b> <code>${num}</code>\n` +
          `📅 <b>ရက်စွဲ:</b> ${dateStr}\n` +
          `🔒 <b>မုဒ်:</b> MANUAL OVERRIDE\n\n` +
          `--------------------------------\n` +
          `🔢 <b>တွတ် ဂဏန်းများ (${tut.allTut.length} ကွက်):</b>\n` +
          `<code>${tut.allTut.join(', ')}</code>\n` +
          `• <i>အပြန်:</i> ${tut.permutations.join(', ') || 'မရှိပါ'}\n` +
          `• <i>ကပ်သီး:</i> ${tut.nearMisses.join(', ')}\n` +
          `--------------------------------\n` +
          `<i>Android အက်ပ်များနှင့် ဆာဗာအားလုံးတွင် ချက်ချင်း ရောင်ပြန်ဟပ်ပါမည်။</i>`
        );
      } catch (err: any) {
        await sendTelegramMessage(env, chatId, `❌ <i>သတ်မှတ်မှု မအောင်မြင်ပါ: ${err.message}</i>`);
      }
      return new Response('ok');
    }

    // Command: /setbatch <number>
    if (text.startsWith('/setbatch')) {
      const parts = text.split(/\s+/);
      const newBatch = parts.length > 1 ? parseInt(parts[1], 10) : NaN;
      if (isNaN(newBatch) || newBatch <= 0) {
        await sendTelegramMessage(env, chatId, '⚠️ <i>မှန်ကန်သော အကြိမ်နံပါတ် ရိုက်ထည့်ပေးပါ။ ဥပမာ: /setbatch 16</i>');
        return new Response('ok');
      }

      try {
        const token = await getFirebaseToken(env);
        await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/current_batch.json`, {
          method: 'PUT',
          headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
          body: JSON.stringify(newBatch)
        });
        await sendTelegramMessage(env, chatId, `✅ <b>အကြိမ်နံပါတ် #${newBatch} သို့ အောင်မြင်စွာ ပြောင်းလဲပြီးပါပြီ။</b>`);
      } catch (err: any) {
        await sendTelegramMessage(env, chatId, `❌ <i>ပြောင်းလဲမှု မအောင်မြင်ပါ: ${err.message}</i>`);
      }
      return new Response('ok');
    }

    // Fallback: Show Main Dashboard
    const dash = await buildAdminDashboardMessage(env);
    await sendTelegramMessage(env, chatId, dash.text, dash.keyboard);
  }

  return new Response('ok');
}
