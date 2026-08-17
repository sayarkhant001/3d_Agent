/**
 * MODULE 1: Scraper & Broadcaster
 * Fetches Myanmar 3D results from Thai Stock Exchange data
 * via thaistock2d.com API and pushes to Firebase.
 * 
 * Cron runs daily at 11:05 UTC (5:35 PM Myanmar time)
 * to capture the 4:30 PM closing result.
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
  live: {
    set: string;
    value: string;
    time: string;
    twod: string;
    date: string;
  };
  result: LiveResult[];
  holiday: {
    status: string;
    date: string;
    name: string;
  };
}

export default {
  // Cron trigger handler
  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(this.processDraw(env));
  },

  // HTTP handler for manual testing
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    if (request.method === 'GET') {
      try {
        await this.processDraw(env);
        return new Response(JSON.stringify({ status: 'ok', message: 'Draw processed' }), {
          headers: { 'Content-Type': 'application/json' }
        });
      } catch (e: any) {
        return new Response(JSON.stringify({ status: 'error', message: e.message }), {
          status: 500,
          headers: { 'Content-Type': 'application/json' }
        });
      }
    }
    return new Response('Method not allowed', { status: 405 });
  },

  async processDraw(env: Env) {
    // 1. Check admin control mode
    const modeRes = await fetch(`${env.FIREBASE_DB_URL}/3d_lottery_config/mode.json`);
    const mode = await modeRes.json();
    if (mode === 'manual') return;

    // 2. Fetch from thaistock2d.com API
    const result = await this.fetchFromThaiStock();

    if (!result) {
      // Alert via Telegram if fetch failed
      await this.sendTelegramAlert(env, 
        '⚠️ Failed to fetch 3D results from API. It may be a holiday or market is still open. Check manually.');
      return;
    }

    // 3. Get current data from Firebase for archiving
    const currentResultsRes = await fetch(`${env.FIREBASE_DB_URL}/3d_live_results.json`);
    const currentResults = await currentResultsRes.json() as Record<string, unknown> | null;

    // 4. Calculate next draw date (next business day)
    const nextDate = this.calculateNextDrawDate(result.date);

    // 5. Build Firebase update payload
    const updates: Record<string, unknown> = {
      '3d_live_results/winning_number': result.threeD,
      '3d_live_results/target_draw_date': nextDate,
      '3d_live_results/set_value': result.setValue,
      '3d_live_results/trade_value': result.tradeValue,
      '3d_live_results/twod': result.twoD,
      '3d_live_results/result_date': result.date,
      '3d_live_results/updated_at': new Date().toISOString(),
      '3d_lottery_status/state': 'declared'
    };

    // Archive previous results
    if (currentResults && (currentResults as any).winning_number) {
      updates['3d_live_results/previous_winning_number'] = (currentResults as any).winning_number;
      updates['3d_live_results/previous_draw_date'] = (currentResults as any).result_date || (currentResults as any).target_draw_date;
    }

    // 6. Push to Firebase
    await fetch(`${env.FIREBASE_DB_URL}/.json`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(updates)
    });

    // 7. Send Telegram notification
    await this.sendTelegramAlert(env, 
      `✅ 3D Result for ${result.date}\n\n🎯 3D: ${result.threeD}\n📊 SET: ${result.setValue}\n💰 Value: ${result.tradeValue}\n🔢 2D: ${result.twoD}`
    );
  },

  async fetchFromThaiStock(): Promise<{
    threeD: string;
    twoD: string;
    setValue: string;
    tradeValue: string;
    date: string;
  } | null> {
    try {
      const res = await fetch('https://api.thaistock2d.com/live', {
        headers: { 'User-Agent': 'Mozilla/5.0' }
      });
      const data = await res.json() as LiveResponse;

      // Check if it's a holiday
      if (data.holiday && data.holiday.status === '1') {
        return null;
      }

      // Find the 4:30 PM (16:30) closing result - this is the official 3D result
      const closingResult = data.result.find(r => r.open_time === '16:30:00');
      
      // Also try the 3:00 PM result as fallback (some days close earlier)
      const afternoonResult = data.result.find(r => r.open_time === '15:00:00');

      const finalResult = closingResult?.history_id ? closingResult : 
                          afternoonResult?.history_id ? afternoonResult : null;

      if (!finalResult || finalResult.set === '--' || !finalResult.history_id) {
        // Market hasn't closed yet or no data
        // Use the last available closing result
        const latestWithData = data.result
          .filter(r => r.history_id !== null && r.set !== '--')
          .pop();
        
        if (!latestWithData) return null;

        // Extract 3D from SET value (last 3 digits before decimal)
        const threeD = this.extract3D(latestWithData.value);
        
        return {
          threeD,
          twoD: latestWithData.twod,
          setValue: latestWithData.set,
          tradeValue: latestWithData.value,
          date: latestWithData.stock_date
        };
      }

      // Extract 3D from SET value (last 3 digits before decimal)
      const threeD = this.extract3D(finalResult.value);

      return {
        threeD,
        twoD: finalResult.twod,
        setValue: finalResult.set,
        tradeValue: finalResult.value,
        date: finalResult.stock_date
      };
    } catch (e) {
      console.error('Failed to fetch from thaistock2d:', e);
      return null;
    }
  },

  /**
   * Extract 3D number from SET trade value
   * e.g., "44,153.28" → remove commas → "44153.28" → integer part "44153" → last 3 = "153"
   */
  extract3D(value: string): string {
    const cleanValue = value.replace(/,/g, '');
    const intPart = cleanValue.split('.')[0];
    return intPart.slice(-3).padStart(3, '0');
  },

  async sendTelegramAlert(env: Env, message: string) {
    if (!env.TELEGRAM_BOT_TOKEN) return;
    
    try {
      const url = `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`;
      await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          chat_id: env.TELEGRAM_CHAT_ID,
          text: message,
          parse_mode: 'HTML'
        })
      });
    } catch (e) {
      console.error('Telegram alert failed:', e);
    }
  },

  /**
   * Calculate next business day (skip weekends)
   */
  calculateNextDrawDate(currentDate: string): string {
    const d = new Date(currentDate);
    d.setDate(d.getDate() + 1);
    
    // Skip Saturday (6) and Sunday (0)
    while (d.getDay() === 0 || d.getDay() === 6) {
      d.setDate(d.getDate() + 1);
    }
    
    return d.toISOString().split('T')[0];
  }
};
