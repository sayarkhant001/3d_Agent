/**
 * Full Telegram Bot Engine for 3D Ledger & Official Thai GLO Lottery
 * Handles Webhooks, Commands (/start, /live, /tut, /check, /status, /batch),
 * Callback Queries, Inline Keyboards, and Admin Controls.
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

// ── Telegram API Helpers ──────────────────────────────────────────────────────

export function getCleanBotToken(env: Env): string {
  const t = (env.TELEGRAM_BOT_TOKEN || '').trim();
  return t.startsWith('bot') ? t.substring(3) : t;
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

export async function answerCallbackQuery(env: Env, callbackQueryId: string, text?: string) {
  const token = getCleanBotToken(env);
  if (!token) return;
  await fetch(`https://api.telegram.org/bot${token}/answerCallbackQuery`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ callback_query_id: callbackQueryId, text }),
  });
}

// ── Keyboards ─────────────────────────────────────────────────────────────────

export function getMainInlineKeyboard(): InlineKeyboardMarkup {
  return {
    inline_keyboard: [
      [
        { text: '🎯 နောက်ဆုံး 3D ရလဒ်', callback_data: 'cb_live' },
        { text: '🔢 တွတ် ဂဏန်းများ', callback_data: 'cb_tut' }
      ],
      [
        { text: '📊 စနစ် အခြေအနေ', callback_data: 'cb_status' },
        { text: '📖 အသုံးပြုနည်း', callback_data: 'cb_help' }
      ]
    ]
  };
}

// ── Message Builders ──────────────────────────────────────────────────────────

export function buildStartMessage(): string {
  return `✨ <b>3D Ledger တရားဝင် Telegram Bot မှ ကြိုဆိုပါသည်</b> ✨\n\n` +
    `ထိုင်းအစိုးရ ထီပေါက်စဉ် (GLO) တရားဝင် 3D ရလဒ်များနှင့် ပေါက်သီးတွတ် စာရင်းများကို ဤနေရာတွင် အချိန်နှင့်တပြေးညီ ကြည့်ရှုနိုင်ပါသည်။\n\n` +
    `<b>အသုံးပြုနိုင်သော ခလုတ်များနှင့် အမိန့်များ:</b>\n` +
    `• /live - တရားဝင် 3D ပေါက်ဂဏန်း ကြည့်မည်\n` +
    `• /tut &lt;ဂဏန်း&gt; - တွတ် ဂဏန်းများ တွက်ချက်မည်\n` +
    `• /check &lt;ဂဏန်း&gt; - ထိုးဂဏန်း ပေါက်/မပေါက် စစ်ဆေးမည်\n` +
    `• /batch - လက်ရှိ အကြိမ်နံပါတ် ကြည့်မည်\n` +
    `• /status - စနစ် အခြေအနေ စစ်ဆေးမည်\n` +
    `• /help - အကူအညီနှင့် အသေးစိတ် ကြည့်မည်\n\n` +
    `<i>အောက်ပါ ခလုတ်များကို နှိပ်၍ အလွယ်တကူ စစ်ဆေးနိုင်ပါသည် 👇</i>`;
}

export function buildHelpMessage(): string {
  return `📖 <b>3D Ledger Bot အသုံးပြုနည်း လမ်းညွှန်</b>\n\n` +
    `<b>1. ပေါက်ဂဏန်း ကြည့်ရှုခြင်း:</b>\n` +
    `• <code>/live</code> သို့မဟုတ် <code>/3d</code> - ထိုင်းထီပေါက်စဉ် (၁ လုံး၊ ၂ လုံး၊ ၃ လုံး) တိုက်ရိုက် ရယူမည်။\n\n` +
    `<b>2. တွတ် (Tut) တွက်ချက်ခြင်း:</b>\n` +
    `• <code>/tut</code> - လက်ရှိ ထွက်ဂဏန်း၏ တွတ် ဂဏန်းများကို တွက်ပေးပါသည်။\n` +
    `• <code>/tut 108</code> - မိမိလိုချင်သော ဂဏန်း၏ အပြန် ၅ ကွက် နှင့် ကပ်သီး ၂ ကွက် (စုစုပေါင်း ၇ ကွက်) ကို တွက်ပေးပါသည်။\n\n` +
    `<b>3. ထိုးဂဏန်း တိုက်စစ်ခြင်း:</b>\n` +
    `• <code>/check 108</code> - ထိုးထားသော ဂဏန်း ပေါက်သီး (ဒဲ့) သို့မဟုတ် တွတ် ပေါက်သီး ဟုတ်/မဟုတ် စစ်ဆေးပေးပါသည်။\n\n` +
    `<b>4. ဒိုင်ချုပ် စီမံခန့်ခွဲမှု (Admin Commands):</b>\n` +
    `• <code>/setbatch 16</code> - လက်ရှိ အကြိမ် အမှတ် ပြောင်းလဲမည်\n` +
    `• <code>/setwinner 108</code> - ပေါက်ဂဏန်း လက်စွဲ သတ်မှတ်မည်`;
}

export async function buildLiveResultMessage(env: Env): Promise<string> {
  // 1. Try fetching from official GLO
  const glo = await fetchFromGloLottery();
  if (glo && glo.threeD) {
    const tut = calculateTutNumbers(glo.threeD);
    const tutListStr = tut.allTut.join(', ');

    return `🎯 <b>ထိုင်းအစိုးရ ထီပေါက်စဉ် (GLO) တရားဝင် 3D ရလဒ်</b> 🎯\n\n` +
      `📅 <b>ထွက်သည့် ရက်စွဲ:</b> ${glo.date || 'လတ်တလော'}\n` +
      `🏆 <b>ပထမဆု (၆ လုံး):</b> <code>${glo.firstPrize || '-'}</code>\n` +
      `✨ <b>3D ပေါက်ဂဏန်း (ဒဲ့):</b> <code>${glo.threeD}</code>\n` +
      `🎲 <b>2D ပေါက်ဂဏန်း:</b> <code>${glo.twoD || '-'}</code>\n\n` +
      `--------------------------------\n` +
      `🔢 <b>တွတ် ဂဏန်းများ (${tut.allTut.length} ကွက်):</b>\n` +
      `<code>${tutListStr}</code>\n` +
      `• <i>အပြန်:</i> ${tut.permutations.join(', ') || 'မရှိပါ'}\n` +
      `• <i>ကပ်သီး:</i> ${tut.nearMisses.join(', ')}\n` +
      `--------------------------------\n` +
      `⚡ <i>တရားဝင် GLO Website မှ တိုက်ရိုက် ထုတ်ပြန်ထားခြင်း ဖြစ်ပါသည်။</i>`;
  }

  // 2. Fallback to Firebase live results
  try {
    const fbRes = await fetch(`${env.FIREBASE_DB_URL}/3d_live_results.json`);
    if (fbRes.ok) {
      const data = await fbRes.json() as Record<string, any>;
      if (data && data.number) {
        const num = String(data.number);
        const tut = calculateTutNumbers(num);
        return `🎯 <b>3D ပေါက်ဂဏန်း ရလဒ် (သိုလှောင်မှု မှ)</b> 🎯\n\n` +
          `📅 <b>ရက်စွဲ:</b> ${data.date || '-'}\n` +
          `✨ <b>3D ပေါက်ဂဏန်း (ဒဲ့):</b> <code>${num}</code>\n` +
          `🔢 <b>တွတ် ဂဏန်းများ:</b> <code>${tut.allTut.join(', ')}</code>\n\n` +
          `⚡ <i>GLO API သို့မဟုတ် ဆာဗာမှ ထုတ်ပြန်ထားပါသည်။</i>`;
      }
    }
  } catch (_) {}

  return `⚠️ <b>ပေါက်ဂဏန်း အချက်အလက် မရရှိသေးပါ</b>\n\nလက်ရှိတွင် တရားဝင် ထွက်ဂဏန်း မရှိသေးပါ သို့မဟုတ် ရလဒ် စောင့်ဆိုင်းနေဆဲ ဖြစ်ပါသည်။`;
}

export async function buildTutMessage(env: Env, inputNumber?: string): Promise<string> {
  let targetNum = inputNumber?.trim();

  // If no number provided, get the latest winning number from GLO or Firebase
  if (!targetNum || !/^\d{3}$/.test(targetNum)) {
    const glo = await fetchFromGloLottery();
    if (glo && glo.threeD) {
      targetNum = glo.threeD;
    } else {
      try {
        const fbRes = await fetch(`${env.FIREBASE_DB_URL}/3d_live_results/number.json`);
        if (fbRes.ok) {
          const num = await fbRes.json();
          if (num && String(num).length === 3) targetNum = String(num);
        }
      } catch (_) {}
    }
  }

  if (!targetNum || !/^\d{3}$/.test(targetNum)) {
    return `⚠️ <b>တွတ် တွက်ရန် ၃ လုံးဂဏန်း ရိုက်ထည့်ပေးပါ</b>\n\nဥပမာ: <code>/tut 108</code> သို့မဟုတ် <code>/tut 212</code>`;
  }

  const tut = calculateTutNumbers(targetNum);

  return `🔢 <b>3D တွတ် (Tut) တွက်ချက်မှု ရလဒ်</b>\n\n` +
    `✨ <b>မူရင်း/ပေါက်ဂဏန်း:</b> <code>${targetNum}</code>\n\n` +
    `📌 <b>အပြန် (Permutations - ${tut.permutations.length} ကွက်):</b>\n` +
    `<code>${tut.permutations.length > 0 ? tut.permutations.join(', ') : 'မရှိပါ'}</code>\n\n` +
    `📌 <b>ကပ်သီး (+1, -1 Near Misses - ${tut.nearMisses.length} ကွက်):</b>\n` +
    `<code>${tut.nearMisses.join(', ')}</code>\n\n` +
    `--------------------------------\n` +
    `⭐ <b>စုစုပေါင်း တွတ် ဂဏန်း (${tut.allTut.length} ကွက်):</b>\n` +
    `<code>${tut.allTut.join(', ')}</code>\n` +
    `--------------------------------\n` +
    `<i>မှတ်ချက်: ဒဲ့ဂဏန်း ${targetNum} ကို တွတ်ထဲတွင် ထည့်သွင်းမတွက်ပါ။</i>`;
}

export async function buildCheckMessage(env: Env, betNumber: string): Promise<string> {
  const clean = betNumber.trim();
  if (!/^\d{3}$/.test(clean)) {
    return `⚠️ <b>စစ်ဆေးရန် ၃ လုံးဂဏန်း ထည့်ပေးပါ</b>\n\nဥပမာ: <code>/check 108</code>`;
  }

  let winNum = '';
  const glo = await fetchFromGloLottery();
  if (glo && glo.threeD) {
    winNum = glo.threeD;
  } else {
    try {
      const fbRes = await fetch(`${env.FIREBASE_DB_URL}/3d_live_results/number.json`);
      if (fbRes.ok) {
        const n = await fbRes.json();
        if (n && String(n).length === 3) winNum = String(n);
      }
    } catch (_) {}
  }

  if (!winNum) {
    return `⚠️ <b>စစ်ဆေးရန် ပေါက်ဂဏန်း မရှိသေးပါ</b>\n\nထီပေါက်စဉ် ထွက်ရှိပြီးမှ စစ်ဆေးနိုင်ပါမည်။`;
  }

  if (clean === winNum) {
    return `🎉🎉🎉 <b>ဂုဏ်ယူပါသည်! ပေါက်သီး (ဒဲ့) ထွက်ပါသည်!</b> 🎉🎉🎉\n\n` +
      `✨ <b>ထိုးဂဏန်း:</b> <code>${clean}</code>\n` +
      `🏆 <b>ပေါက်ဂဏန်း:</b> <code>${winNum}</code> (ဒဲ့)\n\n` +
      `<b>အဆ ၆၀၀ ဖြင့် လျော်ကြေး ရရှိပါမည်။</b>`;
  }

  const tut = calculateTutNumbers(winNum);
  if (tut.allTut.includes(clean)) {
    const isPerm = tut.permutations.includes(clean);
    const typeLabel = isPerm ? 'အပြန် တွတ်' : 'ကပ်သီး တွတ်';

    return `🎊 <b>ဂုဏ်ယူပါသည်! တွတ် (Tut) ပေါက်သီး ထွက်ပါသည်!</b> 🎊\n\n` +
      `✨ <b>ထိုးဂဏန်း:</b> <code>${clean}</code>\n` +
      `🎯 <b>ပေါက်ဂဏန်း:</b> <code>${winNum}</code>\n` +
      `🏷️ <b>အမျိုးအစား:</b> ${typeLabel}\n\n` +
      `<b>တွတ်ဆ (အဆ ၁၀၀) ဖြင့် လျော်ကြေး ရရှိပါမည်။</b>`;
  }

  return `❌ <b>မပေါက်သေးပါ ခင်ဗျာ</b>\n\n` +
    `• ထိုးဂဏန်း: <code>${clean}</code>\n` +
    `• ပေါက်ဂဏန်း (ဒဲ့): <code>${winNum}</code>\n` +
    `• တွတ် ဂဏန်းများ: <code>${tut.allTut.join(', ')}</code>\n\n` +
    `<i>နောက်တစ်ကြိမ်တွင် ကံကောင်းပါစေ!</i>`;
}

export async function buildStatusMessage(env: Env): Promise<string> {
  let gloStatus = 'စစ်ဆေးနေဆဲ...';
  let fbStatus = 'စစ်ဆေးနေဆဲ...';
  let batchNum = '1';
  let mode = 'auto';

  try {
    const g = await fetchFromGloLottery();
    gloStatus = g ? '🟢 ပုံမှန် ချိတ်ဆက်ရရှိသည်' : '🟡 တုန့်ပြန်မှု မရှိပါ';
  } catch (_) {
    gloStatus = '🔴 ချိတ်ဆက်မှု မအောင်မြင်ပါ';
  }

  try {
    const bRes = await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/current_batch.json`);
    if (bRes.ok) {
      const b = await bRes.json();
      if (b) batchNum = String(b);
      fbStatus = '🟢 ချိတ်ဆက်မှု ကောင်းမွန်သည်';
    }
    const mRes = await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/mode.json`);
    if (mRes.ok) {
      const m = await mRes.json();
      if (m) mode = String(m);
    }
  } catch (_) {
    fbStatus = '🔴 ချိတ်ဆက်မှု မအောင်မြင်ပါ';
  }

  return `📊 <b>3D Ledger စနစ် အခြေအနေ စစ်ဆေးချက်</b>\n\n` +
    `• <b>ထိုင်း GLO တရားဝင် API:</b> ${gloStatus}\n` +
    `• <b>Firebase ဒေတာဘေ့စ်:</b> ${fbStatus}\n` +
    `• <b>လက်ရှိ ဖွင့်ထားသော အကြိမ်:</b> #${batchNum}\n` +
    `• <b>ထုတ်ပြန်မှု စနစ်ပုံစံ (Mode):</b> ${mode.toUpperCase()}\n` +
    `• <b>ဆာဗာ ပလက်ဖောင်း:</b> Cloudflare Workers Global Edge`;
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

  // 1. Handle Callback Queries (Inline Button Clicks)
  if (update.callback_query) {
    const cq = update.callback_query;
    const data = cq.data || '';
    const chatId = cq.message?.chat.id || cq.from.id;

    await answerCallbackQuery(env, cq.id);

    if (data === 'cb_live') {
      const msg = await buildLiveResultMessage(env);
      await sendTelegramMessage(env, chatId, msg, getMainInlineKeyboard());
    } else if (data === 'cb_tut') {
      const msg = await buildTutMessage(env);
      await sendTelegramMessage(env, chatId, msg, getMainInlineKeyboard());
    } else if (data === 'cb_status') {
      const msg = await buildStatusMessage(env);
      await sendTelegramMessage(env, chatId, msg, getMainInlineKeyboard());
    } else if (data === 'cb_help') {
      const msg = buildHelpMessage();
      await sendTelegramMessage(env, chatId, msg, getMainInlineKeyboard());
    }

    return new Response(JSON.stringify({ status: 'ok' }), { headers: { 'Content-Type': 'application/json' } });
  }

  // 2. Handle Text Messages & Commands
  if (update.message && update.message.text) {
    const msg = update.message;
    const text = msg.text.trim();
    const chatId = msg.chat.id;
    const senderId = msg.from?.id ? String(msg.from.id) : '';
    const isAdmin = senderId === env.TELEGRAM_CHAT_ID;

    // Command: /start
    if (text.startsWith('/start')) {
      const reply = buildStartMessage();
      await sendTelegramMessage(env, chatId, reply, getMainInlineKeyboard());
      return new Response('ok');
    }

    // Command: /help
    if (text.startsWith('/help')) {
      const reply = buildHelpMessage();
      await sendTelegramMessage(env, chatId, reply, getMainInlineKeyboard());
      return new Response('ok');
    }

    // Command: /live or /3d or /result
    if (text.startsWith('/live') || text.startsWith('/3d') || text.startsWith('/result')) {
      const reply = await buildLiveResultMessage(env);
      await sendTelegramMessage(env, chatId, reply, getMainInlineKeyboard());
      return new Response('ok');
    }

    // Command: /tut [number]
    if (text.startsWith('/tut') || text.startsWith('/twut')) {
      const parts = text.split(/\s+/);
      const inputNum = parts.length > 1 ? parts[1] : undefined;
      const reply = await buildTutMessage(env, inputNum);
      await sendTelegramMessage(env, chatId, reply, getMainInlineKeyboard());
      return new Response('ok');
    }

    // Command: /check <number>
    if (text.startsWith('/check')) {
      const parts = text.split(/\s+/);
      const inputNum = parts.length > 1 ? parts[1] : '';
      const reply = await buildCheckMessage(env, inputNum);
      await sendTelegramMessage(env, chatId, reply, getMainInlineKeyboard());
      return new Response('ok');
    }

    // Command: /status
    if (text.startsWith('/status')) {
      const reply = await buildStatusMessage(env);
      await sendTelegramMessage(env, chatId, reply, getMainInlineKeyboard());
      return new Response('ok');
    }

    // Command: /batch
    if (text.startsWith('/batch')) {
      let b = '1';
      try {
        const res = await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/current_batch.json`);
        if (res.ok) b = String(await res.json() || '1');
      } catch (_) {}
      await sendTelegramMessage(env, chatId, `🏷️ <b>လက်ရှိ ဖွင့်လှစ်ထားသော အကြိမ်:</b> #${b}`);
      return new Response('ok');
    }

    // ── Admin Commands ────────────────────────────────────────────────────────

    // Admin Command: /setbatch <number>
    if (text.startsWith('/setbatch')) {
      if (!isAdmin) {
        await sendTelegramMessage(env, chatId, '⚠️ <i>ဤအမိန့်ကို စီမံခန့်ခွဲသူ (Admin) သာ ပြုလုပ်ခွင့်ရှိပါသည်။</i>');
        return new Response('ok');
      }
      const parts = text.split(/\s+/);
      const newBatch = parts.length > 1 ? parseInt(parts[1], 10) : NaN;
      if (isNaN(newBatch) || newBatch <= 0) {
        await sendTelegramMessage(env, chatId, '⚠️ <i>မှန်ကန်သော အကြိမ်နံပါတ် ရိုက်ထည့်ပေးပါ။ ဥပမာ: /setbatch 16</i>');
        return new Response('ok');
      }

      try {
        const token = await getFirebaseToken(env);
        await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/current_batch.json?auth=${token}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(newBatch)
        });
        await sendTelegramMessage(env, chatId, `✅ <b>အကြိမ်နံပါတ် #${newBatch} သို့ အောင်မြင်စွာ ပြောင်းလဲပြီးပါပြီ။</b>`);
      } catch (err: any) {
        await sendTelegramMessage(env, chatId, `❌ <i>ပြောင်းလဲမှု မအောင်မြင်ပါ: ${err.message}</i>`);
      }
      return new Response('ok');
    }

    // Admin Command: /setwinner <3-digit number>
    if (text.startsWith('/setwinner')) {
      if (!isAdmin) {
        await sendTelegramMessage(env, chatId, '⚠️ <i>ဤအမိန့်ကို စီမံခန့်ခွဲသူ (Admin) သာ ပြုလုပ်ခွင့်ရှိပါသည်။</i>');
        return new Response('ok');
      }
      const parts = text.split(/\s+/);
      const num = parts.length > 1 ? parts[1].trim() : '';
      if (!/^\d{3}$/.test(num)) {
        await sendTelegramMessage(env, chatId, '⚠️ <i>၃ လုံးဂဏန်း ရိုက်ထည့်ပေးပါ။ ဥပမာ: /setwinner 108</i>');
        return new Response('ok');
      }

      try {
        const token = await getFirebaseToken(env);
        const dateStr = new Date().toISOString().split('T')[0];
        const payload = {
          number: num,
          date: dateStr,
          status: 'declared',
          updatedAt: Date.now()
        };
        await fetch(`${env.FIREBASE_DB_URL}/3d_live_results.json?auth=${token}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        const tut = calculateTutNumbers(num);
        await sendTelegramMessage(env, chatId,
          `✅ <b>ပေါက်သီး #${num} ကို လက်စွဲ သတ်မှတ်ပြီးပါပြီ။</b>\n\n` +
          `🔢 တွတ် ဂဏန်းများ: <code>${tut.allTut.join(', ')}</code>`
        );
      } catch (err: any) {
        await sendTelegramMessage(env, chatId, `❌ <i>သတ်မှတ်မှု မအောင်မြင်ပါ: ${err.message}</i>`);
      }
      return new Response('ok');
    }

    // Fallback: If 3-digit number sent directly, treat as /check
    if (/^\d{3}$/.test(text)) {
      const reply = await buildCheckMessage(env, text);
      await sendTelegramMessage(env, chatId, reply, getMainInlineKeyboard());
      return new Response('ok');
    }

    // Unknown command fallback
    await sendTelegramMessage(
      env,
      chatId,
      `❓ <b>နားမလည်သော စာသား ဖြစ်ပါသည်</b>\n\nအမိန့်များ ကြည့်ရှုရန် <code>/help</code> သို့မဟုတ် အောက်ပါ ခလုတ်ကို နှိပ်ပါ။`,
      getMainInlineKeyboard()
    );
  }

  return new Response('ok');
}
