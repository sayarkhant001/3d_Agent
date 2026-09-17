# 3D Ledger System: Complete Infrastructure, Calculations & Operational Guide

> **Version**: 2.0.0 (Production Stable)  
> **Language Support**: Pure Myanmar Unicode (မြန်မာယူနီကုဒ်)  
> **Target Platforms**: Android (Offline-First Native), Cloudflare Workers (Serverless Scraper & Telegram Bot), Cloudflare Pages (React Admin Web Portal), Firebase Realtime Database.

---

## Table of Contents

1. [System Infrastructure & Architecture](#1-system-infrastructure--architecture)
   - [Architectural Overview Diagram](#architectural-overview-diagram)
   - [Subsystem 1: Android Client (`app/`)](#subsystem-1-android-client-app)
   - [Subsystem 2: Cloudflare Worker & GLO Scraper (`3d-scraper-worker/`)](#subsystem-2-cloudflare-worker--glo-scraper-3d-scraper-worker)
   - [Subsystem 3: Cloudflare Telegram Bot Engine (`telegramBot.ts`)](#subsystem-3-cloudflare-telegram-bot-engine-telegrambotts)
   - [Subsystem 4: Cloudflare Pages Admin Portal (`3d-admin/`)](#subsystem-4-cloudflare-pages-admin-portal-3d-admin)
   - [Subsystem 5: Licensing API (`3d-license-api/`)](#subsystem-5-licensing-api-3d-license-api)
2. [Mathematical Calculations & Financial Rules](#2-mathematical-calculations--financial-rules)
   - [Direct Winner (ပေါက်သီး / ဒဲ့) Rules](#direct-winner-ပေါက်သီး--ဒဲ့-rules)
   - [Unified တွတ် (Tut & ကပ်သီး) Calculation Engine](#unified-တွတ်-tut--ကပ်သီး-calculation-engine)
   - [Agent Commission & Settlement Matrix](#agent-commission--settlement-matrix)
   - [Financial Color Semantics](#financial-color-semantics)
   - [Overflow & Upper Agent Isolation (တင်ကွက် / လျှံကွက်)](#overflow--upper-agent-isolation-တင်ကွက်--လျှံကွက်)
   - [Real-World Calculation Scenarios (Verified by Automated Tests)](#real-world-calculation-scenarios-verified-by-automated-tests)
3. [Operational User Guide & Workflows](#3-operational-user-guide--workflows)
   - [Android App Daily Operations](#android-app-daily-operations)
   - [Telegram Bot Commands & Interactive Menu](#telegram-bot-commands--interactive-menu)
   - [Cloudflare Admin Web Portal Operations](#cloudflare-admin-web-portal-operations)
4. [Deployment, Testing & Maintenance](#4-deployment-testing--maintenance)
   - [Cloudflare Workers & Webhook Setup](#cloudflare-workers--webhook-setup)
   - [Android Build & Sideloading Instructions](#android-build--sideloading-instructions)
   - [Automated Test Suites](#automated-test-suites)

---

## 1. System Infrastructure & Architecture

### Architectural Overview Diagram

```mermaid
flowchart TB
    subgraph External["External Services"]
        GLO["🇹🇭 Thai Government Lottery Office (GLO)<br/>Official API: https://www.glo.or.th/api/lottery/getLatestLottery"]
        TG_API["🤖 Telegram Bot API<br/>https://api.telegram.org"]
        FB["🔥 Firebase Realtime Database<br/>(3d_live_results, 3d_lottery_status, 3d_lottery_config)"]
    end

    subgraph Cloudflare["Cloudflare Infrastructure"]
        Worker["Cloudflare Worker (3d-scraper-worker)<br/>- Cron Scraper (1st & 16th Draw Days)<br/>- Full Telegram Webhook Handler<br/>- Google Service Account JWT Auth"]
        AdminPages["Cloudflare Pages (3d-admin)<br/>- React 19 + Vite Dark Glassmorphic Portal<br/>- Live Lottery Control & Manual Overrides"]
        LicenseWorker["Cloudflare Worker (3d-license-api)<br/>- Device ID Binding & JWT Licensing"]
    end

    subgraph Clients["Client Applications"]
        AndroidApp["📱 Android 3D Ledger App<br/>- Jetpack Compose Pure Burmese UI<br/>- Room SQLite Offline Persistence<br/>- Bluetooth ESC/POS 58mm/80mm Thermal Printer<br/>- Sideloading Compliant (No false positives)"]
        TGUsers["👥 Telegram Users & Commissioners<br/>- Live 3D Results (/live, /3d)<br/>- တွတ် Engine Permutations (/tut)<br/>- Direct Check (/check)<br/>- Inline Keyboards"]
        WebAdmin["💻 Web Admin<br/>- Browser Management"]
    end

    GLO -->|Official Draw Data| Worker
    Worker -->|RS256 JWT Signed Patch| FB
    Worker -->|Send Messages & Keyboards| TG_API
    TG_API -->|Webhook POST Events| Worker
    TGUsers <--> TG_API
    FB <-->|Realtime Listener & Snapshot| AndroidApp
    FB <-->|Realtime Sync| AdminPages
    LicenseWorker <-->|License Check| AndroidApp
    WebAdmin <--> AdminPages
```

---

### Subsystem 1: Android Client (`app/`)
- **UI Framework**: Modern declarative Jetpack Compose UI adhering to strict Material 3 design standards.
- **Pure Burmese Typography**: All screens utilize pure Myanmar Unicode without mixing English loanwords (e.g. `ပေါက်သီး`, `တွတ်`, `ဒဲ့`, `ထွိုင်`, `ကော်မရှင်`, `ရရန်`, `ပေးရန်`).
- **Offline-First Storage**: Local Room SQLite database (`LotteryDatabase`) persists all agents, customers, batches, and bet tickets locally. Network connection is only required for syncing official results and license checks.
- **Input Engine**:
  - Custom Numpad with `ထွိုင်` (Permutation shortcut) positioned immediately next to `ဒဲ့`.
  - Advanced Paste Parser supporting free-text, voice-to-text transcriptions, and sequentially numbered exported vouchers (`1. 108 = 50`).
- **Thermal Printing**: High-speed ESC/POS driver supporting 58mm and 80mm Bluetooth thermal printers with Myanmar bitmap font rasterization.
- **Sideloading Safety**: Strict compliance with Google Play Protect guidelines; dangerous permissions such as `REQUEST_INSTALL_PACKAGES` have been removed to prevent false-positive security warnings during offline sideloading.

---

### Subsystem 2: Cloudflare Worker & GLO Scraper (`3d-scraper-worker/`)
- **Runtime**: Cloudflare Workers V8 serverless isolate.
- **Primary Domain**: `https://3d-scraper-worker.khaingkhantkyaw001.workers.dev`
- **Data Source**: Official Government Lottery Office (GLO) of Thailand:
  ```http
  POST https://www.glo.or.th/api/lottery/getLatestLottery
  Content-Type: application/json
  Accept: application/json
  ```
- **Parsing Strategy**:
  - Extracts the official 6-digit First Prize (`response.data.first.number[0].value`).
  - Thai 3D winning number is the **last 3 digits** of the First Prize.
  - Thai 2D winning number is extracted from `response.data.last2.number[0].value` (or last 2 digits of First Prize).
  - Target draw date and next draw date calculated according to the Thai lottery schedule (1st and 16th of each calendar month).
- **Firebase Authentication**: Generates RS256 JWT assertions using `GOOGLE_SERVICE_ACCOUNT_JSON` with SubtleCrypto, exchanging them for Google OAuth2 access tokens to patch the Realtime Database securely.
- **Automated Triggers**: Cron schedules configured in `wrangler.jsonc`:
  - `35 5 * * 1-5`: Morning market/lottery check.
  - `5 11 * * 1-5`: Afternoon session and draw close check.

---

### Subsystem 3: Cloudflare Telegram Bot Engine (`telegramBot.ts`)
- **Engine Location**: Embedded directly inside `3d-scraper-worker`.
- **Endpoints**:
  - `POST /webhook` & `POST /telegram`: Receives update events from Telegram webhook servers.
  - `GET /set-webhook`: One-click registration with Telegram API.
  - `GET /webhook-info`: Queries current webhook status and queue count.
  - `GET /test-telegram`: Sends test dispatch to configured `TELEGRAM_CHAT_ID`.
- **Token Flexibility**: Supports both raw token formats (`123456:ABC...`) and `bot`-prefixed strings, with automatic whitespace trimming.
- **Features**: Full Myanmar Unicode replies, inline interactive keyboards, automatic တွတ် generation, live check, batch inspection, and admin commands.

---

### Subsystem 4: Cloudflare Pages Admin Portal (`3d-admin/`)
- **Technology**: React 19, Vite, Lucide Icons, Glassmorphic CSS.
- **Live Local Port**: `http://localhost:5173`
- **Capabilities**:
  - Realtime dashboard showing active batch, declared winners, and total system sales.
  - Mode toggle: **Automatic Mode** (GLO Scraper controls results) vs **Manual Mode** (Admin manually inputs numbers).
  - Web-based တွတ် calculation engine (`tutLogic.js`).

---

### Subsystem 5: Licensing API (`3d-license-api/`)
- **Runtime**: Cloudflare Workers with KV/D1 storage.
- **Capabilities**: Device ID fingerprint validation, trial expiration dates, and activation key generation.

---

## 2. Mathematical Calculations & Financial Rules

### Direct Winner (ပေါက်သီး / ဒဲ့) Rules
- **Winning Condition**: Bet number matches the official 3-digit Thai lottery winning number exactly in digit and position.
- **Calculation Formula**:
  $$\text{Winning Payout (ဒဲ့)} = \text{Bet Amount} \times \text{Direct Multiplier (ဒဲ့အဆ)}$$
- **Example**:
  - Winning Number: `108`
  - Bet: `108` for `1,000 MMK` with Multiplier `600x`
  - Direct Payout: $1,000 \times 600 = 600,000\text{ MMK}$

---

### Unified တွတ် (Tut & ကပ်သီး) Calculation Engine

In the 3D Ledger system, **တွတ်** is a single unified payout category that comprises two distinct mathematical components:

1. **Permutations (ပတ်လည် / အလှည့်)**:
   - All unique anagram rearrangements of the 3 digits, excluding the direct winning number itself.
   - Distinct digits (e.g. `108`): 5 permutations $\rightarrow$ `180, 018, 081, 810, 801`.
   - Double repeating digits (e.g. `212`): 2 permutations $\rightarrow$ `122, 221`.
   - Triple repeating digits (e.g. `222`): 0 permutations.

2. **Near Numbers (ကပ်သီး / အတိုးအလျော့)**:
   - Numbers that differ from the winning number by exactly $\pm 1$.
   - Includes cyclic modular boundaries ($000 \leftrightarrow 999$):
     - `108` $\rightarrow$ `107` and `109`.
     - `000` $\rightarrow$ `999` and `001`.
     - `999` $\rightarrow$ `998` and `000`.

3. **Unified Tut Set**:
   $$\text{tutSet} = (\text{Permutations} \cup \text{NearNumbers}) \setminus \{\text{WinningNumber}\}$$
   Every number in this set is treated as a **တွတ်** winner.

4. **Tut Payout Formula**:
   $$\text{Winning Payout (တွတ်)} = \text{Bet Amount} \times \text{Tut Multiplier (တွတ်အဆ)}$$

---

### Agent Commission & Settlement Matrix

For every registered commissioner (ကော်မရှင်စား ကိုယ်စားလှယ်):

1. **Total Sales (စုစုပေါင်း ထိုးကြေး)**:
   $$\text{GrossBet} = \sum \text{Bets}$$

2. **Commission Discount (ကော်မရှင် / အထိမ်)**:
   $$\text{CommissionAmount} = \text{GrossBet} \times \left(\frac{\text{CommissionRate\%}}{100}\right)$$

3. **Net Due from Sales (ကော်မရှင်နုတ်ပြီး ထိုးကြေး)**:
   $$\text{NetBetDue} = \text{GrossBet} - \text{CommissionAmount}$$

4. **Total Payout to Agent (စုစုပေါင်း ပေါက်ကြေး)**:
   $$\text{TotalPayout} = \sum \text{DirectPayouts} + \sum \text{TutPayouts}$$

5. **Net Balance Settlement (အသားတင် ကျန်ငွေ)**:
   $$\text{NetBalance} = (\text{NetBetDue} + \text{PreviousDebt}) - \text{TotalPayout}$$

---

### Financial Color Semantics

To ensure intuitive bookkeeping and prevent accounting mistakes:

| Financial State | Meaning | Display Text | Color Code |
| :--- | :--- | :--- | :--- |
| **Net Balance $> 0$** | House receives money from agent | `ကျန်ငွေ (ရရန်)` | **Green** (`#059669` / `#69F0AE`) |
| **Net Balance $< 0$** | House owes payout money to agent | `ကျန်ငွေ (ပေးရန်)` | **Red** (`#DC2626` / `#FF5252`) |
| **Debt $> 0$** | Agent owes previous unpaid balance | `ကြွေးကျန် (ရရန်)` | **Green** (`#059669` / `#69F0AE`) |
| **Debt $< 0$** | House owes previous advance credit | `ကြွေးကျန် (ပေးရန်)` | **Red** (`#DC2626` / `#FF5252`) |

---

### Overflow & Upper Agent Isolation (တင်ကွက် / လျှံကွက်)

1. **Hot Number Limit (သတ်မှတ် အကန့်အသတ်)**:
   - When cumulative bets on a single number exceed the dealer's risk threshold, surplus bets are diverted to Overflow Vouchers.
2. **Strict Commissioner Isolation**:
   - Upper agents who receive overflow bets **NEVER** appear in the commissioner accounting list or customer list.
   - Vouchers are tagged with customer names containing `တင်ကွက်`, `overflow`, `upper`, or `အထက်ဒိုင်` and are strictly excluded by `LotteryDao.kt` and `CommissionerResultScreen.kt`.
3. **Sequential Numbering for Fast Forwarding**:
   - Exported overflow vouchers format lines as `1. 108 = 50`, `2. 245 = 100`.
   - The betting parser strips leading sequential numbers automatically, allowing direct copy-pasting across applications without manual cleanup.

---

### Real-World Calculation Scenarios (Verified by Automated Tests)

These exact scenarios are validated by automated unit tests in [`CalculationVerificationTest.kt`](file:///c:/Users/localhost/Downloads/3d_Agent/app/src/test/java/com/threeDLedger/CalculationVerificationTest.kt):

#### Scenario 1: Typical House Gain (Receivable / ရရန်)
- **Agent Settings**: Commission: `10%`, Direct Multiplier: `600x`, Tut Multiplier: `10x`, Previous Debt: `0 MMK`.
- **Winning Number**: `108`
- **Bets Placed**:
  - `108` (Direct) = `100 MMK`
  - `801` (Permutation Tut) = `200 MMK`
  - `109` (Near Tut) = `300 MMK`
  - `555` (Losing Bet) = `9,400 MMK`
- **Calculations**:
  - $\text{Gross Bet} = 100 + 200 + 300 + 9,400 = 10,000\text{ MMK}$
  - $\text{Commission} = 10,000 \times 10\% = 1,000\text{ MMK}$
  - $\text{Net Bet Due} = 10,000 - 1,000 = 9,000\text{ MMK}$
  - $\text{Direct Payout} = 100 \times 600 = 60,000\text{ MMK}$
  - $\text{Tut Payout} = (200 + 300) \times 10 = 5,000\text{ MMK}$
  - $\text{Total Payout} = 60,000 + 5,000 = 65,000\text{ MMK}$
  - $\text{Net Balance} = 9,000 - 65,000 = -56,000\text{ MMK}$ (House must pay agent `56,000 MMK` $\rightarrow$ **Red** `ကျန်ငွေ (ပေးရန်)`).

#### Scenario 2: Zero Winning Payout (House Full Win)
- **Gross Bet**: `10,000 MMK` (all losing numbers).
- $\text{Commission}: 10,000 \times 10\% = 1,000\text{ MMK}$.
- $\text{Net Bet Due}: 9,000\text{ MMK}$.
- $\text{Payout}: 0\text{ MMK}$.
- $\text{Net Balance}: 9,000 - 0 = +9,000\text{ MMK}$ (Agent must pay house `9,000 MMK` $\rightarrow$ **Green** `ကျန်ငွေ (ရရန်)`).

#### Scenario 3: Settlement with Prior Unpaid Debt
- $\text{Gross Bet}: 10,000\text{ MMK}$, Commission: `10%` $\rightarrow$ $\text{Net Bet Due} = 9,000\text{ MMK}$.
- $\text{Prior Debt}: +15,000\text{ MMK}$ (Agent owed house from last draw).
- $\text{Winning Payout}: 0\text{ MMK}$.
- $\text{Net Balance} = (9,000 + 15,000) - 0 = +24,000\text{ MMK}$ (Agent owes `24,000 MMK` $\rightarrow$ **Green** `ကျန်ငွေ (ရရန်)`).

#### Scenario 4: Boundary Near-Miss တွတ် (`000` & `999`)
- Winning Number: `000`
- Cyclic Near Numbers: `999` (decrement) and `001` (increment).
- Permutations: 0 (triple repeating digits).
- Payout: Bets on `999` and `001` receive full တွတ် payout (`10x`).

---

## 3. Operational User Guide & Workflows

### Android App Daily Operations

#### 1. Betting & Voucher Entry (ထိုးကြေး စာရင်းသွင်းခြင်း)
1. Open the **Betting Screen** (`ထိုးကြေး`).
2. Select the Customer / Commissioner name from the dropdown.
3. Use the on-screen Numpad or type directly:
   - **ဒဲ့ (Direct)**: Enter 3 digits and amount (e.g. `108 = 500`).
   - **ထွိုင် (All Permutations)**: Press `ထွိုင်` after entering 3 digits to bet on all unique anagrams automatically (e.g. `108 ထွိုင် 100` expands to all 6 permutations $\times 100 = 600\text{ MMK}$).
   - **ကပ်သီး (Near Numbers)**: Enter digits with `+` or `-` or use the quick shortcut.
   - **အကွက်ပြိုင် (Parallel Pairs)**: Type multiple numbers separated by dots or spaces.
4. **Pasting Long Vouchers**:
   - Copy text from Viber, Telegram, or Messenger.
   - Tap **စာသားဖြင့် ထည့်ရန် (Paste)** and press **အတည်ပြုမည်**.
   - Leading serial numbers (e.g. `1. `, `2) `) are parsed and stripped automatically.
5. Tap **စာရင်းသွင်းမည်** to save and optionally print via Bluetooth thermal printer.

#### 2. Live Numbers & Risk Ledger (အရောင်းစာရင်း နှင့် အကွက်ချုပ်)
- View the **Ledger Screen** (`စာရင်း`).
- **Before Declaration**:
  - Displays all 1,000 numbers (`000` to `999`) sorted by total bet amount.
  - Highlights numbers that exceed your risk limit in orange/red.
- **After Declaration**:
  - Non-winning numbers automatically collapse.
  - Only the direct winning number (`ပေါက်သီး`, Red) and တွတ် numbers (`တွတ်`, Orange) are displayed.
  - Bottom sticky under-bar displays Total Bets, Winning Bets, Winning Payouts, and Net Balance.

#### 3. Winner Declaration & Settlements (ပေါက်မဲကြေညာခြင်း နှင့် ရှင်းတမ်း)
1. Navigate to the **Winner Screen** (`ပေါက်သီး`).
2. Tap **တိုက်ရိုက်ရယူမည်** to fetch the official Thai GLO winning number immediately via the Cloudflare Worker API.
3. Or type the 3-digit number manually and tap **ပေါက်မဲ သတ်မှတ်မည်**.
4. Open the **Commissioners Screen** (`ကိုယ်စားလှယ်များ`):
   - All registered agents remain visible.
   - Agents are ranked from highest payout to lowest.
   - Tap any agent card to see the full itemized breakdown (Gross, Commission, Net Due, Direct Wins, Tut Wins, Prior Debt, Net Due).
   - Tap **ရှင်းတမ်း ပရင့်ထုတ်မည်** to print the final settlement slip for that agent.

#### 4. Overflow Voucher Forwarding (တင်ကွက် ပို့ဆောင်ခြင်း)
1. In the **Overflow Screen** (`တင်ကွက်`), review numbers exceeding limits.
2. Tap **ဘောင်ချာ ထုတ်ယူမည် (Export Voucher)**.
3. The app generates a formatted, clean voucher:
   ```text
   3D တင်ကွက် ဘောင်ချာ
   ဘောင်ချာအမှတ်: #V-1024
   ရက်စွဲ: 2026-09-17 16:35:00
   အပတ်စဉ်: 15
   --------------------------------
   1. 108 = 500
   2. 801 = 200
   3. 245 = 1000
   --------------------------------
   စုစုပေါင်း: 1,700 ကျပ်
   ```
4. Copy and forward directly to the upper dealer.

---

### Telegram Bot Admin Control Center & Key Management

The Telegram Bot is strictly configured as an **Admin-Only Management Console** (`TELEGRAM_CHAT_ID: 5684146708`). Unauthorized users are automatically blocked.

```text
┌────────────────────────────────────────────────────────────┐
│         👑 3D LEDGER စီမံခန့်ခွဲမှု စင်တာ (ADMIN CONTROL)      │
├────────────────────────────────────────────────────────────┤
│  ➕ ကုတ်အသစ် ထုတ်ရန်           │  📋 အသုံးပြုမှု စောင့်ကြည့်      │
├────────────────────────────────┼───────────────────────────┤
│  ⚡ Auto-Approve: ဖွင့်/ပိတ်     │  ⏳ စောင့်ဆိုင်းဆဲများ        │
├────────────────────────────────┼───────────────────────────┤
│  📊 အရောင်း အစီရင်ခံစာ          │  🚫 ကုတ် ပိတ်သိမ်းရန်          │
├────────────────────────────────┼───────────────────────────┤
│  🎯 ပေါက်မဲ လက်ဖြင့် သတ်မှတ်     │  📦 အပတ်စဉ် ကြည့်ရှု         │
├────────────────────────────────┴───────────────────────────┤
│               🔄 မီနူး အသစ်ပြန်ဖွင့် (Refresh)               │
└────────────────────────────────────────────────────────────┘
```

#### Key Management & Dual Authorization Workflow

1. **Key Generation (`/gen [days] [price]` or `[➕ ကုတ်အသစ် ထုတ်ရန်]`)**:
   - Generates unique 32-character keys (`XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX`).
   - Durations: `7 days` (5,000 MMK), `30 days` (15,000 MMK), `90 days` (35,000 MMK), `1 year` (100,000 MMK), `lifetime` (250,000 MMK).
   - Saved in Firebase Realtime Database at `/3d_licenses/keys/{key}` with `status: "available"`.

2. **Dual Authorization Modes**:
   - **Auto-Approve Mode (`/autoapprove` or `[⚡ Auto-Approve]` button)**:
     - When toggled **ON**, user entering a valid key on Android is instantly activated and granted access without waiting for admin approval.
     - Telegram Bot alerts the admin: `⚡ [Auto-Approved] Key Activated! Device: <model>`.
   - **Manual Telegram Approval Mode**:
     - When toggled **OFF**, user entering a valid key is put into `pending_approval` state.
     - The Android app displays an animated waiting screen (`Admin ၏ ခွင့်ပြုချက်ကို စောင့်ဆိုင်းနေပါသည်`) and polls every 3 seconds.
     - The Admin immediately receives an interactive Telegram notification:
       ```text
       🔔 ခွင့်ပြုချက် တောင်းခံလွှာ အသစ် (New Activation Request)
       🔑 ကုတ်နံပါတ်: A1B2-C3D4-...
       📱 ဖုန်းမော်ဒယ်: Samsung Galaxy A54
       🆔 ID: a1b2c3d4...
       ⏳ သက်တမ်း: 30 ရက်
       [✅ ခွင့်ပြုမည် (Approve)]  [❌ ငြင်းပယ်မည် (Reject)]
       ```
     - Tapping **Approve** immediately unlocks the Android app. Tapping **Reject** resets the key to available.

3. **Key Revocation (`/revoke <key>` or `[🚫 ကုတ် ပိတ်သိမ်းရန်]`)**:
   - Immediately invalidates the key in Firebase (`status: "revoked"`).
   - The Android app enforces real-time revocation on startup and resume, clearing local credentials and kicking revoked devices to the activation screen.

4. **Sales & Usage Report (`/report` or `[📊 အရောင်း အစီရင်ခံစာ]`)**:
   - Total keys generated, active devices, unsold available keys, pending requests, and revoked keys.
   - Total estimated revenue in MMK.
   - Breakdown by duration tier.

5. **Manual Winning & Tut Declaration (`/setwinner <3-digit number>` or `[🎯 ပေါက်မဲ လက်ဖြင့် သတ်မှတ်]`)**:
   - Admin can manually declare the official 3D winning number (e.g. `/setwinner 108`).
   - Automatically computes all unified တွတ် numbers (permutations + near $\pm 1$).
   - Locks Firebase `3d_lottery_config/mode` to `manual` to prevent automated scraper overwrites.
   - Broadcasts the declared numbers immediately to all connected devices.

---

### Cloudflare Admin Web Portal Operations

1. Open `http://localhost:5173` (or the deployed Cloudflare Pages URL).
2. Enter the administrator credentials.
3. **Live Overview**: Inspect real-time gross betting volume, top wagered numbers, and current batch number.
4. **Lottery Mode Switch**:
   - **Automatic Mode**: Thai GLO Scraper Worker runs on schedule and updates the winning number upon official draw completion.
   - **Manual Mode**: Scraper skips automated overwrites, allowing the dealer to control declaration timing.
5. **Instant Override**: Enter any 3-digit number to instantly publish the winner to all connected Android devices and Telegram bots via Firebase.

---

## 4. Deployment, Testing & Maintenance

### Cloudflare Workers & Webhook Setup

#### 1. Worker Deployment
```powershell
cd 3d-scraper-worker
npx wrangler deploy
```
- Deploys the worker to `https://3d-scraper-worker.khaingkhantkyaw001.workers.dev`.

#### 2. Telegram Webhook Registration
To link the Telegram Bot API to the Cloudflare Worker webhook:
```powershell
curl.exe -s "https://3d-scraper-worker.khaingkhantkyaw001.workers.dev/set-webhook"
```
Response:
```json
{
  "status": "ok",
  "webhookUrl": "https://3d-scraper-worker.khaingkhantkyaw001.workers.dev/webhook",
  "telegram": {
    "ok": true,
    "result": true,
    "description": "Webhook was set"
  }
}
```

#### 3. Webhook Status Verification
```powershell
curl.exe -s "https://3d-scraper-worker.khaingkhantkyaw001.workers.dev/webhook-info"
```

---

### Android Build & Sideloading Instructions

To compile a clean APK ready for sideloading on any Android device:
```powershell
.\gradlew.bat assembleDebug
```
- **Binary Output**: `app\build\outputs\apk\debug\app-debug.apk` (~24 MB).
- **Sideloading**: Copy `app-debug.apk` directly to Android devices via USB, Telegram, or Google Drive and install. No Google Play Protect blockages will occur.

---

### Automated Test Suites

The entire system is guarded by multi-layered automated test suites:

| Subsystem | Test Command | Scope |
| :--- | :--- | :--- |
| **Android Calculations** | `.\gradlew.bat testDebugUnitTest` | Validates all 5 real settlement scenarios, တွတ် sets, and numbered voucher parsing. |
| **Android Compose UI** | `.\gradlew.bat testDebugUnitTest` | Validates `ထွိုင်` button interactions and green/red financial indicators. |
| **Cloudflare Worker** | `npm test` in `3d-scraper-worker/` | Validates GLO parser, Telegram `/start`, `/tut`, and inline callbacks. |
| **Cloudflare Web Admin** | `node --test` in `3d-admin/` | Validates တွတ် calculation permutations and boundary cyclic behavior. |

---

*This document is maintained as the authoritative system specification for the 3D Ledger platform.*
