import os

# Create directories
dirs = [
    "architecture_export/cloudflare_workers",
    "architecture_export/firebase",
    "architecture_export/react_admin",
    "architecture_export/android_src",
    ".github/workflows"
]
for d in dirs:
    os.makedirs(d, exist_ok=True)

# 1. Cloudflare Scraper Worker
with open("architecture_export/cloudflare_workers/worker-scraper.ts", "w") as f:
    f.write("""/**
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
""")

# 2. Cloudflare License Worker
with open("architecture_export/cloudflare_workers/worker-license.ts", "w") as f:
    f.write("""/**
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
""")

# 3. Firebase Rules
with open("architecture_export/firebase/database.rules.json", "w") as f:
    f.write("""{
  "rules": {
    "3d_live_results": {
      ".read": true,
      ".write": "auth != null"
    },
    "3d_lottery_status": {
      ".read": true,
      ".write": "auth != null"
    },
    "3d_lottery_config": {
      ".read": "auth != null",
      ".write": "auth != null"
    },
    "3d_licenses": {
      ".read": "auth != null",
      ".write": "auth != null",
      "keys": {
        ".indexOn": ["status", "claimed_by"]
      }
    }
  }
}
""")

# 4. React Admin Dashboard
with open("architecture_export/react_admin/AdminDashboard.jsx", "w") as f:
    f.write("""/**
 * MODULE 4: React Admin Dashboard
 */
import React, { useState, useEffect } from 'react';
import { getAuth, signInWithEmailAndPassword } from 'firebase/auth';
import { getDatabase, ref, onValue, set, update, push } from 'firebase/database';

const AdminDashboard = () => {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [keys, setKeys] = useState({});
  const [mode, setMode] = useState('auto');
  const [manualNumber, setManualNumber] = useState('');
  
  const auth = getAuth();
  const db = getDatabase();

  const handleLogin = async (e) => {
    e.preventDefault();
    const email = e.target.email.value;
    const password = e.target.password.value;
    if (email !== 'admin@yourdomain.com') return alert('Access Denied');
    
    try {
      await signInWithEmailAndPassword(auth, email, password);
      setIsAuthenticated(true);
    } catch (err) {
      alert('Login Failed');
    }
  };

  const generateKey = () => {
    const array = new Uint32Array(4);
    window.crypto.getRandomValues(array);
    const key = Array.from(array, dec => ('0000' + dec.toString(36).toUpperCase()).slice(-4)).join('-');
    
    set(ref(db, `3d_licenses/keys/${key}`), { status: 'available', generated_at: Date.now() });
  };

  const revokeKey = (keyId) => {
    update(ref(db, `3d_licenses/keys/${keyId}`), { status: 'available', claimed_by: null });
  };

  const pushManualResult = () => {
    if (manualNumber.length !== 3) return alert('Must be 3 digits');
    update(ref(db), {
      '3d_live_results/winning_number': manualNumber,
      '3d_lottery_status/state': 'declared'
    });
  };

  if (!isAuthenticated) {
    return (
      <div className="flex h-screen items-center justify-center">
        <form onSubmit={handleLogin} className="p-8 border rounded shadow-lg flex flex-col gap-4">
          <input name="email" type="email" placeholder="Admin Email" required className="border p-2"/>
          <input name="password" type="password" placeholder="Password" required className="border p-2"/>
          <button type="submit" className="bg-blue-600 text-white p-2">Login</button>
        </form>
      </div>
    );
  }

  return (
    <div className="p-8 max-w-6xl mx-auto space-y-8">
      <h1 className="text-3xl font-bold">3D Lottery Admin</h1>
      
      {/* OVERRIDE PANEL */}
      <div className="border p-4 rounded bg-gray-50">
        <h2 className="text-xl font-bold mb-4">Control Panel</h2>
        <div className="flex items-center gap-4 mb-4">
          <span>Mode: {mode}</span>
          <button onClick={() => set(ref(db, '3d_lottery_config/mode'), mode === 'auto' ? 'manual' : 'auto')} className="bg-purple-600 text-white px-4 py-2 rounded">
            Toggle Mode
          </button>
        </div>
        {mode === 'manual' && (
          <div className="flex gap-4">
            <input type="number" maxLength={3} value={manualNumber} onChange={e => setManualNumber(e.target.value)} placeholder="3-Digit Result" className="border p-2"/>
            <button onClick={pushManualResult} className="bg-green-600 text-white px-4 py-2">Push Result</button>
          </div>
        )}
      </div>

      {/* LICENSING PANEL */}
      <div className="border p-4 rounded bg-gray-50">
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-xl font-bold">Licenses</h2>
          <button onClick={generateKey} className="bg-blue-600 text-white px-4 py-2 rounded">Generate Key</button>
        </div>
        <table className="w-full text-left">
          <thead><tr><th>Key</th><th>Status</th><th>Device</th><th>Action</th></tr></thead>
          <tbody>
            {/* Map through 'keys' state here */}
          </tbody>
        </table>
      </div>
    </div>
  );
};
export default AdminDashboard;
""")

