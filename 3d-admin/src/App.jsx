import { useState, useEffect, useCallback, useMemo } from 'react';
import { signInWithEmailAndPassword, signOut, onAuthStateChanged } from 'firebase/auth';
import { ref, onValue, set, update } from 'firebase/database';
import { auth, db } from './firebase';
import './index.css';

import { calculateTutNumbers } from './tutLogic';

const DEFAULT_SALE_PLANS = {
  trial_3d: {
    id: 'trial_3d',
    name: '၃ ရက် အခမဲ့ စမ်းသပ်ခွင့် (3-Day Free Trial)',
    duration: 'trial',
    duration_label: '၃ ရက် စမ်းသပ်ခွင့် (72 နာရီ)',
    price: 0,
    device_changeable: false,
    description: 'ဖုန်း ၁ လုံး (စက်ပြောင်းမရပါ - ၇၂ နာရီ)',
    enabled: true,
  },
  one_year: {
    id: 'one_year',
    name: '၁ နှစ် သက်တမ်း (1-Year Plan)',
    duration: 365,
    duration_label: '၁ နှစ် (365 ရက်)',
    price: 180000,
    device_changeable: true,
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
    description: 'ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည် (1-Device Locked 🔒)',
    enabled: true,
  }
};

const SYNCED_PLAN_IDS = ['trial_3d', 'one_year', 'lifetime'];

function generateCdKeyString() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const randomBytes = new Uint8Array(32);
  window.crypto.getRandomValues(randomBytes);
  const blocks = [];
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

