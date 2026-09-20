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

export interface KeyboardButton {
  text: string;
}

export interface ReplyKeyboardMarkup {
  keyboard: KeyboardButton[][];
  resize_keyboard?: boolean;
  one_time_keyboard?: boolean;
  is_persistent?: boolean;
}

export interface ReplyKeyboardRemove {
  remove_keyboard: true;
}

export type TelegramReplyMarkup = InlineKeyboardMarkup | ReplyKeyboardMarkup | ReplyKeyboardRemove;

export interface AdminState {
  chat_id: number | string;
  action: 'awaiting_reseller' | 'awaiting_admin' | 'awaiting_wave' | 'awaiting_kpay' | 'awaiting_winner' | 'awaiting_reseller_pay' | 'awaiting_ban_key';
  target_id?: string;
  target_name?: string;
  created_at: number;
}

export interface TelegramPhotoSize {
  file_id: string;
  file_unique_id: string;
  width: number;
  height: number;
  file_size?: number;
}

export interface TelegramDocument {
  file_id: string;
  file_unique_id: string;
  file_name?: string;
  mime_type?: string;
  file_size?: number;
}

export interface AppReleaseRecord {
  file_id: string;
  file_name: string;
  file_size: number;
  version_name: string;
  version_code?: number;
  download_url?: string;
  release_notes?: string;
  uploaded_by?: string;
  uploaded_at: number;
}

export interface TelegramForwardOriginUser {
  type: 'user';
  date: number;
  sender_user: TelegramUser;
}

export interface TelegramForwardOriginHiddenUser {
  type: 'hidden_user';
  date: number;
  sender_user_name: string;
}

export interface TelegramForwardOriginChat {
  type: 'chat' | 'channel';
  date: number;
  sender_chat: TelegramChat;
  author_signature?: string;
}

export type TelegramForwardOrigin =
  | TelegramForwardOriginUser
  | TelegramForwardOriginHiddenUser
  | TelegramForwardOriginChat
  | { type: string; [key: string]: any };

export interface TelegramMessage {
  message_id: number;
  from?: TelegramUser;
  chat: TelegramChat;
  date: number;
  text?: string;
  photo?: TelegramPhotoSize[];
  document?: TelegramDocument;
  caption?: string;
  forward_from?: TelegramUser;
  forward_sender_name?: string;
  forward_origin?: TelegramForwardOrigin;
}

export interface ExtractedForwardedUser {
  id?: number;
  first_name: string;
  last_name?: string;
  username?: string;
  is_hidden?: boolean;
}

export function extractForwardedUser(msg: TelegramMessage): ExtractedForwardedUser | null {
  if (msg.forward_from) {
    return {
      id: msg.forward_from.id,
      first_name: msg.forward_from.first_name,
      last_name: msg.forward_from.last_name,
      username: msg.forward_from.username,
      is_hidden: false
    };
  }

  if (msg.forward_origin) {
    const origin = msg.forward_origin as any;
    if (origin.type === 'user' && origin.sender_user) {
      return {
        id: origin.sender_user.id,
        first_name: origin.sender_user.first_name,
        last_name: origin.sender_user.last_name,
        username: origin.sender_user.username,
        is_hidden: false
      };
    }
    if (origin.type === 'hidden_user') {
      return {
        first_name: origin.sender_user_name || 'Hidden User',
        is_hidden: true
      };
    }
  }

  if (msg.forward_sender_name) {
    return {
      first_name: msg.forward_sender_name,
      is_hidden: true
    };
  }

  return null;
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

// ── Sale Plan & License Data Types ──────────────────────────────────────────

export interface SalePlan {
  id: string; // 'trial_3d' | 'one_year' | 'lifetime' | custom
  name: string;
  duration: 'trial' | number | 'lifetime';
  duration_label: string;
  price: number;
  device_changeable: boolean;
  max_devices: number;
  description: string;
  enabled: boolean;
}

export interface LicenseKeyRecord {
  cd_key: string;
  plan_id?: string;
  duration: 'trial' | number | 'lifetime';
  duration_label: string;
  price: number;
  device_changeable?: boolean;
  status: 'available' | 'pending_approval' | 'active' | 'claimed' | 'activated' | 'revoked' | 'banned' | string;
  created_at: number;
  device_fingerprint?: string;
  device_model?: string;
  requested_at?: number;
  activated_at?: number;
  expires_at?: number | null;
  approved_by?: string;
  revoked_at?: number;
  unbanned_at?: number;
  previous_device_fingerprint?: string;
  previous_device_model?: string;
  last_migrated_at?: number;
  migration_count?: number;
  claimed_by?: string;
  claimed_by_telegram_id?: number | string;
  claimed_by_username?: string;
  generated_by_reseller_id?: string;
  reseller_name?: string;
  reseller_due_marked?: boolean;
  commission?: number;
  due_amount?: number;
}

export interface AdminRecord {
  telegram_id: string;
  name: string;
  added_at: number;
  added_by: string;
}

export interface ResellerRecord {
  telegram_id: string;
  name: string;
  username?: string;
  total_generated: number;
  total_activated: number;
  total_commission: number;
  total_due: number;
  total_paid: number;
  created_at: number;
}

export interface PaymentAccounts {
  wave: { number: string; name: string };
  kpay: { number: string; name: string };
}

export interface CommissionConfig {
  lifetime: number;
  one_year: number;
  trial_3d?: number;
}

export interface BuyerOrder {
  order_id: string;
  buyer_chat_id: number | string;
  buyer_name: string;
  buyer_username?: string;
  plan_id: string;
  plan_name: string;
  price: number;
  photo_file_id: string;
  status: 'pending' | 'approved' | 'rejected';
  created_at: number;
  delivered_key?: string;
}

export interface BuyerSession {
  chat_id: number | string;
  plan_id: string;
  plan_name: string;
  price: number;
  waiting_for_slip: boolean;
  updated_at: number;
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
  replyMarkup?: TelegramReplyMarkup,
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

export async function sendTelegramPhoto(
  env: Env,
  chatId: number | string,
  photoFileId: string,
  caption?: string,
  replyMarkup?: InlineKeyboardMarkup,
  parseMode: 'HTML' | 'MarkdownV2' | 'Markdown' = 'HTML'
) {
  const token = getCleanBotToken(env);
  if (!token) return null;
  const payload: Record<string, unknown> = {
    chat_id: chatId,
    photo: photoFileId,
    caption: caption,
    parse_mode: parseMode,
  };
  if (replyMarkup) {
    payload.reply_markup = replyMarkup;
  }

  const res = await fetch(`https://api.telegram.org/bot${token}/sendPhoto`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return res.json();
}

export async function sendTelegramDocumentBlob(
  env: Env,
  chatId: number | string,
  blob: Blob,
  caption?: string,
  replyMarkup?: TelegramReplyMarkup,
  parseMode: 'HTML' | 'MarkdownV2' | 'Markdown' = 'HTML',
  fileName = '3D_Ledger.apk'
) {
  const token = getCleanBotToken(env);
  if (!token) return null;

  const form = new FormData();
  form.append('chat_id', String(chatId));
  form.append('document', blob, fileName);
  if (caption) {
    form.append('caption', caption);
  }
  form.append('parse_mode', parseMode);
  if (replyMarkup) {
    form.append('reply_markup', JSON.stringify(replyMarkup));
  }

  const res = await fetch(`https://api.telegram.org/bot${token}/sendDocument`, {
    method: 'POST',
    body: form,
  });
  return res.json();
}

export async function sendTelegramDocument(
  env: Env,
  chatId: number | string,
  document: string | Blob,
  caption?: string,
  replyMarkup?: TelegramReplyMarkup,
  parseMode: 'HTML' | 'MarkdownV2' | 'Markdown' = 'HTML',
  fileName = '3D_Ledger.apk'
) {
  const token = getCleanBotToken(env);
  if (!token) return null;

  if (document instanceof Blob) {
    return await sendTelegramDocumentBlob(env, chatId, document, caption, replyMarkup, parseMode, fileName);
  }

  if (typeof document === 'string' && (document.startsWith('http://') || document.startsWith('https://'))) {
    try {
      const resp = await fetch(document, {
        headers: { 'User-Agent': 'Mozilla/5.0 (3D-Ledger-Release-Sync)' }
      });
      if (resp.ok) {
        const blob = await resp.blob();
        return await sendTelegramDocumentBlob(env, chatId, blob, caption, replyMarkup, parseMode, fileName);
      }
    } catch (e) {
      console.error('Error downloading document URL for Telegram upload:', e);
    }
  }

  const payload: Record<string, unknown> = {
    chat_id: chatId,
    document: document,
    caption: caption,
    parse_mode: parseMode,
  };
  if (replyMarkup) {
    payload.reply_markup = replyMarkup;
  }

  const res = await fetch(`https://api.telegram.org/bot${token}/sendDocument`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return res.json();
}

export async function getAppRelease(env: Env): Promise<AppReleaseRecord | null> {
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_app_release.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json();
      if (data && typeof data === 'object') {
        const rel = data as AppReleaseRecord;
        if (rel.file_size === 24000000 || !rel.file_size) {
          rel.file_size = 9284449;
        }
        return rel;
      }
    }
  } catch (_) {}
  return null;
}

export async function saveAppRelease(env: Env, release: AppReleaseRecord): Promise<boolean> {
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_app_release.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(release)
    });
    return res.ok;
  } catch (_) {
    return false;
  }
}


export async function isUserAdmin(senderId: number | string, env: Env): Promise<boolean> {
  const sid = String(senderId).trim();
  if (!sid) return false;
  if (sid === String(env.TELEGRAM_CHAT_ID || '').trim()) return true;

  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/admins/${sid}.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json();
      return data != null && typeof data === 'object';
    }
  } catch (_) {}
  return false;
}

export async function getAdmins(env: Env): Promise<AdminRecord[]> {
  const masterId = String(env.TELEGRAM_CHAT_ID || '').trim();
  const list: AdminRecord[] = [];
  if (masterId) {
    list.push({
      telegram_id: masterId,
      name: 'Master Admin',
      added_at: 0,
      added_by: 'system'
    });
  }

  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/admins.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json() as Record<string, AdminRecord> | null;
      if (data && typeof data === 'object') {
        for (const [id, rec] of Object.entries(data)) {
          if (id !== masterId && rec && rec.telegram_id) {
            list.push(rec);
          }
        }
      }
    }
  } catch (_) {}
  return list;
}

export async function addAdmin(env: Env, telegramId: string, name: string, addedBy: string): Promise<boolean> {
  try {
    const token = await getFirebaseToken(env);
    const rec: AdminRecord = {
      telegram_id: telegramId,
      name: name || 'Admin',
      added_at: Date.now(),
      added_by: addedBy
    };
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/admins/${telegramId}.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(rec)
    });
    return res.ok;
  } catch (_) {
    return false;
  }
}

export async function removeAdmin(env: Env, telegramId: string): Promise<boolean> {
  if (String(telegramId).trim() === String(env.TELEGRAM_CHAT_ID).trim()) {
    return false; // Cannot remove master admin
  }
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/admins/${telegramId}.json`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return res.ok;
  } catch (_) {
    return false;
  }
}

export async function notifyAllAdmins(
  env: Env,
  text: string,
  replyMarkup?: InlineKeyboardMarkup,
  parseMode: 'HTML' | 'MarkdownV2' | 'Markdown' = 'HTML'
): Promise<void> {
  const admins = await getAdmins(env);
  for (const a of admins) {
    try {
      await sendTelegramMessage(env, a.telegram_id, text, replyMarkup, parseMode);
    } catch (_) {}
  }
}

export async function notifyAllAdminsWithPhoto(
  env: Env,
  photoFileId: string,
  caption?: string,
  replyMarkup?: InlineKeyboardMarkup,
  parseMode: 'HTML' | 'MarkdownV2' | 'Markdown' = 'HTML'
): Promise<void> {
  const admins = await getAdmins(env);
  for (const a of admins) {
    try {
      await sendTelegramPhoto(env, a.telegram_id, photoFileId, caption, replyMarkup, parseMode);
    } catch (_) {}
  }
}

// ── Reseller Management Helpers ───────────────────────────────────────────────

const inMemoryResellers: Record<string, ResellerRecord> = {};

export async function getReseller(senderId: number | string, env: Env): Promise<ResellerRecord | null> {
  const sid = String(senderId).trim();
  if (!sid) return null;
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/resellers/${sid}.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json() as ResellerRecord | null;
      if (data && typeof data === 'object' && data.telegram_id) {
        inMemoryResellers[sid] = data;
        return data;
      }
    }
  } catch (_) {}
  return inMemoryResellers[sid] || null;
}

export async function getAllResellers(env: Env): Promise<Record<string, ResellerRecord>> {
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/resellers.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json();
      if (data && typeof data === 'object') {
        return { ...inMemoryResellers, ...data };
      }
    }
  } catch (_) {}
  return { ...inMemoryResellers };
}

export async function saveReseller(env: Env, reseller: ResellerRecord): Promise<boolean> {
  inMemoryResellers[reseller.telegram_id] = reseller;
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/resellers/${reseller.telegram_id}.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(reseller)
    });
    return res.ok;
  } catch (_) {
    return true;
  }
}

export async function addReseller(env: Env, telegramId: string, name: string, username?: string): Promise<boolean> {
  const existing = await getReseller(telegramId, env);
  const rec: ResellerRecord = {
    telegram_id: telegramId,
    name: name || 'Reseller',
    username: username || '',
    total_generated: existing?.total_generated || 0,
    total_activated: existing?.total_activated || 0,
    total_commission: existing?.total_commission || 0,
    total_due: existing?.total_due || 0,
    total_paid: existing?.total_paid || 0,
    created_at: existing?.created_at || Date.now()
  };
  return await saveReseller(env, rec);
}

export async function removeReseller(env: Env, telegramId: string): Promise<boolean> {
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/resellers/${telegramId}.json`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return res.ok;
  } catch (_) {
    return false;
  }
}

export async function settleResellerDue(
  env: Env,
  resellerId: string,
  amountOrFull: string | number,
  adminId: string
): Promise<{ ok: boolean; reseller?: ResellerRecord; paidAmount?: number; error?: string }> {
  const reseller = await getReseller(resellerId, env);
  if (!reseller) {
    return { ok: false, error: 'ကိုယ်စားလှယ် မတွေ့ရှိပါ (Reseller not found)' };
  }

  let payAmount = 0;
  if (typeof amountOrFull === 'string' && amountOrFull.toLowerCase() === 'full') {
    payAmount = reseller.total_due || 0;
  } else {
    payAmount = parseInt(String(amountOrFull).replace(/,/g, ''), 10) || 0;
  }

  if (payAmount <= 0) {
    return { ok: false, error: 'ပေးသွင်းငွေ ပမာဏ ၀ ဖြစ်နေပါသည်' };
  }

  reseller.total_paid = (reseller.total_paid || 0) + payAmount;
  reseller.total_due = Math.max(0, (reseller.total_due || 0) - payAmount);
  await saveReseller(env, reseller);

  try {
    const token = await getFirebaseToken(env);
    const entryId = `pay_${Date.now()}`;
    await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/reseller_ledger/${resellerId}/${entryId}.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        type: 'payment',
        amount: payAmount,
        paid_to_admin: adminId,
        timestamp: Date.now()
      })
    });
  } catch (_) {}

  return { ok: true, reseller, paidAmount: payAmount };
}

// ── Commission & Payment Configuration Helpers ───────────────────────────────

export async function getCommissionConfig(env: Env): Promise<CommissionConfig> {
  const defaults: CommissionConfig = {
    lifetime: 5000,
    one_year: 10000,
    trial_3d: 0
  };
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/commissions.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json() as Partial<CommissionConfig> | null;
      if (data && typeof data === 'object') {
        return { ...defaults, ...data };
      }
    }
  } catch (_) {}
  return defaults;
}

export async function setCommissionConfig(env: Env, planId: 'lifetime' | 'one_year', amount: number): Promise<boolean> {
  try {
    const current = await getCommissionConfig(env);
    current[planId] = amount;
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/commissions.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(current)
    });
    return res.ok;
  } catch (_) {
    return false;
  }
}

export async function getPaymentAccounts(env: Env): Promise<PaymentAccounts> {
  const defaults: PaymentAccounts = {
    wave: { number: '09778899001', name: '3D Ledger Admin' },
    kpay: { number: '09778899001', name: '3D Ledger Admin' }
  };
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/payment_accounts.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json() as PaymentAccounts | null;
      if (data && typeof data === 'object') {
        return {
          wave: { ...defaults.wave, ...(data.wave || {}) },
          kpay: { ...defaults.kpay, ...(data.kpay || {}) },
        };
      }
    }
  } catch (_) {}
  return defaults;
}

export async function setPaymentAccount(env: Env, type: 'wave' | 'kpay', number: string, name: string): Promise<boolean> {
  try {
    const current = await getPaymentAccounts(env);
    current[type] = { number, name };
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/payment_accounts.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(current)
    });
    return res.ok;
  } catch (_) {
    return false;
  }
}

// ── Reseller Activation Due Accounting Engine ─────────────────────────────────

export async function recordResellerActivationDue(env: Env, keyRecord: LicenseKeyRecord): Promise<void> {
  if (!keyRecord.generated_by_reseller_id) return;
  if ((keyRecord as any).reseller_due_marked) return;

  const resellerId = keyRecord.generated_by_reseller_id;
  const reseller = await getReseller(resellerId, env);
  if (!reseller) return;

  const commissions = await getCommissionConfig(env);
  let commission = 0;
  if (keyRecord.plan_id === 'lifetime' || keyRecord.duration === 'lifetime') {
    commission = commissions.lifetime ?? 5000;
  } else if (keyRecord.plan_id === 'one_year' || keyRecord.duration === 365) {
    commission = commissions.one_year ?? 10000;
  }

  const price = Number(keyRecord.price || 0);
  const dueAmount = Math.max(0, price - commission);

  const newActivated = (reseller.total_activated || 0) + 1;
  const newCommission = (reseller.total_commission || 0) + commission;
  const newDue = (reseller.total_due || 0) + dueAmount;

  reseller.total_activated = newActivated;
  reseller.total_commission = newCommission;
  reseller.total_due = newDue;
  await saveReseller(env, reseller);

  try {
    const token = await getFirebaseToken(env);
    await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${keyRecord.cd_key}.json`, {
      method: 'PATCH',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        reseller_due_marked: true,
        commission: commission,
        due_amount: dueAmount
      })
    });

    await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/reseller_ledger/${resellerId}/${keyRecord.cd_key}.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        type: 'activation',
        cd_key: keyRecord.cd_key,
        plan_id: keyRecord.plan_id || 'unknown',
        plan_name: keyRecord.duration_label || 'Plan',
        price: price,
        commission: commission,
        due_amount: dueAmount,
        timestamp: Date.now()
      })
    });
  } catch (_) {}

  const adminMsg = `🎉 <b>[ကိုယ်စားလှယ် ကုတ် အသက်ဝင်ခြင်း]</b>\n\n` +
    `👤 ကိုယ်စားလှယ်: <b>${reseller.name}</b> (ID: <code>${resellerId}</code>)\n` +
    `🔑 ကုတ်နံပါတ်: <code>${keyRecord.cd_key}</code>\n` +
    `📦 အစီအစဉ်: <b>${keyRecord.duration_label || keyRecord.plan_id}</b>\n` +
    `💰 ရောင်းဈေး: <b>${price.toLocaleString()} ကျပ်</b>\n` +
    `💵 ကိုယ်စားလှယ် ကော်မရှင်: <b>+${commission.toLocaleString()} ကျပ်</b>\n` +
    `📌 Admin သို့ ပေးသွင်းရန် (Due): <b>+${dueAmount.toLocaleString()} ကျပ်</b>\n` +
    `📊 ကိုယ်စားလှယ်၏ ပေးရန်ကျန်ငွေ စုစုပေါင်း: <b>${newDue.toLocaleString()} ကျပ်</b>`;
  await notifyAllAdmins(env, adminMsg);

  const resellerMsg = `🎉 <b>သင့်ကုတ်နံပါတ် စတင်အသက်ဝင်သွားပါပြီ!</b>\n\n` +
    `🔑 ကုတ်နံပါတ်: <code>${keyRecord.cd_key}</code>\n` +
    `📦 အစီအစဉ်: <b>${keyRecord.duration_label || keyRecord.plan_id}</b>\n` +
    `💵 သင်ရရှိသော ကော်မရှင်: <b>+${commission.toLocaleString()} ကျပ်</b>\n` +
    `📌 Admin ထံ ပေးသွင်းရန် ပေါင်းထည့်ငွေ: <b>+${dueAmount.toLocaleString()} ကျပ်</b>\n` +
    `📊 သင်၏ ပေးရန်ကျန်ငွေ လက်ကျန်: <b>${newDue.toLocaleString()} ကျပ်</b>`;
  await sendTelegramMessage(env, resellerId, resellerMsg);
}

// ── Buyer Orders & Sessions ───────────────────────────────────────────────────

export async function getBuyerSession(chatId: number | string, env: Env): Promise<BuyerSession | null> {
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/buyer_sessions/${chatId}.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (res.ok) {
      return await res.json() as BuyerSession | null;
    }
  } catch (_) {}
  return null;
}

export async function setBuyerSession(chatId: number | string, session: BuyerSession, env: Env): Promise<boolean> {
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/buyer_sessions/${chatId}.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(session)
    });
    return res.ok;
  } catch (_) {
    return false;
  }
}

const inMemoryAdminStates: Record<string, AdminState> = {};

export async function getAdminState(chatId: number | string, env: Env): Promise<AdminState | null> {
  const cid = String(chatId);
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/admin_states/${cid}.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json() as AdminState | null;
      if (data) {
        inMemoryAdminStates[cid] = data;
        return data;
      }
    }
  } catch (_) {}
  return inMemoryAdminStates[cid] || null;
}

export async function setAdminState(chatId: number | string, state: AdminState | null, env: Env): Promise<boolean> {
  const cid = String(chatId);
  if (!state) {
    delete inMemoryAdminStates[cid];
  } else {
    inMemoryAdminStates[cid] = state;
  }
  try {
    const token = await getFirebaseToken(env);
    if (!state) {
      await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/admin_states/${cid}.json`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      return true;
    }
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/admin_states/${cid}.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(state)
    });
    return res.ok;
  } catch (_) {
    return true;
  }
}

export async function getBuyerOrder(orderId: string, env: Env): Promise<BuyerOrder | null> {
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/orders/${orderId}.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (res.ok) {
      return await res.json() as BuyerOrder | null;
    }
  } catch (_) {}
  return null;
}

export async function saveBuyerOrder(order: BuyerOrder, env: Env): Promise<boolean> {
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/orders/${order.order_id}.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(order)
    });
    return res.ok;
  } catch (_) {
    return false;
  }
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

// ── Sale Plans Config & Defaults ──────────────────────────────────────────────

export function getDefaultSalePlans(): Record<string, SalePlan> {
  return {
    trial_3d: {
      id: 'trial_3d',
      name: '၃ ရက် အခမဲ့ စမ်းသပ်ခွင့် (3-Day Free Trial)',
      duration: 'trial',
      duration_label: '၃ ရက် စမ်းသပ်ခွင့် (72 နာရီ)',
      price: 0,
      device_changeable: false,
      max_devices: 1,
      description: 'ဖုန်း ၁ လုံး (စက်ပြောင်းမရပါ - ၇၂ နာရီ)',
      enabled: true,
    },
    one_year: {
      id: 'one_year',
      name: '၁ နှစ် သက်တမ်း (1 Year Plan)',
      duration: 365,
      duration_label: '၁ နှစ် (365 ရက်)',
      price: 180000,
      device_changeable: true,
      max_devices: 1,
      description: 'ဖုန်းပြောင်းသုံးနိုင်သည် (Device Changeable ✅)',
      enabled: true,
    },
    lifetime: {
      id: 'lifetime',
      name: 'တစ်သက်တာ (Lifetime Plan)',
      duration: 'lifetime',
      duration_label: 'တစ်သက်တာ (Lifetime)',
      price: 45000,
      device_changeable: false,
      max_devices: 1,
      description: 'ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည် (1-Device Locked 🔒)',
      enabled: true,
    },
  };
}

export async function getSalePlans(env: Env): Promise<Record<string, SalePlan>> {
  const defaults = getDefaultSalePlans();
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/sale_plans.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json() as Record<string, SalePlan> | null;
      if (data && typeof data === 'object' && Object.keys(data).length > 0) {
        return { ...defaults, ...data };
      }
    }
    // Initialize defaults in Firebase if not present
    await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/sale_plans.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(defaults)
    });
  } catch (_) {}
  return defaults;
}

export async function saveSalePlan(env: Env, plan: SalePlan): Promise<boolean> {
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/sale_plans/${plan.id}.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(plan)
    });
    return res.ok;
  } catch (_) {
    return false;
  }
}

export async function updatePlanPrice(env: Env, planId: string, price: number): Promise<boolean> {
  try {
    const plans = await getSalePlans(env);
    const target = plans[planId];
    if (!target) return false;
    target.price = price;
    return await saveSalePlan(env, target);
  } catch (_) {
    return false;
  }
}

const inMemoryLicenseKeys: Record<string, LicenseKeyRecord> = {};

export async function getAllLicenseKeys(env: Env): Promise<Record<string, LicenseKeyRecord>> {
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys.json`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (res.ok) {
      const data = await res.json();
      if (data && typeof data === 'object') {
        const normalized: Record<string, LicenseKeyRecord> = {};
        for (const [keyId, keyVal] of Object.entries(data)) {
          if (keyVal && typeof keyVal === 'object') {
            normalized[keyId] = {
              ...(keyVal as LicenseKeyRecord),
              cd_key: (keyVal as any).cd_key || keyId
            };
          }
        }
        return { ...inMemoryLicenseKeys, ...normalized };
      }
    }
  } catch (_) {}
  return { ...inMemoryLicenseKeys };
}

export async function saveLicenseKey(env: Env, keyRecord: LicenseKeyRecord): Promise<boolean> {
  inMemoryLicenseKeys[keyRecord.cd_key] = keyRecord;
  try {
    const token = await getFirebaseToken(env);
    const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${keyRecord.cd_key}.json`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(keyRecord)
    });
    return res.ok;
  } catch (_) {
    return true;
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
      device_fingerprint: record.device_fingerprint || 'approved_by_admin',
      device_changeable: record.device_changeable ?? (record.duration === 365 || record.plan_id === 'one_year')
    };

    const patchRes = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cdKey}.json`, {
      method: 'PATCH',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(updates)
    });

    const updatedRecord = { ...record, ...updates } as LicenseKeyRecord;
    if (updatedRecord.generated_by_reseller_id) {
      await recordResellerActivationDue(env, updatedRecord);
    }

    return { ok: patchRes.ok, record: updatedRecord };
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

export async function sendAppToUser(
  env: Env,
  chatId: number | string,
  senderId: string,
  username = '',
  firstName = ''
): Promise<void> {
  const release = await getAppRelease(env);
  let token = '';
  try {
    token = await getFirebaseToken(env);
  } catch (_) {}

  // 1. Retrieve or generate the user's free 3-day trial key
  let trialKey = '';
  if (token) {
    try {
      const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/telegram_trials/${senderId}.json`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (res.ok) {
        const existingTrial = await res.json() as { cd_key: string } | null;
        if (existingTrial?.cd_key) {
          trialKey = existingTrial.cd_key;
        }
      }
    } catch (_) {}
  }

  if (!trialKey) {
    trialKey = generateCdKey();
    const trialRecord: LicenseKeyRecord = {
      cd_key: trialKey,
      plan_id: 'trial_3d',
      duration: 'trial',
      duration_label: '၃ ရက် စမ်းသပ်ခွင့် (72 နာရီ)',
      price: 0,
      device_changeable: false,
      status: 'available',
      created_at: Date.now(),
      claimed_by_telegram_id: senderId,
      claimed_by_username: username || firstName
    };

    if (token) {
      await saveLicenseKey(env, trialRecord);
      await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/telegram_trials/${senderId}.json`, {
        method: 'PUT',
        headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          telegram_id: senderId,
          username: username,
          first_name: firstName,
          cd_key: trialKey,
          created_at: Date.now()
        })
      });

      await notifyAllAdmins(env,
        `🎁 <b>[Free Trial Claimed] အခမဲ့ စမ်းသပ်ခွင့် ကုတ် အသစ် ရယူသွားပါသည်</b>\n\n` +
        `👤 အသုံးပြုသူ: <b>${firstName || 'မိတ်ဆွေ'}</b> (@${username || '-'})\n` +
        `🆔 Telegram ID: <code>${senderId}</code>\n` +
        `🔑 ကုတ်နံပါတ်: <code>${trialKey}</code>\n` +
        `⏳ သက်တမ်း: <b>၇၂ နာရီ (၃ ရက်)</b>`
      );
    }
  }

  const appKb: InlineKeyboardMarkup = {
    inline_keyboard: [
      [
        { text: '🛒 လိုင်စင် ဝယ်ယူမည် (Buy License)', callback_data: 'b_buy_menu' },
        { text: '❓ အသုံးပြုပုံ လမ်းညွှန်', callback_data: 'b_help' }
      ],
      [
        { text: '🇹🇭 3D Live ရလဒ် ကြည့်မည်', callback_data: 'b_live_refresh' },
        { text: '🔢 တွတ် ဂဏန်းများ တွက်မည်', callback_data: 'tut:108' }
      ]
    ]
  };

  const rawSize = (release?.file_size && release.file_size !== 24000000) ? release.file_size : 9284449;
  const sizeMb = ((rawSize) / (1024 * 1024)).toFixed(1);
  const versionName = release?.version_name || 'v1.0.100';

  const caption = `📱 <b>3D LEDGER (မြန်မာ 3D စာရင်းကိုင် ဆော့ဝဲလ် တရားဝင် APK)</b>\n\n` +
    `🔖 <b>ဗားရှင်း:</b> <b>${versionName}</b>\n` +
    `📦 <b>ဖိုင်အရွယ်အစား:</b> <b>${sizeMb} MB</b>\n\n` +
    `🔑 <b>သင်၏ ၃ ရက် အခမဲ့ စမ်းသပ်ခွင့် CD-Key:</b>\n` +
    `<code>${trialKey}</code>\n` +
    `<i>(ကုတ်နံပါတ်ကို နှိပ်၍ Copy ကူးယူပြီး အက်ပ်စဖွင့်ချိန်တွင် ထည့်သွင်းပါ)</i>\n\n` +
    `📥 <b>တပ်ဆင်ပြီး စမ်းသပ်အသုံးပြုနည်း (Installation Guide):</b>\n` +
    `1️⃣ အထက်ပါ APK ဖိုင်ကို နှိပ်၍ ဒေါင်းလုဒ်လုပ်ပြီး ဖုန်းတွင် Install လုပ်ပါ။\n` +
    `2️⃣ Google Play Protect မှ သတိပေးချက် ပေါ်လာပါက <b>"More details"</b> ကို နှိပ်ပြီး <b>"Install anyway"</b> ကို ရွေးချယ်ပေးပါ (ပထမဆုံးအကြိမ် APK တင်သည့်အခါသာ ပေါ်ပါသည်)။\n` +
    `3️⃣ အက်ပ်ကို ဖွင့်ပြီး အထက်ပါ <b>Trial Key</b> ကို ထည့်သွင်းကာ ၇၂ နာရီ အပြည့်အဝ စမ်းသပ် အသုံးပြုနိုင်ပါသည်။\n` +
    `4️⃣ စမ်းသပ်အသုံးပြုပြီးနောက် ဆက်လက်သုံးစွဲလိုပါက အောက်ပါ <b>[🛒 လိုင်စင် ဝယ်ယူမည်]</b> မှတစ်ဆင့် ၁ နှစ် သို့မဟုတ် တစ်သက်တာ လိုင်စင် ဝယ်ယူနိုင်ပါသည်။`;

  if (release && release.file_id) {
    await sendTelegramDocument(env, chatId, release.file_id, caption, appKb);
  } else if (release && release.download_url) {
    // ALWAYS send the native APK document file! Do NOT send text links!
    const sent = await sendTelegramDocument(
      env,
      chatId,
      release.download_url,
      caption,
      appKb,
      'HTML',
      release.file_name || '3D_Ledger.apk'
    );
    if (sent?.result?.document) {
      if (sent.result.document.file_id) release.file_id = sent.result.document.file_id;
      if (sent.result.document.file_size) release.file_size = sent.result.document.file_size;
      await saveAppRelease(env, release);
    }
  } else {
    const noAppMsg = `📲 <b>3D LEDGER စာရင်းကိုင် ဆော့ဝဲလ် တပ်ဆင်ရန်</b>\n\n` +
      `🔑 <b>သင်၏ ၃ ရက် အခမဲ့ စမ်းသပ်ခွင့် CD-Key:</b>\n` +
      `<code>${trialKey}</code>\n\n` +
      `ခေတ္တစောင့်ဆိုင်းပေးပါ၊ တရားဝင် APK ဖိုင်အား စနစ်မှ တင်သွင်းနေဆဲ ဖြစ်ပါသည်…`;
    await sendTelegramMessage(env, chatId, noAppMsg, appKb);
  }
}

export async function getAllRecipientIds(env: Env): Promise<Set<string>> {
  const recipientIds = new Set<string>();

  try {
    const token = await getFirebaseToken(env);

    // 1. telegram_trials
    try {
      const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/telegram_trials.json`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json() as Record<string, { telegram_id?: string | number }> | null;
        if (data && typeof data === 'object') {
          Object.entries(data).forEach(([key, u]) => {
            if (u?.telegram_id) recipientIds.add(String(u.telegram_id).trim());
            else if (/^\d+$/.test(key)) recipientIds.add(key.trim());
          });
        }
      }
    } catch (_) {}

    // 2. buyer_orders
    try {
      const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/buyer_orders.json`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json() as Record<string, { buyer_chat_id?: string | number }> | null;
        if (data && typeof data === 'object') {
          Object.values(data).forEach(o => {
            if (o?.buyer_chat_id) recipientIds.add(String(o.buyer_chat_id).trim());
          });
        }
      }
    } catch (_) {}

    // 3. resellers
    try {
      const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/resellers.json`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json() as Record<string, { telegram_id?: string | number }> | null;
        if (data && typeof data === 'object') {
          Object.entries(data).forEach(([key, r]) => {
            if (r?.telegram_id) recipientIds.add(String(r.telegram_id).trim());
            else if (/^\d+$/.test(key)) recipientIds.add(key.trim());
          });
        }
      }
    } catch (_) {}

    // 4. keys (claimed_by_telegram_id)
    try {
      const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys.json`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json() as Record<string, LicenseKeyRecord> | null;
        if (data && typeof data === 'object') {
          Object.values(data).forEach(k => {
            if (k?.claimed_by_telegram_id) recipientIds.add(String(k.claimed_by_telegram_id).trim());
            else if (k?.claimed_by && /^\d+$/.test(k.claimed_by)) recipientIds.add(k.claimed_by.trim());
          });
        }
      }
    } catch (_) {}

    // 5. master admin / admins
    if (env.TELEGRAM_CHAT_ID) {
      recipientIds.add(String(env.TELEGRAM_CHAT_ID).trim());
    }
  } catch (_) {}

  return recipientIds;
}

export async function broadcastTextMessage(
  env: Env,
  adminChatId: number | string,
  message: string,
  replyMarkup?: InlineKeyboardMarkup
): Promise<number> {
  const recipientIds = await getAllRecipientIds(env);
  let successCount = 0;

  const kb: InlineKeyboardMarkup = replyMarkup || {
    inline_keyboard: [
      [
        { text: '📲 အက်ပ် ဒေါင်းလုဒ်ရယူရန်', callback_data: 'b_app_download' },
        { text: '🛒 လိုင်စင် ဝယ်ယူမည်', callback_data: 'b_buy_menu' }
      ],
      [
        { text: '🇹🇭 3D Live ရလဒ် ကြည့်မည်', callback_data: 'b_live_refresh' },
        { text: '🔢 တွတ် ဂဏန်းများ', callback_data: 'tut:108' }
      ]
    ]
  };

  for (const id of recipientIds) {
    try {
      await sendTelegramMessage(env, id, message, kb);
      successCount++;
    } catch (_) {}
  }

  if (adminChatId) {
    await sendTelegramMessage(
      env,
      adminChatId,
      `📢 <b>အသိပေးစာ အားလုံးသို့ ပို့ဆောင်ပြီးပါပြီ (Broadcast Complete)</b>\n\n` +
      `📊 အောင်မြင်စွာ ပို့ခဲ့သူ: <b>${successCount}</b> ဦး\n` +
      `👥 စုစုပေါင်း ဖုန်း/အသုံးပြုသူ: <b>${recipientIds.size}</b> ဦး`
    );
  }

  return successCount;
}

export async function broadcastAppUpdate(
  env: Env,
  adminChatId: number | string,
  customMessage?: string
): Promise<number> {
  const release = await getAppRelease(env);
  const recipientIds = await getAllRecipientIds(env);

  let successCount = 0;
  const updateCaption = customMessage ||
    `🚀 <b>3D LEDGER အက်ပ် ဗားရှင်းအသစ် [${release?.version_name || 'Latest'}] ထွက်ရှိပါပြီ!</b>\n\n` +
    (release ? `📦 <b>ဖိုင်အမည်:</b> <code>${release.file_name}</code>\n` : '') +
    `📝 <b>အပြောင်းအလဲများ:</b> ${release?.release_notes || 'Physical Tactile Keypad, Haptic Feedback နှင့် Active License ပိတ်သိမ်းနိုင်သော စနစ်သစ်များ ပါဝင်ပါသည်'}\n\n` +
    `အောက်ပါ APK ဖိုင်ကို ဒေါင်းလုဒ်ဆွဲ၍ ယခင်အက်ပ်ပေါ်တွင် အဆင့်မြှင့်တင် (Update) တပ်ဆင်နိုင်ပါပြီ 👇`;

  const kb: InlineKeyboardMarkup = {
    inline_keyboard: [
      [
        { text: '📲 အက်ပ် ဒေါင်းလုဒ်ရယူရန်', callback_data: 'b_app_download' },
        { text: '🛒 လိုင်စင် ဝယ်ယူမည်', callback_data: 'b_buy_menu' }
      ]
    ]
  };

  for (const id of recipientIds) {
    try {
      if (release?.file_id) {
        await sendTelegramDocument(env, id, release.file_id, updateCaption, kb);
        successCount++;
      } else if (release?.download_url) {
        const sent = await sendTelegramDocument(
          env,
          id,
          release.download_url,
          updateCaption,
          kb,
          'HTML',
          release.file_name || '3D_Ledger.apk'
        );
        if (sent?.result?.document?.file_id && !release.file_id) {
          release.file_id = sent.result.document.file_id;
          await saveAppRelease(env, release);
        }
        successCount++;
      } else {
        // Fallback to rich text update notification if no binary release uploaded yet
        await sendTelegramMessage(env, id, updateCaption, kb);
        successCount++;
      }
    } catch (_) {}
  }

  if (adminChatId) {
    await sendTelegramMessage(
      env,
      adminChatId,
      `✅ <b>Update အသိပေးစာ အောင်မြင်စွာ ပို့ဆောင်ပြီးပါပြီ</b>\n\n` +
      `📊 စုစုပေါင်း ပေးပို့ခဲ့သူ: <b>${successCount}</b> ဦး\n` +
      `🔖 ဗားရှင်း: <b>${release?.version_name || 'Latest'}</b>`
    );
  }

  return successCount;
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

export async function unbanLicenseKey(env: Env, cdKey: string): Promise<boolean> {
  try {
    const token = await getFirebaseToken(env);
    const patchRes = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${cdKey}.json`, {
      method: 'PATCH',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        status: 'available',
        claimed_by: null,
        device_fingerprint: null,
        device_model: null,
        unbanned_at: Date.now()
      })
    });
    return patchRes.ok;
  } catch (_) {
    return false;
  }
}

