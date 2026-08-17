/**
 * MODULE 1: Scraper & Broadcaster
 * Run this on Cloudflare Workers via Cron Triggers.
 */
import * as cheerio from 'cheerio';

export interface Env {
  FIREBASE_DB_URL: string;
  TELEGRAM_BOT_TOKEN: string;
  TELEGRAM_CHAT_ID: string;
  GOOGLE_SERVICE_ACCOUNT_JSON: string;
}

export default {
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(this.processDraw(env, ctx));
  },

  async processDraw(env: Env, ctx: ExecutionContext) {
    // 1. Admin Control
    const modeRes = await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/mode.json`);
    const mode = await modeRes.json();
    if (mode === 'manual') return new Response('Manual mode active', { status: 200 });

    // 2. Dual-Validation Fetching
    const [apiResult, scraperResult] = await Promise.allSettled([
      this.fetchFromApi(),
      this.fetchFromScraper()
    ]);

    let finalNumber = null;

    if (scraperResult.status === 'fulfilled' && scraperResult.value) {
      finalNumber = scraperResult.value;
    } else if (apiResult.status === 'fulfilled' && apiResult.value) {
      finalNumber = apiResult.value;
    }

    if (!finalNumber) {
      // 4. Fallback Telegram Alert
      await this.sendTelegramAlert(env, "Both Scraper and API failed to fetch 3D numbers. Switch to manual mode immediately.");
      await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_status/state.json`, {
        method: 'PUT',
        body: JSON.stringify('delayed')
      });
      return;
    }

    // 5. Firebase Update
    const currentResultsRes = await fetch(`${env.FIREBASE_DB_URL}/3d_live_results.json`);
    const currentResults = await currentResultsRes.json();

    const nextDate = this.calculateNextDrawDate();

    const updates = {
      '3d_live_results/previous_draw_date': currentResults.target_draw_date,
      '3d_live_results/previous_winning_number': currentResults.winning_number,
      '3d_live_results/target_draw_date': nextDate,
      '3d_live_results/winning_number': finalNumber,
      '3d_lottery_status/state': 'declared'
    };

    await fetch(`${env.FIREBASE_DB_URL}/.json`, {
      method: 'PATCH',
      body: JSON.stringify(updates)
    });

    // 6. FCM Push
    await this.sendFCM(env, finalNumber);
  },

  async fetchFromApi(): Promise<string | null> {
    const res = await fetch('https://lotto.api.rayriffy.com/latest');
    const data = await res.json() as any;
    const num = data.response.prizes[0].number[0];
    return num ? num.slice(-3) : null;
  },

  async fetchFromScraper(): Promise<string | null> {
    const res = await fetch('https://news.sanook.com/lotto/');
    const html = await res.text();
    const $ = cheerio.load(html);
    // Adjust selector based on actual Sanook HTML structure
    const rawNumber = $('.lotto-prize1 strong').first().text().trim();
    if (rawNumber && rawNumber.length >= 3) {
      return rawNumber.slice(-3);
    }
    return null;
  },

  async sendTelegramAlert(env: Env, message: string) {
    const url = `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`;
    await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        chat_id: env.TELEGRAM_CHAT_ID,
        text: `🚨 3D LOTTERY ALERT: ${message}`
      })
    });
  },

  async sendFCM(env: Env, winningNumber: string) {
    // Requires generating JWT from Google Service Account
    // (Omitted standard JWT generation boiler plate for brevity)
    const token = await this.getGoogleAuthToken(env.GOOGLE_SERVICE_ACCOUNT_JSON);
    const projectId = JSON.parse(env.GOOGLE_SERVICE_ACCOUNT_JSON).project_id;
    
    await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        message: {
          topic: '3d_alerts',
          notification: {
            title: '3D Results Are Out!',
            body: `Winning Number: ${winningNumber}`
          }
        }
      })
    });
  },

  calculateNextDrawDate(): string {
    const d = new Date();
    // Thai lottery is typically 1st and 16th.
    if (d.getDate() < 16) d.setDate(16);
    else { d.setMonth(d.getMonth() + 1); d.setDate(1); }
    return d.toISOString().split('T')[0];
  },

  async getGoogleAuthToken(serviceAccountJson: string): Promise<string> {
    // Implement JWT signing using subtleCrypto for Cloudflare workers here
    return "SIGNED_JWT_TOKEN"; 
  }
};