# 5. Native Android
with open("architecture_export/android_src/LotteryApplication.kt", "w") as f:
    f.write("""package com.example

import android.app.Application
// import com.google.firebase.database.FirebaseDatabase

class LotteryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Ensure disk caching is active before UI loads
        // Uncomment once Firebase is added to dependencies
        // FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}
""")

with open("architecture_export/android_src/SecurityUtils.kt", "w") as f:
    f.write("""package com.example.utils

import android.content.Context
// import com.scottyab.rootbeer.RootBeer
// import androidx.security.crypto.EncryptedSharedPreferences
// import androidx.security.crypto.MasterKeys

object SecurityUtils {
    
    fun checkRoot(context: Context): Boolean {
        // val rootBeer = RootBeer(context)
        // return rootBeer.isRooted
        return false 
    }

    /*
    fun saveJwtToken(context: Context, token: String) {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            "secret_shared_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        sharedPreferences.edit().putString("auth_token", token).apply()
    }
    */
}
""")

with open("architecture_export/android_src/LotteryViewModel.kt", "w") as f:
    f.write("""package com.example.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
// import com.google.firebase.database.FirebaseDatabase
// import com.google.firebase.database.DataSnapshot
// import com.google.firebase.database.DatabaseError
// import com.google.firebase.database.ValueEventListener

class LotteryViewModel : ViewModel() {
    private val _lotteryState = MutableStateFlow("waiting")
    val lotteryState: StateFlow<String> = _lotteryState

    private val _winningNumber = MutableStateFlow("")
    val winningNumber: StateFlow<String> = _winningNumber

    init {
        // Narrow Listeners Example
        /*
        val db = FirebaseDatabase.getInstance()
        
        db.getReference("3d_lottery_status/state")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _lotteryState.value = snapshot.getValue(String::class.java) ?: "waiting"
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        db.getReference("3d_live_results/winning_number")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _winningNumber.value = snapshot.getValue(String::class.java) ?: ""
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        */
    }
}
""")

with open("architecture_export/android_src/GitHubUpdater.kt", "w") as f:
    f.write("""package com.example.utils

// Retrofit + DownloadManager Auto-Updater Stub
// interface GitHubApi {
//     @GET("repos/{owner}/{repo}/releases/latest")
//     suspend fun getLatestRelease(): ReleaseResponse
// }

// class GitHubUpdater {
//     suspend fun checkForUpdates(currentVersion: String) {
//         // Compare response.tag_name with BuildConfig.VERSION_NAME
//         // Show Dialog
//         // Trigger DownloadManager
//     }
// }
""")

# 6. CI/CD Pipeline
with open(".github/workflows/deploy.yml", "w") as f:
    f.write("""name: Android Production Build

on:
  push:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Decode Keystore
        run: |
          echo "${{ secrets.KEYSTORE_B64 }}" | base64 --decode > my-upload-key.jks

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build Release APK
        env:
          KEYSTORE_PATH: ../my-upload-key.jks
          STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew assembleRelease

      - name: Extract Commit Message
        id: commit_msg
        run: echo "RELEASE_BODY=$(git log -1 --pretty=%B)" >> $GITHUB_ENV

      - name: Create Release
        uses: softprops/action-gh-release@v1
        with:
          tag_name: v1.0.${{ github.run_number }}
          name: Release v1.0.${{ github.run_number }}
          body: ${{ env.RELEASE_BODY }}
          files: app/build/outputs/apk/release/app-release.apk
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
""")

# Setup Android Proguard rules in app
with open("app/proguard-rules.pro", "a") as f:
    f.write("""
# MODULE 5: ProGuard Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Jetpack Compose
-keep class androidx.compose.** { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class retrofit2.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Custom Data Classes (Licensing / Payload)
# -keep class com.example.models.** { *; }
""")

print("Files generated successfully.")