/**
 * Robust check if a license key is currently in active / in-use state on a device.
 * Covers status: 'active', 'claimed', 'activated', as well as any non-revoked key
 * that has an assigned device_fingerprint, claimed_by, or activated_at timestamp.
 */
export function isKeyInActiveUse(key: LicenseKeyRecord): boolean {
  if (!key) return false;
  const status = String(key.status || '').toLowerCase().trim();
  if (status === 'revoked' || status === 'banned') return false;
  if (status === 'active' || status === 'claimed' || status === 'activated') return true;
  if (status !== 'available' && (key.device_fingerprint || key.claimed_by || key.activated_at)) return true;
  if (key.device_fingerprint && key.activated_at) return true;
  return false;
}

export async function setWinningNumber(env: Env, num: string, authorId?: string): Promise<boolean> {
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

    const patchRes = await fetch(`${env.FIREBASE_DB_URL}/.json`, {
      method: 'PATCH',
      headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(updates)
    });
    return patchRes.ok;
  } catch (_) {
    return false;
  }
}

// ── Role Persistent Bottom Keyboards ──────────────────────────────────────────

export function getAdminBottomKeyboard(): ReplyKeyboardMarkup {
  return {
    keyboard: [
      [{ text: '📊 ပင်မ ဒက်ရှ်ဘုတ်' }, { text: '➕ ကုတ်အသစ် ထုတ်မည်' }],
      [{ text: '👥 ကိုယ်စားလှယ်များ' }, { text: '💳 ငွေလက်ခံ အကောင့်များ' }],
      [{ text: '👮‍♂️ Admin များ' }, { text: '💵 ကော်မရှင် သတ်မှတ်ချက်' }],
      [{ text: '🏷️ အရောင်း စီမံချက်များ' }, { text: '📋 အသုံးပြုမှု စောင့်ကြည့်' }],
      [{ text: '📤 အက်ပ် တင်မည် (Upload APK)' }, { text: '📲 အက်ပ် ဒေါင်းလုဒ်ရယူရန်' }],
      [{ text: '🎯 ပေါက်မဲ' }, { text: '🇹🇭 3D Live ရလဒ်' }],
      [{ text: '🆔 ကျွန်ုပ်၏ ID' }]
    ],
    resize_keyboard: true,
    is_persistent: true
  };
}

export function getResellerBottomKeyboard(): ReplyKeyboardMarkup {
  return {
    keyboard: [
      [{ text: '💼 ဒက်ရှ်ဘုတ်' }, { text: '➕ ကုတ်အသစ် ထုတ်မည်' }],
      [{ text: '🎁 ၃ ရက် Trial' }, { text: '📅 ၁ နှစ် လိုင်စင်' }, { text: '💎 တစ်သက်တာ လိုင်စင်' }],
      [{ text: '📦 အများပြား ထုတ်မည် (Bulk)' }, { text: '📲 အက်ပ် ဒေါင်းလုဒ်ရယူရန်' }],
      [{ text: '🇹🇭 3D Live ရလဒ်' }, { text: '🆔 ကျွန်ုပ်၏ ID' }]
    ],
    resize_keyboard: true,
    is_persistent: true
  };
}

export function getBuyerBottomKeyboard(): ReplyKeyboardMarkup {
  return {
    keyboard: [
      [{ text: '📲 အက်ပ် ဒေါင်းလုဒ်ရယူရန်' }, { text: '🎁 ၃ ရက် အခမဲ့ စမ်းသပ်ခွင့်' }],
      [{ text: '🛒 လိုင်စင် ဝယ်ယူမည်' }, { text: '🇹🇭 3D Live ရလဒ်' }],
      [{ text: '🔢 တွတ် ဂဏန်းများ' }, { text: '🎯 ပေါက်မဲ စစ်မည်' }],
      [{ text: '🆔 ကျွန်ုပ်၏ ID' }, { text: '❓ အကူအညီ' }]
    ],
    resize_keyboard: true,
    is_persistent: true
  };
}

// ── Admin Keyboards ───────────────────────────────────────────────────────────

export function getAdminMainMenuKeyboard(autoApprove: boolean, pendingCount = 0): InlineKeyboardMarkup {
  const pendingLabel = pendingCount > 0 ? `⏳ စောင့်ဆိုင်းဆဲ (${pendingCount}) 🔔` : `⏳ စောင့်ဆိုင်းဆဲ (၀)`;
  const autoLabel = autoApprove ? '⚡ Auto-Approve: ဖွင့်ထားသည် ✅' : '⚡ Auto-Approve: ပိတ်ထားသည် ❌';

  return {
    inline_keyboard: [
      [
        { text: '➕ ကုတ်အသစ် ထုတ်ရန်', callback_data: 'm_gen_menu' },
        { text: '🔑 ထုတ်ယူထားသော ကုတ်များ', callback_data: 'm_keys_gen:available:1' }
      ],
      [
        { text: '📋 အသုံးပြုမှု စောင့်ကြည့်', callback_data: 'm_keys_active' },
        { text: pendingLabel, callback_data: 'm_keys_pending' }
      ],
      [
        { text: '👥 ကိုယ်စားလှယ်များ (Resellers)', callback_data: 'm_resellers' },
        { text: '💳 ငွေလက်ခံ အကောင့်များ', callback_data: 'm_payments' }
      ],
      [
        { text: '👮‍♂️ Admin များ', callback_data: 'm_admins' },
        { text: '💵 ကော်မရှင် သတ်မှတ်ချက်', callback_data: 'm_commissions' }
      ],
      [
        { text: autoLabel, callback_data: 'm_toggle_auto' },
        { text: '📊 အရောင်း အစီရင်ခံစာ', callback_data: 'm_report' }
      ],
      [
        { text: '🚫 ကုတ် ပိတ်သိမ်းရန်', callback_data: 'm_revoke_list' },
        { text: '🎯 ပေါက်မဲ သတ်မှတ်', callback_data: 'm_declare_menu' }
      ],
      [
        { text: '📱 အက်ပ် စီမံခန့်ခွဲမှု (Release)', callback_data: 'm_app_manage' },
        { text: '📢 Update အားလုံးသို့ ပို့မည်', callback_data: 'm_broadcast_update' }
      ],
      [
        { text: '📦 အပတ်စဉ် ကြည့်ရှု', callback_data: 'm_batch' },
        { text: '🔄 မီနူး အသစ်ပြန်ဖွင့် (Refresh)', callback_data: 'm_refresh' }
      ]
    ]
  };
}

export function getBuyerPlansKeyboard(): InlineKeyboardMarkup {
  return {
    inline_keyboard: [
      [
        { text: '📅 ၁ နှစ် လိုင်စင် (ဖုန်းပြောင်းသုံးနိုင် ✅) - ၁၈၀,၀၀၀ Ks', callback_data: 'buy:one_year' }
      ],
      [
        { text: '💎 တစ်သက်တာ လိုင်စင် (ဖုန်း ၁ လုံးသာ 🔒) - ၄၅,၀၀၀ Ks', callback_data: 'buy:lifetime' }
      ]
    ]
  };
}

export function getOrderApprovalKeyboard(orderId: string): InlineKeyboardMarkup {
  return {
    inline_keyboard: [
      [
        { text: '✅ အတည်ပြုပြီး ကုတ်ထုတ်ပေးမည်', callback_data: `ord_app:${orderId}` },
        { text: '❌ ငြင်းပယ်မည်', callback_data: `ord_rej:${orderId}` }
      ]
    ]
  };
}

export function getResellerDashboardKeyboard(): InlineKeyboardMarkup {
  return {
    inline_keyboard: [
      [
        { text: '🎁 ၃ ရက် စမ်းသပ်ခွင့် (Trial)', callback_data: 'r_gen:trial_3d' }
      ],
      [
        { text: '📅 ၁ နှစ် လိုင်စင် (1-Year)', callback_data: 'r_gen:one_year' },
        { text: '💎 တစ်သက်တာ (Lifetime)', callback_data: 'r_gen:lifetime' }
      ],
      [
        { text: '📦 အများပြား ထုတ်မည် (Bulk Keys)', callback_data: 'r_bulk_menu' },
        { text: '🔑 ကျွန်ုပ်၏ ကုတ်များ (My Keys)', callback_data: 'r_my_keys:1' }
      ],
      [
        { text: '🔄 အသစ်ပြန်ဖွင့် (Refresh)', callback_data: 'r_refresh' }
      ]
    ]
  };
}

export function getResellerBulkKeyboard(): InlineKeyboardMarkup {
  return {
    inline_keyboard: [
      [
        { text: '📅 ၁ နှစ် (၅ ခု)', callback_data: 'r_gen_b:one_year:5' },
        { text: '📅 ၁ နှစ် (၁၀ ခု)', callback_data: 'r_gen_b:one_year:10' }
      ],
      [
        { text: '💎 တစ်သက်တာ (၅ ခု)', callback_data: 'r_gen_b:lifetime:5' },
        { text: '💎 တစ်သက်တာ (၁၀ ခု)', callback_data: 'r_gen_b:lifetime:10' }
      ],
      [
        { text: '🎁 ၃ ရက် Trial (၅ ခု)', callback_data: 'r_gen_b:trial_3d:5' },
        { text: '🎁 ၃ ရက် Trial (၁၀ ခု)', callback_data: 'r_gen_b:trial_3d:10' }
      ],
      [
        { text: '⬅️ ဒက်ရှ်ဘုတ်သို့ ပြန်သွားမည်', callback_data: 'r_refresh' }
      ]
    ]
  };
}

export function buildResellerDashboardMessage(reseller: ResellerRecord): { text: string; keyboard: InlineKeyboardMarkup } {
  const text = `💼 <b>3D LEDGER ကိုယ်စားလှယ် ဒက်ရှ်ဘုတ် (RESELLER PANEL)</b>\n\n` +
    `👤 <b>ကိုယ်စားလှယ် အမည်:</b> <b>${reseller.name}</b>\n` +
    `🆔 <b>Telegram ID:</b> <code>${reseller.telegram_id}</code>\n\n` +
    `📊 <b>ရောင်းအားနှင့် အသုံးပြုမှု အခြေအနေ:</b>\n` +
    `• 🔢 ထုတ်ယူခဲ့သော ကုတ်: <b>${reseller.total_generated || 0}</b> ခု\n` +
    `• 🟢 အသက်ဝင်ပြီးသော ကုတ်: <b>${reseller.total_activated || 0}</b> ခု\n` +
    `• 💵 ရရှိပြီးသော ကော်မရှင်: <b>${(reseller.total_commission || 0).toLocaleString()}</b> ကျပ်\n\n` +
    `💳 <b>ငွေစာရင်း အခြေအနေ (Due Accounting):</b>\n` +
    `• 📌 <b>Admin ထံ ပေးသွင်းရန် ကျန်ငွေ (Due):</b> <code>${(reseller.total_due || 0).toLocaleString()}</code> ကျပ်\n` +
    `• 💳 <b>ပေးသွင်းပြီးသော စုစုပေါင်း:</b> <code>${(reseller.total_paid || 0).toLocaleString()}</code> ကျပ်\n\n` +
    `<i>(ကုတ်နံပါတ် အသက်ဝင်ပါက ကော်မရှင် နုတ်ပြီး ကျန်ငွေကို Admin ထံ ပေးရန်အဖြစ် အလိုအလျောက် မှတ်တမ်းတင်ပါသည်)</i>\n\n` +
    `<i>အောက်ပါ ခလုတ်များမှ ကုတ်နံပါတ် အသစ် ထုတ်ယူနိုင်ပါသည် 👇</i>`;

  return { text, keyboard: getResellerDashboardKeyboard() };
}

export async function buildResellersListMessage(env: Env): Promise<{ text: string; keyboard: InlineKeyboardMarkup }> {
  const resellers = await getAllResellers(env);
  const list = Object.values(resellers);

  let text = `👥 <b>ကိုယ်စားလှယ်များ စာရင်း (${list.length} ဦး)</b>\n\n`;
  const ikb: InlineKeyboardButton[][] = [];

  if (list.length === 0) {
    text += `လက်ရှိတွင် ကိုယ်စားလှယ် မရှိသေးပါ။\n\nအောက်ပါ <b>[➕ ကိုယ်စားလှယ် အသစ် ထည့်မည်]</b> ခလုတ်ကို နှိပ်၍ အသစ် ထည့်သွင်းနိုင်ပါသည်။`;
  } else {
    for (let i = 0; i < list.length; i++) {
      const r = list[i];
      text += `<b>${i + 1}. ${r.name}</b> (<code>${r.telegram_id}</code>)\n` +
        `   • ထုတ်: ${r.total_generated || 0} ခု | သုံး: ${r.total_activated || 0} ခု\n` +
        `   • ကော်မရှင်: ${(r.total_commission || 0).toLocaleString()} Ks\n` +
        `   • 📌 ပေးရန်ကျန် (Due): <b>${(r.total_due || 0).toLocaleString()} Ks</b>\n` +
        `   • 💳 ပေးပြီး: ${(r.total_paid || 0).toLocaleString()} Ks\n\n`;

      ikb.push([
        { text: `💵 ${r.name} ငွေရှင်းမည်`, callback_data: `r_pay:${r.telegram_id}` },
        { text: `❌ ဖယ်ရှားမည်`, callback_data: `r_rem_conf:${r.telegram_id}` }
      ]);
    }
  }

  ikb.push([{ text: '➕ ကိုယ်စားလှယ် အသစ် ထည့်မည်', callback_data: 'r_add_prompt' }]);
  ikb.push([{ text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }]);

  return {
    text,
    keyboard: { inline_keyboard: ikb }
  };
}

export async function buildAdminsListMessage(env: Env): Promise<{ text: string; keyboard: InlineKeyboardMarkup }> {
  const admins = await getAdmins(env);
  let text = `👮‍♂️ <b>Admin များ စာရင်း (${admins.length} ဦး)</b>\n\n`;
  const ikb: InlineKeyboardButton[][] = [];

  for (let i = 0; i < admins.length; i++) {
    const a = admins[i];
    const isMaster = a.added_by === 'system';
    text += `<b>${i + 1}. ${a.name}</b> (<code>${a.telegram_id}</code>) ${isMaster ? '👑 [Master Admin]' : '🛡️ [Admin]'}\n`;
    if (!isMaster) {
      ikb.push([
        { text: `❌ ${a.name} အား Admin မှ ဖယ်ရှားမည်`, callback_data: `adm_rem_conf:${a.telegram_id}` }
      ]);
    }
  }

  ikb.push([{ text: '➕ Admin အသစ် ထည့်မည်', callback_data: 'adm_add_prompt' }]);
  ikb.push([{ text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }]);

  return {
    text,
    keyboard: { inline_keyboard: ikb }
  };
}

export async function buildPaymentsMessage(env: Env): Promise<{ text: string; keyboard: InlineKeyboardMarkup }> {
  const accounts = await getPaymentAccounts(env);
  const text = `💳 <b>ဝယ်ယူသူများ ငွေလွှဲလက်ခံမည့် အကောင့်များ</b>\n\n` +
    `📱 <b>Wave Pay:</b>\n` +
    `• နံပါတ်: <code>${accounts.wave.number}</code>\n` +
    `• အမည်: <b>${accounts.wave.name}</b>\n\n` +
    `📱 <b>KBZPay (KPay):</b>\n` +
    `• နံပါတ်: <code>${accounts.kpay.number}</code>\n` +
    `• အမည်: <b>${accounts.kpay.name}</b>\n\n` +
    `<i>ဖုန်းနံပါတ်ကို Copy ကူးရန် အောက်ပါ Copy ခလုတ်များကို နှိပ်ပါ (သို့မဟုတ်) ပြင်ဆင်နိုင်ပါသည် 👇</i>`;

  return {
    text,
    keyboard: {
      inline_keyboard: [
        [
          { text: `📋 Copy Wave (${accounts.wave.number})`, copy_text: { text: accounts.wave.number } },
          { text: `📋 Copy KPay (${accounts.kpay.number})`, copy_text: { text: accounts.kpay.number } }
        ],
        [
          { text: '✏️ Wave Pay ပြင်ဆင်မည်', callback_data: 'pay_ed:wave' },
          { text: '✏️ KBZPay ပြင်ဆင်မည်', callback_data: 'pay_ed:kpay' }
        ],
        [
          { text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }
        ]
      ]
    }
  };
}

export async function buildCommissionsMessage(env: Env): Promise<{ text: string; keyboard: InlineKeyboardMarkup }> {
  const commissions = await getCommissionConfig(env);
  const text = `💵 <b>ကိုယ်စားလှယ် ကော်မရှင် သတ်မှတ်ချက်များ</b>\n\n` +
    `• <b>တစ်သက်တာ လိုင်စင် (Lifetime):</b> <code>${commissions.lifetime.toLocaleString()}</code> ကျပ်\n` +
    `• <b>၁ နှစ် လိုင်စင် (1-Year):</b> <code>${commissions.one_year.toLocaleString()}</code> ကျပ်\n\n` +
    `<i>ကော်မရှင်နှုန်းထား ပြောင်းလဲလိုပါက အောက်ပါ ခလုတ်များကို နှိပ်ပါ 👇</i>`;

  return {
    text,
    keyboard: {
      inline_keyboard: [
        [
          { text: '✏️ Lifetime ကော်မရှင် ပြင်မည်', callback_data: 'com_menu:lifetime' },
          { text: '✏️ 1-Year ကော်မရှင် ပြင်မည်', callback_data: 'com_menu:one_year' }
        ],
        [
          { text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }
        ]
      ]
    }
  };
}

export function getKeyGenKeyboard(plans?: Record<string, SalePlan>): InlineKeyboardMarkup {
  return {
    inline_keyboard: [
      [
        { text: '📅 ၁ နှစ် (၁ ခု)', callback_data: 'gen_b:one_year:1' },
        { text: '📅 ၁ နှစ် (၅ ခု)', callback_data: 'gen_b:one_year:5' },
        { text: '📅 ၁ နှစ် (၁၀ ခု)', callback_data: 'gen_b:one_year:10' }
      ],
      [
        { text: '💎 တစ်သက်တာ (၁ ခု)', callback_data: 'gen_b:lifetime:1' },
        { text: '💎 တစ်သက်တာ (၅ ခု)', callback_data: 'gen_b:lifetime:5' },
        { text: '💎 တစ်သက်တာ (၁၀ ခု)', callback_data: 'gen_b:lifetime:10' }
      ],
      [
        { text: '🎁 ၃ ရက် Trial (၁ ခု)', callback_data: 'gen_b:trial_3d:1' },
        { text: '🎁 ၃ ရက် Trial (၅ ခု)', callback_data: 'gen_b:trial_3d:5' }
      ],
      [
        { text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }
      ]
    ]
  };
}

export function getPlansMenuKeyboard(plans: Record<string, SalePlan>): InlineKeyboardMarkup {
  const buttons: InlineKeyboardButton[][] = [];
  for (const [id, p] of Object.entries(plans)) {
    const priceText = p.price === 0 ? 'အခမဲ့' : `${p.price.toLocaleString()} Ks`;
    const tag = p.device_changeable ? 'စက်ပြောင်းနိုင်' : 'စက်ပြောင်းမရ';
    buttons.push([
      { text: `✏️ ပြင်ဆင်မည်: ${p.duration_label} (${priceText} - ${tag})`, callback_data: `plan_ed:${id}` }
    ]);
  }
  buttons.push([{ text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }]);
  return { inline_keyboard: buttons };
}

export function getPlanEditKeyboard(plan: SalePlan): InlineKeyboardMarkup {
  const buttons: InlineKeyboardButton[][] = [];
  if (plan.id === 'one_year') {
    buttons.push([
      { text: '💰 150,000 Ks', callback_data: `set_pr:${plan.id}:150000` },
      { text: '💰 180,000 Ks', callback_data: `set_pr:${plan.id}:180000` },
      { text: '💰 200,000 Ks', callback_data: `set_pr:${plan.id}:200000` }
    ]);
  } else if (plan.id === 'lifetime') {
    buttons.push([
      { text: '💰 35,000 Ks', callback_data: `set_pr:${plan.id}:35000` },
      { text: '💰 45,000 Ks', callback_data: `set_pr:${plan.id}:45000` },
      { text: '💰 50,000 Ks', callback_data: `set_pr:${plan.id}:50000` }
    ]);
  } else if (plan.id === 'trial_3d') {
    buttons.push([
      { text: '💰 0 Ks (အခမဲ့)', callback_data: `set_pr:${plan.id}:0` },
      { text: '💰 3,000 Ks', callback_data: `set_pr:${plan.id}:3000` },
      { text: '💰 5,000 Ks', callback_data: `set_pr:${plan.id}:5000` }
    ]);
  }
  buttons.push([
    { text: '🏷️ စီမံချက်များ အားလုံး ကြည့်ရှု', callback_data: 'm_plans' },
    { text: '⬅️ ပင်မ မီနူး', callback_data: 'm_main' }
  ]);
  return { inline_keyboard: buttons };
}

export function buildPlansDashboardMessage(plans: Record<string, SalePlan>): string {
  let text = `🏷️ <b>3D LEDGER အရောင်း အစီအစဉ်များ (SALE PLANS)</b>\n\n` +
    `ဆော့ဝဲလ် ရောင်းချရန် သတ်မှတ်ထားသော စီမံချက်များနှင့် ဈေးနှုန်းများကို ဤနေရာမှ တိုက်ရိုက် စစ်ဆေး/ပြင်ဆင်နိုင်ပါသည်:\n\n`;

  let idx = 1;
  for (const [, p] of Object.entries(plans)) {
    const priceText = p.price === 0 ? 'အခမဲ့ (0 ကျပ်)' : `${p.price.toLocaleString()} ကျပ်`;
    const changeText = p.device_changeable
      ? '✅ <b>စက်ပြောင်းနိုင်သည် (Device Changeable)</b> - ဖုန်းအသစ်သို့ လက်ကျန်ရက်များဖြင့် လွှဲပြောင်းနိုင်သည်'
      : '❌ <b>စက်ပြောင်းမရပါ (Non-Device Changeable)</b> - ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည်';

    text += `<b>${idx++}. ${p.name}</b>\n` +
      `   • 🆔 Plan ID: <code>${p.id}</code>\n` +
      `   • 💰 ဈေးနှုန်း: <b>${priceText}</b>\n` +
      `   • ⏳ သက်တမ်း: <b>${p.duration_label}</b>\n` +
      `   • 📱 စက်ပြောင်းလဲခွင့်: ${changeText}\n` +
      `   • 📝 ဖော်ပြချက်: ${p.description}\n\n`;
  }

  text += `💡 <b>ဈေးနှုန်း ပြင်ဆင်ရန် နည်းလမ်းများ:</b>\n` +
    `• အထက်ပါ ခလုတ်များမှ သက်ဆိုင်ရာ အစီအစဉ်ကို ရွေးချယ်၍ ဈေးနှုန်း ပြောင်းလဲနိုင်ပါသည် (သို့မဟုတ်)\n` +
    `• စာအမိန့်ဖြင့်: <code>/setprice [plan_id] [price]</code>\n` +
    `  <i>ဥပမာ: <code>/setprice one_year 180000</code> သို့မဟုတ် <code>/setprice lifetime 45000</code></i>`;

  return text;
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

export async function buildActiveKeysMessage(
  env: Env,
  page = 1
): Promise<{ text: string; keyboard: InlineKeyboardMarkup }> {
  const keys = await getAllLicenseKeys(env);
  const activeList = Object.values(keys).filter(isKeyInActiveUse);

  if (activeList.length === 0) {
    return {
      text: `📋 <b>အသုံးပြုဆဲ ကုတ်များ စာရင်း</b>\n\n` +
        `လက်ရှိတွင် အသုံးပြုနေသော (Active / Claimed) ကုတ်နံပါတ် မရှိသေးပါ။\n\n` +
        `💡 <i>ကုတ်နံပါတ်ကို တိုက်ရိုက် ရိုက်ထည့်၍ ပိတ်သိမ်းလိုပါက အောက်ပါ [🔍 ကုတ်နံပါတ်ဖြင့် Ban မည်] ခလုတ်ကို နှိပ်ပါ သို့မဟုတ် <code>/ban ကုတ်နံပါတ်</code> ဟု ပို့နိုင်ပါသည်။</i>`,
      keyboard: {
        inline_keyboard: [
          [{ text: '🔍 ကုတ်နံပါတ်ဖြင့် Ban မည်', callback_data: 'm_ban_by_input' }],
          [{ text: '🔄 စာရင်း အသစ်ပြန်ဖွင့် (Refresh)', callback_data: 'm_keys_active:1' }],
          [{ text: '⬅️ ပင်မ မီနူး', callback_data: 'm_main' }]
        ]
      }
    };
  }

  // Sort by newest activity first
  activeList.sort((a, b) => {
    const timeA = a.activated_at || a.created_at || (a as any).generated_at || 0;
    const timeB = b.activated_at || b.created_at || (b as any).generated_at || 0;
    return timeB - timeA;
  });

  const PAGE_SIZE = 6;
  const totalCount = activeList.length;
  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));
  const curPage = Math.min(Math.max(1, page), totalPages);
  const startIdx = (curPage - 1) * PAGE_SIZE;
  const pageItems = activeList.slice(startIdx, startIdx + PAGE_SIZE);

  let text = `📋 <b>အသုံးပြုဆဲ ကုတ်များ စာရင်း (Active / Claimed Keys)</b>\n` +
    `📊 စုစုပေါင်း: <b>${totalCount} ခု</b> (စာမျက်နှာ ${curPage}/${totalPages})\n\n` +
    `<i>အောက်ပါ ကုတ်များသည် ဖုန်းများတွင် အမှန်တကယ် စတင်အသုံးပြုထားသော ကုတ်များဖြစ်ပြီး Admin မှ လိုအပ်ပါက ချက်ချင်း ပိတ်သိမ်း (Ban/Revoke) နိုင်ပါသည် 👇</i>\n\n`;

  const keyboard: InlineKeyboardButton[][] = [];
  const now = Date.now();

  for (let i = 0; i < pageItems.length; i++) {
    const k = pageItems[i];
    const itemNum = startIdx + i + 1;
    let daysLeft = 'မကန့်သတ် (Lifetime)';
    if (k.expires_at) {
      const diff = Math.max(0, Math.ceil((k.expires_at - now) / (1000 * 60 * 60 * 24)));
      daysLeft = `${diff} ရက် ကျန်`;
    }

    const shortKey = k.cd_key.length > 14 ? `${k.cd_key.substring(0, 9)}...${k.cd_key.substring(k.cd_key.length - 4)}` : k.cd_key;
    const devPolicy = k.device_changeable ? '✅ စက်ပြောင်းနိုင်' : '🔒 ဖုန်း ၁ လုံးသာ';
    const planName = k.duration_label || (k.duration === 'lifetime' ? 'တစ်သက်တာ' : k.duration === 'trial' ? '၃ ရက် Trial' : `${k.duration} ရက်`);
    const actDate = k.activated_at ? new Date(k.activated_at).toLocaleDateString('my-MM') : (k.created_at ? new Date(k.created_at).toLocaleDateString('my-MM') : 'လတ်တလော');
    const deviceName = k.device_model || (k.claimed_by ? `User: ${k.claimed_by}` : (k.device_fingerprint ? `ID: ${k.device_fingerprint.substring(0, 10)}...` : 'အမည်မသိ ဖုန်း'));

    text += `<b>${itemNum}.</b> 🔑 <code>${k.cd_key}</code>\n` +
      `   📱 ဖုန်း: <b>${deviceName}</b>\n` +
      `   ⏳ သက်တမ်း: <b>${planName}</b> (${daysLeft})\n` +
      `   🔄 မူဝါဒ: <b>${devPolicy}</b> | အခြေအနေ: 🟢 <b>${k.status || 'active'}</b>\n` +
      `   ⏰ စတင်ချိန်: <i>${actDate}</i>\n\n`;

    keyboard.push([
      { text: `📋 ကုတ် ကူးယူမည် (${itemNum})`, copy_text: { text: k.cd_key } },
      { text: `🚫 Ban/Revoke မည် (${itemNum})`, callback_data: `rev_conf:${k.cd_key}` }
    ]);
  }

  // Pagination row
  const paginationRow: InlineKeyboardButton[] = [];
  if (curPage > 1) {
    paginationRow.push({ text: '⬅️ ရှေ့သို့', callback_data: `m_keys_active:${curPage - 1}` });
  }
  paginationRow.push({ text: `📄 ${curPage}/${totalPages}`, callback_data: `m_keys_active:${curPage}` });
  if (curPage < totalPages) {
    paginationRow.push({ text: 'နောက်သို့ ➡️', callback_data: `m_keys_active:${curPage + 1}` });
  }
  if (paginationRow.length > 1) {
    keyboard.push(paginationRow);
  }

  // Quick Action row
  keyboard.push([
    { text: '🔍 ကုတ်နံပါတ် ရိုက်ထည့်၍ Ban မည်', callback_data: 'm_ban_by_input' },
    { text: '🔄 Refresh စာရင်းပြန်ဖွင့်', callback_data: `m_keys_active:${curPage}` }
  ]);

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