function App() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loginError, setLoginError] = useState('');
  const [keys, setKeys] = useState({});
  const [salePlans, setSalePlans] = useState(DEFAULT_SALE_PLANS);
  const [editingPlanPrice, setEditingPlanPrice] = useState({});
  const [mode, setMode] = useState('auto');
  const [lotteryStatus, setLotteryStatus] = useState('normal');
  const [liveResults, setLiveResults] = useState({});
  const [currentBatch, setCurrentBatch] = useState(1);
  const [batchInput, setBatchInput] = useState('1');
  const [manualNumber, setManualNumber] = useState('');
  const [manualDate, setManualDate] = useState('');
  const [manualStatus, setManualStatus] = useState('waiting');
  const [updateBatchWithResult, setUpdateBatchWithResult] = useState(false);
  const [generatedKey, setGeneratedKey] = useState(null);
  const [bulkGeneratedKeys, setBulkGeneratedKeys] = useState([]);
  const [toast, setToast] = useState(null);
  const [copiedId, setCopiedId] = useState(null);
  const [loggingIn, setLoggingIn] = useState(false);

  // Key filtering & search
  const [keySearch, setKeySearch] = useState('');
  const [keyFilter, setKeyFilter] = useState('all'); // all, available, claimed, revoked, changeable, locked

  // Tut playground test number
  const [calcTestNumber, setCalcTestNumber] = useState('');

  // GLO Thailand Official Lottery Scraper State
  const [gloResult, setGloResult] = useState(null);
  const [fetchingGlo, setFetchingGlo] = useState(false);
  const [gloError, setGloError] = useState('');

  // App Distribution & Telegram Release State
  const [appRelease, setAppRelease] = useState(null);

  // Resellers & Due Settlement State
  const [resellers, setResellers] = useState({});
  const [settleModalReseller, setSettleModalReseller] = useState(null);
  const [manualSettleAmount, setManualSettleAmount] = useState('');
  const [settleNotes, setSettleNotes] = useState('');
  const [settlingDue, setSettlingDue] = useState(false);

  // Key generation options: Strictly 3 plans (trial_3d, one_year, lifetime), device switching mode on/off for each plan, bulk count
  const [keyType, setKeyType] = useState('one_year');
  const [deviceChangeable, setDeviceChangeable] = useState(true);
  const [bulkCount, setBulkCount] = useState(1);

  // Auto-fetch Official Thai GLO results on mount and poll periodically
  useEffect(() => {
    fetchOfficialGlo();
    const interval = setInterval(() => {
      fetchOfficialGlo();
    }, 60000); // Poll GLO every 60 seconds
    return () => clearInterval(interval);
  }, []);

  // Auth state listener
  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (u) => {
      setUser(u);
      setLoading(false);
    });
    return unsubscribe;
  }, []);

  // Realtime data listeners
  useEffect(() => {
    if (!user) return;

    const unsubs = [];

    const keysRef = ref(db, '3d_licenses/keys');
    unsubs.push(onValue(keysRef, (snap) => {
      setKeys(snap.val() || {});
    }));

    const modeRef = ref(db, '3d_lottery_config/mode');
    unsubs.push(onValue(modeRef, (snap) => {
      setMode(snap.val() || 'auto');
    }));

    const statusRef = ref(db, '3d_lottery_status/state');
    unsubs.push(onValue(statusRef, (snap) => {
      setLotteryStatus(snap.val() || 'normal');
    }));

    const resultsRef = ref(db, '3d_live_results');
    unsubs.push(onValue(resultsRef, (snap) => {
      setLiveResults(snap.val() || {});
    }));

    const plansRef = ref(db, '3d_licenses/sale_plans');
    unsubs.push(onValue(plansRef, (snap) => {
      const p = snap.val();
      if (p && Object.keys(p).length > 0) {
        setSalePlans(p);
      } else {
        setSalePlans(DEFAULT_SALE_PLANS);
      }
    }));

    const batchRef = ref(db, '3d_lottery_config/current_batch');
    unsubs.push(onValue(batchRef, (snap) => {
      const b = snap.val() || 1;
      setCurrentBatch(b);
      setBatchInput(String(b));
    }));

    const releaseRef = ref(db, '3d_app_release');
    unsubs.push(onValue(releaseRef, (snap) => {
      setAppRelease(snap.val() || null);
    }));

    const resellersRef = ref(db, '3d_licenses/resellers');
    unsubs.push(onValue(resellersRef, (snap) => {
      setResellers(snap.val() || {});
    }));

    return () => unsubs.forEach(u => u());
  }, [user]);

  const showToast = useCallback((message, type = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  }, []);

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoginError('');
    setLoggingIn(true);
    const email = e.target.email.value;
    const password = e.target.password.value;

    try {
      await signInWithEmailAndPassword(auth, email, password);
    } catch (err) {
      setLoginError('Invalid credentials. Please check email and password.');
    }
    setLoggingIn(false);
  };

  const handleLogout = () => {
    signOut(auth);
  };

  // ── Fetch fast real-time Thai 3D / GLO result with multi-tier failover ────────
  const fetchOfficialGlo = async () => {
    setFetchingGlo(true);
    setGloError('');
    try {
      let data = null;

      // 1. Primary Scraper Worker (Sanook Live ~3:15 PM MMT + GLO fallback)
      try {
        const res = await fetch('https://3d-scraper-worker.khaingkhantkyaw001.workers.dev/latest-glo');
        if (res.ok) {
          const json = await res.json();
          if (json.status === 'ok' && json.data) {
            data = json.data;
          }
        }
      } catch (_) {}

      // 2. Secondary Scraper Worker
      if (!data) {
        try {
          const res2 = await fetch('https://3d-scraper-worker.sayarkhant001.workers.dev/latest-glo');
          if (res2.ok) {
            const json2 = await res2.json();
            if (json2.status === 'ok' && json2.data) {
              data = json2.data;
            }
          }
        } catch (_) {}
      }

      // 3. Rayriffy Community Lottery API (CORS friendly)
      if (!data) {
        try {
          const rayRes = await fetch('https://lotto.api.rayriffy.com/latest');
          if (rayRes.ok) {
            const json = await rayRes.json();
            const first = json.response?.data?.first?.number?.[0]?.value || '';
            const last2 = json.response?.data?.last2?.number?.[0]?.value || '';
            const date = json.response?.date || '';
            if (first && first.length >= 3) {
              data = {
                threeD: first.slice(-3),
                firstPrize: first,
                twoD: last2,
                date: date,
                session: 'Rayriffy Mirror Draw',
                source: 'Rayriffy Mirror'
              };
            }
          }
        } catch (_) {}
      }

      // 4. Direct GLO Thailand API
      if (!data) {
        try {
          const gloRes = await fetch('https://www.glo.or.th/api/lottery/getLatestLottery', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: '{}'
          });
          if (gloRes.ok) {
            const json = await gloRes.json();
            const first = json.response?.data?.first?.number?.[0]?.value || '';
            const last2 = json.response?.data?.last2?.number?.[0]?.value || '';
            const date = json.response?.date || '';
            if (first && first.length >= 3) {
              data = {
                threeD: first.slice(-3),
                firstPrize: first,
                twoD: last2,
                date: date,
                session: 'GLO Official Draw (3:30 PM MMT)',
                source: 'Official Thai Government Lottery (GLO)'
              };
            }
          }
        } catch (_) {}
      }

      if (data) {
        setGloResult(data);
        const sourceLabel = data.source || data.session || 'Live Feed';
        showToast(`3D Result (${sourceLabel}): 3D = ${data.threeD} (1st = ${data.firstPrize})`);
      } else {
        setGloError('Unable to fetch live lottery data. You can enter manually.');
      }
    } catch (err) {
      setGloError(err.message || 'Fetch failed');
    } finally {
      setFetchingGlo(false);
    }
  };

  const applyGloToManual = () => {
    if (!gloResult) return;
    setManualNumber(gloResult.threeD);
    setManualStatus('declared');
    if (gloResult.date) {
      setManualDate(gloResult.date);
    }
    showToast(`Set manual 3D to ${gloResult.threeD} (Declared)`);
  };

  // ── ⚡ 1-Click Apply Live 3D / Official Thai GLO to Firebase App & Devices ─────
  const applyGloDirectlyToFirebase = async () => {
    if (!gloResult || !gloResult.threeD) {
      showToast('No 3D lottery result available to apply', 'error');
      return;
    }

    const tut = calculateTutNumbers(gloResult.threeD);
    const sourceLabel = gloResult.source || gloResult.session || 'Live Fast Feed (~3:15 PM MMT)';
    const updates = {
      '3d_live_results/winning_number': gloResult.threeD,
      '3d_live_results/first_prize': gloResult.firstPrize || '',
      '3d_live_results/twod': gloResult.twoD || '',
      '3d_live_results/result_date': gloResult.date || '',
      '3d_live_results/result_time': gloResult.session || '3:15 PM MMT Live Draw',
      '3d_live_results/source': sourceLabel,
      '3d_live_results/is_final': true,
      '3d_live_results/updated_at': Date.now(),
      '3d_lottery_status/state': 'declared',
      '3d_live_results/tut_permutations': tut.permutations,
      '3d_live_results/tut_near_misses': tut.nearMisses,
      '3d_live_results/tut_all': tut.allTut
    };

    if (updateBatchWithResult) {
      const b = parseInt(batchInput, 10);
      if (!isNaN(b) && b >= 1) {
        updates['3d_lottery_config/current_batch'] = b;
      }
    }

    await update(ref(db), updates);

    // Sync manual inputs for consistency
    setManualNumber(gloResult.threeD);
    setManualStatus('declared');
    if (gloResult.date) setManualDate(gloResult.date);

    // Also notify worker to broadcast if available
    try {
      fetch('https://3d-scraper-worker.khaingkhantkyaw001.workers.dev/apply-glo', { method: 'POST' }).catch(() => {});
    } catch (_) {}

    showToast(`⚡ 3D Result (${gloResult.threeD}) applied to Live App & Firebase!`);
  };

  const handleKeyTypeChange = (newType) => {
    setKeyType(newType);
    // Smart default suggestion based on plan, but admin can toggle ON or OFF for any plan!
    if (newType === 'one_year') {
      setDeviceChangeable(true);
    } else if (newType === 'lifetime' || newType === 'trial_3d') {
      setDeviceChangeable(false);
    }
  };

  const savePlanPrice = async (planId) => {
    const rawVal = editingPlanPrice[planId];
    if (rawVal === undefined || rawVal === '') return;
    const price = parseInt(rawVal, 10);
    if (isNaN(price) || price < 0) {
      showToast('Please enter a valid price', 'error');
      return;
    }
    await update(ref(db, `3d_licenses/sale_plans/${planId}`), { price });
    showToast(`Updated ${planId} price to ${price.toLocaleString()} Ks (Synced with Telegram Bot)`);
    setEditingPlanPrice(prev => ({ ...prev, [planId]: undefined }));
  };

  const downloadKeysAsText = (keysList, planId, isChangeable) => {
    const text = keysList.join('\n');
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `3d_keys_${planId}_${isChangeable ? 'changeable' : 'locked'}_${keysList.length}keys_${new Date().toISOString().slice(0, 10)}.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    showToast(`Downloaded ${keysList.length} keys to .txt file!`);
  };

  // ── CD-Key Generation (Single & Bulk) with Per-Plan Device Switching Mode ──
  const generateKeysBatch = async () => {
    const count = Math.max(1, Math.min(100, parseInt(bulkCount, 10) || 1));
    const currentPlan = salePlans[keyType] || DEFAULT_SALE_PLANS[keyType];

    let durationVal = 'lifetime';
    let durationLbl = 'တစ်သက်တာ (Lifetime)';
    let planPrice = 0;
    let planId = keyType;

    if (keyType === 'trial_3d') {
      durationVal = 'trial';
      durationLbl = '၃ ရက် စမ်းသပ်ခွင့် (72 နာရီ)';
      planPrice = 0;
    } else if (keyType === 'one_year') {
      durationVal = 365;
      durationLbl = '၁ နှစ် (365 ရက်)';
      planPrice = currentPlan?.price ?? 180000;
    } else if (keyType === 'lifetime') {
      durationVal = 'lifetime';
      durationLbl = 'တစ်သက်တာ (Lifetime)';
      planPrice = currentPlan?.price ?? 45000;
    }

    const updates = {};
    const newlyGenerated = [];
    const now = Date.now();

    for (let i = 0; i < count; i++) {
      const key = generateCdKeyString();
      updates[`3d_licenses/keys/${key}`] = {
        cd_key: key,
        status: 'available',
        plan_id: planId,
        duration: durationVal,
        duration_label: durationLbl,
        price: planPrice,
        device_changeable: deviceChangeable,
        created_at: now,
        generated_at: now
      };
      newlyGenerated.push(key);
    }

    await update(ref(db), updates);
    setGeneratedKey(newlyGenerated[0]);
    setBulkGeneratedKeys(newlyGenerated);
    showToast(count === 1 ? 'New CD-Key generated successfully!' : `Successfully generated ${count} CD-Keys!`);
  };

  const generateKey = generateKeysBatch;

  const revokeKey = async (keyId) => {
    await update(ref(db, `3d_licenses/keys/${keyId}`), {
      status: 'available',
      claimed_by: null,
      activated_at: null
    });
    showToast('Key revoked and reset to available');
  };

  const deleteKey = async (keyId) => {
    await set(ref(db, `3d_licenses/keys/${keyId}`), null);
    showToast('Key deleted', 'error');
  };

  const handleOpenSettleModal = (reseller) => {
    setSettleModalReseller(reseller);
    setManualSettleAmount(reseller.total_due ? String(reseller.total_due) : '');
    setSettleNotes('');
  };

  const handleCloseSettleModal = () => {
    setSettleModalReseller(null);
    setManualSettleAmount('');
    setSettleNotes('');
    setSettlingDue(false);
  };

  const handleConfirmSettleDue = async (e) => {
    e.preventDefault();
    if (!settleModalReseller) return;

    const amount = parseInt(String(manualSettleAmount).replace(/,/g, ''), 10);
    if (isNaN(amount) || amount <= 0) {
      showToast('Please enter a valid settlement amount greater than 0', 'error');
      return;
    }

    setSettlingDue(true);
    try {
      const resellerId = settleModalReseller.telegram_id;
      const currentDue = settleModalReseller.total_due || 0;
      const currentPaid = settleModalReseller.total_paid || 0;
      const newDue = Math.max(0, currentDue - amount);
      const newPaid = currentPaid + amount;

      const updates = {};
      updates[`3d_licenses/resellers/${resellerId}/total_due`] = newDue;
      updates[`3d_licenses/resellers/${resellerId}/total_paid`] = newPaid;

      const ledgerId = `pay_${Date.now()}`;
      updates[`3d_licenses/reseller_ledger/${resellerId}/${ledgerId}`] = {
        id: ledgerId,
        reseller_id: resellerId,
        reseller_name: settleModalReseller.name,
        amount: amount,
        previous_due: currentDue,
        remaining_due: newDue,
        settled_at: Date.now(),
        settled_by: user?.email || 'Web Admin',
        notes: settleNotes.trim() || 'Manual Due Settlement (Web Admin)'
      };

      await update(ref(db), updates);
      showToast(`Successfully settled ${amount.toLocaleString()} Ks for ${settleModalReseller.name}!`);
      handleCloseSettleModal();
    } catch (err) {
      console.error(err);
      showToast('Failed to record settlement: ' + (err.message || 'Error'), 'error');
    } finally {
      setSettlingDue(false);
    }
  };

  const toggleMode = async (newMode) => {
    await set(ref(db, '3d_lottery_config/mode'), newMode);
    showToast(`Switched to ${newMode.toUpperCase()} mode`);
  };

  const saveBatch = async () => {
    const b = parseInt(batchInput, 10);
    if (isNaN(b) || b < 1) {
      showToast('Please enter a valid batch number', 'error');
      return;
    }
    await set(ref(db, '3d_lottery_config/current_batch'), b);
    showToast(`Current Batch updated to #${b}`);
  };

  const pushManualResult = async () => {
    if (!/^\d{3}$/.test(manualNumber)) {
      showToast('Must be exactly 3 digits', 'error');
      return;
    }

    const updates = {
      '3d_live_results/winning_number': manualNumber,
      '3d_lottery_status/state': manualStatus,
      '3d_live_results/updated_at': Date.now()
    };

    if (manualDate) {
      updates['3d_live_results/target_draw_date'] = manualDate;
    }

    if (updateBatchWithResult) {
      const b = parseInt(batchInput, 10);
      if (!isNaN(b) && b >= 1) {
        updates['3d_lottery_config/current_batch'] = b;
      }
    }

    await update(ref(db), updates);
    showToast(`Result ${manualNumber} pushed to app as "${manualStatus}"!`);
  };

  const copyToClipboard = (text, id = null) => {
    if (!text) return;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).catch(() => {
        fallbackCopyText(text);
      });
    } else {
      fallbackCopyText(text);
    }
    if (id) {
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 1800);
    }
    showToast('Copied to clipboard!');
  };

  const fallbackCopyText = (text) => {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    try {
      document.execCommand('copy');
    } catch (_) {}
    document.body.removeChild(ta);
  };

  const formatDuration = (duration, planId) => {
    if (planId === 'one_year' || duration === 365) return '1 Year (၁ နှစ်)';
    if (duration === 'trial' || planId === 'trial_3d') return '3 Days Trial (၇၂ နာရီ)';
    if (duration === 'lifetime' || planId === 'lifetime') return 'Lifetime (တစ်သက်တာ) ♾️';
    if (typeof duration === 'number') return `${duration} Days`;
    return duration || '—';
  };

  // Dynamic Tut Calculation for current input or live result
  const activeNumber = manualNumber || liveResults.winning_number || '';
  const tutInfo = useMemo(() => calculateTutNumbers(activeNumber), [activeNumber]);

  // Tut for playground tester
  const playgroundTut = useMemo(() => calculateTutNumbers(calcTestNumber), [calcTestNumber]);

  // Stats & Key filtering
  const keyEntries = Object.entries(keys);
  const totalKeys = keyEntries.length;
  const availableKeys = keyEntries.filter(([, v]) => v.status === 'available').length;
  const claimedKeys = keyEntries.filter(([, v]) => v.status === 'claimed' || v.status === 'active').length;
  const revokedKeys = keyEntries.filter(([, v]) => v.status === 'revoked').length;

  const filteredKeys = useMemo(() => {
    return keyEntries
      .filter(([keyId, keyData]) => {
        if (keyFilter === 'available' && keyData.status !== 'available') return false;
        if (keyFilter === 'claimed' && keyData.status !== 'claimed' && keyData.status !== 'active') return false;
        if (keyFilter === 'revoked' && keyData.status !== 'revoked') return false;
        if (keyFilter === 'changeable' && keyData.device_changeable !== true) return false;
        if (keyFilter === 'locked' && keyData.device_changeable === true) return false;
        if (keyFilter === 'trial_3d' && keyData.plan_id !== 'trial_3d' && keyData.duration !== 'trial') return false;
        if (keyFilter === 'one_year' && keyData.plan_id !== 'one_year' && keyData.duration !== 365) return false;
        if (keyFilter === 'lifetime' && keyData.plan_id !== 'lifetime' && keyData.duration !== 'lifetime') return false;

        if (keySearch.trim()) {
          const q = keySearch.toLowerCase().trim();
          const matchKey = keyId.toLowerCase().includes(q);
          const matchDevice = (keyData.claimed_by || keyData.device_model || keyData.device_fingerprint || '').toLowerCase().includes(q);
          const matchPlan = (keyData.plan_id || keyData.duration_label || '').toLowerCase().includes(q);
          return matchKey || matchDevice || matchPlan;
        }
        return true;
      })
      .sort((a, b) => (b[1].generated_at || b[1].created_at || 0) - (a[1].generated_at || a[1].created_at || 0));
  }, [keyEntries, keyFilter, keySearch]);

  if (loading) {
    return (
      <div className="login-container">
        <div className="loading" style={{ fontSize: 24, color: 'var(--text-secondary)' }}>
          Loading 3D Lottery Admin...
        </div>
      </div>
    );
  }

  // ===== LOGIN SCREEN =====
  if (!user) {
    return (
      <div className="login-container">
        <div className="login-card">
          <div className="logo">🎰</div>
          <h1>3D Lottery Admin</h1>
          <p className="subtitle">Sign in to manage your 3D Ledger system & GLO results</p>

          {loginError && <div className="login-error">{loginError}</div>}

          <form onSubmit={handleLogin}>
            <div className="form-group">
              <label>Email</label>
              <input name="email" type="email" placeholder="admin@yourdomain.com" required />
            </div>
            <div className="form-group">
              <label>Password</label>
              <input name="password" type="password" placeholder="••••••••" required />
            </div>
            <button type="submit" className="btn btn-primary btn-full" disabled={loggingIn}>
              {loggingIn ? '⏳ Signing in...' : '🔐 Sign In'}
            </button>
          </form>
        </div>
      </div>
    );
  }

  // ===== DASHBOARD =====
  return (
    <div className="dashboard">
      {/* Header */}
      <header className="dashboard-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <h1>🎰 <span>3D Lottery Admin (3D စာရင်း စီမံခန့်ခွဲမှု)</span></h1>
          <span style={{
            fontSize: 11,
            background: 'rgba(0, 200, 151, 0.15)',
            color: 'var(--accent-success)',
            border: '1px solid rgba(0, 200, 151, 0.3)',
            borderRadius: 12,
            padding: '2px 8px',
            fontWeight: 700
          }}>
            🟢 Cloudflare Pages Live
          </span>
        </div>
        <div className="header-right">
          <span className="user-info">👤 {user.email}</span>
          <button className="btn btn-outline btn-sm" onClick={handleLogout}>Logout</button>
        </div>
      </header>

      <main className="dashboard-content">
        {/* Stats Row */}
        <div className="stats-row">
          <div className="stat-card purple">
            <div className="stat-icon">🔑</div>
            <div className="stat-value">{totalKeys}</div>
            <div className="stat-label">Total Keys (လိုင်စင်ကုဒ်များ)</div>
          </div>
          <div className="stat-card green">
            <div className="stat-icon">✅</div>
            <div className="stat-value">{availableKeys}</div>
            <div className="stat-label">Available (သုံးနိုင်သော)</div>
          </div>
          <div className="stat-card red">
            <div className="stat-icon">📱</div>
            <div className="stat-value">{claimedKeys}</div>
            <div className="stat-label">Claimed (အသုံးပြုထားသော)</div>
          </div>
          <div className="stat-card orange">
            <div className="stat-icon">🎯</div>
            <div className="stat-value">{liveResults.winning_number || '---'}</div>
            <div className="stat-label">3D ပေါက်ဂဏန်း (Winning 3D)</div>
          </div>
        </div>

        {/* Batch & System Config Bar */}
        <div className="card" style={{ marginBottom: 20 }}>
          <div className="card-body" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
              <span style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-primary)' }}>
                အကြိမ် (Batch Number):
              </span>
              <input
                type="number"
                value={batchInput}
                onChange={e => setBatchInput(e.target.value)}
                style={{
                  width: 90,
                  textAlign: 'center',
                  fontSize: 18,
                  fontWeight: 700,
                  background: 'var(--bg-primary)',
                  border: '1px solid var(--border-color)',
                  color: 'var(--accent-primary)',
                  padding: '6px 10px',
                  borderRadius: 8
                }}
              />
              <button className="btn btn-primary btn-sm" onClick={saveBatch}>
                💾 Save Batch
              </button>
              <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                (Current Live App Batch: #{currentBatch})
              </span>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span style={{ fontSize: 13, color: 'var(--text-secondary)', fontWeight: 600 }}>Scraper Mode:</span>
              <select
                className="select-input"
                value={mode}
                onChange={(e) => toggleMode(e.target.value)}
                style={{
                  background: mode === 'auto' ? 'rgba(0, 200, 151, 0.15)' : 'rgba(255, 179, 71, 0.15)',
                  color: mode === 'auto' ? 'var(--accent-success)' : 'var(--accent-warning)',
                  fontWeight: 700
                }}
              >
                <option value="auto">🤖 AUTO (Official GLO Thailand Scraper)</option>
                <option value="manual">✋ MANUAL (Admin Override)</option>
              </select>
            </div>
          </div>
        </div>

        {/* Real-Time Thai 3D / GLO Official Lottery Live Scraper Card */}
        <div className="card glo-live-banner" style={{ marginBottom: 20 }}>
          <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <span style={{ fontSize: 24 }}>⚡</span>
              <div>
                <h2 style={{ fontSize: 16, margin: 0, fontWeight: 800 }}>Real-Time Thai 3D / GLO Live Feed (ထိုင်း 3D တိုက်ရိုက် ရလဒ် စနစ်)</h2>
                <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                  Fast Live: Sanook Realtime (~3:15 PM MMT အမြန်ဆုံး) &bull; Archive: Official GLO (~3:30 PM MMT)
                </span>
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              {gloResult?.threeD && (
                <span className={`sync-status-indicator ${gloResult.threeD === liveResults.winning_number ? 'synced' : 'out-of-sync'}`}>
                  {gloResult.threeD === liveResults.winning_number
                    ? `🟢 IN SYNC: Live App has ${gloResult.threeD}`
                    : `⚠️ OUT OF SYNC: Live App has ${liveResults.winning_number || 'None'} (Feed: ${gloResult.threeD})`}
                </span>
              )}
              <button
                className="btn btn-primary btn-sm"
                onClick={fetchOfficialGlo}
                disabled={fetchingGlo}
              >
                {fetchingGlo ? '⏳ Fetching Live 3D...' : '🔄 Refresh Live 3D'}
              </button>
            </div>
          </div>
          <div className="card-body">
            {gloError && (
              <div style={{ color: 'var(--accent-secondary)', fontSize: 13, marginBottom: 12 }}>
                ⚠️ {gloError}
              </div>
            )}
            {gloResult ? (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', alignItems: 'center' }}>
                  <div className="glo-stat-box">
                    <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>1st Prize (รางวัลที่ 1)</div>
                    <div style={{ fontSize: 22, fontWeight: 800, fontFamily: 'monospace', color: 'var(--text-primary)', marginTop: 4 }}>
                      {gloResult.firstPrize || '—'}
                    </div>
                  </div>
                  <div className="glo-stat-box" style={{ borderColor: 'rgba(0, 200, 151, 0.4)', background: 'rgba(0, 200, 151, 0.08)' }}>
                    <div style={{ fontSize: 11, color: 'var(--accent-success)', fontWeight: 800, textTransform: 'uppercase' }}>
                      3D Winning (နောက် ၃ လုံး)
                    </div>
                    <div style={{ fontSize: 30, fontWeight: 900, fontFamily: 'monospace', color: 'var(--accent-success)', letterSpacing: 2, marginTop: 2 }}>
                      {gloResult.threeD || '—'}
                    </div>
                  </div>
                  {gloResult.twoD && (
                    <div className="glo-stat-box">
                      <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>2D (အောက် ၂ လုံး)</div>
                      <div style={{ fontSize: 20, fontWeight: 700, fontFamily: 'monospace', color: 'var(--text-primary)', marginTop: 4 }}>
                        {gloResult.twoD}
                      </div>
                    </div>
                  )}
                  {gloResult.date && (
                    <div className="glo-stat-box">
                      <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Draw Date (ရက်စွဲ)</div>
                      <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-secondary)', marginTop: 6 }}>
                        {gloResult.date}
                      </div>
                    </div>
                  )}
                  <div className="glo-stat-box" style={{ borderColor: 'rgba(99, 102, 241, 0.35)', background: 'rgba(99, 102, 241, 0.08)' }}>
                    <div style={{ fontSize: 11, color: '#818cf8', textTransform: 'uppercase', fontWeight: 700 }}>
                      Feed Source (ရင်းမြစ်)
                    </div>
                    <div style={{ fontSize: 12, fontWeight: 800, color: 'var(--text-primary)', marginTop: 6, display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span>{gloResult.source?.includes('Sanook') ? '⚡' : '🏛️'}</span>
                      <span>{gloResult.source || gloResult.session || 'Live Fast Feed'}</span>
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
                  <button
                    className="btn btn-warning"
                    style={{ fontWeight: 800, padding: '10px 18px' }}
                    onClick={applyGloDirectlyToFirebase}
                  >
                    ⚡ Apply 3D Result to Live App & Telegram
                  </button>
                  <button
                    className="btn btn-outline btn-sm"
                    onClick={applyGloToManual}
                    title="Fill the manual form fields below with this 3D number"
                  >
                    📋 Fill Manual Form
                  </button>
                </div>
              </div>
            ) : (
              <div style={{ fontSize: 13, color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: 8 }}>
                {fetchingGlo ? '⏳ Fetching real-time Thai 3D / GLO lottery result...' : 'Auto-checking real-time 3D live result (~3:15 PM MMT). You can also click "Refresh Live 3D" to pull latest draw.'}
              </div>
            )}
          </div>
        </div>

        {/* App Distribution & Telegram Release Pipeline Card */}
        <div className="card" style={{ marginBottom: 20, border: '1px solid rgba(99, 102, 241, 0.3)', background: 'linear-gradient(180deg, rgba(99, 102, 241, 0.04) 0%, var(--bg-card) 100%)' }}>
          <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <span style={{ fontSize: 26 }}>📲</span>
              <div>
                <h2 style={{ fontSize: 16, margin: 0, fontWeight: 800 }}>
                  App Distribution & Telegram Release Manager (အက်ပ်ဗားရှင်း ဖြန့်ချိရေး စနစ်)
                </h2>
                <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                  Zero-server APK hosting directly via Telegram Bot &bull; Real-time automatic updates
                </span>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
              <span style={{
                fontSize: 12,
                fontWeight: 700,
                padding: '4px 10px',
                borderRadius: 20,
                background: appRelease?.file_id ? 'rgba(0, 200, 151, 0.15)' : 'rgba(255, 179, 71, 0.15)',
                color: appRelease?.file_id ? 'var(--accent-success)' : 'var(--accent-warning)',
                border: `1px solid ${appRelease?.file_id ? 'rgba(0, 200, 151, 0.3)' : 'rgba(255, 179, 71, 0.3)'}`
              }}>
                {appRelease?.file_id ? '🟢 Telegram Release Live' : '⏳ Awaiting Initial APK'}
              </span>
            </div>
          </div>
          <div className="card-body">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 16, marginBottom: 16 }}>
              {/* Release Metadata Card */}
              <div style={{ background: 'var(--bg-primary)', padding: 16, borderRadius: 12, border: '1px solid var(--border-color)' }}>
                <div style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 700, textTransform: 'uppercase', marginBottom: 10 }}>
                  📦 Active APK in Telegram Bot
                </div>
                {appRelease ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>File Name:</span>
                      <code style={{ fontSize: 13, fontWeight: 700, color: 'var(--accent-primary)' }}>{appRelease.file_name || '3D_Ledger.apk'}</code>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>Version:</span>
                      <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)' }}>{appRelease.version_name || 'v1.0.0'}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>File Size:</span>
                      <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)' }}>
                        {appRelease.file_size ? `${(appRelease.file_size / (1024 * 1024)).toFixed(2)} MB` : '—'}
                      </span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>Uploaded At:</span>
                      <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                        {appRelease.uploaded_at ? new Date(appRelease.uploaded_at).toLocaleString() : '—'}
                      </span>
                    </div>
                  </div>
                ) : (
                  <div style={{ fontSize: 13, color: 'var(--text-muted)', padding: '10px 0' }}>
                    Admin မှ Telegram Bot သို့ APK ဖိုင် ပို့ထားခြင်း မရှိသေးပါ။ Telegram Bot ထံသို့ <code>.apk</code> ဖိုင် တိုက်ရိုက် ပေးပို့လိုက်ပါက ဤနေရာတွင် အလိုအလျောက် ပေါ်လာပါမည်။
                  </div>
                )}
              </div>

              {/* Security & Anti-Reverse Engineering Status */}
              <div style={{ background: 'var(--bg-primary)', padding: 16, borderRadius: 12, border: '1px solid var(--border-color)' }}>
                <div style={{ fontSize: 12, color: 'var(--accent-success)', fontWeight: 700, textTransform: 'uppercase', marginBottom: 10, display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span>🛡️</span> Security & Anti-Reverse Engineering
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 12 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--text-primary)' }}>
                    <span>✅</span> <b>Anti-Tamper & Anti-Resigning:</b> SHA-256 Certificate Lock (Blocks MT Manager / Lucky Patcher)
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--text-primary)' }}>
                    <span>✅</span> <b>Anti-Frida & Hooking:</b> Scans /proc/self/maps & port 27042
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--text-primary)' }}>
                    <span>✅</span> <b>Anti-Debug Protection:</b> TracerPid & JDWP attachment block
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--text-primary)' }}>
                    <span>✅</span> <b>R8 Aggressive Obfuscation:</b> Repackaged to <code>com.threeDLedger.obf</code>
                  </div>
                </div>
              </div>
            </div>

            {/* Admin Instructions Banner */}
            <div style={{
              background: 'rgba(99, 102, 241, 0.08)',
              border: '1px solid rgba(99, 102, 241, 0.2)',
              borderRadius: 10,
              padding: '12px 16px',
              fontSize: 13,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: 10
            }}>
              <div>
                <b>💡 အက်ပ်ဗားရှင်း အသစ်တင်လိုပါက:</b> Admin Account ဖြင့် Telegram Bot ထံသို့ နောက်ဆုံးထွက် <b>.apk</b> ဖိုင်ကို တိုက်ရိုက် Send File (Document) အဖြစ် ပို့လိုက်ရုံဖြင့် Bot ရှိ <b>[📲 အက်ပ် ဒေါင်းလုဒ်ရယူရန်]</b> ခလုတ်တွင် ချက်ချင်း အလိုအလျောက် Update ဖြစ်သွားပါမည်။
              </div>
              <a
                href="https://t.me/threed_ledger_bot"
                target="_blank"
                rel="noreferrer"
                className="btn btn-outline btn-sm"
                style={{ textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: 6 }}
              >
                <span>🤖 Open Telegram Bot</span>
              </a>
            </div>
          </div>
        </div>

        {/* Two Column Grid */}
        <div className="two-col-grid">

          {/* Lottery Control Panel */}
          <div className="card">
            <div className="card-header">
              <h2>🎱 3D Result & Status (ရလဒ် ထိန်းချုပ်မှု)</h2>
              <span className={`mode-badge ${mode}`}>
                {mode === 'auto' ? '🤖 AUTO' : '✋ MANUAL'}
              </span>
            </div>
            <div className="card-body">
              <div style={{ fontSize: 13, color: 'var(--text-muted)', marginBottom: 14 }}>
                Current Status: <strong style={{ color: lotteryStatus === 'declared' ? 'var(--accent-success)' : 'var(--accent-warning)' }}>
                  {lotteryStatus.toUpperCase()}
                </strong>
                {liveResults.target_draw_date && (
                  <span> · Draw: {liveResults.target_draw_date}</span>
                )}
              </div>

              {gloResult?.threeD && gloResult.threeD !== liveResults.winning_number && (
                <div style={{ background: 'rgba(255, 179, 71, 0.12)', border: '1px solid rgba(255, 179, 71, 0.3)', borderRadius: 8, padding: '10px 14px', marginBottom: 14, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
                  <div>
                    <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--accent-warning)' }}>
                      🇹🇭 Official Thai GLO 3D: <strong>{gloResult.threeD}</strong>
                    </span>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                      Live App has "{liveResults.winning_number || 'None'}". Click to sync:
                    </div>
                  </div>
                  <button className="btn btn-warning btn-sm" style={{ fontWeight: 700 }} onClick={applyGloDirectlyToFirebase}>
                    ⚡ Sync Live App ({gloResult.threeD})
                  </button>
                </div>
              )}

              <div className="manual-override-panel">
                <h3 style={{ fontSize: 14, fontWeight: 700, color: 'var(--accent-warning)', marginBottom: 12 }}>
                  {mode === 'manual' ? '✋ Manual Override Panel' : '📋 Quick Manual Push'}
                </h3>

                <div className="form-group">
                  <label>3D Winning Number (ပေါက်ဂဏန်း ၃ လုံး)</label>
                  <input
                    type="text"
                    maxLength={3}
                    value={manualNumber}
                    onChange={e => setManualNumber(e.target.value.replace(/\D/g, ''))}
                    placeholder="000"
                    style={{ textAlign: 'center', fontSize: 24, letterSpacing: 8, fontWeight: 800, color: 'var(--accent-primary)' }}
                  />
                </div>

                <div className="form-group">
                  <label>Draw Date (ရက်စွဲ)</label>
                  <input
                    type="text"
                    value={manualDate}
                    onChange={e => setManualDate(e.target.value)}
                    placeholder="e.g. 1 September 2026"
                  />
                </div>

                <div className="form-group">
                  <label>Display Status (အခြေအနေ)</label>
                  <select
                    className="select-input"
                    value={manualStatus}
                    onChange={e => setManualStatus(e.target.value)}
                  >
                    <option value="waiting">⏳ Waiting (စောင့်ဆိုင်းဆဲ)</option>
                    <option value="pending">🔄 Pending (ထွက်ခါနီး)</option>
                    <option value="declared">✅ Declared (အတည်ပြု ပေါက်သီးထွက်ပြီ)</option>
                    <option value="delayed">🔴 Delayed (ရွှေ့ဆိုင်းဆဲ)</option>
                  </select>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 14 }}>
                  <input
                    type="checkbox"
                    id="updateBatchCheck"
                    checked={updateBatchWithResult}
                    onChange={e => setUpdateBatchWithResult(e.target.checked)}
                    style={{ width: 'auto', cursor: 'pointer' }}
                  />
                  <label htmlFor="updateBatchCheck" style={{ margin: 0, fontSize: 12, cursor: 'pointer', textTransform: 'none' }}>
                    Sync Batch #{batchInput} with this result
                  </label>
                </div>

                <button className="btn btn-warning btn-full" onClick={pushManualResult}>
                  📤 Push Live Result to App (ပေါက်သီး လွှင့်တင်မည်)
                </button>
              </div>

              {/* ── တွတ် (Tut) Live Breakdown Preview ───────────────────────── */}
              {activeNumber.length === 3 && (
                <div style={{ marginTop: 20, padding: 14, background: 'rgba(108, 99, 255, 0.08)', borderRadius: 10, border: '1px solid rgba(108, 99, 255, 0.2)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                    <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--accent-primary)' }}>
                      🔄 တွတ် ({tutInfo.allTut.length} ဂဏန်း) — {activeNumber} အတွက်
                    </span>
                    <button
                      type="button"
                      className={`copy-btn ${copiedId === 'tut-all' ? 'copied' : ''}`}
                      onClick={() => copyToClipboard(tutInfo.allTut.join(', '), 'tut-all')}
                    >
                      {copiedId === 'tut-all' ? '✅ Copied All' : '📋 Copy All Tut'}
                    </button>
                  </div>

                  {/* Direct / Winning */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                    <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--accent-secondary)' }}>🎯 ဒဲ့:</span>
                    <span style={{
                      background: 'rgba(233, 69, 96, 0.2)',
                      color: 'var(--accent-secondary)',
                      padding: '2px 8px',
                      borderRadius: 4,
                      fontFamily: 'monospace',
                      fontSize: 13,
                      fontWeight: 800
                    }}>
                      {activeNumber}
                    </span>
                  </div>

                  {/* Permutations */}
                  <div style={{ marginBottom: 8 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                      <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                        🔄 အပြန် ({tutInfo.permutations.length}):
                      </span>
                      {tutInfo.permutations.length > 0 && (
                        <span
                          className="copy-link"
                          onClick={() => copyToClipboard(tutInfo.permutations.join(', '), 'tut-perm')}
                        >
                          {copiedId === 'tut-perm' ? '✅ Copied' : 'Copy အပြန်'}
                        </span>
                      )}
                    </div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                      {tutInfo.permutations.map(n => (
                        <span
                          key={n}
                          style={{
                            background: 'rgba(108, 99, 255, 0.2)',
                            color: '#fff',
                            padding: '2px 7px',
                            borderRadius: 5,
                            fontFamily: 'monospace',
                            fontSize: 12,
                            fontWeight: 700
                          }}
                        >
                          {n}
                        </span>
                      ))}
                      {tutInfo.permutations.length === 0 && (
                        <span style={{ fontSize: 11, color: 'var(--text-muted)', fontStyle: 'italic' }}>
                          အပြန်မရှိပါ (သုံးလုံးတူ)
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Near Misses */}
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                      <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                        ⚡ ကပ်သီး (+1, -1) ({tutInfo.nearMisses.length}):
                      </span>
                      {tutInfo.nearMisses.length > 0 && (
                        <span
                          className="copy-link"
                          onClick={() => copyToClipboard(tutInfo.nearMisses.join(', '), 'tut-near')}
                        >
                          {copiedId === 'tut-near' ? '✅ Copied' : 'Copy ကပ်သီး'}
                        </span>
                      )}
                    </div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                      {tutInfo.nearMisses.map(n => (
                        <span
                          key={n}
                          style={{
                            background: 'rgba(255, 179, 71, 0.2)',
                            color: 'var(--accent-warning)',
                            padding: '2px 7px',
                            borderRadius: 5,
                            fontFamily: 'monospace',
                            fontSize: 12,
                            fontWeight: 700
                          }}
                        >
                          {n}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Right Column: Synced Sale Plans + Key Generation + Tut Playground */}
          <div>
            {/* Telegram & Cloudflare Synced Sale Plans */}
            <div className="card" style={{ marginBottom: 20 }}>
              <div className="card-header">
                <h2>🏷️ Synced Sale Plans (အရောင်း အစီအစဉ်များ)</h2>
              </div>
              <div className="card-body">
                <p style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 12 }}>
                  Telegram Bot နှင့် တိုက်ရိုက်ချိတ်ဆက်ထားသော စီမံချက် ၃ ခု (ဈေးနှုန်း ပြင်ဆင်နိုင်သည်):
                </p>

                {SYNCED_PLAN_IDS.map(planId => {
                  const plan = salePlans[planId] || DEFAULT_SALE_PLANS[planId];
                  if (!plan) return null;
                  const isChangeable = plan.device_changeable;
                  const currentPrice = editingPlanPrice[planId] !== undefined ? editingPlanPrice[planId] : plan.price;
                  return (
                    <div key={planId} className="plan-sync-card">
                      <div style={{ flex: 1 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <strong style={{ fontSize: 13, color: 'var(--text-primary)' }}>{plan.name}</strong>
                          <span className={`device-badge ${isChangeable ? 'changeable' : 'locked'}`}>
                            {isChangeable ? '🔄 စက်ပြောင်းနိုင်' : '🔒 စက်ပြောင်းမရ'}
                          </span>
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
                          {plan.duration_label} • {plan.description}
                        </div>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <input
                          type="number"
                          value={currentPrice}
                          onChange={e => setEditingPlanPrice(prev => ({ ...prev, [planId]: e.target.value }))}
                          style={{ width: 95, padding: '4px 8px', fontSize: 12, textAlign: 'right' }}
                        />
                        <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Ks</span>
                        {editingPlanPrice[planId] !== undefined && (
                          <button
                            className="btn btn-primary btn-sm"
                            style={{ padding: '4px 8px', fontSize: 11 }}
                            onClick={() => savePlanPrice(planId)}
                          >
                            Save
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Key Generation Panel */}
            <div className="card">
              <div className="card-header">
                <h2>✨ Generate CD-Key (လိုင်စင်ကုဒ် ထုတ်ရန်)</h2>
              </div>
              <div className="card-body">
                <div className="form-group">
                  <label>Sale Plan / Key Type (အစီအစဉ် ရွေးချယ်ပါ - စီမံချက် ၃ ခု သီးသန့်)</label>
                  <select
                    className="select-input"
                    value={keyType}
                    onChange={e => handleKeyTypeChange(e.target.value)}
                  >
                    <option value="trial_3d">
                      ⏱️ 3-Day Free Trial (၃ ရက် စမ်းသပ်ခွင့်) — 0 Ks [🔒 1-Device Only]
                    </option>
                    <option value="one_year">
                      ⭐ 1-Year Plan (၁ နှစ် သက်တမ်း) — {(salePlans.one_year?.price ?? 180000).toLocaleString()} Ks [🔄 Device Changeable ✅]
                    </option>
                    <option value="lifetime">
                      💎 Lifetime Plan (တစ်သက်တာ) — {(salePlans.lifetime?.price ?? 45000).toLocaleString()} Ks [🔒 1-Device Only 🔒]
                    </option>
                  </select>
                </div>

                {/* Device Switching Mode (ON / OFF for each plan) */}
                <div className="device-switch-toggle-card">
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                    <label style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-primary)', margin: 0 }}>
                      🔄 Device Switching Mode (စက်ပြောင်းခွင့် ထိန်းချုပ်မှု)
                    </label>
                    <span className={`device-badge ${deviceChangeable ? 'changeable' : 'locked'}`}>
                      {deviceChangeable ? '🔄 ON (Device Changeable)' : '🔒 OFF (1-Device Only)'}
                    </span>
                  </div>

                  <div className="segmented-switch">
                    <button
                      type="button"
                      className={`segment-btn ${deviceChangeable ? 'active-on' : ''}`}
                      onClick={() => setDeviceChangeable(true)}
                    >
                      🔄 ON · စက်ပြောင်းခွင့် ပြုမည်
                    </button>
                    <button
                      type="button"
                      className={`segment-btn ${!deviceChangeable ? 'active-off' : ''}`}
                      onClick={() => setDeviceChangeable(false)}
                    >
                      🔒 OFF · ဖုန်း ၁ လုံးတည်း သီးသန့်
                    </button>
                  </div>

                  <div style={{ fontSize: 12, color: deviceChangeable ? 'var(--accent-success)' : 'var(--text-muted)', marginTop: 8 }}>
                    {deviceChangeable
                      ? '✅ စက်ပြောင်းခွင့် ဖွင့်ထားပါသည်: ဝယ်ယူသူသည် ဖုန်းအသစ်လဲပါက စက်ဟောင်း အလိုအလျောက် ပိတ်သွားပြီး လက်ကျန်ရက်များဖြင့် ဖုန်းအသစ်တွင် ဆက်သုံးနိုင်ပါမည်။'
                      : '🔒 စက်ပြောင်းခွင့် ပိတ်ထားပါသည်: ပထမဆုံး အသက်သွင်းသည့် ဖုန်း ၁ လုံးတည်းတွင်သာ အသုံးပြုနိုင်မည်ဖြစ်ပြီး စက်ပြောင်း၍ မရပါ။'}
                  </div>
                </div>

                {/* Bulk Generation Option */}
                <div className="form-group" style={{ marginTop: 14 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <label>Quantity (ထုတ်မည့် အရေအတွက် - Bulk Keys)</label>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Max: 100 per batch</span>
                  </div>
                  <input
                    type="number"
                    min="1"
                    max="100"
                    value={bulkCount}
                    onChange={e => setBulkCount(e.target.value)}
                    placeholder="Quantity"
                    style={{ fontWeight: 700 }}
                  />
                  <div className="chip-group">
                    {[1, 5, 10, 25, 50, 100].map(cnt => (
                      <button
                        key={cnt}
                        type="button"
                        className={`chip-btn ${parseInt(bulkCount, 10) === cnt ? 'active' : ''}`}
                        onClick={() => setBulkCount(cnt)}
                      >
                        {cnt === 1 ? '1 Key' : `${cnt} Keys`}
                      </button>
                    ))}
                  </div>
                </div>

                <button
                  className="btn btn-primary btn-full"
                  onClick={generateKeysBatch}
                  style={{ marginTop: 16, padding: '12px 16px', fontSize: 14, fontWeight: 700 }}
                >
                  ✨ Generate {parseInt(bulkCount, 10) > 1 ? `${bulkCount} Keys` : 'Key'} ({deviceChangeable ? '🔄 Device Changeable' : '🔒 1-Device Only'})
                </button>

                <div style={{ marginTop: 14, fontSize: 12, color: 'var(--text-muted)', textAlign: 'center', lineHeight: 1.6 }}>
                  {keyType === 'trial_3d' && '⏱️ ၃ ရက် အခမဲ့ စမ်းသပ်ခွင့် (၇၂ နာရီတိတိ သက်တမ်း • ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည် 🔒)'}
                  {keyType === 'one_year' && '⭐ ၁ နှစ် သက်တမ်း (၃၆၅ ရက် • ဖုန်းအသစ်သို့ စက်ပြောင်းလဲ အသုံးပြုနိုင်ပါသည် ✅)'}
                  {keyType === 'lifetime' && '💎 တစ်သက်တာ သက်တမ်း (မကန့်သတ် • ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်သည် - စက်ပြောင်းမရပါ 🔒)'}
                </div>
              </div>
            </div>

            {/* တွတ် (Tut) Interactive Simulator / Tester */}
            <div className="card" style={{ marginTop: 20 }}>
              <div className="card-header">
                <h2>🧪 Tut Simulator (တွတ် စမ်းသပ်တွက်စက်)</h2>
              </div>
              <div className="card-body">
                <div className="form-group">
                  <label>Enter Any 3D Number to Test</label>
                  <input
                    type="text"
                    maxLength={3}
                    value={calcTestNumber}
                    onChange={e => setCalcTestNumber(e.target.value.replace(/\D/g, ''))}
                    placeholder="e.g. 212, 123, 789"
                    style={{ textAlign: 'center', fontSize: 18, fontWeight: 700, letterSpacing: 4 }}
                  />
                </div>

                {calcTestNumber.length === 3 ? (
                  <div style={{ background: 'var(--bg-primary)', padding: 12, borderRadius: 8, border: '1px solid var(--border-color)' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                      <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--accent-success)' }}>
                        တွတ် စုစုပေါင်း ({playgroundTut.allTut.length} ဂဏန်း)
                      </span>
                      <button
                        type="button"
                        className={`copy-btn ${copiedId === 'play-tut-all' ? 'copied' : ''}`}
                        style={{ fontSize: 10, padding: '2px 8px' }}
                        onClick={() => copyToClipboard(playgroundTut.allTut.join(', '), 'play-tut-all')}
                      >
                        {copiedId === 'play-tut-all' ? '✅ Copied All' : '📋 Copy All'}
                      </button>
                    </div>

                    <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>
                      အပြန်: {playgroundTut.permutations.join(', ') || 'မရှိပါ'}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 8 }}>
                      ကပ်သီး: {playgroundTut.nearMisses.join(', ')}
                    </div>

                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                      {playgroundTut.allTut.map(n => (
                        <span
                          key={n}
                          style={{
                            background: 'rgba(0, 200, 151, 0.15)',
                            color: 'var(--accent-success)',
                            padding: '2px 6px',
                            borderRadius: 4,
                            fontFamily: 'monospace',
                            fontSize: 12,
                            fontWeight: 700
                          }}
                        >
                          {n}
                        </span>
                      ))}
                    </div>
                  </div>
                ) : (
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    ဂဏန်း ၃ လုံး ရိုက်ထည့်ပြီး တွတ်ဂဏန်းများ (အပြန် ၅ လုံး + ကပ်သီး ၂ လုံး) ကို စမ်းသပ်ကြည့်ရှုနိုင်ပါသည်။
                  </div>
                )}
              </div>
            </div>
          </div>

        </div>

        {/* Resellers & Due Settlement Card */}
        <div className="card" style={{ marginTop: 24, border: '1px solid rgba(245, 158, 11, 0.3)' }}>
          <div className="card-header" style={{ flexWrap: 'wrap', gap: 12, justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <h2>👥 Resellers & Due Settlement (အရောင်းကိုယ်စားလှယ်များနှင့် ငွေစာရင်း)</h2>
              <div style={{ display: 'flex', gap: 8, fontSize: 12, flexWrap: 'wrap' }}>
                <span className="status-badge" style={{ background: 'rgba(99, 102, 241, 0.15)', color: '#818cf8' }}>
                  👤 {Object.keys(resellers).length} Resellers
                </span>
                <span className="status-badge" style={{ background: 'rgba(239, 68, 68, 0.15)', color: '#f87171' }}>
                  📌 Total Due: {Object.values(resellers).reduce((sum, r) => sum + (r.total_due || 0), 0).toLocaleString()} Ks
                </span>
                <span className="status-badge" style={{ background: 'rgba(16, 185, 129, 0.15)', color: '#34d399' }}>
                  💵 Total Paid: {Object.values(resellers).reduce((sum, r) => sum + (r.total_paid || 0), 0).toLocaleString()} Ks
                </span>
              </div>
            </div>
          </div>

          <div className="card-body" style={{ padding: 0 }}>
            {Object.keys(resellers).length === 0 ? (
              <div className="empty-state">
                <div className="empty-icon">👥</div>
                <p>No resellers registered yet. Add resellers via Telegram bot or /addreseller command.</p>
              </div>
            ) : (
              <table className="keys-table">
                <thead>
                  <tr>
                    <th>Reseller Name</th>
                    <th>Telegram ID</th>
                    <th>Generated / Active</th>
                    <th>Commission</th>
                    <th>Due Balance (ပေးရန်ကျန်ငွေ)</th>
                    <th>Total Paid (ပေးပြီးငွေ)</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.values(resellers).map((r) => {
                    const due = r.total_due || 0;
                    const paid = r.total_paid || 0;
                    const commission = r.total_commission || 0;
                    return (
                      <tr key={r.telegram_id}>
                        <td>
                          <strong>{r.name}</strong>
                          {r.username && <span style={{ fontSize: 11, color: 'var(--text-muted)', display: 'block' }}>@{r.username}</span>}
                        </td>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                            <code>{r.telegram_id}</code>
                            <button
                              type="button"
                              className={`copy-btn ${copiedId === `reseller-${r.telegram_id}` ? 'copied' : ''}`}
                              onClick={() => copyToClipboard(r.telegram_id, `reseller-${r.telegram_id}`)}
                              title="Copy Telegram ID"
                            >
                              {copiedId === `reseller-${r.telegram_id}` ? '✅' : '📋'}
                            </button>
                          </div>
                        </td>
                        <td>
                          <span style={{ fontSize: 13 }}>
                            🔢 {r.total_generated || 0} ထုတ် / 🟢 {r.total_activated || 0} သုံး
                          </span>
                        </td>
                        <td>
                          <span style={{ color: '#10b981', fontWeight: 600 }}>
                            {commission.toLocaleString()} Ks
                          </span>
                        </td>
                        <td>
                          <span
                            className="status-badge"
                            style={{
                              background: due > 0 ? 'rgba(239, 68, 68, 0.2)' : 'rgba(16, 185, 129, 0.15)',
                              color: due > 0 ? '#ef4444' : '#10b981',
                              fontWeight: 700,
                              fontSize: 13
                            }}
                          >
                            {due.toLocaleString()} Ks
                          </span>
                        </td>
                        <td>
                          <span style={{ color: '#6366f1', fontWeight: 600 }}>
                            {paid.toLocaleString()} Ks
                          </span>
                        </td>
                        <td>
                          <button
                            type="button"
                            className="btn btn-sm btn-primary"
                            onClick={() => handleOpenSettleModal(r)}
                            style={{ padding: '6px 12px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}
                          >
                            💳 Clear Due / ရှင်းလင်းမည်
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        </div>

        {/* Licenses Table with Filter & Search */}
        <div className="card" style={{ marginTop: 24 }}>
          <div className="card-header" style={{ flexWrap: 'wrap', gap: 12, justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <h2>🔑 License Keys ({totalKeys} ကုဒ်)</h2>
              <div style={{ display: 'flex', gap: 8, fontSize: 12 }}>
                <span className="status-badge available">🟢 Available ({availableKeys})</span>
                <span className="status-badge claimed">🔴 Claimed ({claimedKeys})</span>
                {revokedKeys > 0 && <span className="status-badge revoked">⚪ Revoked ({revokedKeys})</span>}
              </div>
            </div>

            {/* Filter Tabs & Search */}
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
              <input
                type="text"
                value={keySearch}
                onChange={e => setKeySearch(e.target.value)}
                placeholder="🔍 Search key or device..."
                style={{
                  padding: '6px 12px',
                  fontSize: 12,
                  borderRadius: 6,
                  border: '1px solid var(--border-color)',
                  background: 'var(--bg-primary)',
                  color: 'var(--text-primary)',
                  width: 180
                }}
              />
              <select
                value={keyFilter}
                onChange={e => setKeyFilter(e.target.value)}
                className="select-input"
                style={{ padding: '6px 10px', fontSize: 12, width: 'auto' }}
              >
                <option value="all">All Keys (အားလုံး)</option>
                <option value="available">🟢 Available Only</option>
                <option value="claimed">🔴 Claimed/Active Only</option>
                <option value="changeable">🔄 Device Changeable Only</option>
                <option value="locked">🔒 1-Device Only</option>
                <option value="trial_3d">⏱️ 3-Day Trial Only</option>
                <option value="one_year">⭐ 1-Year Only</option>
                <option value="lifetime">💎 Lifetime Only</option>
                <option value="revoked">⚪ Revoked Only</option>
              </select>
            </div>
          </div>

          <div className="card-body" style={{ padding: 0 }}>
            {filteredKeys.length === 0 ? (
              <div className="empty-state">
                <div className="empty-icon">🔐</div>
                <p>
                  {totalKeys === 0
                    ? 'No license keys yet. Generate your first key above!'
                    : 'No license keys match your filter criteria.'}
                </p>
              </div>
            ) : (
              <table className="keys-table">
                <thead>
                  <tr>
                    <th>CD-Key</th>
                    <th>Plan / Duration</th>
                    <th>Device Mode</th>
                    <th>Status</th>
                    <th>Active Device</th>
                    <th>Date</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredKeys.map(([keyId, keyData]) => (
                    <tr key={keyId}>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <span
                            className="key-code"
                            style={{ cursor: 'pointer' }}
                            onClick={() => copyToClipboard(keyId, `key-${keyId}`)}
                            title="Click to copy"
                          >
                            {keyId}
                          </span>
                          <button
                            type="button"
                            className={`copy-btn ${copiedId === `key-${keyId}` ? 'copied' : ''}`}
                            onClick={() => copyToClipboard(keyId, `key-${keyId}`)}
                            title="Copy CD-Key"
                          >
                            {copiedId === `key-${keyId}` ? '✅ Copied' : '📋 Copy'}
                          </button>
                        </div>
                      </td>
                      <td>
                        <span className={`duration-badge ${keyData.plan_id === 'lifetime' || keyData.duration === 'lifetime' ? 'lifetime' : keyData.plan_id === 'trial_3d' || keyData.duration === 'trial' ? 'trial' : 'custom'}`}>
                          {formatDuration(keyData.duration, keyData.plan_id)}
                        </span>
                      </td>
                      <td>
                        <span className={`device-badge ${keyData.device_changeable ? 'changeable' : 'locked'}`}>
                          {keyData.device_changeable ? '🔄 Changeable' : '🔒 1 Device'}
                        </span>
                      </td>
                      <td>
                        <span className={`status-badge ${keyData.status}`}>
                          {keyData.status === 'available' ? '🟢' : keyData.status === 'active' || keyData.status === 'claimed' ? '🔴' : '⚪'} {keyData.status}
                        </span>
                      </td>
                      <td>
                        <span className="device-text" title={keyData.claimed_by || keyData.device_fingerprint || ''}>
                          {keyData.device_model ? `${keyData.device_model}` : (keyData.claimed_by || keyData.device_fingerprint || '—')}
                          {keyData.previous_device_fingerprint && (
                            <span style={{ color: 'var(--accent-warning)', marginLeft: 4, fontWeight: 700 }} title={`Previous device: ${keyData.previous_device_fingerprint}`}>
                              (Migrated)
                            </span>
                          )}
                        </span>
                      </td>
                      <td style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                        {keyData.generated_at || keyData.created_at
                          ? new Date(keyData.generated_at || keyData.created_at).toLocaleDateString()
                          : '—'}
                      </td>
                      <td>
                        <div style={{ display: 'flex', gap: 6 }}>
                          {(keyData.status === 'claimed' || keyData.status === 'active') && (
                            <button className="btn btn-warning btn-sm" onClick={() => revokeKey(keyId)}>
                              Revoke
                            </button>
                          )}
                          <button className="btn btn-danger btn-sm" onClick={() => deleteKey(keyId)}>
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      </main>

      {/* Generated Key Popup (Single & Bulk Support) */}
      {generatedKey && (
        <div className="key-popup-overlay" onClick={() => { setGeneratedKey(null); setBulkGeneratedKeys([]); }}>
          <div className="key-popup" onClick={e => e.stopPropagation()} style={{ maxWidth: 520, width: '90%' }}>
            <div className="popup-icon">🎉</div>
            <h3>
              {bulkGeneratedKeys.length > 1
                ? `${bulkGeneratedKeys.length} CD-Keys Generated!`
                : 'Key Generated! (လိုင်စင်ကုဒ် ထွက်ပါပြီ)'}
            </h3>
            <div style={{ color: 'var(--text-secondary)', fontSize: 13, marginBottom: 14, display: 'flex', justifyContent: 'center', gap: 8, alignItems: 'center' }}>
              <span>Plan: <strong>{keyType === 'one_year' ? '1-Year Plan (၁ နှစ်)' : keyType === 'lifetime' ? 'Lifetime (တစ်သက်တာ)' : '3-Day Trial (၃ ရက်)'}</strong></span>
              <span className={`device-badge ${deviceChangeable ? 'changeable' : 'locked'}`}>
                {deviceChangeable ? '🔄 Device Changeable' : '🔒 1 Device Only'}
              </span>
            </div>

            {bulkGeneratedKeys.length > 1 ? (
              <div>
                <textarea
                  readOnly
                  value={bulkGeneratedKeys.join('\n')}
                  style={{
                    width: '100%',
                    height: 160,
                    fontFamily: 'monospace',
                    fontSize: 13,
                    padding: 10,
                    borderRadius: 8,
                    background: 'var(--bg-primary)',
                    color: 'var(--accent-primary)',
                    border: '1px solid var(--border-color)',
                    resize: 'vertical',
                    marginBottom: 12
                  }}
                />
                <div className="popup-actions" style={{ display: 'flex', gap: 8, justifyContent: 'center', flexWrap: 'wrap' }}>
                  <button
                    className={`btn ${copiedId === 'popup-bulk' ? 'btn-success' : 'btn-primary'}`}
                    onClick={() => copyToClipboard(bulkGeneratedKeys.join('\n'), 'popup-bulk')}
                  >
                    {copiedId === 'popup-bulk' ? '✅ Copied All Keys!' : `📋 Copy All Keys (${bulkGeneratedKeys.length})`}
                  </button>
                  <button
                    className="btn btn-outline"
                    style={{ background: 'rgba(0, 200, 151, 0.15)', color: 'var(--accent-success)', borderColor: 'rgba(0, 200, 151, 0.3)' }}
                    onClick={() => downloadKeysAsText(bulkGeneratedKeys, keyType, deviceChangeable)}
                  >
                    💾 Download .TXT
                  </button>
                  <button className="btn btn-outline" onClick={() => { setGeneratedKey(null); setBulkGeneratedKeys([]); }}>
                    Close
                  </button>
                </div>
              </div>
            ) : (
              <div>
                <div
                  className="generated-key"
                  onClick={() => copyToClipboard(generatedKey, 'popup-single')}
                  title="Click to copy"
                >
                  {generatedKey}
                </div>
                <p className="hint">Click the key to copy it to clipboard</p>
                <div className="popup-actions">
                  <button
                    className={`btn ${copiedId === 'popup-single' ? 'btn-success' : 'btn-primary'}`}
                    onClick={() => copyToClipboard(generatedKey, 'popup-single')}
                  >
                    {copiedId === 'popup-single' ? '✅ Copied CD-Key!' : '📋 Copy CD-Key'}
                  </button>
                  <button className="btn btn-outline" onClick={() => { setGeneratedKey(null); setBulkGeneratedKeys([]); }}>
                    Close
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Reseller Due Settlement Modal */}
      {settleModalReseller && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0, 0, 0, 0.75)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            backdropFilter: 'blur(4px)',
            padding: 16
          }}
        >
          <div
            className="card"
            style={{
              width: '100%',
              maxWidth: 480,
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.5), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
              border: '1px solid var(--border-color)',
              borderRadius: 12,
              overflow: 'hidden'
            }}
          >
            <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, fontSize: 16 }}>
                💳 Clear Reseller Due (ငွေစာရင်း ရှင်းလင်းခြင်း)
              </h3>
              <button
                type="button"
                onClick={handleCloseSettleModal}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--text-muted)',
                  cursor: 'pointer',
                  fontSize: 20
                }}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleConfirmSettleDue}>
              <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <div
                  style={{
                    background: 'var(--bg-primary)',
                    padding: 14,
                    borderRadius: 8,
                    border: '1px solid var(--border-color)'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                    <span style={{ color: 'var(--text-muted)' }}>Reseller Name:</span>
                    <strong>{settleModalReseller.name}</strong>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                    <span style={{ color: 'var(--text-muted)' }}>Telegram ID:</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <code>{settleModalReseller.telegram_id}</code>
                      <button
                        type="button"
                        className={`copy-btn ${copiedId === `modal-${settleModalReseller.telegram_id}` ? 'copied' : ''}`}
                        style={{ padding: '1px 6px', fontSize: 10 }}
                        onClick={() => copyToClipboard(settleModalReseller.telegram_id, `modal-${settleModalReseller.telegram_id}`)}
                        title="Copy Telegram ID"
                      >
                        {copiedId === `modal-${settleModalReseller.telegram_id}` ? '✅' : '📋'}
                      </button>
                    </div>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                    <span style={{ color: 'var(--text-muted)' }}>Current Due (ပေးရန်ကျန်ငွေ):</span>
                    <strong style={{ color: '#ef4444', fontSize: 15 }}>
                      {(settleModalReseller.total_due || 0).toLocaleString()} Ks
                    </strong>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', paddingTop: 6, borderTop: '1px dashed var(--border-color)' }}>
                    <span style={{ color: 'var(--text-muted)' }}>Remaining Due after Settle:</span>
                    <strong style={{
                      color: Math.max(0, (settleModalReseller.total_due || 0) - (parseInt(manualSettleAmount, 10) || 0)) === 0 ? 'var(--accent-success)' : 'var(--accent-warning)',
                      fontSize: 14,
                      fontWeight: 700
                    }}>
                      {Math.max(0, (settleModalReseller.total_due || 0) - (parseInt(manualSettleAmount, 10) || 0)).toLocaleString()} Ks
                    </strong>
                  </div>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
                    💵 Settlement Amount (ရှင်းလင်းမည့် ငွေပမာဏ - ကျပ်):
                  </label>
                  <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                    <input
                      type="number"
                      value={manualSettleAmount}
                      onChange={(e) => setManualSettleAmount(e.target.value)}
                      placeholder="ဥပမာ: 35000"
                      required
                      className="text-input"
                      style={{
                        flex: 1,
                        fontSize: 16,
                        fontWeight: 'bold',
                        padding: '10px 14px',
                        border: '1px solid var(--primary)',
                        borderRadius: 8
                      }}
                    />
                    <button
                      type="button"
                      className="btn btn-outline"
                      style={{ whiteSpace: 'nowrap', fontSize: 12 }}
                      onClick={() => setManualSettleAmount(String(settleModalReseller.total_due || 0))}
                    >
                      Full Due (အပြည့်)
                    </button>
                  </div>
                  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                    {[50000, 100000, 200000, 500000].map((preset) => (
                      <button
                        key={preset}
                        type="button"
                        className="btn btn-sm"
                        style={{
                          fontSize: 11,
                          padding: '4px 8px',
                          background: 'var(--bg-primary)',
                          border: '1px solid var(--border-color)'
                        }}
                        onClick={() => setManualSettleAmount(String(preset))}
                      >
                        +{preset.toLocaleString()} Ks
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 6 }}>
                    📝 Notes / Payment Reference (မှတ်ချက် - စိတ်ကြိုက်):
                  </label>
                  <input
                    type="text"
                    value={settleNotes}
                    onChange={(e) => setSettleNotes(e.target.value)}
                    placeholder="e.g. KPay / Wave / Cash payment receipt"
                    className="text-input"
                    style={{ width: '100%', padding: '8px 12px', fontSize: 13 }}
                  />
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 8 }}>
                  <button
                    type="button"
                    className="btn btn-outline"
                    onClick={handleCloseSettleModal}
                    disabled={settlingDue}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn btn-primary"
                    disabled={settlingDue}
                    style={{ minWidth: 140 }}
                  >
                    {settlingDue ? 'Settling...' : '✅ Confirm Settlement'}
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Toast */}
      {toast && (
        <div className={`toast ${toast.type}`}>
          {toast.type === 'success' ? '✅' : '❌'} {toast.message}
        </div>
      )}
    </div>
  );
}

export default App;