export async function buildGeneratedKeysMessage(
  env: Env,
  filterStatus: 'available' | 'active' | 'pending_approval' | 'revoked' | 'all' = 'available',
  page = 1
): Promise<{ text: string; keyboard: InlineKeyboardMarkup }> {
  const allKeys = await getAllLicenseKeys(env);
  const list = Object.values(allKeys);

  // Filter keys
  let filtered = list;
  if (filterStatus === 'available') {
    filtered = list.filter(k => k.status === 'available');
  } else if (filterStatus === 'active') {
    filtered = list.filter(isKeyInActiveUse);
  } else if (filterStatus === 'pending_approval') {
    filtered = list.filter(k => k.status === 'pending_approval');
  } else if (filterStatus === 'revoked') {
    filtered = list.filter(k => k.status === 'revoked' || k.status === 'banned');
  }

  // Sort by created_at / generated_at descending (newest first)
  filtered.sort((a, b) => (b.created_at || (b as any).generated_at || 0) - (a.created_at || (a as any).generated_at || 0));

  const totalCount = filtered.length;
  const PAGE_SIZE = 8;
  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));
  const curPage = Math.min(Math.max(1, page), totalPages);
  const startIdx = (curPage - 1) * PAGE_SIZE;
  const pageItems = filtered.slice(startIdx, startIdx + PAGE_SIZE);

  const filterNames: Record<string, string> = {
    available: '⚪ ရောင်းရန်အသင့် (Available)',
    active: '🟢 အသုံးပြုဆဲ (Active / Claimed)',
    pending_approval: '⏳ စောင့်ဆိုင်းဆဲ (Pending)',
    revoked: '🔴 ပိတ်သိမ်းပြီး (Revoked)',
    all: '📋 အားလုံး (All)'
  };

  let text = `🔑 <b>ထုတ်ယူထားသော ကုတ်များ စာရင်း</b>\n` +
    `📂 အမျိုးအစား: <b>${filterNames[filterStatus] || filterStatus}</b>\n` +
    `📊 စုစုပေါင်း: <b>${totalCount} ခု</b> (စာမျက်နှာ ${curPage}/${totalPages})\n\n`;

  if (totalCount === 0) {
    text += `<i>လက်ရှိ အမျိုးအစားတွင် ကုတ်နံပါတ် မရှိသေးပါ။</i>\n\n` +
      `💡 အသစ်ထုတ်ယူလိုပါက ပင်မမီနူးမှ <b>[➕ ကုတ်အသစ် ထုတ်ရန်]</b> သို့မဟုတ် Web Admin မှ ထုတ်ယူနိုင်ပါသည်။`;
  } else {
    const now = Date.now();
    for (let i = 0; i < pageItems.length; i++) {
      const k = pageItems[i];
      const itemNum = startIdx + i + 1;
      let badge = '⚪';
      let statusText = 'ရောင်းရန်အသင့် (Available)';
      if (isKeyInActiveUse(k)) {
        badge = '🟢';
        statusText = `အသုံးပြုဆဲ (${k.status || 'active'})`;
      } else if (k.status === 'pending_approval') {
        badge = '⏳';
        statusText = 'စောင့်ဆိုင်းဆဲ (Pending)';
      } else if (k.status === 'revoked' || k.status === 'banned') {
        badge = '🔴';
        statusText = 'ပိတ်သိမ်းထား (Revoked)';
      }

      const planName = k.duration_label || (k.duration === 'lifetime' ? 'တစ်သက်တာ' : k.duration === 'trial' ? '၃ ရက် Trial' : `${k.duration} ရက်`);
      const priceStr = k.price ? `${k.price.toLocaleString()} Ks` : 'အခမဲ့';
      const createdTs = k.created_at || (k as any).generated_at;
      const createdDate = createdTs ? new Date(createdTs).toLocaleDateString('en-GB') : '-';
      const source = k.reseller_name
        ? `👤 ကိုယ်စားလှယ်: <b>${k.reseller_name}</b>`
        : k.generated_by_reseller_id
        ? `👤 ကိုယ်စားလှယ် ID: <code>${k.generated_by_reseller_id}</code>`
        : `🌐 Web Admin / Bot Admin`;

      const devPolicy = k.device_changeable ? '✅ စက်ပြောင်းနိုင်' : '🔒 ဖုန်း ၁ လုံးသာ';
      text += `<b>${itemNum}.</b> 🔑 <code>${k.cd_key}</code>\n` +
        `   • အခြေအနေ: ${badge} <b>${statusText}</b>\n` +
        `   • သက်တမ်း: <b>${planName}</b> (${priceStr}) | မူဝါဒ: <b>${devPolicy}</b>\n` +
        `   • ထုတ်ယူသူ: ${source} | ရက်စွဲ: <i>${createdDate}</i>\n`;

      if (isKeyInActiveUse(k)) {
        const dev = k.device_model || (k.claimed_by ? `User: ${k.claimed_by}` : (k.device_fingerprint ? `ID: ${k.device_fingerprint.substring(0, 10)}...` : 'မိုဘိုင်း'));
        let daysLeft = 'Lifetime';
        if (k.expires_at) {
          const diff = Math.max(0, Math.ceil((k.expires_at - now) / (1000 * 60 * 60 * 24)));
          daysLeft = `${diff} ရက် ကျန်`;
        }
        text += `   • 📱 ဖုန်း: <b>${dev}</b> (${daysLeft})\n`;
      }
      text += `\n`;
    }
  }

  // Build keyboard
  const tabRow1: InlineKeyboardButton[] = [
    { text: filterStatus === 'available' ? '🔘 ရောင်းရန်' : '⚪ ရောင်းရန်', callback_data: 'm_keys_gen:available:1' },
    { text: filterStatus === 'active' ? '🔘 အသုံးပြုဆဲ' : '🟢 အသုံးပြုဆဲ', callback_data: 'm_keys_gen:active:1' }
  ];
  const tabRow2: InlineKeyboardButton[] = [
    { text: filterStatus === 'pending_approval' ? '🔘 စောင့်ဆိုင်းဆဲ' : '⏳ စောင့်ဆိုင်းဆဲ', callback_data: 'm_keys_gen:pending_approval:1' },
    { text: filterStatus === 'revoked' ? '🔘 ပိတ်သိမ်းပြီး' : '🔴 ပိတ်သိမ်းပြီး', callback_data: 'm_keys_gen:revoked:1' },
    { text: filterStatus === 'all' ? '🔘 အားလုံး' : '📋 အားလုံး', callback_data: 'm_keys_gen:all:1' }
  ];

  const actionRows: InlineKeyboardButton[][] = [];

  // If viewing active keys, add Ban buttons for quick moderation!
  if (filterStatus === 'active' && pageItems.length > 0) {
    for (let i = 0; i < pageItems.length; i += 2) {
      const row: InlineKeyboardButton[] = [];
      const item1 = pageItems[i];
      const num1 = startIdx + i + 1;
      row.push({ text: `🚫 Ban (${num1})`, callback_data: `rev_conf:${item1.cd_key}` });
      if (i + 1 < pageItems.length) {
        const item2 = pageItems[i + 1];
        const num2 = startIdx + i + 2;
        row.push({ text: `🚫 Ban (${num2})`, callback_data: `rev_conf:${item2.cd_key}` });
      }
      actionRows.push(row);
    }
  }

  // If viewing revoked keys, add Unban buttons to restore access if needed!
  if (filterStatus === 'revoked' && pageItems.length > 0) {
    for (let i = 0; i < pageItems.length; i += 2) {
      const row: InlineKeyboardButton[] = [];
      const item1 = pageItems[i];
      const num1 = startIdx + i + 1;
      row.push({ text: `🟢 Unban (${num1})`, callback_data: `act_unban:${item1.cd_key}` });
      if (i + 1 < pageItems.length) {
        const item2 = pageItems[i + 1];
        const num2 = startIdx + i + 2;
        row.push({ text: `🟢 Unban (${num2})`, callback_data: `act_unban:${item2.cd_key}` });
      }
      actionRows.push(row);
    }
  }

  // Quick copy chips for available keys
  const copyRow: InlineKeyboardButton[] = [];
  const availableItems = pageItems.filter(k => k.status === 'available');
  if (availableItems.length > 0 && availableItems.length <= 4) {
    for (const item of availableItems) {
      const shortKey = item.cd_key.slice(-6);
      copyRow.push({ text: `📋 ..${shortKey}`, copy_text: { text: item.cd_key } });
    }
  }

  const paginationRow: InlineKeyboardButton[] = [];
  if (curPage > 1) {
    paginationRow.push({ text: '⬅️ ရှေ့သို့', callback_data: `m_keys_gen:${filterStatus}:${curPage - 1}` });
  }
  if (totalPages > 1) {
    paginationRow.push({ text: `📄 ${curPage}/${totalPages}`, callback_data: `m_keys_gen:${filterStatus}:${curPage}` });
  }
  if (curPage < totalPages) {
    paginationRow.push({ text: 'နောက်သို့ ➡️', callback_data: `m_keys_gen:${filterStatus}:${curPage + 1}` });
  }

  const navRow: InlineKeyboardButton[] = [
    { text: '➕ ကုတ်အသစ် ထုတ်မည်', callback_data: 'm_gen_menu' },
    { text: '⬅️ ပင်မ မီနူး', callback_data: 'm_main' }
  ];

  const inline_keyboard: InlineKeyboardButton[][] = [
    tabRow1,
    tabRow2,
    ...actionRows,
    ...(copyRow.length > 0 ? [copyRow] : []),
    ...(paginationRow.length > 0 ? [paginationRow] : []),
    navRow
  ];

  return { text, keyboard: { inline_keyboard } };
}

export async function buildResellerGeneratedKeysMessage(
  env: Env,
  resellerId: string,
  page = 1
): Promise<{ text: string; keyboard: InlineKeyboardMarkup }> {
  const allKeys = await getAllLicenseKeys(env);
  const myKeys = Object.values(allKeys).filter(k => k.generated_by_reseller_id === String(resellerId));

  // Sort newest first
  myKeys.sort((a, b) => (b.created_at || (b as any).generated_at || 0) - (a.created_at || (a as any).generated_at || 0));

  const totalCount = myKeys.length;
  const PAGE_SIZE = 8;
  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));
  const curPage = Math.min(Math.max(1, page), totalPages);
  const startIdx = (curPage - 1) * PAGE_SIZE;
  const pageItems = myKeys.slice(startIdx, startIdx + PAGE_SIZE);

  let text = `🔑 <b>ကျွန်ုပ် ထုတ်ယူထားသော ကုတ်များ စာရင်း</b>\n\n` +
    `📊 စုစုပေါင်း: <b>${totalCount} ခု</b> (စာမျက်နှာ ${curPage}/${totalPages})\n\n`;

  if (totalCount === 0) {
    text += `<i>သင်ထုတ်ယူထားသော ကုတ်နံပါတ် မရှိသေးပါ။</i>\n\n` +
      `💡 အောက်ပါ ခလုတ်များမှ ကုတ်နံပါတ် စတင်ထုတ်ယူနိုင်ပါသည်။`;
  } else {
    const now = Date.now();
    for (let i = 0; i < pageItems.length; i++) {
      const k = pageItems[i];
      const itemNum = startIdx + i + 1;
      let badge = '⚪';
      let statusText = 'ရောင်းရန်အသင့်';
      if (k.status === 'active') {
        badge = '🟢';
        statusText = 'အသုံးပြုဆဲ';
      } else if (k.status === 'pending_approval') {
        badge = '⏳';
        statusText = 'စောင့်ဆိုင်းဆဲ';
      } else if (k.status === 'revoked') {
        badge = '🔴';
        statusText = 'ပိတ်သိမ်းထား';
      }

      const planName = k.duration_label || (k.duration === 'lifetime' ? 'တစ်သက်တာ' : k.duration === 'trial' ? '၃ ရက် Trial' : `${k.duration} ရက်`);
      const createdTs = k.created_at || (k as any).generated_at;
      const createdDate = createdTs ? new Date(createdTs).toLocaleDateString('en-GB') : '-';

      const devPolicy = k.device_changeable ? '✅ စက်ပြောင်းနိုင်' : '🔒 ဖုန်း ၁ လုံးသာ';
      text += `<b>${itemNum}.</b> 🔑 <code>${k.cd_key}</code>\n` +
        `   • အခြေအနေ: ${badge} <b>${statusText}</b>\n` +
        `   • သက်တမ်း: <b>${planName}</b> | မူဝါဒ: <b>${devPolicy}</b>\n` +
        `   • ရက်စွဲ: <i>${createdDate}</i>\n`;

      if (k.status === 'active') {
        const dev = k.device_model || 'မိုဘိုင်း';
        let daysLeft = 'Lifetime';
        if (k.expires_at) {
          const diff = Math.max(0, Math.ceil((k.expires_at - now) / (1000 * 60 * 60 * 24)));
          daysLeft = `${diff} ရက် ကျန်`;
        }
        text += `   • 📱 ဖုန်း: ${dev} (${daysLeft})\n`;
      }
      text += `\n`;
    }
  }

  const copyRow: InlineKeyboardButton[] = [];
  const availableItems = pageItems.filter(k => k.status === 'available');
  if (availableItems.length > 0 && availableItems.length <= 4) {
    for (const item of availableItems) {
      const shortKey = item.cd_key.slice(-6);
      copyRow.push({ text: `📋 ..${shortKey}`, copy_text: { text: item.cd_key } });
    }
  }

  const paginationRow: InlineKeyboardButton[] = [];
  if (curPage > 1) {
    paginationRow.push({ text: '⬅️ ရှေ့သို့', callback_data: `r_my_keys:${curPage - 1}` });
  }
  if (totalPages > 1) {
    paginationRow.push({ text: `📄 ${curPage}/${totalPages}`, callback_data: `r_my_keys:${curPage}` });
  }
  if (curPage < totalPages) {
    paginationRow.push({ text: 'နောက်သို့ ➡️', callback_data: `r_my_keys:${curPage + 1}` });
  }

  const navRow: InlineKeyboardButton[] = [
    { text: '➕ ကုတ်အသစ် ထုတ်မည်', callback_data: 'r_bulk_menu' },
    { text: '⬅️ ဒက်ရှ်ဘုတ်သို့ ပြန်သွားမည်', callback_data: 'r_refresh' }
  ];

  const inline_keyboard: InlineKeyboardButton[][] = [
    ...(copyRow.length > 0 ? [copyRow] : []),
    ...(paginationRow.length > 0 ? [paginationRow] : []),
    navRow
  ];

  return { text, keyboard: { inline_keyboard } };
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

    const userIsAdmin = await isUserAdmin(chatId, env);
    const reseller = await getReseller(chatId, env);

    // 1.1 Direct Buyer Purchase Plan Buttons (open to anyone)
    if (data.startsWith('buy:')) {
      const planId = data.split(':')[1];
      const plans = await getSalePlans(env);
      const plan = plans[planId] || (planId === 'lifetime' ? getDefaultSalePlans().lifetime : getDefaultSalePlans().one_year);
      const accounts = await getPaymentAccounts(env);

      await setBuyerSession(chatId, {
        chat_id: chatId,
        plan_id: plan.id,
        plan_name: plan.name,
        price: plan.price,
        waiting_for_slip: true,
        updated_at: Date.now()
      }, env);

      await answerCallbackQuery(env, cq.id);

      const priceText = `${plan.price.toLocaleString()} ကျပ်`;
      const changeText = plan.device_changeable
        ? '✅ <b>ဖုန်းပြောင်းသုံးနိုင်သည် (Device Changeable)</b>\n<i>(ဖုန်းအသစ်လဲပါက စက်ဟောင်းအလိုအလျောက် ပိတ်သွားပြီး လက်ကျန်ရက်များဖြင့် ဖုန်းအသစ်တွင် ဆက်လက်အသုံးပြုနိုင်ပါသည်)</i>'
        : '🔒 <b>ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည် (1-Device Locked)</b>\n<i>(ပထမဆုံး အသက်သွင်းသည့် ဖုန်းတစ်လုံးတည်းတွင်သာ သက်တမ်းကုန်ဆုံးခြင်းမရှိဘဲ အသုံးပြုနိုင်ပြီး၊ အခြားဖုန်းသို့ ပြောင်းလဲ၍ မရပါ)</i>';
      const payText = `💳 <b>ငွေလွှဲပေးချေရန် အချက်အလက်များ</b>\n\n` +
        `📦 <b>ဝယ်ယူမည့် အစီအစဉ်:</b> <b>${plan.name}</b>\n` +
        `💰 <b>ကျသင့်ငွေ:</b> <b>${priceText}</b>\n` +
        `⏳ <b>သက်တမ်း:</b> <b>${plan.duration_label}</b>\n` +
        `📱 <b>စက်မူဝါဒ:</b> ${changeText}\n\n` +
        `----------------------------------\n` +
        `📱 <b>Wave Pay အကောင့်:</b>\n` +
        `• ဖုန်းနံပါတ်: <code>${accounts.wave.number}</code>\n` +
        `• အကောင့်အမည်: <b>${accounts.wave.name}</b>\n\n` +
        `📱 <b>KBZPay (KPay) အကောင့်:</b>\n` +
        `• ဖုန်းနံပါတ်: <code>${accounts.kpay.number}</code>\n` +
        `• အကောင့်အမည်: <b>${accounts.kpay.name}</b>\n` +
        `----------------------------------\n\n` +
        `📸 <b>ငွေလွှဲပြီးပါက လုပ်ဆောင်ရန်:</b>\n` +
        `ငွေလွှဲပြေစာ Screenshot (ဓာတ်ပုံ) ကို ဤ Bot သို့ ပေးပို့ (Send Photo) ပေးပါ။\n\n` +
        `<i>Admin မှ စစ်ဆေးအတည်ပြုပြီးသည်နှင့် Activation Key ကို ဤနေရာသို့ ချက်ချင်း အလိုအလျောက် ပေးပို့ပေးပါမည်။</i>`;

      const payKeyboard: InlineKeyboardMarkup = {
        inline_keyboard: [
          [
            { text: `📋 Wave (${accounts.wave.number}) ကူးမည်`, copy_text: { text: accounts.wave.number } },
            { text: `📋 KPay (${accounts.kpay.number}) ကူးမည်`, copy_text: { text: accounts.kpay.number } }
          ],
          [
            { text: '⬅️ အစီအစဉ်များ ပြန်ရွေးမည်', callback_data: 'b_buy_menu' }
          ]
        ]
      };

      await sendTelegramMessage(env, chatId, payText, payKeyboard);
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    // 1.2 Reseller Key Generation Callbacks
    else if (data.startsWith('r_gen:')) {
      if (!reseller) {
        await answerCallbackQuery(env, cq.id, '⛔ ကိုယ်စားလှယ် အဖြစ် စာရင်းမရှိပါ', true);
        return new Response('unauthorized', { status: 200 });
      }

      const planId = data.split(':')[1];
      if (planId !== 'trial_3d' && planId !== 'one_year' && planId !== 'lifetime') {
        await answerCallbackQuery(env, cq.id, 'ခွင့်ပြုမထားသော အစီအစဉ် ဖြစ်ပါသည်', true);
        return new Response('ok');
      }

      const plans = await getSalePlans(env);
      const plan = plans[planId] || getDefaultSalePlans()[planId];
      const newKey = generateCdKey();
      const record: LicenseKeyRecord = {
        cd_key: newKey,
        plan_id: plan.id,
        duration: plan.duration,
        duration_label: plan.duration_label,
        price: plan.price,
        device_changeable: plan.device_changeable,
        status: 'available',
        created_at: Date.now(),
        generated_by_reseller_id: String(chatId),
        reseller_name: reseller.name
      };

      await saveLicenseKey(env, record);
      reseller.total_generated = (reseller.total_generated || 0) + 1;
      await saveReseller(env, reseller);

      await answerCallbackQuery(env, cq.id, '✅ ကုတ် အောင်မြင်စွာ ထုတ်ယူပြီးပါပြီ');

      const priceText = plan.price === 0 ? 'အခမဲ့ (0 Ks)' : `${plan.price.toLocaleString()} ကျပ်`;
      const changeText = plan.device_changeable
        ? '✅ <b>ဖုန်းပြောင်းသုံးနိုင်သည် (Device Changeable)</b>'
        : '🔒 <b>ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည် (1-Device Locked)</b>';
      const msgText = `✅ <b>[ကိုယ်စားလှယ်] လိုင်စင်ကုတ် အသစ် ထုတ်ယူပြီးပါပြီ</b>\n\n` +
        `🔑 <b>ကုတ်နံပါတ် (CD-Key):</b>\n` +
        `<code>${newKey}</code>\n\n` +
        `📦 <b>အစီအစဉ်:</b> <b>${plan.name}</b>\n` +
        `⏳ <b>သက်တမ်း:</b> <b>${plan.duration_label}</b>\n` +
        `💰 <b>ဈေးနှုန်း:</b> <b>${priceText}</b>\n` +
        `📱 <b>စက်မူဝါဒ:</b> ${changeText}\n` +
        `📌 <b>အခြေအနေ:</b> ⚪ ရောင်းရန် အသင့်ရှိသည် (Available)\n` +
        `⏰ <b>ထုတ်သည့်အချိန်:</b> ${new Date().toLocaleTimeString('my-MM')}\n\n` +
        `<i>ဝယ်ယူသူထံ ပေးပို့ရန် အောက်ပါ ခလုတ်ကို နှိပ်၍ CD-Key ကို ချက်ချင်း Copy ကူးယူနိုင်ပါသည်။</i>`;

      const resellerKeyKb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [{ text: '📋 CD-Key ကူးယူမည် (Copy Key)', copy_text: { text: newKey } }],
          ...getResellerDashboardKeyboard().inline_keyboard
        ]
      };
      await sendTelegramMessage(env, chatId, msgText, resellerKeyKb);

      const adminNotice = `📢 <b>[ကိုယ်စားလှယ် ကုတ်ထုတ်ယူမှု]</b>\n\n` +
        `👤 ကိုယ်စားလှယ်: <b>${reseller.name}</b> (ID: <code>${chatId}</code>)\n` +
        `📦 အစီအစဉ်: <b>${plan.name}</b>\n` +
        `🔑 ကုတ်နံပါတ်: <code>${newKey}</code>\n` +
        `💰 ဈေးနှုန်း: <b>${priceText}</b>\n` +
        `⏰ အချိန်: ${new Date().toLocaleTimeString('my-MM')}`;
      await notifyAllAdmins(env, adminNotice);
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    // 1.2b Reseller Bulk Menu
    else if (data === 'r_bulk_menu') {
      if (!reseller) {
        await answerCallbackQuery(env, cq.id, '⛔ ကိုယ်စားလှယ် အဖြစ် စာရင်းမရှိပါ', true);
        return new Response('unauthorized', { status: 200 });
      }
      await answerCallbackQuery(env, cq.id);
      const text = `📦 <b>[ကိုယ်စားလှယ်] အများပြား ကုတ်ထုတ်ယူရန် (Bulk Keys)</b>\n\n` +
        `ထုတ်ယူလိုသော အစီအစဉ်နှင့် အရေအတွက်ကို အောက်ပါ ခလုတ်များမှ ရွေးချယ်ပါ 👇`;
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, getResellerBulkKeyboard());
      } else {
        await sendTelegramMessage(env, chatId, text, getResellerBulkKeyboard());
      }
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    // 1.2c Reseller Bulk Generation Execution
    else if (data.startsWith('r_gen_b:')) {
      if (!reseller) {
        await answerCallbackQuery(env, cq.id, '⛔ ကိုယ်စားလှယ် အဖြစ် စာရင်းမရှိပါ', true);
        return new Response('unauthorized', { status: 200 });
      }
      const [, planId, countStr] = data.split(':');
      const count = Math.min(50, Math.max(1, parseInt(countStr, 10) || 5));
      const plans = await getSalePlans(env);
      const plan = plans[planId] || getDefaultSalePlans()[planId];
      if (!plan) {
        await answerCallbackQuery(env, cq.id, 'အစီအစဉ် မတွေ့ရှိပါ', true);
        return new Response('ok');
      }

      const keysList: string[] = [];
      for (let i = 0; i < count; i++) {
        const k = generateCdKey();
        const rec: LicenseKeyRecord = {
          cd_key: k,
          plan_id: plan.id,
          duration: plan.duration,
          duration_label: plan.duration_label,
          price: plan.price,
          device_changeable: plan.device_changeable,
          status: 'available',
          created_at: Date.now(),
          generated_by_reseller_id: String(chatId),
          reseller_name: reseller.name
        };
        await saveLicenseKey(env, rec);
        keysList.push(k);
      }

      reseller.total_generated = (reseller.total_generated || 0) + count;
      await saveReseller(env, reseller);

      await answerCallbackQuery(env, cq.id, `✅ ကုတ်ပေါင်း (${count}) ခု ထုတ်ယူပြီးပါပြီ`);

      const formattedKeys = keysList.map((k, idx) => `${idx + 1}. <code>${k}</code>`).join('\n');
      const priceText = plan.price === 0 ? 'အခမဲ့ (0 Ks)' : `${plan.price.toLocaleString()} ကျပ်`;
      const changeText = plan.device_changeable
        ? '✅ <b>ဖုန်းပြောင်းသုံးနိုင်သည် (Device Changeable)</b>'
        : '🔒 <b>ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည် (1-Device Locked)</b>';

      const reply = `📦 <b>[ကိုယ်စားလှယ်] ကုတ်ပေါင်း (${count}) ခု အောင်မြင်စွာ ထုတ်ယူပြီးပါပြီ</b>\n\n` +
        `🏷️ အစီအစဉ်: <b>${plan.name}</b> (${priceText})\n` +
        `📱 စက်မူဝါဒ: ${changeText}\n\n` +
        `🔑 <b>ကုတ်နံပါတ်များ စာရင်း:</b>\n` +
        `${formattedKeys}\n\n` +
        `<i>ဝယ်ယူသူများထံသို့ ကုတ်များကို ပေးပို့နိုင်ပါသည်။ အောက်ပါခလုတ်ကို နှိပ်၍ ကုတ်အားလုံးကို တစ်ခါတည်း Copy ကူးယူနိုင်ပါသည် 👇</i>`;

      const bulkKb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [{ text: `📋 ကုတ်အားလုံး ကူးယူမည် (Copy All ${count} Keys)`, copy_text: { text: keysList.join('\n') } }],
          ...getResellerDashboardKeyboard().inline_keyboard
        ]
      };
      await sendTelegramMessage(env, chatId, reply, bulkKb);

      await notifyAllAdmins(env,
        `📢 <b>[ကိုယ်စားလှယ် ကုတ်ထုတ်ယူမှု - အများပြား]</b>\n\n` +
        `👤 ကိုယ်စားလှယ်: <b>${reseller.name}</b> (ID: <code>${chatId}</code>)\n` +
        `📦 အစီအစဉ်: <b>${plan.name}</b>\n` +
        `🔢 အရေအတွက်: <b>${count} ခု</b>\n` +
        `⏰ အချိန်: ${new Date().toLocaleTimeString('my-MM')}`
      );

      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    // 1.3 Reseller Dashboard Refresh
    else if (data === 'r_refresh') {
      if (!reseller) {
        await answerCallbackQuery(env, cq.id, '⛔ ကိုယ်စားလှယ် အဖြစ် စာရင်းမရှိပါ', true);
        return new Response('unauthorized', { status: 200 });
      }
      await answerCallbackQuery(env, cq.id, '🔄 ဒက်ရှ်ဘုတ် အသစ် ပြင်ဆင်ပြီးပါပြီ');
      const dash = buildResellerDashboardMessage(reseller);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, dash.text, dash.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, dash.text, dash.keyboard);
      }
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    // 1.4 Admin Buyer Order Verification
    else if (data.startsWith('ord_app:')) {
      if (!userIsAdmin) {
        await answerCallbackQuery(env, cq.id, '⛔ Admin သီးသန့် ဖြစ်ပါသည်', true);
        return new Response('unauthorized', { status: 200 });
      }

      const orderId = data.split(':')[1];
      const order = await getBuyerOrder(orderId, env);
      if (!order) {
        await answerCallbackQuery(env, cq.id, 'အော်ဒါ မတွေ့ရှိပါ', true);
        return new Response('ok');
      }

      if (order.status !== 'pending') {
        await answerCallbackQuery(env, cq.id, `ဤအော်ဒါသည် [${order.status}] ဖြစ်ပြီးပါပြီ`, true);
        return new Response('ok');
      }

      const plans = await getSalePlans(env);
      const plan = plans[order.plan_id] || (order.plan_id === 'lifetime' ? getDefaultSalePlans().lifetime : getDefaultSalePlans().one_year);

      const newKey = generateCdKey();
      const record: LicenseKeyRecord = {
        cd_key: newKey,
        plan_id: plan.id,
        duration: plan.duration,
        duration_label: plan.duration_label,
        price: plan.price,
        device_changeable: plan.device_changeable,
        status: 'available',
        created_at: Date.now(),
        claimed_by_telegram_id: order.buyer_chat_id,
        claimed_by_username: order.buyer_username || order.buyer_name
      };

      await saveLicenseKey(env, record);

      order.status = 'approved';
      order.delivered_key = newKey;
      await saveBuyerOrder(order, env);

      await answerCallbackQuery(env, cq.id, '✅ အော်ဒါ အတည်ပြုပြီး ကုတ်ထုတ်ပေးလိုက်ပါပြီ!', true);

      // Auto-send key directly to buyer
      const changeNotice = plan.device_changeable
        ? '✅ <b>ဖုန်းပြောင်းသုံးနိုင်သည် (Device Changeable)</b> - ဖုန်းအသစ်သို့ လွှဲပြောင်းနိုင်သည်'
        : '🔒 <b>ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည် (1-Device Locked)</b> - စက်ပြောင်းလဲ၍ မရပါ';

      const buyerMsg = `🎉 <b>သင့်ငွေလွှဲ အတည်ပြုပြီးပါပြီ!</b>\n\n` +
        `<b>3D LEDGER စာရင်းကိုင် ဆော့ဝဲလ်</b> လိုင်စင် အသုံးပြုခွင့်ကုတ်နံပါတ် ရရှိပါပြီ:\n\n` +
        `🔑 <b>သင်၏ လိုင်စင်ကုတ် (CD-Key):</b>\n` +
        `<code>${newKey}</code>\n\n` +
        `📦 အစီအစဉ်: <b>${order.plan_name}</b>\n` +
        `⏳ သက်တမ်း: <b>${plan.duration_label}</b>\n` +
        `📱 စက်မူဝါဒ: ${changeNotice}\n` +
        `💰 ပေးချေငွေ: <b>${order.price.toLocaleString()} ကျပ်</b>\n\n` +
        `<i>(အောက်ပါခလုတ်ကို နှိပ်၍ CD-Key ကို ချက်ချင်း Copy ကူးယူပြီး Android အက်ပ်တွင် ထည့်သွင်း အသက်သွင်းနိုင်ပါပြီ)</i>\n\n` +
        `ကျေးဇူးတင်ရှိပါသည်။`;

      const buyerKb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [{ text: '📋 CD-Key ကူးယူမည် (Copy Key)', copy_text: { text: newKey } }]
        ]
      };
      await sendTelegramMessage(env, order.buyer_chat_id, buyerMsg, buyerKb);

      // Update Admin message
      const adminUpdatedText = `✅ <b>အော်ဒါ အတည်ပြုပြီးပါပြီ (Approved)</b>\n\n` +
        `🧾 အော်ဒါအမှတ်: <code>${orderId}</code>\n` +
        `👤 ဝယ်ယူသူ: <b>${order.buyer_name}</b> (ID: <code>${order.buyer_chat_id}</code>)\n` +
        `📦 အစီအစဉ်: <b>${order.plan_name}</b>\n` +
        `💰 ပေးချေငွေ: <b>${order.price.toLocaleString()} ကျပ်</b>\n` +
        `🔑 ထုတ်ပေးလိုက်သော ကုတ်: <code>${newKey}</code>\n\n` +
        `<i>ဝယ်ယူသူထံသို့ ကုတ်နံပါတ် တိုက်ရိုက် ပေးပို့ပြီးပါပြီ။</i>`;

      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, adminUpdatedText);
      }
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    else if (data.startsWith('ord_rej:')) {
      if (!userIsAdmin) {
        await answerCallbackQuery(env, cq.id, '⛔ Admin သီးသန့် ဖြစ်ပါသည်', true);
        return new Response('unauthorized', { status: 200 });
      }

      const orderId = data.split(':')[1];
      const order = await getBuyerOrder(orderId, env);
      if (!order) {
        await answerCallbackQuery(env, cq.id, 'အော်ဒါ မတွေ့ရှိပါ', true);
        return new Response('ok');
      }

      order.status = 'rejected';
      await saveBuyerOrder(order, env);

      await answerCallbackQuery(env, cq.id, '❌ အော်ဒါ ငြင်းပယ်လိုက်ပါသည်', true);

      const rejBuyerMsg = `❌ <b>ငွေလွှဲပြေစာအား ပယ်ဖျက်လိုက်ပါသည်</b>\n\n` +
        `🧾 အော်ဒါအမှတ်: <code>${orderId}</code>\n` +
        `ငွေလွှဲပြေစာ မပြည့်စုံခြင်း သို့မဟုတ် ငွေဝင်ရောက်မှု မရှိသေးပါသဖြင့် ပယ်ဖျက်လိုက်ပါသည်။ အသေးစိတ် သိရှိလိုပါက Admin ထံ ဆက်သွယ်ပါ။`;
      await sendTelegramMessage(env, order.buyer_chat_id, rejBuyerMsg);

      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, `❌ <b>အော်ဒါ ငြင်းပယ်ပြီးပါပြီ</b>\n🧾 အော်ဒါအမှတ်: <code>${orderId}</code>`);
      }
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    // 1.5 Activation Approvals (Admin OR Key Reseller)
    else if (data.startsWith('act_app:')) {
      const keyToApprove = data.split(':')[1];
      let keyRecord: LicenseKeyRecord | null = null;
      try {
        const token = await getFirebaseToken(env);
        const kRes = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${keyToApprove}.json`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (kRes.ok) keyRecord = await kRes.json() as LicenseKeyRecord;
      } catch (_) {}

      const isKeyReseller = keyRecord?.generated_by_reseller_id === String(chatId);
      if (!userIsAdmin && !isKeyReseller) {
        await answerCallbackQuery(env, cq.id, '⛔ ဤကုတ်အား အတည်ပြုရန် ခွင့်ပြုချက် မရှိပါ', true);
        return new Response(JSON.stringify({ status: 'unauthorized' }), { headers: { 'Content-Type': 'application/json' } });
      }

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
            inline_keyboard: [[{ text: '⬅️ ပင်မ မီနူး', callback_data: userIsAdmin ? 'm_main' : 'r_refresh' }]]
          });
        }
      } else {
        await answerCallbackQuery(env, cq.id, '❌ ခွင့်ပြုခြင်း မအောင်မြင်ပါ', true);
      }
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    else if (data.startsWith('act_rej:')) {
      const keyToReject = data.split(':')[1];
      let keyRecord: LicenseKeyRecord | null = null;
      try {
        const token = await getFirebaseToken(env);
        const kRes = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${keyToReject}.json`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (kRes.ok) keyRecord = await kRes.json() as LicenseKeyRecord;
      } catch (_) {}

      const isKeyReseller = keyRecord?.generated_by_reseller_id === String(chatId);
      if (!userIsAdmin && !isKeyReseller) {
        await answerCallbackQuery(env, cq.id, '⛔ ဤကုတ်အား ငြင်းပယ်ရန် ခွင့်ပြုချက် မရှိပါ', true);
        return new Response(JSON.stringify({ status: 'unauthorized' }), { headers: { 'Content-Type': 'application/json' } });
      }

      await rejectLicenseKey(env, keyToReject);
      await answerCallbackQuery(env, cq.id, '❌ ငြင်းပယ်လိုက်ပါပြီ', true);
      const text = `❌ <b>အသုံးပြုခွင့် ငြင်းပယ်လိုက်ပါသည် (Rejected)</b>\n\n` +
        `🔑 ကုတ်နံပါတ်: <code>${keyToReject}</code>\n` +
        `အဆိုပါ တောင်းဆိုမှုကို ပယ်ဖျက်လိုက်ပါပြီ။ ကုတ်အား အခြားဖုန်းတွင် ပြန်လည်အသုံးပြုနိုင်ပါသည်။`;
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, {
          inline_keyboard: [[{ text: '⬅️ ပင်မ မီနူး', callback_data: userIsAdmin ? 'm_main' : 'r_refresh' }]]
        });
      }
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    // Public Tut Calculator callback: tut:<number>
    else if (data.startsWith('tut:')) {
      const num = data.split(':')[1];
      if (/^\d{3}$/.test(num)) {
        const tut = calculateTutNumbers(num);
        await answerCallbackQuery(env, cq.id, `ဂဏန်း ${num} ၏ တွတ်များ`);
        const tutText = `🔢 <b>ဂဏန်း <code>${num}</code> ၏ တွတ် ဂဏန်းများ (${tut.allTut.length} ကွက်):</b>\n\n` +
          `<code>${tut.allTut.join(', ')}</code>\n\n` +
          `• <i>အပြန်:</i> ${tut.permutations.join(', ') || 'မရှိပါ'}\n` +
          `• <i>ကပ်သီး:</i> ${tut.nearMisses.join(', ')}`;
        await sendTelegramMessage(env, chatId, tutText);
      } else {
        await answerCallbackQuery(env, cq.id, 'ဂဏန်း မှားယွင်းနေပါသည်', true);
      }
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    // Public Live Refresh callback
    else if (data === 'b_live_refresh') {
      const glo = await fetchFromGloLottery();
      await answerCallbackQuery(env, cq.id, '🔄 3D Live ရလဒ် စစ်ဆေးပြီးပါပြီ');
      if (glo) {
        const liveKb: InlineKeyboardMarkup = {
          inline_keyboard: [
            [
              { text: '🔄 အသစ်ပြန်စစ်မည်', callback_data: 'b_live_refresh' },
              { text: '🔢 တွတ် ဂဏန်းများ', callback_data: `tut:${glo.threeD}` }
            ],
            [
              { text: '📲 အက်ပ် ဒေါင်းလုဒ်ရယူရန်', callback_data: 'b_app_download' },
              { text: '🛒 လိုင်စင် ဝယ်ယူမည်', callback_data: 'b_buy_menu' }
            ]
          ]
        };
        const text = `🇹🇭 <b>ထိုင်း GLO တရားဝင် 3D ရလဒ်</b>\n\n` +
          `🎯 <b>3D ပေါက်ဂဏန်း:</b> <code>${glo.threeD}</code>\n` +
          `🥇 <b>ပထမဆု (1st Prize):</b> <code>${glo.firstPrize}</code>\n` +
          `🔢 <b>2D:</b> <code>${glo.twoD}</code>\n` +
          `📅 <b>ထွက်သည့်ရက်:</b> ${glo.date}\n` +
          `⚡ <b>ရင်းမြစ်:</b> ${glo.source || glo.session}`;
        await sendTelegramMessage(env, chatId, text, liveKb);
      }
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    // Public App Download callback
    else if (data === 'b_app_download') {
      await answerCallbackQuery(env, cq.id);
      await sendAppToUser(env, chatId, senderId, cq.from?.username, cq.from?.first_name);
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    // Admin Broadcast Update callback
    else if (data === 'm_broadcast_update') {
      if (!userIsAdmin) {
        await answerCallbackQuery(env, cq.id, '⛔ Admin သီးသန့် ဖြစ်ပါသည်', true);
        return new Response('unauthorized', { status: 200 });
      }
      await answerCallbackQuery(env, cq.id, '📢 Update အားလုံးသို့ ပို့ဆောင်နေပါသည်...');
      await broadcastAppUpdate(env, chatId);
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    // Admin App Release Management callback
    else if (data === 'm_app_manage') {
      if (!userIsAdmin) {
        await answerCallbackQuery(env, cq.id, '⛔ Admin သီးသန့် ဖြစ်ပါသည်', true);
        return new Response('unauthorized', { status: 200 });
      }
      await answerCallbackQuery(env, cq.id);
      const release = await getAppRelease(env);
      const curInfo = release ?
        `• <b>ဖိုင်အမည်:</b> <code>${release.file_name}</code>\n` +
        `• <b>ဗားရှင်း:</b> <b>${release.version_name}</b> (Code: #${release.version_code || 100})\n` +
        `• <b>အရွယ်အစား:</b> ${((release.file_size || 0)/(1024*1024)).toFixed(2)} MB\n` +
        `• <b>Telegram File ID:</b> <code>${release.file_id || 'မရှိပါ (Download link သုံးထားသည်)'}</code>\n` +
        (release.download_url ? `• <b>Download Link:</b> ${release.download_url}\n` : '') +
        `• <b>တင်သည့်အချိန်:</b> ${new Date(release.uploaded_at).toLocaleString('my-MM')}\n` +
        `• <b>မှတ်ချက်:</b> ${release.release_notes || '-'}` :
        `<i>လက်ရှိတွင် အက်ပ်ဗားရှင်း မတင်ရသေးပါ</i>`;

      const msgText = `📱 <b>[အက်ပ်ဗားရှင်း စီမံခန့်ခွဲမှု] APK Release Control</b>\n\n` +
        `${curInfo}\n\n` +
        `💡 <b>အက်ပ် အသစ် တင်ရန် နည်းလမ်းများ:</b>\n` +
        `၁။ <b>Direct Upload:</b> APK ဖိုင်ကို ဤ Bot ထံ File (Document) အဖြစ် တိုက်ရိုက် ပို့ပါ\n` +
        `၂။ <b>File ID ဖြင့် သတ်မှတ်ရန်:</b> <code>/setappid [file_id] [version] [notes]</code>\n` +
        `၃။ <b>Direct Link ဖြင့် သတ်မှတ်ရန်:</b> <code>/setapplink [url] [version] [notes]</code>\n` +
        `၄။ <b>GitHub Auto-Sync:</b> GitHub Release ထွက်တိုင်း ဤနေရာသို့ အလိုအလျောက် ရောက်ရှိပါမည်။`;

      const manageKb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [
            { text: '📢 Update အားလုံးသို့ ပို့မည်', callback_data: 'm_broadcast_update' },
            { text: '📥 Test Download', callback_data: 'b_app_download' }
          ],
          [
            { text: '🔄 ဒက်ရှ်ဘုတ် သို့ ပြန်သွားရန်', callback_data: 'm_refresh' }
          ]
        ]
      };

      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, msgText, manageKb);
      } else {
        await sendTelegramMessage(env, chatId, msgText, manageKb);
      }
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    // Public Buy Menu callback
    else if (data === 'b_buy_menu') {
      await answerCallbackQuery(env, cq.id);
      const plans = await getSalePlans(env);
      const oneYearPrice = plans.one_year?.price ? plans.one_year.price.toLocaleString() : '180,000';
      const lifetimePrice = plans.lifetime?.price ? plans.lifetime.price.toLocaleString() : '45,000';
      const buyMsg = `💎 <b>3D LEDGER လိုင်စင် အစီအစဉ်များ ဝယ်ယူရန်</b>\n\n` +
        `📅 <b>၁ နှစ် သက်တမ်း (1-Year Plan):</b>\n` +
        `• 💰 ဈေးနှုန်း: <b>${oneYearPrice} ကျပ်</b> / နှစ်\n` +
        `• 📱 စက်မူဝါဒ: ✅ <b>ဖုန်းပြောင်းသုံးနိုင်သည် (Device Changeable)</b>\n` +
        `  <i>(ဖုန်းလဲပါက လက်ကျန်ရက်များဖြင့် စက်အသစ်သို့ ပြောင်းသုံးနိုင်ပါသည်)</i>\n\n` +
        `💎 <b>တစ်သက်တာ သက်တမ်း (Lifetime Plan):</b>\n` +
        `• 💰 ဈေးနှုန်း: <b>${lifetimePrice} ကျပ်</b>\n` +
        `• 📱 စက်မူဝါဒ: 🔒 <b>ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည် (1-Device Locked)</b>\n` +
        `  <i>(ဖုန်း ၁ လုံးတည်းတွင်သာ အသုံးပြုနိုင်ပြီး အခြားဖုန်းသို့ ပြောင်းမရပါ)</i>\n\n` +
        `ဝယ်ယူလိုသော အစီအစဉ်ကို အောက်ပါ ခလုတ်မှ ရွေးချယ်ပါ 👇`;
      await sendTelegramMessage(env, chatId, buyMsg, getBuyerPlansKeyboard());
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    // Public Help callback
    else if (data === 'b_help') {
      await answerCallbackQuery(env, cq.id);
      const helpText = `📖 <b>3D LEDGER စာရင်းကိုင် ဆော့ဝဲလ် အသုံးပြုနည်း လမ်းညွှန်</b>\n\n` +
        `1️⃣ <b>အက်ပ် ဒေါင်းလုဒ်ဆွဲခြင်း:</b>\n` +
        `• <b>[📲 အက်ပ် ဒေါင်းလုဒ်ရယူရန်]</b> ခလုတ်ကို နှိပ်၍ APK ကို ဖုန်းထဲသို့ ဒေါင်းလုဒ်ဆွဲပြီး Install လုပ်ပါ။\n\n` +
        `2️⃣ <b>အခမဲ့ စမ်းသပ်ခွင့် (Free Trial):</b>\n` +
        `• <b>[🎁 ၃ ရက် အခမဲ့ စမ်းသပ်ခွင့်]</b> ခလုတ်ကို နှိပ်၍ ရရှိလာသော CD-Key ကို အက်ပ်တွင် ထည့်သွင်းပါ။\n` +
        `• စတင်ထည့်သွင်းချိန်မှ ၇၂ နာရီ စတင် ရေတွက်ပါမည်။\n\n` +
        `3️⃣ <b>လိုင်စင် ဝယ်ယူခြင်း:</b>\n` +
        `• <b>[🛒 လိုင်စင် ဝယ်ယူမည်]</b> ခလုတ်ကို နှိပ်ပြီး Wave Pay / KBZPay သို့ ငွေလွှဲပြေစာ ပို့ပေးပါက Admin မှ လိုင်စင် အတည်ပြုပေးပါမည်။`;
      await sendTelegramMessage(env, chatId, helpText);
      return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
    }

    // 1.6 Admin Menus (Requires Admin)
    if (!userIsAdmin) {
      await answerCallbackQuery(env, cq.id, '⛔ ခွင့်ပြုချက် မရှိပါ (Unauthorized)', true);
      return new Response(JSON.stringify({ status: 'unauthorized' }), { headers: { 'Content-Type': 'application/json' } });
    }

    await answerCallbackQuery(env, cq.id);

    // Resellers Menu
    if (data === 'm_resellers') {
      const res = await buildResellersListMessage(env);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, res.text, res.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      }
    }

    // Admins Menu
    else if (data === 'm_admins') {
      const res = await buildAdminsListMessage(env);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, res.text, res.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      }
    }

    // Payments Accounts Menu
    else if (data === 'm_payments') {
      const res = await buildPaymentsMessage(env);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, res.text, res.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      }
    }

    // Commissions Menu
    else if (data === 'm_commissions') {
      const res = await buildCommissionsMessage(env);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, res.text, res.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      }
    }

    // Main Menu
    else if (data === 'm_main' || data === 'm_refresh') {
      const dash = await buildAdminDashboardMessage(env);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, dash.text, dash.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, dash.text, dash.keyboard);
      }
    }

    // Sale Plans Dashboard Menu
    else if (data === 'm_plans') {
      const plans = await getSalePlans(env);
      const text = buildPlansDashboardMessage(plans);
      const kb = getPlansMenuKeyboard(plans);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Specific Plan Edit Menu
    else if (data.startsWith('plan_ed:')) {
      const planId = data.split(':')[1];
      const plans = await getSalePlans(env);
      const plan = plans[planId];
      if (!plan) {
        await answerCallbackQuery(env, cq.id, 'အစီအစဉ် မတွေ့ရှိပါ', true);
        return new Response('ok');
      }
      const priceText = plan.price === 0 ? 'အခမဲ့' : `${plan.price.toLocaleString()} ကျပ်`;
      const changeText = plan.device_changeable ? '✅ ပြောင်းနိုင်သည်' : '❌ မရပါ (1 Device)';
      const text = `✏️ <b>အစီအစဉ် ပြင်ဆင်ရန်: ${plan.name}</b>\n\n` +
        `• 🆔 Plan ID: <code>${plan.id}</code>\n` +
        `• 💰 လက်ရှိ ဈေးနှုန်း: <b>${priceText}</b>\n` +
        `• ⏳ သက်တမ်း: <b>${plan.duration_label}</b>\n` +
        `• 📱 စက်ပြောင်းခွင့်: <b>${changeText}</b>\n\n` +
        `ဈေးနှုန်း အသစ်ကို အောက်ပါ ခလုတ်များမှ ရွေးချယ်ပါ (သို့မဟုတ် <code>/setprice ${plan.id} [price]</code> ဖြင့် စာပို့ပါ):`;
      const kb = getPlanEditKeyboard(plan);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Set Price Execution from Button
    else if (data.startsWith('set_pr:')) {
      const [, planId, priceStr] = data.split(':');
      const newPrice = parseInt(priceStr, 10) || 0;
      await updatePlanPrice(env, planId, newPrice);
      await answerCallbackQuery(env, cq.id, `✅ ဈေးနှုန်း ${newPrice.toLocaleString()} Ks သို့ ပြောင်းလဲပြီးပါပြီ`, true);
      const plans = await getSalePlans(env);
      const text = buildPlansDashboardMessage(plans);
      const kb = getPlansMenuKeyboard(plans);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Key Generation Menu (Dynamic from Sale Plans)
    else if (data === 'm_gen_menu') {
      const plans = await getSalePlans(env);
      const text = `➕ <b>ကုတ်အသစ် ထုတ်ယူရန် စီမံချက် ရွေးချယ်ပါ</b>\n\n` +
        `အသုံးပြုသူထံ ရောင်းချလိုသော အစီအစဉ်နှင့် ဈေးနှုန်းကို အောက်ပါ ခလုတ်များမှ ရွေးချယ်ပါ 👇`;
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, getKeyGenKeyboard(plans));
      } else {
        await sendTelegramMessage(env, chatId, text, getKeyGenKeyboard(plans));
      }
    }

    // Generate Key Execution from Plan: gen_p:<plan_id>
    else if (data.startsWith('gen_p:')) {
      const planId = data.split(':')[1];
      const plans = await getSalePlans(env);
      const plan = plans[planId] || getDefaultSalePlans()[planId];
      if (!plan) {
        await sendTelegramMessage(env, chatId, '❌ <i>အစီအစဉ် မတွေ့ရှိပါ</i>');
        return new Response('ok');
      }

      const newKey = generateCdKey();
      const record: LicenseKeyRecord = {
        cd_key: newKey,
        plan_id: plan.id,
        duration: plan.duration,
        duration_label: plan.duration_label,
        price: plan.price,
        device_changeable: plan.device_changeable,
        status: 'available',
        created_at: Date.now()
      };

      const saved = await saveLicenseKey(env, record);
      if (saved) {
        const priceText = plan.price === 0 ? 'အခမဲ့ (0 Ks)' : `${plan.price.toLocaleString()} ကျပ်`;
        const changeText = plan.device_changeable ? '✅ စက်ပြောင်းလဲနိုင်သည်' : '❌ စက်ပြောင်းမရပါ (ဖုန်း ၁ လုံးသီးသန့်)';
        const text = `✅ <b>လိုင်စင်ကုတ် အသစ် ထုတ်ယူပြီးပါပြီ</b>\n\n` +
          `🔑 <b>ကုတ်နံပါတ် (CD-Key):</b>\n` +
          `<code>${newKey}</code>\n\n` +
          `📦 <b>အစီအစဉ်:</b> <b>${plan.name}</b>\n` +
          `⏳ <b>သက်တမ်း:</b> <b>${plan.duration_label}</b>\n` +
          `💰 <b>ဈေးနှုန်း:</b> <b>${priceText}</b>\n` +
          `📱 <b>စက်ပြောင်းလဲခွင့်:</b> <b>${changeText}</b>\n` +
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

    // Generate Key Execution: gen:<days>:<price>:<label> (Fallback)
    else if (data.startsWith('gen:')) {
      const [, daysStr, priceStr, label] = data.split(':');
      const newKey = generateCdKey();
      const price = parseInt(priceStr, 10) || 0;
      const duration = daysStr === 'lifetime' ? 'lifetime' : (daysStr === 'trial' ? 'trial' : (parseInt(daysStr, 10) || 30));
      const durLabel = decodeURIComponent(label || daysStr).replace(/_/g, ' ');
      const isChangeable = daysStr === '365' || daysStr === '1_year';

      const record: LicenseKeyRecord = {
        cd_key: newKey,
        duration: duration,
        duration_label: durLabel,
        price: price,
        device_changeable: isChangeable,
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

    // Active Keys Monitor: m_keys_active[:<page>] or m_revoke_list[:<page>]
    else if (data.startsWith('m_keys_active') || data.startsWith('m_revoke_list')) {
      const parts = data.split(':');
      const page = parseInt(parts[1] || '1', 10) || 1;
      const res = await buildActiveKeysMessage(env, page);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, res.text, res.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      }
    }

    // Direct Input Ban Prompt: m_ban_by_input
    else if (data === 'm_ban_by_input') {
      await setAdminState(chatId, {
        chat_id: chatId,
        action: 'awaiting_ban_key',
        created_at: Date.now()
      }, env);
      await answerCallbackQuery(env, cq.id);
      const promptText = `🔍 <b>ပိတ်သိမ်းမည့် (Ban/Revoke) ကုတ်နံပါတ် ရိုက်ထည့်ပါ</b>\n\n` +
        `ဖုန်းများတွင် အသုံးပြုဆဲဖြစ်သော သို့မဟုတ် Ban/Revoke ပြုလုပ်လိုသော ကုတ်နံပါတ် (ဥပမာ: <code>ABCD-1234-EFGH</code>) ကို ဤနေရာတွင် စာရိုက်၍ ပို့ပေးပါ:\n\n` +
        `<i>(မလုပ်တော့ပါက အောက်ပါ 'မလုပ်တော့ပါ' ခလုတ်ကို နှိပ်ပါ သို့မဟုတ် <code>/cancel</code> ဟု ရိုက်ထည့်ပါ)</i>`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [{ text: '❌ မလုပ်တော့ပါ (Cancel)', callback_data: 'm_keys_active:1' }]
        ]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, promptText, kb);
      } else {
        await sendTelegramMessage(env, chatId, promptText, kb);
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

    // Generated / Categorized Keys Monitor: m_keys_gen[:<filter>[:<page>]]
    else if (data.startsWith('m_keys_gen')) {
      const parts = data.split(':');
      const filter = (parts[1] || 'available') as 'available' | 'active' | 'pending_approval' | 'revoked' | 'all';
      const page = parseInt(parts[2] || '1', 10) || 1;
      const res = await buildGeneratedKeysMessage(env, filter, page);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, res.text, res.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      }
    }

    // Reseller Generated Keys Monitor: r_my_keys[:<page>]
    else if (data.startsWith('r_my_keys')) {
      const parts = data.split(':');
      const page = parseInt(parts[1] || '1', 10) || 1;
      const res = await buildResellerGeneratedKeysMessage(env, String(chatId), page);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, res.text, res.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, res.text, res.keyboard);
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
            { text: '❌ မလုပ်တော့ပါ', callback_data: 'm_keys_active:1' }
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
          inline_keyboard: [
            [{ text: '📋 အသုံးပြုဆဲ ကုတ်များ ကြည့်မည်', callback_data: 'm_keys_active:1' }],
            [{ text: '⬅️ ပင်မ မီနူး', callback_data: 'm_main' }]
          ]
        });
      }
    }

    // Execute Unban Key: act_unban:<key>
    else if (data.startsWith('act_unban:')) {
      const keyToUnban = data.split(':')[1];
      const ok = await unbanLicenseKey(env, keyToUnban);
      if (ok) {
        await answerCallbackQuery(env, cq.id, '🟢 ကုတ် ပြန်လည်အသုံးပြုနိုင်ပါပြီ', true);
        const text = `🟢 <b>ကုတ်နံပါတ် ပြန်လည်ဖွင့်ပေးလိုက်ပါပြီ (Unbanned)</b>\n\n` +
          `🔑 <code>${keyToUnban}</code>\n\n` +
          `အဆိုပါ ကုတ်ကို Available အခြေအနေသို့ ပြန်လည်သတ်မှတ်လိုက်ပြီဖြစ်၍ မည်သည့် ဖုန်းတွင်မဆို ပြန်လည် အသုံးပြုနိုင်ပါပြီ။`;
        if (messageId) {
          await editTelegramMessage(env, chatId, messageId, text, {
            inline_keyboard: [
              [{ text: '📋 ကုတ်များ အားလုံး စာရင်း', callback_data: 'm_keys_gen:all:1' }],
              [{ text: '⬅️ ပင်မ မီနူး', callback_data: 'm_main' }]
            ]
          });
        }
      } else {
        await answerCallbackQuery(env, cq.id, '❌ မအောင်မြင်ပါ', true);
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

    // Admin Key Generation Execution (Chips: 1, 5, 10): gen_b:<planId>:<count>
    else if (data.startsWith('gen_b:')) {
      const [, planId, countStr] = data.split(':');
      const count = Math.min(50, Math.max(1, parseInt(countStr, 10) || 1));
      const plans = await getSalePlans(env);
      const plan = plans[planId] || getDefaultSalePlans()[planId];
      if (!plan) {
        await sendTelegramMessage(env, chatId, '❌ <i>အစီအစဉ် မတွေ့ရှိပါ</i>');
        return new Response('ok');
      }

      const isChangeable = plan.device_changeable;
      const changeText = isChangeable
        ? '✅ <b>ဖုန်းပြောင်းသုံးနိုင်သည် (Device Changeable)</b>'
        : '🔒 <b>ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည် (1-Device Locked)</b>';
      const priceText = plan.price === 0 ? 'အခမဲ့ (0 Ks)' : `${plan.price.toLocaleString()} ကျပ်`;

      if (count === 1) {
        const newKey = generateCdKey();
        const record: LicenseKeyRecord = {
          cd_key: newKey,
          plan_id: plan.id,
          duration: plan.duration,
          duration_label: plan.duration_label,
          price: plan.price,
          device_changeable: isChangeable,
          status: 'available',
          created_at: Date.now()
        };
        await saveLicenseKey(env, record);

        const text = `✅ <b>လိုင်စင်ကုတ် အသစ် ထုတ်ယူပြီးပါပြီ</b>\n\n` +
          `🔑 <b>ကုတ်နံပါတ် (CD-Key):</b>\n` +
          `<code>${newKey}</code>\n\n` +
          `📦 <b>အစီအစဉ်:</b> <b>${plan.name}</b>\n` +
          `⏳ <b>သက်တမ်း:</b> <b>${plan.duration_label}</b>\n` +
          `💰 <b>ဈေးနှုန်း:</b> <b>${priceText}</b>\n` +
          `📱 <b>စက်မူဝါဒ:</b> ${changeText}\n` +
          `📌 <b>အခြေအနေ:</b> ⚪ ရောင်းရန် အသင့်ရှိသည် (Available)\n` +
          `⏰ <b>ထုတ်သည့်အချိန်:</b> ${new Date().toLocaleTimeString('my-MM')}\n\n` +
          `<i>ဝယ်ယူသူထံ ပေးပို့ရန် အောက်ပါ ခလုတ်ကို နှိပ်၍ CD-Key ကို ချက်ချင်း Copy ကူးယူနိုင်ပါသည်။</i>`;

        const kb: InlineKeyboardMarkup = {
          inline_keyboard: [
            [{ text: '📋 CD-Key ကူးယူမည် (Copy Key)', copy_text: { text: newKey } }],
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
        const keysList: string[] = [];
        for (let i = 0; i < count; i++) {
          const k = generateCdKey();
          const rec: LicenseKeyRecord = {
            cd_key: k,
            plan_id: plan.id,
            duration: plan.duration,
            duration_label: plan.duration_label,
            price: plan.price,
            device_changeable: isChangeable,
            status: 'available',
            created_at: Date.now()
          };
          await saveLicenseKey(env, rec);
          keysList.push(k);
        }

        const formattedKeys = keysList.map((k, idx) => `${idx + 1}. <code>${k}</code>`).join('\n');
        const text = `📦 <b>ကုတ်ပေါင်း (${count}) ခု အောင်မြင်စွာ ထုတ်ယူပြီးပါပြီ (Bulk)</b>\n\n` +
          `📦 <b>အစီအစဉ်:</b> <b>${plan.name}</b>\n` +
          `⏳ <b>သက်တမ်း:</b> <b>${plan.duration_label}</b>\n` +
          `💰 <b>ဈေးနှုန်း:</b> <b>${priceText}</b>\n` +
          `📱 <b>စက်မူဝါဒ:</b> ${changeText}\n\n` +
          `🔑 <b>ကုတ်နံပါတ်များ စာရင်း:</b>\n` +
          `${formattedKeys}\n\n` +
          `<i>(အောက်ပါ ခလုတ်ကို နှိပ်၍ ကုတ်အားလုံးကို တစ်ခါတည်း Copy ကူးယူနိုင်ပါသည်)</i>`;

        const kb: InlineKeyboardMarkup = {
          inline_keyboard: [
            [{ text: `📋 ကုတ်အားလုံး ကူးယူမည် (Copy All ${count} Keys)`, copy_text: { text: keysList.join('\n') } }],
            [{ text: '➕ နောက်ထပ် ကုတ်ထုတ်မည်', callback_data: 'm_gen_menu' }],
            [{ text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }]
          ]
        };

        if (messageId) {
          await editTelegramMessage(env, chatId, messageId, text, kb);
        } else {
          await sendTelegramMessage(env, chatId, text, kb);
        }
      }
    }

    // Reseller Due Settlement Menu: r_pay:<resellerId>
    else if (data.startsWith('r_pay:')) {
      const resellerId = data.split(':')[1];
      const reseller = await getReseller(resellerId, env);
      if (!reseller) {
        await answerCallbackQuery(env, cq.id, 'ကိုယ်စားလှယ် မတွေ့ရှိပါ', true);
        return new Response('ok');
      }

      const due = reseller.total_due || 0;
      const text = `💵 <b>ကိုယ်စားလှယ် ငွေစာရင်း ရှင်းလင်းခြင်း</b>\n\n` +
        `👤 <b>ကိုယ်စားလှယ်:</b> <b>${reseller.name}</b>\n` +
        `🆔 <b>Telegram ID:</b> <code>${resellerId}</code>\n` +
        `📌 <b>လက်ရှိ ပေးရန်ကျန်ငွေ (Due):</b> <code>${due.toLocaleString()}</code> ကျပ်\n` +
        `💳 <b>ပေးပြီး စုစုပေါင်း:</b> <code>${(reseller.total_paid || 0).toLocaleString()}</code> ကျပ်\n\n` +
        `ရှင်းလင်းမည့် ငွေပမာဏကို အောက်ပါ ခလုတ်များမှ ရွေးချယ်ပါ 👇`;

      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [
            { text: `💰 အပြည့်ရှင်းမည် (${due.toLocaleString()} Ks)`, callback_data: `r_pay_do:${resellerId}:full` }
          ],
          [
            { text: `💵 50,000 Ks`, callback_data: `r_pay_do:${resellerId}:50000` },
            { text: `💵 100,000 Ks`, callback_data: `r_pay_do:${resellerId}:100000` }
          ],
          [
            { text: `💵 200,000 Ks`, callback_data: `r_pay_do:${resellerId}:200000` },
            { text: `💵 500,000 Ks`, callback_data: `r_pay_do:${resellerId}:500000` }
          ],
          [
            { text: `✏️ စိတ်ကြိုက် ပမာဏ ရိုက်ထည့်မည် (Manual Enter)`, callback_data: `r_pay_manual:${resellerId}` }
          ],
          [
            { text: `⬅️ ကိုယ်စားလှယ်များ စာရင်း`, callback_data: `m_resellers` }
          ]
        ]
      };

      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Reseller Due Settlement Prompt for Manual Amount: r_pay_manual:<resellerId>
    else if (data.startsWith('r_pay_manual:')) {
      const resellerId = data.split(':')[1];
      const reseller = await getReseller(resellerId, env);
      if (!reseller) {
        await answerCallbackQuery(env, cq.id, 'ကိုယ်စားလှယ် မတွေ့ရှိပါ', true);
        return new Response('ok');
      }

      await setAdminState(chatId, {
        chat_id: chatId,
        action: 'awaiting_reseller_pay',
        target_id: resellerId,
        target_name: reseller.name,
        created_at: Date.now()
      }, env);

      await answerCallbackQuery(env, cq.id);
      const promptText = `✏️ <b>ကိုယ်စားလှယ် [${reseller.name}] အတွက် ရှင်းလင်းမည့် ငွေပမာဏကို ရိုက်ထည့်ပါ</b>\n\n` +
        `👤 ကိုယ်စားလှယ်: <b>${reseller.name}</b> (ID: <code>${resellerId}</code>)\n` +
        `📌 လက်ရှိ ပေးရန်ကျန်ငွေ (Due): <b>${(reseller.total_due || 0).toLocaleString()} ကျပ်</b>\n` +
        `💳 ပေးပြီး စုစုပေါင်း: <b>${(reseller.total_paid || 0).toLocaleString()} ကျပ်</b>\n\n` +
        `ရှင်းလင်းမည့် ငွေပမာဏ ဂဏန်းကို ရိုက်ထည့်ပေးပါ:\n` +
        `<i>(ဥပမာ: <code>35000</code> သို့မဟုတ် <code>75,000</code> သို့မဟုတ် အပြည့်ရှင်းရန် <code>full</code>)</i>\n\n` +
        `❌ မလုပ်တော့ပါက <code>/cancel</code> ဟု ရိုက်ထည့်ပါ`;

      const cancelKb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [{ text: '❌ မလုပ်တော့ပါ (Cancel)', callback_data: 'm_resellers' }]
        ]
      };

      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, promptText, cancelKb);
      } else {
        await sendTelegramMessage(env, chatId, promptText, cancelKb);
      }
    }

    // Execute Reseller Due Settlement: r_pay_do:<resellerId>:<amountStr>
    else if (data.startsWith('r_pay_do:')) {
      const [, resellerId, amountStr] = data.split(':');
      const res = await settleResellerDue(env, resellerId, amountStr, String(chatId));
      if (res.ok && res.reseller && res.paidAmount) {
        await answerCallbackQuery(env, cq.id, `✅ ငွေ ${res.paidAmount.toLocaleString()} Ks ရှင်းလင်းပြီးပါပြီ`, true);

        // Notify reseller
        await sendTelegramMessage(env, resellerId,
          `💳 <b>Admin ထံ ငွေပေးသွင်းမှု မှတ်တမ်းတင်ပြီးပါပြီ</b>\n\n` +
          `• ပေးသွင်းငွေ: <b>${res.paidAmount.toLocaleString()} ကျပ်</b>\n` +
          `• Admin သို့ ပေးရန်ကျန်ငွေ လက်ကျန်: <b>${(res.reseller.total_due || 0).toLocaleString()} ကျပ်</b>\n` +
          `ကျေးဇူးတင်ရှိပါသည်။`
        );

        // Show updated resellers list
        const updated = await buildResellersListMessage(env);
        if (messageId) {
          await editTelegramMessage(env, chatId, messageId, updated.text, updated.keyboard);
        } else {
          await sendTelegramMessage(env, chatId, updated.text, updated.keyboard);
        }
      } else {
        await answerCallbackQuery(env, cq.id, `❌ မအောင်မြင်ပါ: ${res.error || 'အချက်အလက် စစ်ဆေးပါ'}`, true);
      }
    }

    // Reseller Remove Confirmation: r_rem_conf:<resellerId>
    else if (data.startsWith('r_rem_conf:')) {
      const resellerId = data.split(':')[1];
      const reseller = await getReseller(resellerId, env);
      const name = reseller?.name || resellerId;
      const text = `⚠️ <b>ကိုယ်စားလှယ် [${name}] အား ဖယ်ရှားရန် သေချာပါသလား?</b>\n\n` +
        `ဖယ်ရှားလိုက်ပါက အဆိုပါ အကောင့်သည် ကုတ်ထုတ်ယူခွင့် မရှိတော့ပါ။`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [
            { text: '🔴 ဟုတ်ကဲ့၊ ဖယ်ရှားမည်', callback_data: `r_rem_do:${resellerId}` },
            { text: '❌ မလုပ်တော့ပါ', callback_data: 'm_resellers' }
          ]
        ]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Execute Reseller Removal: r_rem_do:<resellerId>
    else if (data.startsWith('r_rem_do:')) {
      const resellerId = data.split(':')[1];
      await removeReseller(env, resellerId);
      await answerCallbackQuery(env, cq.id, '✅ ကိုယ်စားလှယ် အား ဖယ်ရှားပြီးပါပြီ', true);
      const updated = await buildResellersListMessage(env);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, updated.text, updated.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, updated.text, updated.keyboard);
      }
    }

    // Reseller Add Interactive Prompt: r_add_prompt
    else if (data === 'r_add_prompt') {
      await setAdminState(chatId, { chat_id: chatId, action: 'awaiting_reseller', created_at: Date.now() }, env);
      const text = `➕ <b>ကိုယ်စားလှယ် အသစ် ထည့်သွင်းရန်</b>\n\n` +
        `👉 <b>နည်းလမ်း (၁) - အလွယ်ဆုံးနည်း:</b>\n` +
        `ကိုယ်စားလှယ် ခန့်လိုသူ၏ Message (စာ သို့မဟုတ် အသံဖိုင်) ကို ဤ Chat သို့ <b>Forward</b> လုပ်ပို့ပေးလိုက်ပါ!\n\n` +
        `👉 <b>နည်းလမ်း (၂):</b>\n` +
        `ကိုယ်စားလှယ်၏ <b>Telegram ID</b> နှင့် <b>အမည်</b> ကို စာရိုက်ပို့ပေးပါ:\n` +
        `<code>987654321 ကိုစိုး</code>`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [[{ text: '❌ မလုပ်တော့ပါ (Cancel)', callback_data: 'cancel_state' }]]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Admin Remove Confirmation: adm_rem_conf:<adminId>
    else if (data.startsWith('adm_rem_conf:')) {
      const adminId = data.split(':')[1];
      const text = `⚠️ <b>Admin (ID: <code>${adminId}</code>) အား ဖယ်ရှားရန် သေချာပါသလား?</b>\n\n` +
        `ဖယ်ရှားလိုက်ပါက အဆိုပါ အကောင့်သည် စနစ်အား စီမံခန့်ခွဲခွင့် မရှိတော့ပါ။`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [
            { text: '🔴 ဟုတ်ကဲ့၊ ဖယ်ရှားမည်', callback_data: `adm_rem_do:${adminId}` },
            { text: '❌ မလုပ်တော့ပါ', callback_data: 'm_admins' }
          ]
        ]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Execute Admin Removal: adm_rem_do:<adminId>
    else if (data.startsWith('adm_rem_do:')) {
      const adminId = data.split(':')[1];
      const ok = await removeAdmin(env, adminId);
      if (ok) {
        await answerCallbackQuery(env, cq.id, '✅ Admin အား ဖယ်ရှားပြီးပါပြီ', true);
      } else {
        await answerCallbackQuery(env, cq.id, '❌ Master Admin အား ဖယ်ရှား၍ မရပါ', true);
      }
      const updated = await buildAdminsListMessage(env);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, updated.text, updated.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, updated.text, updated.keyboard);
      }
    }

    // Admin Add Interactive Prompt: adm_add_prompt
    else if (data === 'adm_add_prompt') {
      await setAdminState(chatId, { chat_id: chatId, action: 'awaiting_admin', created_at: Date.now() }, env);
      const text = `➕ <b>Admin အသစ် ခန့်အပ်ရန်</b>\n\n` +
        `👉 <b>နည်းလမ်း (၁) - အလွယ်ဆုံးနည်း:</b>\n` +
        `Admin ခန့်မည့်သူ၏ Message ကို ဤ Chat သို့ <b>Forward</b> လုပ်ပို့ပေးလိုက်ပါ!\n\n` +
        `👉 <b>နည်းလမ်း (၂):</b>\n` +
        `Admin ၏ <b>Telegram ID</b> နှင့် <b>အမည်</b> ကို စာရိုက်ပို့ပေးပါ:\n` +
        `<code>123456789 ဦးအောင်</code>`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [[{ text: '❌ မလုပ်တော့ပါ (Cancel)', callback_data: 'cancel_state' }]]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Forwarded User Action: fwd_add_res:<targetId>:<encName>
    else if (data.startsWith('fwd_add_res:')) {
      const parts = data.split(':');
      const targetId = parts[1];
      const targetName = decodeURIComponent(parts.slice(2).join(':')) || 'Reseller';

      const ok = await addReseller(env, targetId, targetName);
      if (ok) {
        await answerCallbackQuery(env, cq.id, `✅ [${targetName}] အား ကိုယ်စားလှယ် ထည့်သွင်းပြီးပါပြီ`, true);
        await sendTelegramMessage(env, targetId,
          `🎉 <b>မင်္ဂလာပါ ${targetName}! သင့်အား 3D LEDGER ကိုယ်စားလှယ် (Reseller) အဖြစ် စာရင်းသွင်းလိုက်ပါပြီ။</b>\n\n` +
          `• 3-Day Trial, 1-Year နှင့် Lifetime ကုတ်များကို မိမိကိုယ်တိုင် ထုတ်ယူ ရောင်းချနိုင်ပါသည်။\n` +
          `• ကိုယ်စားလှယ် ဒက်ရှ်ဘုတ် ဖွင့်ရန် အောက်ပါ ခလုတ်ကို နှိပ်ပါ 👇`,
          getResellerBottomKeyboard()
        );
        const text = `✅ <b>[ကိုယ်စားလှယ် အသစ် ထည့်သွင်းပြီးပါပြီ]</b>\n\n` +
          `👤 <b>အမည်:</b> <b>${targetName}</b>\n` +
          `🆔 <b>Telegram ID:</b> <code>${targetId}</code>\n\n` +
          `အဆိုပါ ကိုယ်စားလှယ်သည် ကုတ်ထုတ်ယူခွင့် ရရှိသွားပါပြီ။`;
        if (messageId) {
          await editTelegramMessage(env, chatId, messageId, text, {
            inline_keyboard: [[{ text: '👥 ကိုယ်စားလှယ်များ စာရင်း', callback_data: 'm_resellers' }]]
          });
        } else {
          await sendTelegramMessage(env, chatId, text);
        }
      } else {
        await answerCallbackQuery(env, cq.id, '❌ ကိုယ်စားလှယ် ထည့်သွင်းခြင်း မအောင်မြင်ပါ', true);
      }
    }

    // Forwarded User Action: fwd_add_adm:<targetId>:<encName>
    else if (data.startsWith('fwd_add_adm:')) {
      const parts = data.split(':');
      const targetId = parts[1];
      const targetName = decodeURIComponent(parts.slice(2).join(':')) || 'Admin';

      const ok = await addAdmin(env, targetId, targetName, String(chatId));
      if (ok) {
        await answerCallbackQuery(env, cq.id, `✅ [${targetName}] အား Admin ခန့်အပ်ပြီးပါပြီ`, true);
        await sendTelegramMessage(env, targetId,
          `🎉 <b>ဂုဏ်ယူပါသည်! သင့်အား 3D LEDGER စနစ်၏ Admin အဖြစ် ခန့်အပ်လိုက်ပါပြီ။</b>`,
          getAdminBottomKeyboard()
        );
        const text = `✅ <b>[Admin အသစ် ခန့်အပ်ပြီးပါပြီ]</b>\n\n` +
          `👤 <b>အမည်:</b> <b>${targetName}</b>\n` +
          `🆔 <b>Telegram ID:</b> <code>${targetId}</code>\n\n` +
          `အဆိုပါ Admin သည် စနစ်အား စီမံခန့်ခွဲခွင့် ရရှိသွားပါပြီ။`;
        if (messageId) {
          await editTelegramMessage(env, chatId, messageId, text, {
            inline_keyboard: [[{ text: '👮‍♂️ Admin များ စာရင်း', callback_data: 'm_admins' }]]
          });
        } else {
          await sendTelegramMessage(env, chatId, text);
        }
      } else {
        await answerCallbackQuery(env, cq.id, '❌ Admin ထည့်သွင်းခြင်း မအောင်မြင်ပါ', true);
      }
    }

    // Wave Pay Edit Prompt: pay_ed:wave
    else if (data === 'pay_ed:wave') {
      await setAdminState(chatId, { chat_id: chatId, action: 'awaiting_wave', created_at: Date.now() }, env);
      const text = `✏️ <b>Wave Pay အကောင့် ပြင်ဆင်ရန်</b>\n\n` +
        `ဖုန်းနံပါတ်နှင့် အကောင့်အမည်ကို အောက်ပါအတိုင်း စာရိုက်ပို့ပေးပါ:\n\n` +
        `<code>09778899001 ဦးအောင်ကို</code>`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [[{ text: '❌ မလုပ်တော့ပါ (Cancel)', callback_data: 'cancel_state' }]]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // KBZPay Edit Prompt: pay_ed:kpay
    else if (data === 'pay_ed:kpay') {
      await setAdminState(chatId, { chat_id: chatId, action: 'awaiting_kpay', created_at: Date.now() }, env);
      const text = `✏️ <b>KBZPay (KPay) အကောင့် ပြင်ဆင်ရန်</b>\n\n` +
        `ဖုန်းနံပါတ်နှင့် အကောင့်အမည်ကို အောက်ပါအတိုင်း စာရိုက်ပို့ပေးပါ:\n\n` +
        `<code>09778899001 ဦးအောင်ကို</code>`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [[{ text: '❌ မလုပ်တော့ပါ (Cancel)', callback_data: 'cancel_state' }]]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Cancel Interactive Prompt State: cancel_state
    else if (data === 'cancel_state') {
      await setAdminState(chatId, null, env);
      await answerCallbackQuery(env, cq.id, '✅ ပယ်ဖျက်လိုက်ပါပြီ');
      const dash = await buildAdminDashboardMessage(env);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, dash.text, dash.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, dash.text, dash.keyboard);
      }
    }

    // Commission Menu: Lifetime
    else if (data === 'com_menu:lifetime') {
      const text = `💵 <b>တစ်သက်တာ လိုင်စင် (Lifetime) ကော်မရှင် ပြင်ဆင်ရန်</b>\n\n` +
        `သတ်မှတ်လိုသော ကော်မရှင်ငွေ ပမာဏကို အောက်ပါ ခလုတ်များမှ ရွေးချယ်ပါ 👇`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [
            { text: '💰 3,000 Ks', callback_data: 'com_set:lifetime:3000' },
            { text: '💰 5,000 Ks', callback_data: 'com_set:lifetime:5000' }
          ],
          [
            { text: '💰 7,000 Ks', callback_data: 'com_set:lifetime:7000' },
            { text: '💰 10,000 Ks', callback_data: 'com_set:lifetime:10000' }
          ],
          [
            { text: '⬅️ ကော်မရှင် မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_commissions' }
          ]
        ]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Commission Menu: 1-Year
    else if (data === 'com_menu:one_year') {
      const text = `💵 <b>၁ နှစ် လိုင်စင် (1-Year) ကော်မရှင် ပြင်ဆင်ရန်</b>\n\n` +
        `သတ်မှတ်လိုသော ကော်မရှင်ငွေ ပမာဏကို အောက်ပါ ခလုတ်များမှ ရွေးချယ်ပါ 👇`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [
            { text: '💰 5,000 Ks', callback_data: 'com_set:one_year:5000' },
            { text: '💰 10,000 Ks', callback_data: 'com_set:one_year:10000' },
            { text: '💰 15,000 Ks', callback_data: 'com_set:one_year:15000' }
          ],
          [
            { text: '💰 20,000 Ks', callback_data: 'com_set:one_year:20000' },
            { text: '💰 25,000 Ks', callback_data: 'com_set:one_year:25000' },
            { text: '💰 30,000 Ks', callback_data: 'com_set:one_year:30000' }
          ],
          [
            { text: '⬅️ ကော်မရှင် မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_commissions' }
          ]
        ]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Commission Set Execution: com_set:<planType>:<amount>
    else if (data.startsWith('com_set:')) {
      const [, planType, amountStr] = data.split(':');
      const amount = parseInt(amountStr, 10) || 0;
      await setCommissionConfig(env, planType as 'lifetime' | 'one_year', amount);
      await answerCallbackQuery(env, cq.id, `✅ [${planType}] ကော်မရှင် ${amount.toLocaleString()} Ks သို့ သတ်မှတ်ပြီးပါပြီ`, true);
      const res = await buildCommissionsMessage(env);
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, res.text, res.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      }
    }

    // Declare Winner Menu
    else if (data === 'm_declare_menu') {
      const text = `🎯 <b>3D ပေါက်မဲ သတ်မှတ်ခြင်း (Winner Declaration)</b>\n\n` +
        `ပေါက်ဂဏန်း သတ်မှတ်ရန် နည်းလမ်းကို အောက်ပါ ခလုတ်များမှ ရွေးချယ်ပါ 👇\n\n` +
        `• <b>ထိုင်း GLO ရလဒ် ရယူမည်:</b> တရားဝင် ထိုင်းထီပေါက်မဲကို အလိုအလျောက် ဆွဲယူသတ်မှတ်မည်\n` +
        `• <b>ကိုယ်တိုင် သတ်မှတ်မည်:</b> မိမိစိတ်ကြိုက် ၃ လုံးဂဏန်းကို ရိုက်ထည့်သတ်မှတ်မည်`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [{ text: '🇹🇭 Thai GLO ရလဒ် အလိုအလျောက် သတ်မှတ်မည်', callback_data: 'w_fetch_glo' }],
          [{ text: '✏️ ဂဏန်း ကိုယ်တိုင် သတ်မှတ်မည်', callback_data: 'w_manual_prompt' }],
          [{ text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }]
        ]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Fetch Thai GLO Result & Declare Winner: w_fetch_glo
    else if (data === 'w_fetch_glo') {
      const glo = await fetchFromGloLottery();
      if (glo && glo.threeD) {
        await setWinningNumber(env, glo.threeD, String(chatId));
        await answerCallbackQuery(env, cq.id, `✅ Thai GLO ${glo.threeD} အား သတ်မှတ်ပြီးပါပြီ!`, true);
        const tut = calculateTutNumbers(glo.threeD);
        const text = `🎯 <b>Thai GLO ပေါက်ဂဏန်း အောင်မြင်စွာ ကြေညာပြီးပါပြီ</b>\n\n` +
          `✨ <b>3D ပေါက်သီး (ဒဲ့):</b> <code>${glo.threeD}</code>\n` +
          `🥇 <b>ပထမဆု:</b> <code>${glo.firstPrize}</code>\n` +
          `📅 <b>ရက်စွဲ:</b> ${glo.date}\n\n` +
          `🔢 <b>တွတ် ဂဏန်းများ (${tut.allTut.length} ကွက်):</b>\n` +
          `<code>${tut.allTut.join(', ')}</code>\n\n` +
          `<i>Android အက်ပ်များနှင့် ဆာဗာအားလုံးတွင် ချက်ချင်း ရောင်ပြန်ဟပ်ပါမည်။</i>`;
        const kb: InlineKeyboardMarkup = {
          inline_keyboard: [[{ text: '⬅️ ပင်မ မီနူး', callback_data: 'm_main' }]]
        };
        if (messageId) {
          await editTelegramMessage(env, chatId, messageId, text, kb);
        } else {
          await sendTelegramMessage(env, chatId, text, kb);
        }
      } else {
        await answerCallbackQuery(env, cq.id, '⚠️ လတ်တလော Thai GLO ပေါက်မဲ မရရှိသေးပါ', true);
      }
    }

    // Manual Winner Prompt: w_manual_prompt
    else if (data === 'w_manual_prompt') {
      await setAdminState(chatId, { chat_id: chatId, action: 'awaiting_winner', created_at: Date.now() }, env);
      const text = `✏️ <b>ပေါက်ဂဏန်း ကိုယ်တိုင် သတ်မှတ်ရန်</b>\n\n` +
        `သတ်မှတ်လိုသော <b>၃ လုံးဂဏန်း</b> ကို စာရိုက်ပို့ပေးပါ:\n\n` +
        `<code>108</code>\n\n` +
        `<i>(ပေါက်သီးနှင့် တွတ်ဂဏန်းများကို စနစ်မှ အလိုအလျောက် တွက်ချက်ကာ Manual Override ပြုလုပ်ပါမည်)</i>`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [[{ text: '❌ မလုပ်တော့ပါ (Cancel)', callback_data: 'cancel_state' }]]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Batch Menu: m_batch
    else if (data === 'm_batch') {
      let b = 1;
      let drawDate = '';
      let nextDrawDate = '';
      try {
        const res = await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/current_batch.json`);
        if (res.ok) b = parseInt(await res.json() || '1', 10) || 1;
        const liveRes = await fetch(`${env.FIREBASE_DB_URL}/3d_live_results.json`);
        if (liveRes.ok) {
          const liveData = await liveRes.json() as any;
          drawDate = liveData?.result_date || '';
          nextDrawDate = liveData?.target_draw_date || '';
        }
      } catch (_) {}
      const text = `📦 <b>3D ထီထွက်ရက်စွဲ နှင့် အပတ်စဉ် အချက်အလက်</b>\n\n` +
        (drawDate ? `📅 <b>နောက်ဆုံးထွက်ရက်:</b> <code>${drawDate}</code>\n` : '') +
        (nextDrawDate ? `⏭️ <b>နောက်ထွက်မည့်ရက်:</b> <code>${nextDrawDate}</code>\n` : '') +
        `🔢 <b>စနစ် သတ်မှတ် အကြိမ်:</b> #${b}\n\n` +
        `💡 <i>မှတ်ချက်: အက်ပ်အသုံးပြုသူများသည် မိမိဖုန်းအလိုက် အကြိမ်များကို သီးခြားစီမံနိုင်ပါသည်။</i>\n\n` +
        `အကြိမ် တိုး/လျှော့ ပြုလုပ်လိုပါက အောက်ပါ ခလုတ်များကို နှိပ်ပါ 👇`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [
            { text: '➕ အကြိမ် +1 တိုးမည်', callback_data: 'b_inc' },
            { text: '➖ အကြိမ် -1 လျှော့မည်', callback_data: 'b_dec' }
          ],
          [
            { text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }
          ]
        ]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      } else {
        await sendTelegramMessage(env, chatId, text, kb);
      }
    }

    // Batch Increment: b_inc
    else if (data === 'b_inc') {
      let b = 1;
      try {
        const res = await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/current_batch.json`);
        if (res.ok) b = parseInt(await res.json() || '1', 10) || 1;
      } catch (_) {}
      b = b + 1;
      try {
        const token = await getFirebaseToken(env);
        await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/current_batch.json`, {
          method: 'PUT',
          headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
          body: JSON.stringify(b)
        });
      } catch (_) {}
      await answerCallbackQuery(env, cq.id, `✅ အကြိမ် #${b} သို့ တိုးမြှင့်လိုက်ပါပြီ`);
      const text = `📦 <b>လက်ရှိ ဖွင့်လှစ်ထားသော အကြိမ်:</b> #${b}\n\nအကြိမ် တိုး/လျှော့ ပြုလုပ်လိုပါက အောက်ပါ ခလုတ်များကို နှိပ်ပါ 👇`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [
            { text: '➕ အကြိမ် +1 တိုးမည်', callback_data: 'b_inc' },
            { text: '➖ အကြိမ် -1 လျှော့မည်', callback_data: 'b_dec' }
          ],
          [
            { text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }
          ]
        ]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      }
    }

    // Batch Decrement: b_dec
    else if (data === 'b_dec') {
      let b = 1;
      try {
        const res = await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/current_batch.json`);
        if (res.ok) b = parseInt(await res.json() || '1', 10) || 1;
      } catch (_) {}
      b = Math.max(1, b - 1);
      try {
        const token = await getFirebaseToken(env);
        await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/current_batch.json`, {
          method: 'PUT',
          headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
          body: JSON.stringify(b)
        });
      } catch (_) {}
      await answerCallbackQuery(env, cq.id, `✅ အကြိမ် #${b} သို့ ပြောင်းလိုက်ပါပြီ`);
      const text = `📦 <b>လက်ရှိ ဖွင့်လှစ်ထားသော အကြိမ်:</b> #${b}\n\nအကြိမ် တိုး/လျှော့ ပြုလုပ်လိုပါက အောက်ပါ ခလုတ်များကို နှိပ်ပါ 👇`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [
            { text: '➕ အကြိမ် +1 တိုးမည်', callback_data: 'b_inc' },
            { text: '➖ အကြိမ် -1 လျှော့မည်', callback_data: 'b_dec' }
          ],
          [
            { text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }
          ]
        ]
      };
      if (messageId) {
        await editTelegramMessage(env, chatId, messageId, text, kb);
      }
    }
    return new Response('ok');
  }

  // 1.85 Handle Forwarded Messages from Admin (Instant Add Reseller or Admin)
  if (update.message) {
    const msg = update.message;
    const chatId = msg.chat.id;
    const senderId = msg.from?.id ? String(msg.from.id) : String(chatId);
    const userIsAdmin = await isUserAdmin(senderId, env);

    // Only process forwarded messages if sender is Admin and not an APK upload
    const isApkUpload = msg.document && (msg.document.file_name?.toLowerCase().endsWith('.apk') || msg.document.mime_type === 'application/vnd.android.package-archive');
    const fwdUser = !isApkUpload ? extractForwardedUser(msg) : null;

    if (userIsAdmin && fwdUser) {
      if (fwdUser.is_hidden || !fwdUser.id) {
        const warnText = `⚠️ <b>[သတိပေးချက်: အသုံးပြုသူ၏ Telegram ID မရရှိနိုင်ပါ]</b>\n\n` +
          `👤 <b>အမည်:</b> <b>${fwdUser.first_name}</b>\n\n` +
          `ဤအသုံးပြုသူသည် Telegram Privacy Setting တွင် <b>Forwarded Messages</b> အား ဖျောက်ထားပါသဖြင့် ID မရရှိနိုင်ပါ။\n\n` +
          `💡 <b>အကြံပြုချက်:</b>\n` +
          `1️⃣ အဆိုပါ အသုံးပြုသူအား ဤ Bot ထံသို့ <code>/id</code> ဟု ရိုက်ပို့စေပြီး ရရှိလာသော Telegram ID ကို ပေးပို့ပါ\n` +
          `(သို့မဟုတ်)\n` +
          `2️⃣ အဆိုပါ အသုံးပြုသူအား Telegram Settings > Privacy and Security > Forwarded Messages တွင် 'Everybody' သို့ ခေတ္တ ပြောင်းလဲခိုင်းပြီးမှ ပြန်လည် Forward လုပ်ပေးပါ။`;
        await sendTelegramMessage(env, chatId, warnText, getAdminBottomKeyboard());
        return new Response('ok');
      }

      const targetId = String(fwdUser.id);
      const targetName = `${fwdUser.first_name}${fwdUser.last_name ? ' ' + fwdUser.last_name : ''}`.trim();
      const targetUsername = fwdUser.username ? `@${fwdUser.username}` : '';

      if (targetId === senderId) {
        await sendTelegramMessage(env, chatId, '⚠️ <b>သင်၏ ကိုယ်ပိုင် အကောင့် Message ဖြစ်နေပါသည်။</b>\n\nခန့်အပ်လိုသော အခြား မိတ်ဆွေ၏ Message ကို Forward လုပ်ပေးပါ။', getAdminBottomKeyboard());
        return new Response('ok');
      }

      // Check if admin is currently awaiting input
      const adminState = await getAdminState(chatId, env);

      if (adminState?.action === 'awaiting_reseller') {
        await setAdminState(chatId, null, env);
        const ok = await addReseller(env, targetId, targetName);
        if (ok) {
          await sendTelegramMessage(env, chatId, `✅ <b>ကိုယ်စားလှယ် အသစ် [${targetName}] (ID: <code>${targetId}</code>) အား Forward မှတစ်ဆင့် အောင်မြင်စွာ ထည့်သွင်းလိုက်ပါပြီ။</b>`);
          await sendTelegramMessage(env, targetId,
            `🎉 <b>မင်္ဂလာပါ ${targetName}! သင့်အား 3D LEDGER ကိုယ်စားလှယ် (Reseller) အဖြစ် စာရင်းသွင်းလိုက်ပါပြီ။</b>\n\n` +
            `• 3-Day Trial, 1-Year နှင့် Lifetime ကုတ်များကို မိမိကိုယ်တိုင် ထုတ်ယူ ရောင်းချနိုင်ပါသည်။\n` +
            `• ကိုယ်စားလှယ် ဒက်ရှ်ဘုတ် ဖွင့်ရန် အောက်ပါ ခလုတ်ကို နှိပ်ပါ 👇`,
            getResellerBottomKeyboard()
          );
          const res = await buildResellersListMessage(env);
          await sendTelegramMessage(env, chatId, res.text, res.keyboard);
        } else {
          await sendTelegramMessage(env, chatId, '❌ ကိုယ်စားလှယ် ထည့်သွင်းခြင်း မအောင်မြင်ပါ');
        }
        return new Response('ok');
      }

      if (adminState?.action === 'awaiting_admin') {
        await setAdminState(chatId, null, env);
        const ok = await addAdmin(env, targetId, targetName, senderId);
        if (ok) {
          await sendTelegramMessage(env, chatId, `✅ <b>Admin အသစ် [${targetName}] (ID: <code>${targetId}</code>) အား Forward မှတစ်ဆင့် အောင်မြင်စွာ ခန့်အပ်လိုက်ပါပြီ။</b>`);
          await sendTelegramMessage(env, targetId,
            `🎉 <b>ဂုဏ်ယူပါသည်! သင့်အား 3D LEDGER စနစ်၏ Admin အဖြစ် ခန့်အပ်လိုက်ပါပြီ။</b>`,
            getAdminBottomKeyboard()
          );
          const res = await buildAdminsListMessage(env);
          await sendTelegramMessage(env, chatId, res.text, res.keyboard);
        } else {
          await sendTelegramMessage(env, chatId, '❌ Admin ထည့်သွင်းခြင်း မအောင်မြင်ပါ');
        }
        return new Response('ok');
      }

      // If not in a specific state, provide role assignment buttons
      const encName = encodeURIComponent(targetName);
      const fwdKb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [
            { text: '💼 ကိုယ်စားလှယ် (Reseller) အဖြစ် ထည့်မည်', callback_data: `fwd_add_res:${targetId}:${encName}` }
          ],
          [
            { text: '👮‍♂️ Admin အဖြစ် ခန့်အပ်မည်', callback_data: `fwd_add_adm:${targetId}:${encName}` }
          ],
          [
            { text: '❌ မလုပ်တော့ပါ (Cancel)', callback_data: 'cancel_state' }
          ]
        ]
      };

      const promptText = `📩 <b>[Forwarded အသုံးပြုသူ တွေ့ရှိပါသည်]</b>\n\n` +
        `👤 <b>အမည်:</b> <b>${targetName}</b> ${targetUsername}\n` +
        `🆔 <b>Telegram ID:</b> <code>${targetId}</code>\n\n` +
        `ဤအသုံးပြုသူအား မည်သည့် ရာထူး သတ်မှတ်ပေးလိုပါသလဲ? အောက်ပါ ခလုတ်မှ ရွေးချယ်ပါ 👇`;

      await sendTelegramMessage(env, chatId, promptText, fwdKb);
      return new Response('ok');
    }
  }

  // 1.9 Handle Document Messages (Admin APK Upload / Release Sync)
  if (update.message && update.message.document) {
    const msg = update.message;
    const chatId = msg.chat.id;
    const senderId = msg.from?.id ? String(msg.from.id) : String(chatId);
    const userIsAdmin = await isUserAdmin(senderId, env);

    if (userIsAdmin) {
      const doc = msg.document;
      const fileName = doc.file_name || '3D_Ledger.apk';
      const isApk = fileName.toLowerCase().endsWith('.apk') || doc.mime_type === 'application/vnd.android.package-archive';

      if (isApk) {
        const rawVersion = msg.caption?.match(/v?\d+(\.\d+)+/i)?.[0] || 'v1.0.100';
        const versionName = rawVersion.startsWith('v') ? rawVersion : `v${rawVersion}`;
        const versionCode = parseInt(versionName.replace(/\D/g, ''), 10) || 100;
        const releaseNotes = msg.caption?.replace(/^\/\w+\s*/, '').trim() || '3D Ledger နောက်ဆုံးထွက် Official APK ဗားရှင်း';

        const release: AppReleaseRecord = {
          file_id: doc.file_id,
          file_name: fileName,
          file_size: doc.file_size || 0,
          version_name: versionName,
          version_code: versionCode,
          release_notes: releaseNotes,
          uploaded_by: senderId,
          uploaded_at: Date.now()
        };

        await saveAppRelease(env, release);

        const sizeMb = ((doc.file_size || 0) / (1024 * 1024)).toFixed(2);
        const adminReply = `✅ <b>[အက်ပ်ဗားရှင်း အသစ် တင်ပြီးပါပြီ]</b>\n\n` +
          `📦 <b>ဖိုင်အမည်:</b> <code>${fileName}</code>\n` +
          `🔖 <b>ဗားရှင်း:</b> <b>${versionName}</b> (Code: #${versionCode})\n` +
          `📊 <b>အရွယ်အစား:</b> <b>${sizeMb} MB</b>\n` +
          `🆔 <b>Telegram File ID:</b> <code>${doc.file_id}</code>\n` +
          `📝 <b>မှတ်ချက်:</b> ${releaseNotes}\n` +
          `⏰ <b>တင်သည့်အချိန်:</b> ${new Date().toLocaleTimeString('my-MM')}\n\n` +
          `🚀 <b>ဝယ်ယူသူ/စမ်းသပ်သူ မည်သူမဆို Bot ရှိ [📲 အက်ပ် ဒေါင်းလုဒ်ရယူရန်] ခလုတ်ကို နှိပ်လိုက်သည်နှင့် ဤ APK အသစ်အား ချက်ချင်း ဒေါင်းလုဒ် ရယူနိုင်ပါပြီ။</b>`;

        const uploadKb: InlineKeyboardMarkup = {
          inline_keyboard: [
            [
              { text: '📢 Update အားလုံးသို့ အသိပေးစာ ပို့မည်', callback_data: 'm_broadcast_update' },
              { text: '📥 Test Download', callback_data: 'b_app_download' }
            ]
          ]
        };

        await sendTelegramMessage(env, chatId, adminReply, uploadKb);
        return new Response('ok');
      }
    }
  }

  // 2. Handle Photo Messages (Direct Buyer Payment Screenshot Upload)
  if (update.message && update.message.photo && update.message.photo.length > 0) {
    const msg = update.message;
    const chatId = msg.chat.id;
    const senderId = msg.from?.id ? String(msg.from.id) : String(chatId);
    const senderName = msg.from?.first_name || 'Buyer';
    const senderUsername = msg.from?.username || '';
    const largestPhoto = msg.photo[msg.photo.length - 1];

    const session = await getBuyerSession(senderId, env);
    const planId = session?.plan_id || 'one_year';
    const planName = session?.plan_name || '၁ နှစ် သက်တမ်း (1 Year Plan)';
    const price = session?.price || 180000;

    const orderId = 'ORD-' + Math.random().toString(36).substring(2, 8).toUpperCase();
    const order: BuyerOrder = {
      order_id: orderId,
      buyer_chat_id: senderId,
      buyer_name: senderName,
      buyer_username: senderUsername,
      plan_id: planId,
      plan_name: planName,
      price: price,
      photo_file_id: largestPhoto.file_id,
      status: 'pending',
      created_at: Date.now()
    };

    await saveBuyerOrder(order, env);

    const buyerReply = `🧾 <b>ငွေလွှဲပြေစာ လက်ခံရရှိပါသည်</b>\n\n` +
      `• 🧾 အော်ဒါအမှတ်: <code>${orderId}</code>\n` +
      `• 📦 အစီအစဉ်: <b>${planName}</b>\n` +
      `• 💰 ကျသင့်ငွေ: <b>${price.toLocaleString()} ကျပ်</b>\n\n` +
      `Admin မှ ငွေလွှဲပြေစာအား စစ်ဆေးအတည်ပြုပြီးပါက လိုင်စင်ကုတ် (CD-Key) အား ဤနေရာသို့ ချက်ချင်း အလိုအလျောက် ပေးပို့ပေးပါမည်။ ခေတ္တ စောင့်ဆိုင်းပေးပါရန် မေတ္တာရပ်ခံအပ်ပါသည်။`;
    await sendTelegramMessage(env, chatId, buyerReply);

    const adminCaption = `🛒 <b>[ငွေလွှဲပြေစာ အသစ်] လိုင်စင် ဝယ်ယူမှု တောင်းဆိုလွှာ</b>\n\n` +
      `👤 ဝယ်ယူသူ: <b>${senderName}</b> (@${senderUsername || '-'})\n` +
      `🆔 Telegram ID: <code>${senderId}</code>\n` +
      `📦 အစီအစဉ်: <b>${planName}</b>\n` +
      `💰 ကျသင့်ငွေ: <b>${price.toLocaleString()} ကျပ်</b>\n` +
      `🧾 အော်ဒါအမှတ်: <code>${orderId}</code>\n` +
      `⏰ အချိန်: ${new Date().toLocaleTimeString('my-MM')}\n\n` +
      `<i>ငွေလွှဲပြေစာ မှန်ကန်ပါက ကုတ်ထုတ်ပေးမည် ကို နှိပ်ပါ 👇</i>`;
    await notifyAllAdminsWithPhoto(env, largestPhoto.file_id, adminCaption, getOrderApprovalKeyboard(orderId));

    return new Response('ok');
  }

  // 3. Handle Text Messages & Commands
  if (update.message && update.message.text) {
    const msg = update.message;
    const text = msg.text.trim();
    const chatId = msg.chat.id;
    const senderId = msg.from?.id ? String(msg.from.id) : String(chatId);

    const userIsAdmin = await isUserAdmin(senderId, env);
    const reseller = await getReseller(senderId, env);

    // ── Admin Active Interactive Prompt State Handling ──────────────────────
    if (userIsAdmin) {
      const adminState = await getAdminState(chatId, env);
      if (adminState) {
        if (text === '❌ မလုပ်တော့ပါ' || text === '/cancel' || text.toLowerCase() === 'cancel' || text === 'ပယ်ဖျက်မည်') {
          await setAdminState(chatId, null, env);
          await sendTelegramMessage(env, chatId, '✅ လုပ်ဆောင်ချက်ကို ပယ်ဖျက်လိုက်ပါပြီ။', getAdminBottomKeyboard());
          return new Response('ok');
        }

        if (adminState.action === 'awaiting_reseller') {
          const parts = text.split(/\s+/);
          const targetId = parts[0];
          const targetName = parts.slice(1).join(' ') || 'Reseller';
          if (!/^\d+$/.test(targetId)) {
            await sendTelegramMessage(env, chatId, '⚠️ မှန်ကန်သော Telegram ID (ဂဏန်းသီးသန့်) နှင့် အမည် ထည့်ပေးပါ။\nဥပမာ: <code>987654321 ကိုစိုး</code>');
            return new Response('ok');
          }
          await setAdminState(chatId, null, env);
          const ok = await addReseller(env, targetId, targetName);
          if (ok) {
            await sendTelegramMessage(env, chatId, `✅ <b>ကိုယ်စားလှယ် အသစ် [${targetName}] (ID: <code>${targetId}</code>) အား အောင်မြင်စွာ ထည့်သွင်းလိုက်ပါပြီ။</b>`);
            await sendTelegramMessage(env, targetId,
              `🎉 <b>မင်္ဂလာပါ ${targetName}! သင့်အား 3D LEDGER ကိုယ်စားလှယ် (Reseller) အဖြစ် စာရင်းသွင်းလိုက်ပါပြီ။</b>\n\n` +
              `• 3-Day Trial, 1-Year နှင့် Lifetime ကုတ်များကို မိမိကိုယ်တိုင် ထုတ်ယူ ရောင်းချနိုင်ပါသည်။\n` +
              `• ကိုယ်စားလှယ် ဒက်ရှ်ဘုတ် ဖွင့်ရန် အောက်ပါ ခလုတ်ကို နှိပ်ပါ 👇`,
              getResellerBottomKeyboard()
            );
            const res = await buildResellersListMessage(env);
            await sendTelegramMessage(env, chatId, res.text, res.keyboard);
          } else {
            await sendTelegramMessage(env, chatId, '❌ ကိုယ်စားလှယ် ထည့်သွင်းခြင်း မအောင်မြင်ပါ');
          }
          return new Response('ok');
        }

        if (adminState.action === 'awaiting_admin') {
          const parts = text.split(/\s+/);
          const targetId = parts[0];
          const targetName = parts.slice(1).join(' ') || 'Admin';
          if (!/^\d+$/.test(targetId)) {
            await sendTelegramMessage(env, chatId, '⚠️ မှန်ကန်သော Telegram ID (ဂဏန်းသီးသန့်) နှင့် အမည် ထည့်ပေးပါ။\nဥပမာ: <code>123456789 ဦးလှ</code>');
            return new Response('ok');
          }
          await setAdminState(chatId, null, env);
          const ok = await addAdmin(env, targetId, targetName, senderId);
          if (ok) {
            await sendTelegramMessage(env, chatId, `✅ <b>Admin အသစ် [${targetName}] (ID: <code>${targetId}</code>) အား အောင်မြင်စွာ ခန့်အပ်လိုက်ပါပြီ။</b>`);
            await sendTelegramMessage(env, targetId,
              `🎉 <b>ဂုဏ်ယူပါသည်! သင့်အား 3D LEDGER စနစ်၏ Admin အဖြစ် ခန့်အပ်လိုက်ပါပြီ။</b>`,
              getAdminBottomKeyboard()
            );
            const res = await buildAdminsListMessage(env);
            await sendTelegramMessage(env, chatId, res.text, res.keyboard);
          } else {
            await sendTelegramMessage(env, chatId, '❌ Admin ထည့်သွင်းခြင်း မအောင်မြင်ပါ');
          }
          return new Response('ok');
        }

        if (adminState.action === 'awaiting_wave') {
          const parts = text.split(/\s+/);
          const num = parts[0];
          const name = parts.slice(1).join(' ') || '3D Ledger Admin';
          if (!num) {
            await sendTelegramMessage(env, chatId, '⚠️ ဖုန်းနံပါတ်နှင့် အကောင့်အမည် ထည့်ပေးပါ။\nဥပမာ: <code>09778899001 ဦးအောင်ကို</code>');
            return new Response('ok');
          }
          await setAdminState(chatId, null, env);
          await setPaymentAccount(env, 'wave', num, name);
          await sendTelegramMessage(env, chatId, `✅ <b>Wave Pay အကောင့် အချက်အလက်များ အောင်မြင်စွာ ပြင်ဆင်ပြီးပါပြီ:</b>\n\n• နံပါတ်: <code>${num}</code>\n• အမည်: <b>${name}</b>`);
          const res = await buildPaymentsMessage(env);
          await sendTelegramMessage(env, chatId, res.text, res.keyboard);
          return new Response('ok');
        }

        if (adminState.action === 'awaiting_kpay') {
          const parts = text.split(/\s+/);
          const num = parts[0];
          const name = parts.slice(1).join(' ') || '3D Ledger Admin';
          if (!num) {
            await sendTelegramMessage(env, chatId, '⚠️ ဖုန်းနံပါတ်နှင့် အကောင့်အမည် ထည့်ပေးပါ။\nဥပမာ: <code>09778899001 ဦးအောင်ကို</code>');
            return new Response('ok');
          }
          await setAdminState(chatId, null, env);
          await setPaymentAccount(env, 'kpay', num, name);
          await sendTelegramMessage(env, chatId, `✅ <b>KBZPay အကောင့် အချက်အလက်များ အောင်မြင်စွာ ပြင်ဆင်ပြီးပါပြီ:</b>\n\n• နံပါတ်: <code>${num}</code>\n• အမည်: <b>${name}</b>`);
          const res = await buildPaymentsMessage(env);
          await sendTelegramMessage(env, chatId, res.text, res.keyboard);
          return new Response('ok');
        }

        if (adminState.action === 'awaiting_winner') {
          const num = text.trim();
          if (!/^\d{3}$/.test(num)) {
            await sendTelegramMessage(env, chatId, '⚠️ <i>၃ လုံးဂဏန်း ရိုက်ထည့်ပေးပါ။ ဥပမာ: 108</i>');
            return new Response('ok');
          }
          await setAdminState(chatId, null, env);
          await setWinningNumber(env, num, senderId);
          const tut = calculateTutNumbers(num);
          await sendTelegramMessage(env, chatId,
            `🎯 <b>3D ပေါက်ဂဏန်း အောင်မြင်စွာ ကြေညာပြီးပါပြီ</b>\n\n` +
            `✨ <b>ပေါက်သီး (ဒဲ့):</b> <code>${num}</code>\n` +
            `🔒 <b>မုဒ်:</b> MANUAL OVERRIDE\n\n` +
            `🔢 <b>တွတ် ဂဏန်းများ (${tut.allTut.length} ကွက်):</b>\n` +
            `<code>${tut.allTut.join(', ')}</code>\n\n` +
            `<i>Android အက်ပ်များနှင့် ဆာဗာအားလုံးတွင် ချက်ချင်း ရောင်ပြန်ဟပ်ပါမည်။</i>`
          );
          return new Response('ok');
        }

        if (adminState.action === 'awaiting_reseller_pay') {
          const rawText = text.trim();
          const targetResellerId = adminState.target_id || '';
          const targetName = adminState.target_name || targetResellerId;

          if (rawText.toLowerCase() === 'full' || rawText === 'အပြည့်' || rawText === 'အကုန်') {
            await setAdminState(chatId, null, env);
            const res = await settleResellerDue(env, targetResellerId, 'full', senderId);
            if (res.ok && res.reseller && res.paidAmount) {
              await sendTelegramMessage(env, chatId,
                `✅ <b>ကိုယ်စားလှယ် [${targetName}] ငွေစာရင်း အပြည့် ရှင်းလင်းပြီးပါပြီ</b>\n\n` +
                `👤 ကိုယ်စားလှယ်: <b>${res.reseller.name}</b> (ID: <code>${targetResellerId}</code>)\n` +
                `💵 ရှင်းလင်းငွေ ပမာဏ: <b>${res.paidAmount.toLocaleString()} ကျပ်</b>\n` +
                `📌 လက်ကျန် ပေးရန်ကျန်ငွေ (Due): <b>${(res.reseller.total_due || 0).toLocaleString()} ကျပ်</b>\n` +
                `💳 စုစုပေါင်း ပေးပြီးငွေ: <b>${(res.reseller.total_paid || 0).toLocaleString()} ကျပ်</b>`
              );
              // Notify Reseller
              await sendTelegramMessage(env, targetResellerId,
                `💳 <b>Admin ထံ ငွေပေးသွင်းမှု မှတ်တမ်းတင်ပြီးပါပြီ</b>\n\n` +
                `• ပေးသွင်းငွေ: <b>${res.paidAmount.toLocaleString()} ကျပ်</b>\n` +
                `• Admin သို့ ပေးရန်ကျန်ငွေ လက်ကျန်: <b>${(res.reseller.total_due || 0).toLocaleString()} ကျပ်</b>\n` +
                `ကျေးဇူးတင်ရှိပါသည်။`
              );
              const updated = await buildResellersListMessage(env);
              await sendTelegramMessage(env, chatId, updated.text, updated.keyboard);
            } else {
              await sendTelegramMessage(env, chatId, `❌ ငွေစာရင်း မှတ်တမ်းတင်ခြင်း မအောင်မြင်ပါ: ${res.error || 'အချက်အလက် စစ်ဆေးပါ'}`);
            }
            return new Response('ok');
          }

          // Clean number string (remove commas, ks, spaces, etc.)
          const cleanNumStr = rawText.replace(/[,kKsS\sကျပ်]/g, '');
          const amount = parseInt(cleanNumStr, 10);
          if (isNaN(amount) || amount <= 0) {
            await sendTelegramMessage(env, chatId,
              `⚠️ <b>ငွေပမာဏ မမှန်ကန်ပါ</b>\n\n` +
              `ကျေးဇူးပြု၍ ဂဏန်းသီးသန့် ရိုက်ထည့်ပေးပါ။\n` +
              `ဥပမာ: <code>35000</code> သို့မဟုတ် <code>75,000</code>\n\n` +
              `မလုပ်တော့ပါက <code>/cancel</code> ဟု ရိုက်ထည့်ပါ။`
            );
            return new Response('ok');
          }

          await setAdminState(chatId, null, env);
          const res = await settleResellerDue(env, targetResellerId, String(amount), senderId);
          if (res.ok && res.reseller && res.paidAmount) {
            await sendTelegramMessage(env, chatId,
              `✅ <b>ကိုယ်စားလှယ် [${targetName}] ငွေစာရင်း ရှင်းလင်းမှု အောင်မြင်ပါသည်</b>\n\n` +
              `👤 ကိုယ်စားလှယ်: <b>${res.reseller.name}</b> (ID: <code>${targetResellerId}</code>)\n` +
              `💵 ပေးသွင်းငွေ ပမာဏ: <b>${res.paidAmount.toLocaleString()} ကျပ်</b>\n` +
              `📌 လက်ကျန် ပေးရန်ကျန်ငွေ (Due): <b>${(res.reseller.total_due || 0).toLocaleString()} ကျပ်</b>\n` +
              `💳 စုစုပေါင်း ပေးပြီးငွေ: <b>${(res.reseller.total_paid || 0).toLocaleString()} ကျပ်</b>`
            );
            // Notify Reseller
            await sendTelegramMessage(env, targetResellerId,
              `💳 <b>Admin ထံ ငွေပေးသွင်းမှု မှတ်တမ်းတင်ပြီးပါပြီ</b>\n\n` +
              `• ပေးသွင်းငွေ: <b>${res.paidAmount.toLocaleString()} ကျပ်</b>\n` +
              `• Admin သို့ ပေးရန်ကျန်ငွေ လက်ကျန်: <b>${(res.reseller.total_due || 0).toLocaleString()} ကျပ်</b>\n` +
              `ကျေးဇူးတင်ရှိပါသည်။`
            );
            const updated = await buildResellersListMessage(env);
            await sendTelegramMessage(env, chatId, updated.text, updated.keyboard);
          } else {
            await sendTelegramMessage(env, chatId, `❌ ငွေစာရင်း မှတ်တမ်းတင်ခြင်း မအောင်မြင်ပါ: ${res.error || 'အချက်အလက် စစ်ဆေးပါ'}`);
          }
          return new Response('ok');
        }

        if (adminState.action === 'awaiting_ban_key') {
          const targetKey = text.trim();
          if (!targetKey) {
            await sendTelegramMessage(env, chatId, '⚠️ ပိတ်သိမ်းမည့် ကုတ်နံပါတ် ရိုက်ထည့်ပေးပါ။\nဥပမာ: <code>ABCD-1234-EFGH</code>');
            return new Response('ok');
          }
          await setAdminState(chatId, null, env);
          const ok = await revokeLicenseKey(env, targetKey);
          if (ok) {
            await sendTelegramMessage(env, chatId,
              `🔴 <b>ကုတ်နံပါတ် အား ပိတ်သိမ်း (Revoke/Ban) လိုက်ပါပြီ</b>\n\n` +
              `🔑 <code>${targetKey}</code>\n\n` +
              `အဆိုပါ ဖုန်းတွင် ဆော့ဝဲလ် အသုံးပြုခွင့်ကို အပြီးအပိုင် ရပ်ဆိုင်းလိုက်ပါပြီ။`,
              getAdminBottomKeyboard()
            );
            const activeMsg = await buildActiveKeysMessage(env, 1);
            await sendTelegramMessage(env, chatId, activeMsg.text, activeMsg.keyboard);
          } else {
            await sendTelegramMessage(env, chatId, `❌ ကုတ်နံပါတ် <code>${targetKey}</code> အား ပိတ်သိမ်းခြင်း မအောင်မြင်ပါ သို့မဟုတ် ကုတ်နံပါတ် မတွေ့ရှိပါ။`, getAdminBottomKeyboard());
          }
          return new Response('ok');
        }
      }
    }

    const userRoleKb = userIsAdmin ? getAdminBottomKeyboard() : (reseller ? getResellerBottomKeyboard() : getBuyerBottomKeyboard());

    // ── Public Interactive Bottom Keyboard Button Actions ─────────────────────
    if (text === '📲 အက်ပ် ဒေါင်းလုဒ်ရယူရန်' || text === '📲 အက်ပ် ဒေါင်းလုဒ်' || text === '/app' || text === '/download') {
      await sendAppToUser(env, chatId, senderId, msg.from?.username, msg.from?.first_name);
      return new Response('ok');
    }

    if (text === '📤 အက်ပ် တင်မည် (Upload APK)' || text === '📤 အက်ပ် တင်မည်' || text === '/uploadapp') {
      if (!userIsAdmin) {
        await sendTelegramMessage(env, chatId, '⛔ ဤလုပ်ဆောင်ချက်သည် Admin သီးသန့် ဖြစ်ပါသည်', userRoleKb);
        return new Response('unauthorized', { status: 200 });
      }
      const release = await getAppRelease(env);
      const curInfo = release ? `\n\n📌 <b>လက်ရှိ တင်ထားသော ဗားရှင်း:</b> <code>${release.file_name}</code> (${((release.file_size || 0)/(1024*1024)).toFixed(2)} MB)` : '';
      const prompt = `📤 <b>အက်ပ်ဗားရှင်း အသစ် တင်ရန်</b>${curInfo}\n\n` +
        `သင့်ဖုန်း သို့မဟုတ် ကွန်ပျူတာမှ နောက်ဆုံးထွက် <b>.apk</b> ဖိုင်ကို ဤ Telegram Chat ထံသို့ <b>File (Document)</b> အဖြစ် တိုက်ရိုက် ပို့ပေးပါ (Send File as Document)။\n\n` +
        `Bot မှ အလိုအလျောက် ဖမ်းယူပြီး ဝယ်ယူသူများထံ ချက်ချင်း ဖြန့်ဝေပေးပါမည်။`;
      await sendTelegramMessage(env, chatId, prompt, getAdminBottomKeyboard());
      return new Response('ok');
    }

    if (text === '🆔 ကျွန်ုပ်၏ ID' || text === '/id' || text === '/myid') {
      const roleName = userIsAdmin ? '👑 စီမံခန့်ခွဲသူ (Admin)' : (reseller ? `💼 ကိုယ်စားလှယ် (${reseller.name})` : '👤 အသုံးပြုသူ (Buyer)');
      await sendTelegramMessage(env, chatId,
        `🆔 <b>သင်၏ Telegram အချက်အလက်</b>\n\n` +
        `• <b>Telegram ID:</b> <code>${senderId}</code>\n` +
        `• <b>အမည်:</b> <b>${msg.from?.first_name || '-'}</b> ${msg.from?.last_name || ''} (@${msg.from?.username || '-'})\n` +
        `• <b>အဆင့်အတန်း:</b> ${roleName}`,
        userRoleKb
      );
      return new Response('ok');
    }

    if (text === '🇹🇭 3D Live ရလဒ်' || text.startsWith('/live') || text.startsWith('/3d') || text.startsWith('/result')) {
      const glo = await fetchFromGloLottery();
      if (glo) {
        const liveKb: InlineKeyboardMarkup = {
          inline_keyboard: [
            [
              { text: '🔄 အသစ်ပြန်စစ်မည်', callback_data: 'b_live_refresh' },
              { text: '🔢 တွတ် ဂဏန်းများ', callback_data: `tut:${glo.threeD}` }
            ],
            [
              { text: '📲 အက်ပ် ဒေါင်းလုဒ်ရယူရန်', callback_data: 'b_app_download' },
              { text: '🛒 လိုင်စင် ဝယ်ယူမည်', callback_data: 'b_buy_menu' }
            ]
          ]
        };
        await sendTelegramMessage(env, chatId,
          `🇹🇭 <b>ထိုင်း GLO တရားဝင် 3D ရလဒ်</b>\n\n` +
          `🎯 <b>3D ပေါက်ဂဏန်း:</b> <code>${glo.threeD}</code>\n` +
          `🥇 <b>ပထမဆု (1st Prize):</b> <code>${glo.firstPrize}</code>\n` +
          `🔢 <b>2D:</b> <code>${glo.twoD}</code>\n` +
          `📅 <b>ထွက်သည့်ရက်:</b> ${glo.date}\n` +
          `⚡ <b>ရင်းမြစ်:</b> ${glo.source || glo.session}`,
          userRoleKb
        );
        await sendTelegramMessage(env, chatId, '👇 အောက်ပါ ခလုတ်များကို နှိပ်၍ ဆက်လက် လုပ်ဆောင်နိုင်ပါသည်:', liveKb);
      } else {
        await sendTelegramMessage(env, chatId, '⚠️ <i>လတ်တလော ထိုင်း GLO ပေါက်မဲ မရရှိသေးပါ</i>', userRoleKb);
      }
      return new Response('ok');
    }

    if (text === '🔢 တွတ် ဂဏန်းများ') {
      let cur = '640';
      try {
        const token = await getFirebaseToken(env);
        const res = await fetch(`${env.FIREBASE_DB_URL}/3d_live_results/winning_number.json`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (res.ok) {
          const val = await res.json();
          if (val && /^\d{3}$/.test(String(val))) cur = String(val);
        }
      } catch (_) {}

      const tutKb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [
            { text: `🎯 ပေါက်သီး ${cur} တွတ်`, callback_data: `tut:${cur}` },
            { text: '108 တွတ်', callback_data: 'tut:108' }
          ],
          [
            { text: '212 တွတ်', callback_data: 'tut:212' },
            { text: '345 တွတ်', callback_data: 'tut:345' },
            { text: '567 တွတ်', callback_data: 'tut:567' }
          ],
          [
            { text: '890 တွတ်', callback_data: 'tut:890' },
            { text: '000 တွတ်', callback_data: 'tut:000' }
          ]
        ]
      };
      await sendTelegramMessage(env, chatId,
        `🔢 <b>မြန်မာ 3D တွတ် (Tut) ဂဏန်းများ တွက်ချက်ခြင်း</b>\n\n` +
        `အောက်ပါ ဂဏန်းခလုတ်များကို နှိပ်၍ တွတ်ဂဏန်းများ ချက်ချင်း စစ်ဆေးနိုင်ပါသည် 👇`,
        userRoleKb
      );
      await sendTelegramMessage(env, chatId, 'ဂဏန်း ရွေးချယ်ပါ:', tutKb);
      return new Response('ok');
    }

    if (text === '🎯 ပေါက်မဲ စစ်မည်') {
      let currentWinner = '';
      try {
        const token = await getFirebaseToken(env);
        const res = await fetch(`${env.FIREBASE_DB_URL}/3d_live_results/winning_number.json`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (res.ok) currentWinner = String(await res.json() || '');
      } catch (_) {}

      if (currentWinner) {
        const tut = calculateTutNumbers(currentWinner);
        const winnerKb: InlineKeyboardMarkup = {
          inline_keyboard: [
            [
              { text: `🔢 ပေါက်သီး (${currentWinner}) တွတ်များ ကြည့်မည်`, callback_data: `tut:${currentWinner}` }
            ],
            [
              { text: '🔄 အသစ်ပြန်စစ်မည်', callback_data: 'b_live_refresh' },
              { text: '🛒 လိုင်စင် ဝယ်ယူမည်', callback_data: 'b_buy_menu' }
            ]
          ]
        };
        await sendTelegramMessage(env, chatId,
          `🎯 <b>လက်ရှိ တရားဝင် ပေါက်ဂဏန်း</b>\n\n` +
          `✨ <b>ပေါက်သီး (ဒဲ့):</b> <code>${currentWinner}</code>\n\n` +
          `🔢 <b>တွတ် ဂဏန်းများ (${tut.allTut.length} ကွက်):</b>\n<code>${tut.allTut.join(', ')}</code>`,
          userRoleKb
        );
        await sendTelegramMessage(env, chatId, '👇 ဆက်လက်လုပ်ဆောင်ရန် ခလုတ်များ:', winnerKb);
      } else {
        await sendTelegramMessage(env, chatId, '⚠️ <i>လက်ရှိ ပေါက်ဂဏန်း မကြေညာရသေးပါ။ ထွက်ပြီးပါက ဤနေရာတွင် အလိုအလျောက် ပြသပေးပါမည်။</i>', userRoleKb);
      }
      return new Response('ok');
    }

    if (text === '❓ အကူအညီ' || text === '/help') {
      const helpText = `📖 <b>3D LEDGER စာရင်းကိုင် ဆော့ဝဲလ် အသုံးပြုနည်း လမ်းညွှန်</b>\n\n` +
        `1️⃣ <b>အက်ပ် ဒေါင်းလုဒ်ဆွဲခြင်း:</b>\n` +
        `• <b>[📲 အက်ပ် ဒေါင်းလုဒ်ရယူရန်]</b> ခလုတ်ကို နှိပ်၍ APK ဖိုင်ကို တိုက်ရိုက် ဒေါင်းလုဒ်ဆွဲပြီး Install လုပ်ပါ။\n\n` +
        `2️⃣ <b>အခမဲ့ စမ်းသပ်ခွင့် (Free Trial):</b>\n` +
        `• <b>[🎁 ၃ ရက် အခမဲ့ စမ်းသပ်ခွင့်]</b> ခလုတ်ကို နှိပ်၍ CD-Key ရယူပါ။\n` +
        `• Android အက်ပ်တွင် ကုတ်နံပါတ် ထည့်သွင်းပြီးသည်နှင့် ၇၂ နာရီ စတင် ရေတွက်ပါမည်။\n\n` +
        `3️⃣ <b>လိုင်စင် ဝယ်ယူခြင်း:</b>\n` +
        `• <b>[🛒 လိုင်စင် ဝယ်ယူမည်]</b> ခလုတ်ကို နှိပ်ပြီး ၁ နှစ် သို့မဟုတ် တစ်သက်တာ အစီအစဉ် ရွေးချယ်ပါ။\n` +
        `• ပြသထားသော Wave Pay သို့မဟုတ် KBZPay သို့ ငွေလွှဲပြီး ပြေစာ Screenshot ပို့ပေးပါက Admin မှ ကုတ်နံပါတ် ချက်ချင်း ထုတ်ပေးပါမည်။\n\n` +
        `4️⃣ <b>ပေါက်မဲနှင့် တွတ် စစ်ဆေးခြင်း:</b>\n` +
        `• <b>[🇹🇭 3D Live ရလဒ်]</b> ခလုတ်ဖြင့် တရားဝင် ထိုင်း GLO ပေါက်မဲကို တိုက်ရိုက် ကြည့်ရှုနိုင်ပါသည်။`;
      await sendTelegramMessage(env, chatId, helpText, userRoleKb);
      return new Response('ok');
    }

    if (text.startsWith('/tut')) {
      const parts = text.split(/\s+/);
      const num = parts.length > 1 ? parts[1].trim() : '';
      if (/^\d{3}$/.test(num)) {
        const tut = calculateTutNumbers(num);
        await sendTelegramMessage(env, chatId,
          `🔢 <b>ဂဏန်း <code>${num}</code> ၏ တွတ် ဂဏန်းများ (${tut.allTut.length} ကွက်):</b>\n\n` +
          `<code>${tut.allTut.join(', ')}</code>\n\n` +
          `• <i>အပြန်:</i> ${tut.permutations.join(', ') || 'မရှိပါ'}\n` +
          `• <i>ကပ်သီး:</i> ${tut.nearMisses.join(', ')}`,
          userRoleKb
        );
      } else {
        await sendTelegramMessage(env, chatId, '⚠️ <i>၃ လုံးဂဏန်း ရိုက်ထည့်ပေးပါ။ ဥပမာ: /tut 108</i>', userRoleKb);
      }
      return new Response('ok');
    }

    if (text.startsWith('/check')) {
      const parts = text.split(/\s+/);
      const num = parts.length > 1 ? parts[1].trim() : '';
      if (/^\d{3}$/.test(num)) {
        let currentWinner = '';
        try {
          const token = await getFirebaseToken(env);
          const res = await fetch(`${env.FIREBASE_DB_URL}/3d_live_results/winning_number.json`, {
            headers: { 'Authorization': `Bearer ${token}` }
          });
          if (res.ok) currentWinner = String(await res.json() || '');
        } catch (_) {}

        if (!currentWinner) {
          await sendTelegramMessage(env, chatId, '⚠️ <i>လက်ရှိ ပေါက်ဂဏန်း မကြေညာရသေးပါ</i>', userRoleKb);
        } else if (num === currentWinner) {
          await sendTelegramMessage(env, chatId, `🎉 <b>ဂုဏ်ယူပါသည်! <code>${num}</code> သည် တိုက်ရိုက်ပေါက်သီး (ဒဲ့) ဖြစ်ပါသည်!</b>`, userRoleKb);
        } else {
          const tut = calculateTutNumbers(currentWinner);
          if (tut.allTut.includes(num)) {
            await sendTelegramMessage(env, chatId, `✨ <b>ဂုဏ်ယူပါသည်! <code>${num}</code> သည် တွတ် ဂဏန်း ပေါက်ပါသည်!</b> (ပေါက်သီး: <code>${currentWinner}</code>)`, userRoleKb);
          } else {
            await sendTelegramMessage(env, chatId, `❌ <code>${num}</code> သည် ပေါက်မဲ မဟုတ်ပါ။ (ပေါက်သီး: <code>${currentWinner}</code>)`, userRoleKb);
          }
        }
      } else {
        await sendTelegramMessage(env, chatId, '⚠️ <i>၃ လုံးဂဏန်း ရိုက်ထည့်ပေးပါ။ ဥပမာ: /check 108</i>', userRoleKb);
      }
      return new Response('ok');
    }

    if (text.startsWith('/batch')) {
      let b = '1';
      let drawDate = '';
      let nextDrawDate = '';
      try {
        const token = await getFirebaseToken(env);
        const res = await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/current_batch.json`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (res.ok) b = String(await res.json() || '1');
        const liveRes = await fetch(`${env.FIREBASE_DB_URL}/3d_live_results.json`);
        if (liveRes.ok) {
          const liveData = await liveRes.json() as any;
          drawDate = liveData?.result_date || '';
          nextDrawDate = liveData?.target_draw_date || '';
        }
      } catch (_) {}
      const msg = `📦 <b>3D ထီထွက်ရက်စွဲ နှင့် အပတ်စဉ်</b>\n\n` +
        (drawDate ? `📅 <b>နောက်ဆုံးထွက်ရက်:</b> <code>${drawDate}</code>\n` : '') +
        (nextDrawDate ? `⏭️ <b>နောက်ထွက်မည့်ရက်:</b> <code>${nextDrawDate}</code>\n` : '') +
        `🔢 <b>စနစ် သတ်မှတ် အကြိမ်:</b> #${b}\n\n` +
        `💡 <i>မှတ်ချက်: အက်ပ်အသုံးပြုသူများသည် မိမိဖုန်းအလိုက် အကြိမ်များကို သီးခြားစီမံနိုင်ပါသည်။</i>`;
      await sendTelegramMessage(env, chatId, msg, userRoleKb);
      return new Response('ok');
    }

    // ── NON-ADMIN, NON-RESELLER USER FLOW (DIRECT BUYER) ─────────────────────
    if (!userIsAdmin && !reseller) {
      if (text === '/menu' || text === '👋 မီနူး') {
        const plans = await getSalePlans(env);
        const oneYearPrice = plans.one_year?.price ? plans.one_year.price.toLocaleString() : '180,000';
        const lifetimePrice = plans.lifetime?.price ? plans.lifetime.price.toLocaleString() : '45,000';
        const menuMsg = `👋 <b>မင်္ဂလာပါ! 3D LEDGER စနစ်မှ ကြိုဆိုပါသည်</b>\n\n` +
          `📅 <b>၁ နှစ် သက်တမ်း:</b> <b>${oneYearPrice} ကျပ်</b> / နှစ် (ဖုန်းပြောင်းသုံးနိုင်သည် ✅)\n` +
          `💎 <b>တစ်သက်တာ သက်တမ်း:</b> <b>${lifetimePrice} ကျပ်</b> (ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည် 🔒)\n\n` +
          `လုပ်ဆောင်လိုသော လုပ်ငန်းစဉ်ကို အောက်ပါ ခလုတ်များမှ ရွေးချယ်ပါ 👇`;
        await sendTelegramMessage(env, chatId, menuMsg, getBuyerBottomKeyboard());
        await sendTelegramMessage(env, chatId, 'လိုင်စင် အစီအစဉ်များ:', getBuyerPlansKeyboard());
        return new Response('ok');
      }

      // Protect Admin/Reseller commands from unauthorized users
      if (
        text.startsWith('/admin') ||
        text.startsWith('/keys') ||
        text.startsWith('/pending') ||
        text.startsWith('/report') ||
        text.startsWith('/revoke') ||
        text.startsWith('/setwinner') ||
        text.startsWith('/setbatch') ||
        text.startsWith('/setprice') ||
        text.startsWith('/autoapprove') ||
        text.startsWith('/auto') ||
        text.startsWith('/gen') ||
        text.startsWith('/admins') ||
        text.startsWith('/addadmin') ||
        text.startsWith('/removeadmin') ||
        text.startsWith('/resellers') ||
        text.startsWith('/addreseller') ||
        text.startsWith('/removereseller') ||
        text.startsWith('/payreseller') ||
        text.startsWith('/commissions') ||
        text.startsWith('/setcommission') ||
        text.startsWith('/payments') ||
        text.startsWith('/setwave') ||
        text.startsWith('/setkpay')
      ) {
        await sendTelegramMessage(
          env,
          chatId,
          `⛔ <b>ခွင့်ပြုချက် မရှိပါ (Access Denied)</b>\n\n` +
          `ဤ Telegram Bot သည် <b>3D Ledger စနစ် စီမံခန့်ခွဲသူ (Admin) နှင့် ကိုယ်စားလှယ်</b> များ သီးသန့် အသုံးပြုရန် ဖြစ်ပါသည်။\n\n` +
          `သင့် Telegram ID (<code>${senderId}</code>) တွင် အသုံးပြုခွင့် မရှိပါ။`,
          getBuyerBottomKeyboard()
        );
        return new Response('unauthorized', { status: 200 });
      }

      // Handle Direct Purchase Button click
      if (text === '🛒 လိုင်စင် ဝယ်ယူမည်' || text === '/buy') {
        const plans = await getSalePlans(env);
        const oneYearPrice = plans.one_year?.price ? plans.one_year.price.toLocaleString() : '180,000';
        const lifetimePrice = plans.lifetime?.price ? plans.lifetime.price.toLocaleString() : '45,000';
        const buyMsg = `💎 <b>3D LEDGER လိုင်စင် အစီအစဉ်များ ဝယ်ယူရန်</b>\n\n` +
          `📅 <b>၁ နှစ် သက်တမ်း (1-Year Plan):</b>\n` +
          `• 💰 ဈေးနှုန်း: <b>${oneYearPrice} ကျပ်</b> / နှစ်\n` +
          `• 📱 စက်မူဝါဒ: ✅ <b>ဖုန်းပြောင်းသုံးနိုင်သည် (Device Changeable)</b>\n\n` +
          `💎 <b>တစ်သက်တာ သက်တမ်း (Lifetime Plan):</b>\n` +
          `• 💰 ဈေးနှုန်း: <b>${lifetimePrice} ကျပ်</b>\n` +
          `• 📱 စက်မူဝါဒ: 🔒 <b>ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည် (1-Device Locked)</b>\n\n` +
          `ဝယ်ယူလိုသော အစီအစဉ်ကို အောက်ပါ ခလုတ်မှ ရွေးချယ်ပါ 👇`;
        await sendTelegramMessage(env, chatId, buyMsg, getBuyerPlansKeyboard());
        return new Response('ok');
      }

      // Ensure buyer bottom keyboard is pinned
      await sendTelegramMessage(env, chatId, '👋 <b>3D LEDGER စနစ်မှ ကြိုဆိုပါသည်!</b>', getBuyerBottomKeyboard());

      // Deep-link: /start download (Triggered from App Update or Website)
      if (text.startsWith('/start download')) {
        const release = await getAppRelease(env);
        if (release && release.file_id) {
          const sizeMb = ((release.file_size || 0) / (1024 * 1024)).toFixed(2);
          const caption = `📱 <b>3D LEDGER စာရင်းကိုင် ဆော့ဝဲလ် (Official APK)</b>\n\n` +
            `📦 <b>ဖိုင်အမည်:</b> <code>${release.file_name}</code> (${sizeMb} MB)\n` +
            `🔖 <b>ဗားရှင်း:</b> ${release.version_name || 'Latest'}\n\n` +
            `📥 <b>ဒေါင်းလုဒ်ဆွဲပြီး စမ်းသပ်အသုံးပြုနည်း လမ်းညွှန်:</b>\n` +
            `1️⃣ အထက်ပါ APK ဖိုင်ကို နှိပ်၍ Download ဆွဲပြီး ဖုန်းတွင် Install ပြုလုပ်ပါ။\n` +
            `2️⃣ အက်ပ်ကို ဖွင့်ပြီး Bot မှ ရရှိထားသော <b>[🎁 ၃ ရက် အခမဲ့ CD-Key]</b> ကို ထည့်သွင်းပါ။\n` +
            `3️⃣ ၇၂ နာရီ စိတ်ကြိုက် စမ်းသပ် သုံးစွဲနိုင်ပါပြီ။\n` +
            `4️⃣ စမ်းသပ်ပြီး သဘောကျပါက <b>[🛒 လိုင်စင် ဝယ်ယူမည်]</b> မှတစ်ဆင့် ၁ နှစ် သို့မဟုတ် တစ်သက်တာ လိုင်စင် ဝယ်ယူနိုင်ပါသည်။`;
          await sendTelegramDocument(env, chatId, release.file_id, caption, getBuyerPlansKeyboard());
          return new Response('ok');
        }
      }

      // Default or /start: Free 3-Day Trial + Plans Showcase with Buy Buttons!
      let token = '';
      try {
        token = await getFirebaseToken(env);
      } catch (_) {}

      let existingTrial: { cd_key: string; created_at: number } | null = null;
      if (token) {
        try {
          const res = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/telegram_trials/${senderId}.json`, {
            headers: { 'Authorization': `Bearer ${token}` }
          });
          if (res.ok) {
            existingTrial = await res.json() as any;
          }
        } catch (_) {}
      }

      const plans = await getSalePlans(env);
      const oneYearPrice = plans.one_year?.price ? plans.one_year.price.toLocaleString() : '180,000';
      const lifetimePrice = plans.lifetime?.price ? plans.lifetime.price.toLocaleString() : '45,000';

      if (!existingTrial) {
        const trialKey = generateCdKey();
        const trialRecord: LicenseKeyRecord = {
          cd_key: trialKey,
          plan_id: 'trial_3d',
          duration: 'trial',
          duration_label: '၃ ရက် စမ်းသပ်ခွင့် (72 နာရီ)',
          price: 0,
          device_changeable: false,
          status: 'available',
          created_at: Date.now(),
          claimed_by_telegram_id: senderId,
          claimed_by_username: msg.from?.username || msg.from?.first_name || ''
        };

        if (token) {
          await saveLicenseKey(env, trialRecord);
          await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/telegram_trials/${senderId}.json`, {
            method: 'PUT',
            headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({
              telegram_id: senderId,
              username: msg.from?.username || '',
              first_name: msg.from?.first_name || '',
              cd_key: trialKey,
              created_at: Date.now()
            })
          });

          await notifyAllAdmins(env,
            `🎁 <b>[Free Trial Claimed] အခမဲ့ စမ်းသပ်ခွင့် ကုတ် အသစ် ရယူသွားပါသည်</b>\n\n` +
            `👤 အသုံးပြုသူ: <b>${msg.from?.first_name || 'မိတ်ဆွေ'}</b> (@${msg.from?.username || '-'})\n` +
            `🆔 Telegram ID: <code>${senderId}</code>\n` +
            `🔑 ကုတ်နံပါတ်: <code>${trialKey}</code>\n` +
            `⏳ သက်တမ်း: <b>၇၂ နာရီ (၃ ရက်)</b>`
          );
        }

        const welcomeText = `🎁 <b>မင်္ဂလာပါ ${msg.from?.first_name || 'မိတ်ဆွေ'}</b>\n\n` +
          `<b>3D LEDGER စာရင်းကိုင် ဆော့ဝဲလ်</b> မှ ကြိုဆိုပါသည်!\n` +
          `သင့်အတွက် <b>၃ ရက် အခမဲ့ စမ်းသပ်ခွင့် (3-Day Free Trial)</b> ကုတ်နံပါတ် ထုတ်ပေးလိုက်ပါပြီ:\n\n` +
          `🔑 <b>သင်၏ အခမဲ့ CD-Key:</b>\n` +
          `<code>${trialKey}</code>\n` +
          `<i>(ကုတ်နံပါတ်ကို နှိပ်၍ Copy ကူးပြီး Android အက်ပ်တွင် ရိုက်ထည့်ပါ)</i>\n\n` +
          `⚠️ <b>အရေးကြီး သက်တမ်း သတိပေးချက်:</b>\n` +
          `• ဤကုတ်သည် အက်ပ်တွင် စတင်ထည့်သွင်းချိန်မှ <b>၇၂ နာရီ (၃ ရက်)</b> ပြည့်ပါက Online / Offline ဖြစ်စေ သက်တမ်း အလိုအလျောက် ကုန်ဆုံးပါမည်။\n` +
          `• ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်ပြီး အခြားဖုန်းသို့ စက်ပြောင်းလဲ၍ မရပါ။\n` +
          `• သက်တမ်း ကုန်ဆုံးပါက အက်ပ်သို့ ဆက်လက်ဝင်ရောက်နိုင်မည် မဟုတ်ဘဲ လိုင်စင် အသစ် ဝယ်ယူရပါမည်။\n\n` +
          `💎 <b>ဝယ်ယူရရှိနိုင်သော လိုင်စင် အစီအစဉ်များ:</b>\n` +
          `• <b>၁ နှစ် သက်တမ်း:</b> <b>${oneYearPrice} ကျပ်</b> / နှစ် (ဖုန်းပြောင်းသုံးနိုင်သည် - Device Changeable ✅)\n` +
          `• <b>တစ်သက်တာ သက်တမ်း:</b> <b>${lifetimePrice} ကျပ်</b> (ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည် - 1-Device Locked 🔒)\n\n` +
          `🛒 <b>တိုက်ရိုက် ဝယ်ယူလိုပါက အောက်ပါ ခလုတ်များကို နှိပ်ပါ 👇</b>`;

        const trialKeyKb: InlineKeyboardMarkup = {
          inline_keyboard: [
            [{ text: '📋 အခမဲ့ CD-Key ကူးယူမည် (Copy Key)', copy_text: { text: trialKey } }],
            ...getBuyerPlansKeyboard().inline_keyboard
          ]
        };
        await sendTelegramMessage(env, chatId, welcomeText, trialKeyKb);
      } else {
        const keyDataRes = await fetch(`${env.FIREBASE_DB_URL}/3d_licenses/keys/${existingTrial.cd_key}.json`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        const keyData = keyDataRes.ok ? await keyDataRes.json() as any : null;

        let statusText = '⚪ အသင့်ရှိသည် (မသုံးရသေးပါ)';
        if (keyData?.status === 'active') {
          if (keyData.expires_at && Date.now() >= keyData.expires_at) {
            statusText = '🔴 သက်တမ်း ကုန်ဆုံးသွားပါပြီ (Expired)';
          } else if (keyData.expires_at) {
            const hoursLeft = Math.max(0, Math.ceil((keyData.expires_at - Date.now()) / (1000 * 60 * 60)));
            statusText = `🟢 အသုံးပြုဆဲ (${hoursLeft} နာရီ ကျန်)`;
          } else {
            statusText = '🟢 အသုံးပြုဆဲ';
          }
        } else if (keyData?.status === 'revoked') {
          statusText = '🔴 ပိတ်သိမ်းထားပါသည်';
        }

        const msgText = `👋 <b>မင်္ဂလာပါ ${msg.from?.first_name || 'မိတ်ဆွေ'}</b>\n\n` +
          `သင်သည် <b>၃ ရက် အခမဲ့ စမ်းသပ်ခွင့်</b> ရယူထားပြီး ဖြစ်ပါသည်:\n\n` +
          `🔑 <b>သင်၏ CD-Key:</b>\n` +
          `<code>${existingTrial.cd_key}</code>\n` +
          `📌 <b>အခြေအနေ:</b> ${statusText}\n\n` +
          `⚠️ <i>စမ်းသပ်ခွင့် ၇၂ နာရီ ကုန်ဆုံးပါက ဆက်လက်အသုံးပြုရန် လိုင်စင် အသစ် ဝယ်ယူရပါမည်။</i>\n\n` +
          `💎 <b>ဝယ်ယူရရှိနိုင်သော လိုင်စင် အစီအစဉ်များ:</b>\n` +
          `• <b>၁ နှစ် သက်တမ်း:</b> <b>${oneYearPrice} ကျပ်</b> / နှစ် (ဖုန်းပြောင်းသုံးနိုင်သည် - Device Changeable ✅)\n` +
          `• <b>တစ်သက်တာ သက်တမ်း:</b> <b>${lifetimePrice} ကျပ်</b> (ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည် - 1-Device Locked 🔒)\n\n` +
          `🛒 <b>တိုက်ရိုက် ဝယ်ယူလိုပါက အောက်ပါ ခလုတ်များကို နှိပ်ပါ 👇</b>`;

        const existingTrialKb: InlineKeyboardMarkup = {
          inline_keyboard: [
            [{ text: '📋 သင်၏ CD-Key ကူးယူမည် (Copy Key)', copy_text: { text: existingTrial.cd_key } }],
            ...getBuyerPlansKeyboard().inline_keyboard
          ]
        };
        await sendTelegramMessage(env, chatId, msgText, existingTrialKb);
      }
      return new Response('ok');
    }

    // ── RESELLER USER FLOW ───────────────────────────────────────────────────
    if (reseller && !userIsAdmin) {
      if (text === '/start' || text === '/reseller' || text === '/menu' || text === '💼 ဒက်ရှ်ဘုတ်') {
        await sendTelegramMessage(env, chatId, '💼 <b>3D LEDGER ကိုယ်စားလှယ် ဒက်ရှ်ဘုတ်</b>', getResellerBottomKeyboard());
        const dash = buildResellerDashboardMessage(reseller);
        await sendTelegramMessage(env, chatId, dash.text, dash.keyboard);
        return new Response('ok');
      }

      if (text === '➕ ကုတ်အသစ် ထုတ်မည်') {
        const genText = `➕ <b>ကိုယ်စားလှယ် ကုတ်အသစ် ထုတ်ယူရန် စီမံချက် ရွေးချယ်ပါ</b>\n\n` +
          `ထုတ်ယူလိုသော အစီအစဉ်ကို အောက်ပါ ခလုတ်များမှ ရွေးချယ်ပါ 👇`;
        await sendTelegramMessage(env, chatId, genText, getResellerDashboardKeyboard());
        return new Response('ok');
      }

      if (text === '🎁 ၃ ရက် Trial' || text === '📅 ၁ နှစ် လိုင်စင်' || text === '💎 တစ်သက်တာ လိုင်စင်') {
        let planKey = 'one_year';
        if (text === '🎁 ၃ ရက် Trial') planKey = 'trial_3d';
        else if (text === '💎 တစ်သက်တာ လိုင်စင်') planKey = 'lifetime';

        const plans = await getSalePlans(env);
        const targetPlan = plans[planKey] || getDefaultSalePlans()[planKey];
        const isChangeable = targetPlan.device_changeable;
        const changeTag = isChangeable ? '🔄 စက်ပြောင်းနိုင် (Device Changeable)' : '🔒 စက်ပြောင်းမရ (1 Device Only)';
        const priceText = targetPlan.price === 0 ? 'အခမဲ့ (0 Ks)' : `${targetPlan.price.toLocaleString()} ကျပ်`;

        const newKey = generateCdKey();
        const record: LicenseKeyRecord = {
          cd_key: newKey,
          plan_id: targetPlan.id,
          duration: targetPlan.duration,
          duration_label: targetPlan.duration_label,
          price: targetPlan.price,
          device_changeable: isChangeable,
          status: 'available',
          created_at: Date.now(),
          generated_by_reseller_id: senderId,
          reseller_name: reseller.name
        };

        await saveLicenseKey(env, record);
        reseller.total_generated = (reseller.total_generated || 0) + 1;
        await saveReseller(env, reseller);

        await sendTelegramMessage(
          env,
          chatId,
          `✅ <b>[ကိုယ်စားလှယ်] လိုင်စင်ကုတ် အသစ် ထုတ်ယူပြီးပါပြီ</b>\n\n` +
          `🔑 <b>ကုတ်နံပါတ်:</b>\n<code>${newKey}</code>\n\n` +
          `🏷️ အစီအစဉ်: <b>${targetPlan.name}</b>\n` +
          `⏳ သက်တမ်း: <b>${targetPlan.duration_label}</b>\n` +
          `📱 စက်ပြောင်းခွင့်: <b>${changeTag}</b>\n` +
          `💰 ဈေးနှုန်း: <b>${priceText}</b>\n` +
          `📌 အခြေအနေ: ⚪ အသင့်ရှိသည် (Available)\n\n` +
          `<i>(ဝယ်ယူသူထံ ပေးပို့နိုင်ပါသည်)</i>`,
          getResellerDashboardKeyboard()
        );

        await notifyAllAdmins(env,
          `📢 <b>[ကိုယ်စားလှယ် ကုတ်ထုတ်ယူမှု]</b>\n\n` +
          `👤 ကိုယ်စားလှယ်: <b>${reseller.name}</b> (ID: <code>${senderId}</code>)\n` +
          `📦 အစီအစဉ်: <b>${targetPlan.name}</b>\n` +
          `🔑 ကုတ်နံပါတ်: <code>${newKey}</code>\n` +
          `💰 ဈေးနှုန်း: <b>${priceText}</b>\n` +
          `⏰ အချိန်: ${new Date().toLocaleTimeString('my-MM')}`
        );
        return new Response('ok');
      }

      if (text === '📦 အများပြား ထုတ်မည် (Bulk)') {
        const bulkText = `📦 <b>[ကိုယ်စားလှယ်] အများပြား ကုတ်ထုတ်ယူရန် (Bulk Keys)</b>\n\n` +
          `ထုတ်ယူလိုသော အစီအစဉ်နှင့် အရေအတွက်ကို အောက်ပါ ခလုတ်များမှ ရွေးချယ်ပါ 👇`;
        await sendTelegramMessage(env, chatId, bulkText, getResellerBulkKeyboard());
        return new Response('ok');
      }

      if (text.startsWith('/gen')) {
        const parts = text.split(/\s+/);
        const plans = await getSalePlans(env);

        if (parts.length === 1) {
          const genText = `➕ <b>ကိုယ်စားလှယ် ကုတ်အသစ် ထုတ်ယူရန် စီမံချက် ရွေးချယ်ပါ</b>\n\n` +
            `• <code>/gen one_year</code> - ၁ နှစ် သက်တမ်း (Device Changeable)\n` +
            `• <code>/gen lifetime</code> - တစ်သက်တာ (1 Device Only)\n` +
            `• <code>/gen trial_3d</code> - ၃ ရက် စမ်းသပ်ခွင့် (Free Trial)\n` +
            `• <code>/gen one_year 5</code> - ၁ နှစ် ကုတ် ၅ ခု တစ်ပြိုင်နက် ထုတ်ရန်`;
          await sendTelegramMessage(env, chatId, genText, getResellerDashboardKeyboard());
          return new Response('ok');
        }

        const arg1 = parts[1].toLowerCase();
        let targetPlan: SalePlan | null = null;
        let bulkCount = 1;

        if (arg1 === 'one_year' || arg1 === 'year' || arg1 === '1y' || arg1 === '365') {
          targetPlan = plans.one_year || getDefaultSalePlans().one_year;
        } else if (arg1 === 'lifetime' || arg1 === 'life' || arg1 === 'lt') {
          targetPlan = plans.lifetime || getDefaultSalePlans().lifetime;
        } else if (arg1 === 'trial_3d' || arg1 === 'trial' || arg1 === '3d' || arg1 === '3') {
          targetPlan = plans.trial_3d || getDefaultSalePlans().trial_3d;
        } else if (plans[arg1] && (arg1 === 'one_year' || arg1 === 'lifetime' || arg1 === 'trial_3d')) {
          targetPlan = plans[arg1];
        }

        if (!targetPlan) {
          await sendTelegramMessage(env, chatId, '⚠️ ကိုယ်စားလှယ်များ အနေဖြင့် trial_3d, one_year နှင့် lifetime ကုတ်များကိုသာ ထုတ်ယူနိုင်ပါသည်။');
          return new Response('ok');
        }

        if (parts.length > 2) {
          const parsedCount = parseInt(parts[2], 10);
          if (!isNaN(parsedCount) && parsedCount >= 1 && parsedCount <= 50) {
            bulkCount = parsedCount;
          }
        }

        const isChangeable = targetPlan.device_changeable;
        const changeTag = isChangeable ? '🔄 စက်ပြောင်းနိုင် (Device Changeable)' : '🔒 စက်ပြောင်းမရ (1 Device Only)';
        const priceText = targetPlan.price === 0 ? 'အခမဲ့ (0 Ks)' : `${targetPlan.price.toLocaleString()} ကျပ်`;

        if (bulkCount === 1) {
          const newKey = generateCdKey();
          const record: LicenseKeyRecord = {
            cd_key: newKey,
            plan_id: targetPlan.id,
            duration: targetPlan.duration,
            duration_label: targetPlan.duration_label,
            price: targetPlan.price,
            device_changeable: isChangeable,
            status: 'available',
            created_at: Date.now(),
            generated_by_reseller_id: senderId,
            reseller_name: reseller.name
          };

          await saveLicenseKey(env, record);
          reseller.total_generated = (reseller.total_generated || 0) + 1;
          await saveReseller(env, reseller);

          await sendTelegramMessage(
            env,
            chatId,
            `✅ <b>[ကိုယ်စားလှယ်] လိုင်စင်ကုတ် အသစ် ထုတ်ယူပြီးပါပြီ</b>\n\n` +
            `🔑 <b>ကုတ်နံပါတ်:</b>\n<code>${newKey}</code>\n\n` +
            `🏷️ အစီအစဉ်: <b>${targetPlan.name}</b>\n` +
            `⏳ သက်တမ်း: <b>${targetPlan.duration_label}</b>\n` +
            `📱 စက်ပြောင်းခွင့်: <b>${changeTag}</b>\n` +
            `💰 ဈေးနှုန်း: <b>${priceText}</b>\n` +
            `📌 အခြေအနေ: ⚪ အသင့်ရှိသည် (Available)\n\n` +
            `<i>(ဝယ်ယူသူထံ ပေးပို့နိုင်ပါသည်)</i>`
          );

          await notifyAllAdmins(env,
            `📢 <b>[ကိုယ်စားလှယ် ကုတ်ထုတ်ယူမှု]</b>\n\n` +
            `👤 ကိုယ်စားလှယ်: <b>${reseller.name}</b> (ID: <code>${senderId}</code>)\n` +
            `📦 အစီအစဉ်: <b>${targetPlan.name}</b>\n` +
            `🔑 ကုတ်နံပါတ်: <code>${newKey}</code>\n` +
            `💰 ဈေးနှုန်း: <b>${priceText}</b>\n` +
            `⏰ အချိန်: ${new Date().toLocaleTimeString('my-MM')}`
          );
        } else {
          const keysList: string[] = [];
          for (let i = 0; i < bulkCount; i++) {
            const k = generateCdKey();
            const rec: LicenseKeyRecord = {
              cd_key: k,
              plan_id: targetPlan.id,
              duration: targetPlan.duration,
              duration_label: targetPlan.duration_label,
              price: targetPlan.price,
              device_changeable: isChangeable,
              status: 'available',
              created_at: Date.now(),
              generated_by_reseller_id: senderId,
              reseller_name: reseller.name
            };
            await saveLicenseKey(env, rec);
            keysList.push(k);
          }

          reseller.total_generated = (reseller.total_generated || 0) + bulkCount;
          await saveReseller(env, reseller);

          const formattedKeys = keysList.map((k, idx) => `${idx + 1}. <code>${k}</code>`).join('\n');
          await sendTelegramMessage(
            env,
            chatId,
            `📦 <b>[ကိုယ်စားလှယ်] ကုတ်ပေါင်း (${bulkCount}) ခု ထုတ်ယူပြီးပါပြီ</b>\n\n` +
            `🏷️ အစီအစဉ်: <b>${targetPlan.name}</b>\n` +
            `⏳ သက်တမ်း: <b>${targetPlan.duration_label}</b>\n` +
            `📱 စက်ပြောင်းခွင့်: <b>${changeTag}</b>\n` +
            `💰 ဈေးနှုန်း: <b>${priceText}</b>\n\n` +
            `🔑 <b>ကုတ်များ စာရင်း:</b>\n` +
            `${formattedKeys}`
          );

          await notifyAllAdmins(env,
            `📢 <b>[ကိုယ်စားလှယ် ကုတ်ထုတ်ယူမှု - အများပြား]</b>\n\n` +
            `👤 ကိုယ်စားလှယ်: <b>${reseller.name}</b> (ID: <code>${senderId}</code>)\n` +
            `📦 အစီအစဉ်: <b>${targetPlan.name}</b>\n` +
            `🔢 အရေအတွက်: <b>${bulkCount} ခု</b>\n` +
            `⏰ အချိန်: ${new Date().toLocaleTimeString('my-MM')}`
          );
        }
        return new Response('ok');
      }

      // Block admin-only commands
      await sendTelegramMessage(env, chatId, '⛔ ဤအမိန့်သည် ဒိုင်ချုပ် / စီမံခန့်ခွဲသူ (Admin) သီးသန့် ဖြစ်ပါသည်');
      return new Response('unauthorized', { status: 200 });
    }

    // ── ADMIN USER FLOW ───────────────────────────────────────────────────────

    // Command: /start or /menu or /admin or Bottom Keyboard: '📊 ပင်မ ဒက်ရှ်ဘုတ်'
    if (text === '/start' || text === '/menu' || text === '/admin' || text === '📊 ပင်မ ဒက်ရှ်ဘုတ်') {
      await sendTelegramMessage(env, chatId, '👑 <b>3D LEDGER စီမံခန့်ခွဲသူ (Admin) စနစ်</b>', getAdminBottomKeyboard());
      const dash = await buildAdminDashboardMessage(env);
      await sendTelegramMessage(env, chatId, dash.text, dash.keyboard);
      return new Response('ok');
    }

    if (text === '➕ ကုတ်အသစ် ထုတ်မည်') {
      const plans = await getSalePlans(env);
      const text = `➕ <b>ကုတ်အသစ် ထုတ်ယူရန် စီမံချက် ရွေးချယ်ပါ</b>\n\n` +
        `အသုံးပြုသူထံ ရောင်းချလိုသော အစီအစဉ်နှင့် အရေအတွက်ကို အောက်ပါ ခလုတ်များမှ ရွေးချယ်ပါ 👇`;
      await sendTelegramMessage(env, chatId, text, getKeyGenKeyboard(plans));
      return new Response('ok');
    }

    if (text === '👥 ကိုယ်စားလှယ်များ' || text === '/resellers') {
      const res = await buildResellersListMessage(env);
      await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      return new Response('ok');
    }

    if (text === '💳 ငွေလက်ခံ အကောင့်များ' || text === '/payments') {
      const res = await buildPaymentsMessage(env);
      await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      return new Response('ok');
    }

    if (text === '👮‍♂️ Admin များ' || text === '/admins') {
      const res = await buildAdminsListMessage(env);
      await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      return new Response('ok');
    }

    if (text === '💵 ကော်မရှင် သတ်မှတ်ချက်' || text === '/commissions') {
      const res = await buildCommissionsMessage(env);
      await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      return new Response('ok');
    }

    if (text === '🏷️ အရောင်း စီမံချက်များ' || text === '/plans') {
      const plans = await getSalePlans(env);
      const text = buildPlansDashboardMessage(plans);
      const kb = getPlansMenuKeyboard(plans);
      await sendTelegramMessage(env, chatId, text, kb);
      return new Response('ok');
    }

    if (text === '📋 အသုံးပြုမှု စောင့်ကြည့်' || text === '🚫 ကုတ် ပိတ်သိမ်းရန်' || text === '🚫 ကုတ် ပိတ်သိမ်း') {
      const res = await buildActiveKeysMessage(env, 1);
      await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      return new Response('ok');
    }

    if (text === '🔑 ထုတ်ယူထားသော ကုတ်များ' || text === '/genkeys' || text === '/generatedkeys') {
      const res = await buildGeneratedKeysMessage(env, 'available', 1);
      await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      return new Response('ok');
    }

    if (text === '🔑 ကျွန်ုပ်၏ ကုတ်များ' || text === '/mykeys') {
      const res = await buildResellerGeneratedKeysMessage(env, String(chatId), 1);
      await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      return new Response('ok');
    }

    if (text === '🎯 ပေါက်မဲ') {
      const text = `🎯 <b>3D ပေါက်မဲ သတ်မှတ်ခြင်း (Winner Declaration)</b>\n\n` +
        `ပေါက်ဂဏန်း သတ်မှတ်ရန် နည်းလမ်းကို အောက်ပါ ခလုတ်များမှ ရွေးချယ်ပါ 👇\n\n` +
        `• <b>ထိုင်း GLO ရလဒ် ရယူမည်:</b> တရားဝင် ထိုင်းထီပေါက်မဲကို အလိုအလျောက် ဆွဲယူသတ်မှတ်မည်\n` +
        `• <b>ကိုယ်တိုင် သတ်မှတ်မည်:</b> မိမိစိတ်ကြိုက် ၃ လုံးဂဏန်းကို ရိုက်ထည့်သတ်မှတ်မည်`;
      const kb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [{ text: '🇹🇭 Thai GLO ရလဒ် အလိုအလျောက် သတ်မှတ်မည်', callback_data: 'w_fetch_glo' }],
          [{ text: '✏️ ဂဏန်း ကိုယ်တိုင် သတ်မှတ်မည်', callback_data: 'w_manual_prompt' }],
          [{ text: '⬅️ ပင်မ မီနူးသို့ ပြန်သွားမည်', callback_data: 'm_main' }]
        ]
      };
      await sendTelegramMessage(env, chatId, text, kb);
      return new Response('ok');
    }

    // Command: /reseller (for Admin: show their reseller dashboard if registered, else list resellers)
    if (text === '/reseller') {
      if (reseller) {
        const dash = buildResellerDashboardMessage(reseller);
        await sendTelegramMessage(env, chatId, dash.text, dash.keyboard);
      } else {
        const res = await buildResellersListMessage(env);
        await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      }
      return new Response('ok');
    }

    // Command: /addadmin <id> [name]
    if (text.startsWith('/addadmin')) {
      const parts = text.split(/\s+/);
      const targetId = parts.length > 1 ? parts[1].trim() : '';
      const targetName = parts.length > 2 ? parts.slice(2).join(' ').trim() : 'Admin';
      if (!targetId || !/^\d+$/.test(targetId)) {
        await sendTelegramMessage(env, chatId, '⚠️ အသုံးပြုပုံ: <code>/addadmin [telegram_id] [အမည်]</code>\nဥပမာ: <code>/addadmin 123456789 ဦးအောင်</code>');
        return new Response('ok');
      }

      const ok = await addAdmin(env, targetId, targetName, senderId);
      if (ok) {
        await sendTelegramMessage(env, chatId, `✅ <b>Admin အသစ် [${targetName}] (ID: <code>${targetId}</code>) အား အောင်မြင်စွာ ခန့်အပ်လိုက်ပါပြီ။</b>`);
        await sendTelegramMessage(env, targetId, `🎉 <b>ဂုဏ်ယူပါသည်! သင့်အား 3D LEDGER စနစ်၏ Admin အဖြစ် ခန့်အပ်လိုက်ပါပြီ။</b>\n\nစီမံခန့်ခွဲမှု မီနူး ဖွင့်ရန် <code>/admin</code> ဟု စာပို့ပါ`);
      } else {
        await sendTelegramMessage(env, chatId, '❌ Admin ထည့်သွင်းခြင်း မအောင်မြင်ပါ');
      }
      return new Response('ok');
    }

    // Command: /removeadmin <id>
    if (text.startsWith('/removeadmin')) {
      const parts = text.split(/\s+/);
      const targetId = parts.length > 1 ? parts[1].trim() : '';
      if (!targetId) {
        await sendTelegramMessage(env, chatId, '⚠️ အသုံးပြုပုံ: <code>/removeadmin [telegram_id]</code>');
        return new Response('ok');
      }

      const ok = await removeAdmin(env, targetId);
      if (ok) {
        await sendTelegramMessage(env, chatId, `✅ <b>Admin (ID: <code>${targetId}</code>) အား ဖယ်ရှားလိုက်ပါပြီ။</b>`);
      } else {
        await sendTelegramMessage(env, chatId, '❌ Admin ဖယ်ရှားခြင်း မအောင်မြင်ပါ (Master Admin ကို ဖယ်ရှား၍ မရပါ)');
      }
      return new Response('ok');
    }

    // Command: /resellers
    if (text === '/resellers') {
      const res = await buildResellersListMessage(env);
      await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      return new Response('ok');
    }

    // Command: /addreseller <id> [name]
    if (text.startsWith('/addreseller')) {
      const parts = text.split(/\s+/);
      const targetId = parts.length > 1 ? parts[1].trim() : '';
      const targetName = parts.length > 2 ? parts.slice(2).join(' ').trim() : 'Reseller';
      if (!targetId || !/^\d+$/.test(targetId)) {
        await sendTelegramMessage(env, chatId, '⚠️ အသုံးပြုပုံ: <code>/addreseller [telegram_id] [အမည်]</code>\nဥပမာ: <code>/addreseller 987654321 ကိုစိုး</code>');
        return new Response('ok');
      }

      const ok = await addReseller(env, targetId, targetName);
      if (ok) {
        await sendTelegramMessage(env, chatId, `✅ <b>ကိုယ်စားလှယ် အသစ် [${targetName}] (ID: <code>${targetId}</code>) အား အောင်မြင်စွာ ထည့်သွင်းလိုက်ပါပြီ။</b>`);
        await sendTelegramMessage(env, targetId,
          `🎉 <b>မင်္ဂလာပါ ${targetName}! သင့်အား 3D LEDGER ကိုယ်စားလှယ် (Reseller) အဖြစ် စာရင်းသွင်းလိုက်ပါပြီ။</b>\n\n` +
          `• 3-Day Trial, 1-Year နှင့် Lifetime ကုတ်များကို မိမိကိုယ်တိုင် ထုတ်ယူ ရောင်းချနိုင်ပါသည်။\n` +
          `• ကိုယ်စားလှယ် ဒက်ရှ်ဘုတ် ကြည့်ရန် <code>/reseller</code> ဟု စာပို့ပေးပါခင်ဗျာ။`
        );
      } else {
        await sendTelegramMessage(env, chatId, '❌ ကိုယ်စားလှယ် ထည့်သွင်းခြင်း မအောင်မြင်ပါ');
      }
      return new Response('ok');
    }

    // Command: /removereseller <id>
    if (text.startsWith('/removereseller')) {
      const parts = text.split(/\s+/);
      const targetId = parts.length > 1 ? parts[1].trim() : '';
      if (!targetId) {
        await sendTelegramMessage(env, chatId, '⚠️ အသုံးပြုပုံ: <code>/removereseller [telegram_id]</code>');
        return new Response('ok');
      }

      const ok = await removeReseller(env, targetId);
      if (ok) {
        await sendTelegramMessage(env, chatId, `✅ <b>ကိုယ်စားလှယ် (ID: <code>${targetId}</code>) အား ဖယ်ရှားလိုက်ပါပြီ။</b>`);
      } else {
        await sendTelegramMessage(env, chatId, '❌ ကိုယ်စားလှယ် ဖယ်ရှားခြင်း မအောင်မြင်ပါ');
      }
      return new Response('ok');
    }

    // Command: /payreseller <id> <amount|full>
    if (text.startsWith('/payreseller')) {
      const parts = text.split(/\s+/);
      const targetId = parts.length > 1 ? parts[1].trim() : '';
      const amountStr = parts.length > 2 ? parts[2].trim() : '';
      if (!targetId || !amountStr) {
        await sendTelegramMessage(env, chatId,
          `⚠️ <b>အသုံးပြုပုံ မှားယွင်းနေပါသည်</b>\n\n` +
          `• အပြည့် ပေးသွင်းရန်: <code>/payreseller [id] full</code>\n` +
          `• တစ်စိတ်တစ်ပိုင်း ပေးသွင်းရန်: <code>/payreseller [id] 50000</code>\n\n` +
          `ဥပမာ: <code>/payreseller 987654321 full</code>`
        );
        return new Response('ok');
      }

      const res = await settleResellerDue(env, targetId, amountStr, senderId);
      if (res.ok && res.reseller && res.paidAmount) {
        await sendTelegramMessage(env, chatId,
          `✅ <b>ကိုယ်စားလှယ် ငွေပေးသွင်းမှု အောင်မြင်စွာ မှတ်တမ်းတင်ပြီးပါပြီ</b>\n\n` +
          `👤 ကိုယ်စားလှယ်: <b>${res.reseller.name}</b> (ID: <code>${targetId}</code>)\n` +
          `💵 ပေးသွင်းငွေ ပမာဏ: <b>${res.paidAmount.toLocaleString()} ကျပ်</b>\n` +
          `📌 လက်ကျန် ပေးရန်ငွေ (Due): <b>${(res.reseller.total_due || 0).toLocaleString()} ကျပ်</b>\n` +
          `💳 စုစုပေါင်း ပေးပြီးငွေ: <b>${(res.reseller.total_paid || 0).toLocaleString()} ကျပ်</b>`
        );

        // Notify Reseller
        await sendTelegramMessage(env, targetId,
          `💳 <b>Admin ထံ ငွေပေးသွင်းမှု မှတ်တမ်းတင်ပြီးပါပြီ</b>\n\n` +
          `• ပေးသွင်းငွေ: <b>${res.paidAmount.toLocaleString()} ကျပ်</b>\n` +
          `• Admin သို့ ပေးရန်ကျန်ငွေ လက်ကျန်: <b>${(res.reseller.total_due || 0).toLocaleString()} ကျပ်</b>\n` +
          `ကျေးဇူးတင်ရှိပါသည်။`
        );
      } else {
        await sendTelegramMessage(env, chatId, `❌ <i>ငွေစာရင်း မှတ်တမ်းတင်ခြင်း မအောင်မြင်ပါ: ${res.error || 'အချက်အလက် စစ်ဆေးပါ'}</i>`);
      }
      return new Response('ok');
    }

    // Command: /commissions
    if (text === '/commissions') {
      const res = await buildCommissionsMessage(env);
      await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      return new Response('ok');
    }

    // Command: /setcommission <lifetime|one_year> <amount>
    if (text.startsWith('/setcommission')) {
      const parts = text.split(/\s+/);
      const planType = parts.length > 1 ? parts[1].toLowerCase().trim() : '';
      const amount = parts.length > 2 ? parseInt(parts[2].replace(/,/g, ''), 10) : NaN;

      if ((planType !== 'lifetime' && planType !== 'one_year') || isNaN(amount) || amount < 0) {
        await sendTelegramMessage(env, chatId,
          `⚠️ <b>အသုံးပြုပုံ:</b> <code>/setcommission [lifetime|one_year] [ပမာဏ]</code>\n\n` +
          `ဥပမာ:\n` +
          `• <code>/setcommission lifetime 5000</code>\n` +
          `• <code>/setcommission one_year 10000</code>`
        );
        return new Response('ok');
      }

      const ok = await setCommissionConfig(env, planType as 'lifetime' | 'one_year', amount);
      if (ok) {
        await sendTelegramMessage(env, chatId, `✅ <b>[${planType}] အစီအစဉ်၏ ကိုယ်စားလှယ် ကော်မရှင်အား <code>${amount.toLocaleString()}</code> ကျပ် သို့ ပြောင်းလဲသတ်မှတ်လိုက်ပါပြီ။</b>`);
      } else {
        await sendTelegramMessage(env, chatId, '❌ ကော်မရှင် ပြင်ဆင်ခြင်း မအောင်မြင်ပါ');
      }
      return new Response('ok');
    }

    // Command: /payments
    if (text === '/payments') {
      const res = await buildPaymentsMessage(env);
      await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      return new Response('ok');
    }

    // Command: /setwave <number> [name]
    if (text.startsWith('/setwave')) {
      const parts = text.split(/\s+/);
      const num = parts.length > 1 ? parts[1].trim() : '';
      const name = parts.length > 2 ? parts.slice(2).join(' ').trim() : '3D Ledger Admin';
      if (!num) {
        await sendTelegramMessage(env, chatId, '⚠️ အသုံးပြုပုံ: <code>/setwave [နံပါတ်] [အကောင့်အမည်]</code>\nဥပမာ: <code>/setwave 09778899001 ဦးအောင်ကို</code>');
        return new Response('ok');
      }
      const ok = await setPaymentAccount(env, 'wave', num, name);
      if (ok) {
        await sendTelegramMessage(env, chatId, `✅ <b>Wave Pay အကောင့် အချက်အလက်များကို အောင်မြင်စွာ ပြင်ဆင်လိုက်ပါပြီ:</b>\n\n• နံပါတ်: <code>${num}</code>\n• အမည်: <b>${name}</b>`);
      } else {
        await sendTelegramMessage(env, chatId, '❌ Wave Pay အကောင့် ပြင်ဆင်ခြင်း မအောင်မြင်ပါ');
      }
      return new Response('ok');
    }

    // Command: /setkpay <number> [name]
    if (text.startsWith('/setkpay')) {
      const parts = text.split(/\s+/);
      const num = parts.length > 1 ? parts[1].trim() : '';
      const name = parts.length > 2 ? parts.slice(2).join(' ').trim() : '3D Ledger Admin';
      if (!num) {
        await sendTelegramMessage(env, chatId, '⚠️ အသုံးပြုပုံ: <code>/setkpay [နံပါတ်] [အကောင့်အမည်]</code>\nဥပမာ: <code>/setkpay 09778899001 ဦးအောင်ကို</code>');
        return new Response('ok');
      }
      const ok = await setPaymentAccount(env, 'kpay', num, name);
      if (ok) {
        await sendTelegramMessage(env, chatId, `✅ <b>KBZPay အကောင့် အချက်အလက်များကို အောင်မြင်စွာ ပြင်ဆင်လိုက်ပါပြီ:</b>\n\n• နံပါတ်: <code>${num}</code>\n• အမည်: <b>${name}</b>`);
      } else {
        await sendTelegramMessage(env, chatId, '❌ KBZPay အကောင့် ပြင်ဆင်ခြင်း မအောင်မြင်ပါ');
      }
      return new Response('ok');
    }

    // Command: /plans
    if (text === '/plans' || text.startsWith('/plan')) {
      const plans = await getSalePlans(env);
      const dash = buildPlansDashboardMessage(plans);
      const kb = getPlansMenuKeyboard(plans);
      await sendTelegramMessage(env, chatId, dash, kb);
      return new Response('ok');
    }

    // Command: /setprice <planId> <price>
    if (text.startsWith('/setprice')) {
      const parts = text.split(/\s+/);
      const planId = parts.length > 1 ? parts[1].trim() : '';
      const price = parts.length > 2 ? parseInt(parts[2].replace(/,/g, ''), 10) : NaN;

      if (!planId || isNaN(price)) {
        await sendTelegramMessage(
          env,
          chatId,
          `⚠️ <b>အသုံးပြုပုံ မှားယွင်းနေပါသည်</b>\n\n` +
          `အသုံးပြုပုံ: <code>/setprice [plan_id] [price]</code>\n\n` +
          `ဥပမာ:\n` +
          `• <code>/setprice one_year 180000</code>\n` +
          `• <code>/setprice lifetime 45000</code>\n` +
          `• <code>/setprice trial_3d 0</code>`
        );
        return new Response('ok');
      }

      const ok = await updatePlanPrice(env, planId, price);
      if (ok) {
        await sendTelegramMessage(
          env,
          chatId,
          `✅ <b>အစီအစဉ် [<code>${planId}</code>] ၏ ဈေးနှုန်းအား <code>${price.toLocaleString()}</code> ကျပ် သို့ ပြောင်းလဲသတ်မှတ်လိုက်ပါပြီ။</b>`
        );
        const plans = await getSalePlans(env);
        const dash = buildPlansDashboardMessage(plans);
        const kb = getPlansMenuKeyboard(plans);
        await sendTelegramMessage(env, chatId, dash, kb);
      } else {
        await sendTelegramMessage(env, chatId, `❌ <i>ဈေးနှုန်း ပြောင်းလဲခြင်း မအောင်မြင်ပါ (Plan ID မတွေ့ရှိပါ)</i>`);
      }
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

    // Command: /gen [plan_id or duration] [count or price]
    if (text.startsWith('/gen')) {
      const parts = text.split(/\s+/);
      const plans = await getSalePlans(env);

      if (parts.length === 1) {
        const genText = `➕ <b>ကုတ်အသစ် ထုတ်ယူရန် စီမံချက် ရွေးချယ်ပါ</b>\n\n` +
          `အောက်ပါ တရားဝင် အစီအစဉ်များမှ ရွေးချယ်ပါ (သို့မဟုတ် <code>/gen [plan_id] [count]</code> ရိုက်ထည့်ပါ):\n\n` +
          `• <code>/gen one_year</code> - ၁ နှစ် သက်တမ်း (Device Changeable)\n` +
          `• <code>/gen lifetime</code> - တစ်သက်တာ (1 Device Only)\n` +
          `• <code>/gen trial_3d</code> - ၃ ရက် စမ်းသပ်ခွင့် (Free Trial)\n` +
          `• <code>/gen one_year 5</code> - ၁ နှစ် ကုတ် ၅ ခု တစ်ပြိုင်နက် ထုတ်ရန် (Bulk)`;
        await sendTelegramMessage(env, chatId, genText, getKeyGenKeyboard(plans));
        return new Response('ok');
      }

      const arg1 = parts[1].toLowerCase();
      let targetPlan: SalePlan | null = null;
      let bulkCount = 1;

      if (arg1 === 'one_year' || arg1 === 'year' || arg1 === '1y' || arg1 === '365') {
        targetPlan = plans.one_year || getDefaultSalePlans().one_year;
      } else if (arg1 === 'lifetime' || arg1 === 'life' || arg1 === 'lt') {
        targetPlan = plans.lifetime || getDefaultSalePlans().lifetime;
      } else if (arg1 === 'trial_3d' || arg1 === 'trial' || arg1 === '3d' || arg1 === '3') {
        targetPlan = plans.trial_3d || getDefaultSalePlans().trial_3d;
      } else if (plans[arg1]) {
        targetPlan = plans[arg1];
      }

      if (parts.length > 2) {
        const parsedCount = parseInt(parts[2], 10);
        if (!isNaN(parsedCount) && parsedCount >= 1 && parsedCount <= 50) {
          bulkCount = parsedCount;
        }
      }

      if (!targetPlan) {
        targetPlan = plans.one_year || getDefaultSalePlans().one_year;
      }

      const isChangeable = targetPlan.device_changeable;
      const changeTag = isChangeable ? '🔄 စက်ပြောင်းနိုင် (Device Changeable)' : '🔒 စက်ပြောင်းမရ (1 Device Only)';
      const priceText = targetPlan.price === 0 ? 'အခမဲ့ (0 Ks)' : `${targetPlan.price.toLocaleString()} ကျပ်`;

      if (bulkCount === 1) {
        const newKey = generateCdKey();
        const record: LicenseKeyRecord = {
          cd_key: newKey,
          plan_id: targetPlan.id,
          duration: targetPlan.duration,
          duration_label: targetPlan.duration_label,
          price: targetPlan.price,
          device_changeable: isChangeable,
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
            `🏷️ အစီအစဉ်: <b>${targetPlan.name}</b>\n` +
            `⏳ သက်တမ်း: <b>${targetPlan.duration_label}</b>\n` +
            `📱 စက်ပြောင်းခွင့်: <b>${changeTag}</b>\n` +
            `💰 ဈေးနှုန်း: <b>${priceText}</b>\n` +
            `📌 အခြေအနေ: ⚪ အသင့်ရှိသည် (Available)`
          );
        } else {
          await sendTelegramMessage(env, chatId, '❌ ကုတ် သိမ်းဆည်းခြင်း မအောင်မြင်ပါ');
        }
      } else {
        const keysList: string[] = [];
        for (let i = 0; i < bulkCount; i++) {
          const k = generateCdKey();
          const rec: LicenseKeyRecord = {
            cd_key: k,
            plan_id: targetPlan.id,
            duration: targetPlan.duration,
            duration_label: targetPlan.duration_label,
            price: targetPlan.price,
            device_changeable: isChangeable,
            status: 'available',
            created_at: Date.now()
          };
          await saveLicenseKey(env, rec);
          keysList.push(k);
        }

        const formattedKeys = keysList.map((k, idx) => `${idx + 1}. <code>${k}</code>`).join('\n');
        await sendTelegramMessage(
          env,
          chatId,
          `📦 <b>[Bulk Generated] ကုတ်ပေါင်း (${bulkCount}) ခု ထုတ်ယူပြီးပါပြီ</b>\n\n` +
          `🏷️ အစီအစဉ်: <b>${targetPlan.name}</b>\n` +
          `⏳ သက်တမ်း: <b>${targetPlan.duration_label}</b>\n` +
          `📱 စက်ပြောင်းခွင့်: <b>${changeTag}</b>\n` +
          `💰 ဈေးနှုန်း: <b>${priceText}</b>\n\n` +
          `🔑 <b>ကုတ်များ စာရင်း:</b>\n` +
          `${formattedKeys}\n\n` +
          `<i>(ကုတ်နံပါတ်ကို နှိပ်၍ အလွယ်တကူ Copy ကူးနိုင်ပါသည်)</i>`
        );
      }
      return new Response('ok');
    }

    // Command: /keys (shows generated & available keys with status filter tabs)
    if (text.startsWith('/keys')) {
      const res = await buildGeneratedKeysMessage(env, 'available', 1);
      await sendTelegramMessage(env, chatId, res.text, res.keyboard);
      return new Response('ok');
    }

    // Command: /activekeys, /active, /inuse
    if (text.startsWith('/activekeys') || text.startsWith('/active') || text.startsWith('/inuse')) {
      const parts = text.split(/\s+/);
      const page = parseInt(parts[1] || '1', 10) || 1;
      const res = await buildActiveKeysMessage(env, page);
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

    // Command: /revoke <key> or /ban <key>
    if (text.startsWith('/revoke') || text.startsWith('/ban')) {
      const parts = text.split(/\s+/);
      const targetKey = parts.length > 1 ? parts[1].trim() : '';
      if (!targetKey) {
        await setAdminState(chatId, {
          chat_id: chatId,
          action: 'awaiting_ban_key',
          created_at: Date.now()
        }, env);
        await sendTelegramMessage(env, chatId,
          `⚠️ <b>ပိတ်သိမ်းမည့် ကုတ်နံပါတ် ရိုက်ထည့်ပေးပါ</b>\n\n` +
          `ဥပမာ: <code>/ban ABCD-1234-EFGH</code> သို့မဟုတ် ကုတ်နံပါတ်ကို ဤနေရာတွင် တိုက်ရိုက် စာရိုက်ပို့ပေးနိုင်ပါသည်။\n\n` +
          `<i>(မလုပ်တော့ပါက <code>/cancel</code> ဟု ရိုက်ထည့်ပါ)</i>`
        );
        return new Response('ok');
      }
      const ok = await revokeLicenseKey(env, targetKey);
      if (ok) {
        await sendTelegramMessage(env, chatId, `🔴 <b>ကုတ်နံပါတ် <code>${targetKey}</code> အား ပိတ်သိမ်း (Revoke/Ban) လိုက်ပါပြီ။</b>`);
        const activeMsg = await buildActiveKeysMessage(env, 1);
        await sendTelegramMessage(env, chatId, activeMsg.text, activeMsg.keyboard);
      } else {
        await sendTelegramMessage(env, chatId, '❌ ပိတ်သိမ်းမှု မအောင်မြင်ပါ သို့မဟုတ် ကုတ်နံပါတ် မတွေ့ရှိပါ');
      }
      return new Response('ok');
    }

    // Command: /unban <key>
    if (text.startsWith('/unban')) {
      const parts = text.split(/\s+/);
      const targetKey = parts.length > 1 ? parts[1].trim() : '';
      if (!targetKey) {
        await sendTelegramMessage(env, chatId, '⚠️ ပြန်လည်ဖွင့်ပေးမည့် ကုတ်နံပါတ် ထည့်ပေးပါ။ ဥပမာ: <code>/unban ABCD-1234-EFGH</code>');
        return new Response('ok');
      }
      const ok = await unbanLicenseKey(env, targetKey);
      if (ok) {
        await sendTelegramMessage(env, chatId, `🟢 <b>ကုတ်နံပါတ် <code>${targetKey}</code> အား ပြန်လည်အသုံးပြုနိုင်အောင် ဖွင့်ပေးလိုက်ပါပြီ (Unbanned)။</b>`);
      } else {
        await sendTelegramMessage(env, chatId, '❌ Unban မအောင်မြင်ပါ သို့မဟုတ် ကုတ်နံပါတ် မတွေ့ရှိပါ');
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

    // Command: /setappid <file_id> [version] [release_notes]
    if (text.startsWith('/setappid')) {
      const parts = text.split(/\s+/);
      const fileId = parts.length > 1 ? parts[1].trim() : '';
      const version = parts.length > 2 ? parts[2].trim() : 'v1.0.100';
      const notes = parts.length > 3 ? parts.slice(3).join(' ').trim() : '3D Ledger APK နောက်ဆုံးထွက် ဗားရှင်း';

      if (!fileId) {
        await sendTelegramMessage(env, chatId,
          `⚠️ <b>အသုံးပြုပုံ:</b> <code>/setappid [telegram_file_id] [version] [notes]</code>\n\n` +
          `ဥပမာ: <code>/setappid BQACAgUAA... v1.0.100 Update release</code>`
        );
        return new Response('ok');
      }

      const release: AppReleaseRecord = {
        file_id: fileId,
        file_name: `3D_Ledger_${version}.apk`,
        file_size: 9284449,
        version_name: version.startsWith('v') ? version : `v${version}`,
        version_code: parseInt(version.replace(/\D/g, ''), 10) || 100,
        release_notes: notes,
        uploaded_by: senderId,
        uploaded_at: Date.now()
      };

      await saveAppRelease(env, release);

      const confirmMsg = `✅ <b>Telegram File ID ဖြင့် အက်ပ်ဗားရှင်း အသစ် သတ်မှတ်ပြီးပါပြီ</b>\n\n` +
        `🆔 <b>File ID:</b> <code>${fileId}</code>\n` +
        `🔖 <b>ဗားရှင်း:</b> <b>${release.version_name}</b>\n` +
        `📝 <b>မှတ်ချက်:</b> ${notes}\n\n` +
        `🚀 <i>ဝယ်ယူသူများ Bot တွင် [📲 အက်ပ် ဒေါင်းလုဒ်ရယူရန်] နှိပ်ပါက ဤ APK အား ချက်ချင်း ပေးပို့ပါမည်။</i>`;

      const confirmKb: InlineKeyboardMarkup = {
        inline_keyboard: [
          [
            { text: '📢 Update အားလုံးသို့ အသိပေးစာ ပို့မည်', callback_data: 'm_broadcast_update' },
            { text: '📥 Test Download', callback_data: 'b_app_download' }
          ]
        ]
      };

      await sendTelegramMessage(env, chatId, confirmMsg, confirmKb);
      return new Response('ok');
    }

    // Command: /setapplink <url> [version] [release_notes]
    if (text.startsWith('/setapplink')) {
      const parts = text.split(/\s+/);
      const url = parts.length > 1 ? parts[1].trim() : '';
      const version = parts.length > 2 ? parts[2].trim() : 'v1.0.100';
      const notes = parts.length > 3 ? parts.slice(3).join(' ').trim() : '3D Ledger Direct APK Download';

      if (!url || !url.startsWith('http')) {
        await sendTelegramMessage(env, chatId,
          `⚠️ <b>အသုံးပြုပုံ:</b> <code>/setapplink [download_url] [version] [notes]</code>\n\n` +
          `ဥပမာ: <code>/setapplink https://example.com/app.apk v1.0.100 New Release</code>`
        );
        return new Response('ok');
      }

      const release: AppReleaseRecord = {
        file_id: '',
        download_url: url,
        file_name: `3D_Ledger_${version}.apk`,
        file_size: 9284449,
        version_name: version.startsWith('v') ? version : `v${version}`,
        version_code: parseInt(version.replace(/\D/g, ''), 10) || 100,
        release_notes: notes,
        uploaded_by: senderId,
        uploaded_at: Date.now()
      };

      await saveAppRelease(env, release);

      await sendTelegramMessage(env, chatId,
        `✅ <b>တိုက်ရိုက် ဒေါင်းလုဒ် လင့်ခ် အောင်မြင်စွာ သတ်မှတ်ပြီးပါပြီ</b>\n\n` +
        `🔗 <b>Download URL:</b> ${url}\n` +
        `🔖 <b>ဗားရှင်း:</b> <b>${release.version_name}</b>\n` +
        `📝 <b>မှတ်ချက်:</b> ${notes}`
      );
      return new Response('ok');
    }

    // Command: /broadcast [custom message] or /broadcastupdate [custom message] or /announce [message]
    if (text.startsWith('/broadcast') || text.startsWith('/announce')) {
      const isAppUpdate = text.startsWith('/broadcastupdate');
      const customMsg = text.replace(/^\/(broadcastupdate|broadcast|announce)\s*/i, '').trim();

      if (isAppUpdate || !customMsg) {
        await sendTelegramMessage(env, chatId, '📢 <i>ဝယ်ယူသူများနှင့် စမ်းသပ်သူများထံ Update အသိပေးစာ စတင် ပို့ဆောင်နေပါသည်...</i>');
        await broadcastAppUpdate(env, chatId, customMsg || undefined);
      } else {
        await sendTelegramMessage(env, chatId, '📢 <i>အသုံးပြုသူများ အားလုံးထံ အသိပေးစာ စတင် ပို့ဆောင်နေပါသည်...</i>');
        await broadcastTextMessage(env, chatId, `📢 <b>[3D LEDGER အသိပေးချက်]</b>\n\n${customMsg}`);
      }
      return new Response('ok');
    }

    // Fallback: Show Main Dashboard
    const dash = await buildAdminDashboardMessage(env);
    await sendTelegramMessage(env, chatId, dash.text, dash.keyboard);
  }

  return new Response('ok');
}
