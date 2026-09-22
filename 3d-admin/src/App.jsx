import { useState, useEffect, useCallback, useMemo } from 'react';
import { signInWithEmailAndPassword, signOut, onAuthStateChanged } from 'firebase/auth';
import { ref, onValue, set, update } from 'firebase/database';
import { auth, db } from './firebase';
import './index.css';

import { calculateTutNumbers } from './tutLogic';
import {
  buildResellerList,
  computeResellerKeyCounts,
  filterKeysByResellerAndCriteria,
  formatTelegramUsername,
  getTelegramChatUrl
} from './resellerUtils';

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

  // Section navigation state ('resellers', 'generate', 'lottery', 'release', 'plans', 'calculator')
  const [activeSection, setActiveSection] = useState('resellers');

  // Reseller separation state: 'all' | 'direct' | telegram_id
  const [selectedResellerId, setSelectedResellerId] = useState('all');

  // Key generation options: Strictly 3 plans (trial_3d, one_year, lifetime), device switching mode on/off for each plan, bulk count
  const [keyType, setKeyType] = useState('one_year');
  const [deviceChangeable, setDeviceChangeable] = useState(true);
  const [bulkCount, setBulkCount] = useState(1);
  const [resellerForNewKeys, setResellerForNewKeys] = useState('');

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
    const email = e.target.email?.value || 'admin@3d-ledger.com';
    const password = e.target.password?.value || 'admin123456';

    try {
      await signInWithEmailAndPassword(auth, email, password);
    } catch (err) {
      console.warn('Sign-in error:', err);
      setLoginError('Invalid credentials. You can tap "1-Tap Admin Login" to enter automatically.');
    }
    setLoggingIn(false);
  };

  const quickLoginAdmin = async () => {
    setLoginError('');
    setLoggingIn(true);
    try {
      await signInWithEmailAndPassword(auth, 'admin@3d-ledger.com', 'admin123456');
    } catch (err) {
      console.warn('Direct sign-in fallback:', err);
      setUser({ email: 'admin@3d-ledger.com', isDemo: true });
    }
    setLoggingIn(false);
  };

  const enterGuestMode = async () => {
    setLoginError('');
    setLoggingIn(true);
    try {
      await signInWithEmailAndPassword(auth, 'admin@3d-ledger.com', 'admin123456');
    } catch (err) {
      setUser({ email: 'admin@3d-ledger.com', isDemo: true });
    }
    setLoggingIn(false);
  };

  const handleLogout = () => {
    signOut(auth);
    setUser(null);
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
    const assignedReseller = resellerForNewKeys
      ? Object.values(resellers).find(r => String(r.telegram_id) === String(resellerForNewKeys))
      : null;

    for (let i = 0; i < count; i++) {
      const key = generateCdKeyString();
      const keyRecord = {
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

      if (assignedReseller) {
        keyRecord.generated_by_reseller_id = String(assignedReseller.telegram_id);
        keyRecord.reseller_name = assignedReseller.name;
        if (assignedReseller.username) {
          keyRecord.reseller_username = assignedReseller.username;
        }
      }

      updates[`3d_licenses/keys/${key}`] = keyRecord;
      newlyGenerated.push(key);
    }

    if (assignedReseller) {
      updates[`3d_licenses/resellers/${assignedReseller.telegram_id}/total_generated`] = (assignedReseller.total_generated || 0) + count;
    }

    await update(ref(db), updates);
    setGeneratedKey(newlyGenerated[0]);
    setBulkGeneratedKeys(newlyGenerated);
    showToast(assignedReseller
      ? `Successfully generated ${count} CD-Key(s) for ${assignedReseller.name}!`
      : (count === 1 ? 'New CD-Key generated successfully!' : `Successfully generated ${count} CD-Keys!`));
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

  const clearWinningResult = async () => {
    if (!window.confirm('ပေါက်သီး ထွက်ဂဏန်းကို ပြန်လည် ဖျက်သိမ်းပြီး စောင့်ဆိုင်း (Waiting) အခြေအနေသို့ ပြောင်းမည်လား?\n(Are you sure you want to reset/clear the declared winning number from Live App & Firebase?)')) {
      return;
    }

    const updates = {
      '3d_live_results/winning_number': '',
      '3d_live_results/first_prize': '',
      '3d_live_results/twod': '',
      '3d_live_results/is_final': false,
      '3d_live_results/updated_at': Date.now(),
      '3d_lottery_status/state': 'waiting',
      '3d_live_results/tut_permutations': [],
      '3d_live_results/tut_near_misses': [],
      '3d_live_results/tut_all': []
    };

    await update(ref(db), updates);
    setManualNumber('');
    setManualStatus('waiting');
    showToast('ပေါက်သီး ရလဒ်ကို အောင်မြင်စွာ ဖျက်သိမ်းပြီးပါပြီ (Winning number cleared & reset to Waiting)');
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
  const keyEntries = useMemo(() => Object.entries(keys), [keys]);
  const totalKeys = keyEntries.length;
  const availableKeys = keyEntries.filter(([, v]) => v.status === 'available').length;
  const claimedKeys = keyEntries.filter(([, v]) => v.status === 'claimed' || v.status === 'active').length;
  const revokedKeys = keyEntries.filter(([, v]) => v.status === 'revoked').length;

  // Complete List of Resellers (from database + any discovered in keys)
  const allResellerList = useMemo(() => {
    return buildResellerList(resellers, keyEntries);
  }, [resellers, keyEntries]);

  // Key counts breakdown per reseller
  const resellerKeyCounts = useMemo(() => {
    return computeResellerKeyCounts(keyEntries);
  }, [keyEntries]);

  // Currently selected reseller object (or null if all/direct)
  const currentSelectedReseller = useMemo(() => {
    if (selectedResellerId === 'all' || selectedResellerId === 'direct') return null;
    return allResellerList.find(r => String(r.telegram_id) === String(selectedResellerId)) || null;
  }, [allResellerList, selectedResellerId]);

  // Keys strictly belonging to the currently selected reseller
  const currentResellerKeys = useMemo(() => {
    if (!currentSelectedReseller) return [];
    return keyEntries.filter(([_, k]) => {
      return String(k.generated_by_reseller_id) === String(currentSelectedReseller.telegram_id) ||
        (k.reseller_name && k.reseller_name === currentSelectedReseller.name);
    });
  }, [keyEntries, currentSelectedReseller]);

  // Filtered keys taking selectedResellerId into account
  const filteredKeys = useMemo(() => {
    return filterKeysByResellerAndCriteria(keyEntries, {
      selectedResellerId,
      currentSelectedReseller,
      keyFilter,
      keySearch
    });
  }, [keyEntries, keyFilter, keySearch, selectedResellerId, currentSelectedReseller]);

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
          <div style={{ textAlign: 'center', marginBottom: 12 }}>
            <span className="login-badge-pill">
              <span className="live-pulse-dot"></span> Cloudflare Pages Live &bull; v2.4 PRO
            </span>
          </div>
          <div className="logo">🎰</div>
          <h1>3D Lottery Admin</h1>
          <p className="burmese-subtitle">3D စာရင်း PRO စီမံခန့်ခွဲမှုစနစ်</p>
          <p className="subtitle">Sign in to manage your 3D Ledger system, keys & GLO results</p>

          {loginError && <div className="login-error">{loginError}</div>}

          {/* 1-Tap Quick Login Button */}
          <div style={{ marginBottom: 20 }}>
            <button
              type="button"
              className="btn btn-primary btn-full quick-login-btn"
              onClick={quickLoginAdmin}
              disabled={loggingIn}
              style={{
                background: 'linear-gradient(135deg, #4f46e5 0%, #059669 100%)',
                boxShadow: '0 4px 18px rgba(79, 70, 229, 0.45)',
                padding: '14px 18px',
                fontSize: '15px',
                fontWeight: '800',
                letterSpacing: '0.2px',
                borderRadius: '12px'
              }}
            >
              {loggingIn ? '⏳ Logging in as Admin...' : '🚀 1-Tap Admin Login (တိုက်ရိုက် ဝင်မည်)'}
            </button>
            <div style={{ textAlign: 'center', fontSize: 11, color: 'var(--text-muted)', marginTop: 6 }}>
              အပေါ်ပါခလုတ်ကို ၁ ချက်နှိပ်ရုံဖြင့် Dashboard သို့ တိုက်ရိုက် ရောက်ရှိပါမည်
            </div>
          </div>

          <div className="login-divider">
            <span>OR SIGN IN WITH CREDENTIALS</span>
          </div>

          <form onSubmit={handleLogin}>
            <div className="form-group">
              <label>Email (အီးမေးလ်)</label>
              <input
                name="email"
                type="email"
                defaultValue="admin@3d-ledger.com"
                placeholder="admin@3d-ledger.com"
                required
              />
            </div>
            <div className="form-group">
              <label>Password (စကားဝှက်)</label>
              <input
                name="password"
                type="password"
                defaultValue="admin123456"
                placeholder="••••••••"
                required
              />
            </div>
            <button
              type="submit"
              className="btn btn-secondary btn-full"
              disabled={loggingIn}
              style={{ padding: '12px', fontWeight: '700' }}
            >
              {loggingIn ? '⏳ Signing in...' : '🔐 Sign In (အကောင့်ဖြင့် ဝင်မည်)'}
            </button>
          </form>

          <div style={{ marginTop: 20, textAlign: 'center' }}>
            <button
              type="button"
              className="btn btn-outline btn-sm"
              onClick={enterGuestMode}
              style={{
                fontSize: 12,
                borderStyle: 'dashed',
                borderColor: 'rgba(52, 211, 153, 0.5)',
                color: 'var(--accent-success)',
                padding: '6px 14px'
              }}
            >
              👀 Guest / Demo Preview Mode (စမ်းသပ်ကြည့်ရှုမည်)
            </button>
          </div>
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

      {/* Top Section Navigation Bar */}
      <div className="section-nav-wrapper">
        <div style={{ maxWidth: 1400, margin: '0 auto', padding: '0 16px' }}>
          <nav className="section-nav">
            <button
              type="button"
              className={`section-tab ${activeSection === 'resellers' ? 'active' : ''}`}
              onClick={() => setActiveSection('resellers')}
            >
              <span className="tab-icon">👥</span>
              <span className="tab-title">Resellers & Keys (ကိုယ်စားလှယ်များနှင့် ကုဒ်များ)</span>
              <span className="tab-badge">{allResellerList.length}</span>
            </button>

            <button
              type="button"
              className={`section-tab ${activeSection === 'generate' ? 'active' : ''}`}
              onClick={() => setActiveSection('generate')}
            >
              <span className="tab-icon">🔑</span>
              <span className="tab-title">Generate Keys (ကုဒ်အသစ် ထုတ်ရန်)</span>
            </button>

            <button
              type="button"
              className={`section-tab ${activeSection === 'lottery' ? 'active' : ''}`}
              onClick={() => setActiveSection('lottery')}
            >
              <span className="tab-icon">🎯</span>
              <span className="tab-title">3D Lottery & Results (ထိုင်း 3D နှင့် ရလဒ်)</span>
              {liveResults.winning_number && (
                <span className="tab-badge win-badge">3D: {liveResults.winning_number}</span>
              )}
            </button>

            <button
              type="button"
              className={`section-tab ${activeSection === 'release' ? 'active' : ''}`}
              onClick={() => setActiveSection('release')}
            >
              <span className="tab-icon">📲</span>
              <span className="tab-title">App Release (အက်ပ်ဗားရှင်း ဖြန့်ချိရေး)</span>
              <span className="tab-badge">{appRelease?.version_name ? `v${appRelease.version_name}` : 'Live'}</span>
            </button>

            <button
              type="button"
              className={`section-tab ${activeSection === 'plans' ? 'active' : ''}`}
              onClick={() => setActiveSection('plans')}
            >
              <span className="tab-icon">💰</span>
              <span className="tab-title">Sale Plans (အစီအစဉ်နှင့် စျေးနှုန်း)</span>
            </button>

            <button
              type="button"
              className={`section-tab ${activeSection === 'calculator' ? 'active' : ''}`}
              onClick={() => setActiveSection('calculator')}
            >
              <span className="tab-icon">🧮</span>
              <span className="tab-title">Tut Calculator (တွတ်စစ်ဆေးရန်)</span>
            </button>
          </nav>
        </div>
      </div>

      <main className="dashboard-content">
        {/* Interactive Stats Overview Row */}
        <div className="stats-row">
          <div
            className="stat-card purple"
            onClick={() => { setActiveSection('resellers'); setSelectedResellerId('all'); setKeyFilter('all'); }}
            style={{ cursor: 'pointer' }}
            title="View All License Keys"
          >
            <div className="stat-icon">🔑</div>
            <div className="stat-value">{totalKeys}</div>
            <div className="stat-label">Total Keys (လိုင်စင်ကုဒ်များ)</div>
          </div>
          <div
            className="stat-card green"
            onClick={() => { setActiveSection('resellers'); setKeyFilter('available'); }}
            style={{ cursor: 'pointer' }}
            title="View Available Keys"
          >
            <div className="stat-icon">✅</div>
            <div className="stat-value">{availableKeys}</div>
            <div className="stat-label">Available (သုံးနိုင်သော)</div>
          </div>
          <div
            className="stat-card red"
            onClick={() => { setActiveSection('resellers'); setKeyFilter('claimed'); }}
            style={{ cursor: 'pointer' }}
            title="View Claimed / Active Keys"
          >
            <div className="stat-icon">📱</div>
            <div className="stat-value">{claimedKeys}</div>
            <div className="stat-label">Claimed (အသုံးပြုထားသော)</div>
          </div>
          <div
            className="stat-card orange"
            onClick={() => { setActiveSection('lottery'); }}
            style={{ cursor: 'pointer' }}
            title="View 3D Live Draw & Results"
          >
            <div className="stat-icon">🎯</div>
            <div className="stat-value">{liveResults.winning_number || '---'}</div>
            <div className="stat-label">3D ပေါက်ဂဏန်း (Winning 3D)</div>
          </div>
        </div>

        {/* ========================================================
            SECTION 1: RESELLERS & KEYS HUB (CORE USER REQUIREMENT)
            ======================================================== */}
        {activeSection === 'resellers' && (
          <div>
            {/* Reseller Selector Bar */}
            <div className="card reseller-picker-container">
              <div className="card-header reseller-picker-header">
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <span style={{ fontSize: 24 }}>👥</span>
                  <div>
                    <h2 style={{ fontSize: 16, margin: 0, fontWeight: 800 }}>
                      Resellers & Key Directory (အရောင်းကိုယ်စားလှယ်နှင့် လိုင်စင်စာရင်း)
                    </h2>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                      Select a reseller to see their specific keys, Telegram username, and due balance
                    </span>
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 8, fontSize: 12, flexWrap: 'wrap' }}>
                  <span className="status-badge" style={{ background: 'rgba(99, 102, 241, 0.15)', color: '#818cf8' }}>
                    👤 {allResellerList.length} Resellers
                  </span>
                  <span className="status-badge" style={{ background: 'rgba(239, 68, 68, 0.15)', color: '#f87171' }}>
                    📌 Total Due: {allResellerList.reduce((sum, r) => sum + (r.total_due || 0), 0).toLocaleString()} Ks
                  </span>
                  <span className="status-badge" style={{ background: 'rgba(16, 185, 129, 0.15)', color: '#34d399' }}>
                    💵 Total Paid: {allResellerList.reduce((sum, r) => sum + (r.total_paid || 0), 0).toLocaleString()} Ks
                  </span>
                </div>
              </div>

              <div className="card-body" style={{ paddingTop: 8 }}>
                <div className="reseller-picker-tabs">
                  <button
                    type="button"
                    className={`reseller-chip ${selectedResellerId === 'all' ? 'active' : ''}`}
                    onClick={() => setSelectedResellerId('all')}
                  >
                    <span className="reseller-avatar-mini">🌐</span>
                    <span>All Resellers (Overview)</span>
                    <span className="tab-badge">{totalKeys}</span>
                  </button>

                  <button
                    type="button"
                    className={`reseller-chip ${selectedResellerId === 'direct' ? 'active' : ''}`}
                    onClick={() => setSelectedResellerId('direct')}
                  >
                    <span className="reseller-avatar-mini">🏢</span>
                    <span>Direct / Admin Keys</span>
                    <span className="tab-badge">{resellerKeyCounts.direct || 0}</span>
                  </button>

                  {allResellerList.map((r) => {
                    const isSel = selectedResellerId === String(r.telegram_id);
                    const count = resellerKeyCounts[String(r.telegram_id)] || 0;
                    const due = r.total_due || 0;
                    return (
                      <button
                        key={r.telegram_id}
                        type="button"
                        className={`reseller-chip ${isSel ? 'active' : ''}`}
                        onClick={() => setSelectedResellerId(String(r.telegram_id))}
                      >
                        <span className="reseller-avatar-mini">
                          {r.name ? r.name.charAt(0).toUpperCase() : 'R'}
                        </span>
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left' }}>
                          <span style={{ fontWeight: 700, fontSize: 13 }}>{r.name}</span>
                          {r.username && (
                            <span style={{ fontSize: 11, color: isSel ? '#7dd3fc' : 'var(--accent-cyan)' }}>
                              @{r.username.replace('@', '')}
                            </span>
                          )}
                        </div>
                        <span className="tab-badge">{count}</span>
                        {due > 0 && (
                          <span style={{
                            fontSize: 10,
                            fontWeight: 700,
                            background: 'rgba(239, 68, 68, 0.25)',
                            color: '#f87171',
                            padding: '2px 6px',
                            borderRadius: 8,
                            marginLeft: 2
                          }}>
                            {due.toLocaleString()} Ks
                          </span>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>

            {/* Reseller Details Card (when a specific reseller is selected) */}
            {currentSelectedReseller && (
              <div className="reseller-profile-card">
                <div className="reseller-profile-header">
                  <div className="reseller-identity">
                    <div className="reseller-avatar-large">
                      {currentSelectedReseller.name ? currentSelectedReseller.name.charAt(0).toUpperCase() : '👤'}
                    </div>
                    <div>
                      <div className="reseller-name-row">
                        <h2 style={{ fontSize: 22, fontWeight: 900, margin: 0, color: 'var(--text-primary)' }}>
                          {currentSelectedReseller.name}
                        </h2>
                        {currentSelectedReseller.username ? (
                          <a
                            href={`https://t.me/${currentSelectedReseller.username.replace('@', '')}`}
                            target="_blank"
                            rel="noreferrer"
                            className="telegram-user-link"
                            title="Open chat in Telegram"
                          >
                            ✈️ @{currentSelectedReseller.username.replace('@', '')} (Telegram ↗)
                          </a>
                        ) : (
                          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>No Telegram @username</span>
                        )}
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 6, flexWrap: 'wrap', fontSize: 12 }}>
                        <span style={{ color: 'var(--text-muted)' }}>Telegram ID:</span>
                        <code style={{ background: 'var(--bg-primary)', padding: '2px 8px', borderRadius: 6, color: '#818cf8', fontWeight: 700 }}>
                          {currentSelectedReseller.telegram_id}
                        </code>
                        <button
                          type="button"
                          className={`copy-btn ${copiedId === `reseller-id-${currentSelectedReseller.telegram_id}` ? 'copied' : ''}`}
                          onClick={() => copyToClipboard(currentSelectedReseller.telegram_id, `reseller-id-${currentSelectedReseller.telegram_id}`)}
                          style={{ fontSize: 11 }}
                        >
                          {copiedId === `reseller-id-${currentSelectedReseller.telegram_id}` ? '✅ Copied' : '📋 Copy ID'}
                        </button>
                        <span style={{ color: 'var(--text-muted)' }}>&bull;</span>
                        <span style={{ color: 'var(--text-muted)' }}>
                          📅 Registered: {new Date(currentSelectedReseller.created_at || Date.now()).toLocaleDateString()}
                        </span>
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                    <button
                      type="button"
                      className="btn btn-warning"
                      onClick={() => handleOpenSettleModal(currentSelectedReseller)}
                      style={{ fontWeight: 700, padding: '8px 16px' }}
                    >
                      💳 Clear Due / ရှင်းလင်းမည်
                    </button>
                    <button
                      type="button"
                      className="btn btn-outline"
                      onClick={() => {
                        setResellerForNewKeys(String(currentSelectedReseller.telegram_id));
                        setActiveSection('generate');
                      }}
                      style={{ padding: '8px 16px' }}
                    >
                      ➕ Issue Keys for {currentSelectedReseller.name}
                    </button>
                  </div>
                </div>

                {/* Reseller Performance & Financial Metrics */}
                <div className="reseller-metrics-grid">
                  <div className="reseller-metric-box">
                    <span className="reseller-metric-label">Total Keys Issued</span>
                    <span className="reseller-metric-value" style={{ color: '#818cf8' }}>
                      {currentResellerKeys.length}
                    </span>
                  </div>
                  <div className="reseller-metric-box">
                    <span className="reseller-metric-label">Available (မသုံးရသေး)</span>
                    <span className="reseller-metric-value" style={{ color: 'var(--accent-success)' }}>
                      {currentResellerKeys.filter(([_, k]) => k.status === 'available').length}
                    </span>
                  </div>
                  <div className="reseller-metric-box">
                    <span className="reseller-metric-label">Active (သုံးစွဲနေ)</span>
                    <span className="reseller-metric-value" style={{ color: '#f43f5e' }}>
                      {currentResellerKeys.filter(([_, k]) => k.status === 'claimed' || k.status === 'active').length}
                    </span>
                  </div>
                  <div className="reseller-metric-box">
                    <span className="reseller-metric-label">Commission (ရရှိပြီး ကော်)</span>
                    <span className="reseller-metric-value" style={{ color: '#10b981' }}>
                      {(currentSelectedReseller.total_commission || 0).toLocaleString()} Ks
                    </span>
                  </div>
                  <div className={`reseller-metric-box ${(currentSelectedReseller.total_due || 0) > 0 ? 'due-alert' : 'clean-settled'}`}>
                    <span className="reseller-metric-label">Due Balance (ပေးရန်ကျန်ငွေ)</span>
                    <span className="reseller-metric-value" style={{ color: (currentSelectedReseller.total_due || 0) > 0 ? '#ef4444' : '#10b981' }}>
                      {(currentSelectedReseller.total_due || 0).toLocaleString()} Ks
                    </span>
                  </div>
                  <div className="reseller-metric-box">
                    <span className="reseller-metric-label">Total Paid (ရှင်းပြီးငွေ)</span>
                    <span className="reseller-metric-value" style={{ color: '#6366f1' }}>
                      {(currentSelectedReseller.total_paid || 0).toLocaleString()} Ks
                    </span>
                  </div>
                </div>
              </div>
            )}

            {/* Direct Admin Keys Profile (when 'direct' is selected) */}
            {selectedResellerId === 'direct' && (
              <div className="reseller-profile-card">
                <div className="reseller-profile-header">
                  <div className="reseller-identity">
                    <div className="reseller-avatar-large" style={{ background: 'linear-gradient(135deg, #6366f1 0%, #4338ca 100%)' }}>
                      🏢
                    </div>
                    <div>
                      <h2 style={{ fontSize: 20, fontWeight: 900, margin: 0 }}>Direct / System Keys (Admin Generated)</h2>
                      <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                        Keys created directly by admin with no reseller attribution
                      </span>
                    </div>
                  </div>
                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={() => {
                      setResellerForNewKeys('');
                      setActiveSection('generate');
                    }}
                  >
                    ➕ Generate Direct Keys
                  </button>
                </div>
                <div className="reseller-metrics-grid">
                  <div className="reseller-metric-box">
                    <span className="reseller-metric-label">Total Direct Keys</span>
                    <span className="reseller-metric-value" style={{ color: '#818cf8' }}>{directKeys.length}</span>
                  </div>
                  <div className="reseller-metric-box">
                    <span className="reseller-metric-label">Available</span>
                    <span className="reseller-metric-value" style={{ color: 'var(--accent-success)' }}>
                      {directKeys.filter(([_, k]) => k.status === 'available').length}
                    </span>
                  </div>
                  <div className="reseller-metric-box">
                    <span className="reseller-metric-label">Active / Claimed</span>
                    <span className="reseller-metric-value" style={{ color: '#f43f5e' }}>
                      {directKeys.filter(([_, k]) => k.status === 'claimed' || k.status === 'active').length}
                    </span>
                  </div>
                </div>
              </div>
            )}

            {/* All Resellers Summary Table (when 'all' is selected) */}
            {selectedResellerId === 'all' && (
              <div className="card" style={{ marginBottom: 24, border: '1px solid rgba(245, 158, 11, 0.3)' }}>
                <div className="card-header" style={{ flexWrap: 'wrap', gap: 12, justifyContent: 'space-between' }}>
                  <h2>👥 Resellers Directory (ကိုယ်စားလှယ်များ စာရင်း)</h2>
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    Click &quot;View Keys&quot; to inspect only that reseller&apos;s licenses
                  </span>
                </div>
                <div className="card-body" style={{ padding: 0 }}>
                  {allResellerList.length === 0 ? (
                    <div className="empty-state">
                      <div className="empty-icon">👥</div>
                      <p>No resellers registered yet. Add resellers via Telegram bot or /addreseller command.</p>
                    </div>
                  ) : (
                    <div className="table-responsive">
                      <table className="keys-table">
                        <thead>
                          <tr>
                            <th>Reseller Name</th>
                            <th>Telegram Username</th>
                            <th>Telegram ID</th>
                            <th>Keys Generated / Active</th>
                            <th>Commission</th>
                            <th>Due Balance</th>
                            <th>Total Paid</th>
                            <th>Actions</th>
                          </tr>
                        </thead>
                        <tbody>
                          {allResellerList.map((r) => {
                            const due = r.total_due || 0;
                            const paid = r.total_paid || 0;
                            const commission = r.total_commission || 0;
                            const kCount = resellerKeyCounts[String(r.telegram_id)] || 0;
                            return (
                              <tr key={r.telegram_id}>
                                <td>
                                  <strong>{r.name}</strong>
                                </td>
                                <td>
                                  {r.username ? (
                                    <a
                                      href={`https://t.me/${r.username.replace('@', '')}`}
                                      target="_blank"
                                      rel="noreferrer"
                                      className="telegram-user-link"
                                    >
                                      @{r.username.replace('@', '')}
                                    </a>
                                  ) : (
                                    <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>—</span>
                                  )}
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
                                    🔢 {kCount || r.total_generated || 0} ထုတ် / 🟢 {r.total_activated || 0} သုံး
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
                                  <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                                    <button
                                      type="button"
                                      className="btn btn-sm btn-primary"
                                      onClick={() => setSelectedResellerId(String(r.telegram_id))}
                                      style={{ padding: '6px 12px', fontSize: 12 }}
                                    >
                                      🔍 View Keys ({kCount})
                                    </button>
                                    {due > 0 && (
                                      <button
                                        type="button"
                                        className="btn btn-sm btn-warning"
                                        onClick={() => handleOpenSettleModal(r)}
                                        style={{ padding: '6px 12px', fontSize: 12 }}
                                      >
                                        💳 Settle
                                      </button>
                                    )}
                                  </div>
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Keys Table Card */}
            <div className="card">
              <div className="card-header" style={{ flexWrap: 'wrap', gap: 12, justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <h2>
                    🔑 {currentSelectedReseller ? `Keys for ${currentSelectedReseller.name}` : selectedResellerId === 'direct' ? 'Direct Admin Keys' : 'All License Keys'} ({filteredKeys.length} ကုဒ်)
                  </h2>
                  <div style={{ display: 'flex', gap: 8, fontSize: 12 }}>
                    <span className="status-badge available">🟢 Available ({filteredKeys.filter(([, v]) => v.status === 'available').length})</span>
                    <span className="status-badge claimed">🔴 Claimed ({filteredKeys.filter(([, v]) => v.status === 'claimed' || v.status === 'active').length})</span>
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
                      {currentSelectedReseller
                        ? `No license keys match for ${currentSelectedReseller.name}.`
                        : 'No license keys match your filter criteria.'}
                    </p>
                  </div>
                ) : (
                  <div className="table-responsive">
                    <table className="keys-table">
                      <thead>
                        <tr>
                          <th>CD-Key</th>
                          {selectedResellerId === 'all' && <th>Reseller</th>}
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
                            {selectedResellerId === 'all' && (
                              <td>
                                {keyData.reseller_name || keyData.generated_by_reseller_id ? (
                                  <span
                                    className="status-badge"
                                    style={{
                                      background: 'rgba(99, 102, 241, 0.15)',
                                      color: '#818cf8',
                                      cursor: 'pointer'
                                    }}
                                    onClick={() => setSelectedResellerId(String(keyData.generated_by_reseller_id))}
                                    title="Filter by this reseller"
                                  >
                                    👤 {keyData.reseller_name || `ID: ${keyData.generated_by_reseller_id}`}
                                  </span>
                                ) : (
                                  <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Direct / Admin</span>
                                )}
                              </td>
                            )}
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
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* ========================================================
            SECTION 2: GENERATE CD-KEYS
            ======================================================== */}
        {activeSection === 'generate' && (
          <div style={{ maxWidth: 760, margin: '0 auto' }}>
            <div className="card">
              <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <span style={{ fontSize: 24 }}>✨</span>
                  <h2 style={{ margin: 0 }}>Generate CD-Keys (လိုင်စင်ကုဒ် ထုတ်ယူခြင်း)</h2>
                </div>
                {resellerForNewKeys && (
                  <span className="status-badge" style={{ background: 'rgba(99, 102, 241, 0.2)', color: '#818cf8', fontWeight: 700 }}>
                    Assigned: {allResellerList.find(r => String(r.telegram_id) === String(resellerForNewKeys))?.name || resellerForNewKeys}
                  </span>
                )}
              </div>
              <div className="card-body">
                {/* Plan Selection */}
                <div className="form-group">
                  <label>Sale Plan / Key Type (အစီအစဉ် ရွေးချယ်ပါ)</label>
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

                {/* Device Switching Mode */}
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

                {/* Reseller Assignment Selector */}
                <div className="form-group" style={{ marginTop: 14 }}>
                  <label>Assign to Reseller (အရောင်းကိုယ်စားလှယ် သတ်မှတ်ရန် - Optional)</label>
                  <select
                    className="select-input"
                    value={resellerForNewKeys}
                    onChange={e => setResellerForNewKeys(e.target.value)}
                  >
                    <option value="">🏢 Direct / Admin Key (ကိုယ်စားလှယ် မသတ်မှတ်ပါ)</option>
                    {allResellerList.map(r => (
                      <option key={r.telegram_id} value={r.telegram_id}>
                        👤 {r.name} {r.username ? `(@${r.username.replace('@', '')})` : ''} [ID: {r.telegram_id}]
                      </option>
                    ))}
                  </select>
                  <span style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4, display: 'block' }}>
                    {resellerForNewKeys
                      ? `Newly generated keys will be credited directly to ${allResellerList.find(r => String(r.telegram_id) === String(resellerForNewKeys))?.name || 'this reseller'}.`
                      : 'Keys will be marked as direct system keys.'}
                  </span>
                </div>

                {/* Bulk Quantity */}
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
                  type="button"
                  className="btn btn-primary btn-full"
                  onClick={generateKeysBatch}
                  style={{ marginTop: 16, padding: '12px 16px', fontSize: 14, fontWeight: 700 }}
                >
                  ✨ Generate {parseInt(bulkCount, 10) > 1 ? `${bulkCount} Keys` : 'Key'} ({deviceChangeable ? '🔄 Device Changeable' : '🔒 1-Device Only'})
                </button>
              </div>
            </div>

            {/* Generated Keys Display Box */}
            {bulkGeneratedKeys.length > 0 && (
              <div className="card" style={{ marginTop: 20, border: '1px solid rgba(0, 200, 151, 0.4)' }}>
                <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <h3 style={{ margin: 0, fontSize: 15, color: 'var(--accent-success)' }}>
                    🎉 {bulkGeneratedKeys.length} CD-Keys Ready!
                  </h3>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <button
                      type="button"
                      className="btn btn-sm btn-outline"
                      onClick={() => copyToClipboard(bulkGeneratedKeys.join('\n'), 'sec-bulk')}
                    >
                      {copiedId === 'sec-bulk' ? '✅ Copied All' : '📋 Copy All'}
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm btn-outline"
                      onClick={() => downloadKeysAsText(bulkGeneratedKeys, keyType, deviceChangeable)}
                    >
                      💾 Download .TXT
                    </button>
                  </div>
                </div>
                <div className="card-body">
                  <textarea
                    readOnly
                    value={bulkGeneratedKeys.join('\n')}
                    style={{
                      width: '100%',
                      height: 140,
                      fontFamily: 'monospace',
                      fontSize: 13,
                      padding: 10,
                      borderRadius: 8,
                      background: 'var(--bg-primary)',
                      color: 'var(--accent-primary)',
                      border: '1px solid var(--border-color)',
                      resize: 'vertical'
                    }}
                  />
                </div>
              </div>
            )}
          </div>
        )}

        {/* ========================================================
            SECTION 3: 3D LOTTERY & LIVE RESULTS
            ======================================================== */}
        {activeSection === 'lottery' && (
          <div>
            {/* Batch & System Config Bar */}
            <div className="card" style={{ marginBottom: 20 }}>
              <div className="card-body" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 14 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 'clamp(13px, 2vw, 15px)', fontWeight: 700, color: 'var(--text-primary)' }}>
                    အကြိမ် (Batch):
                  </span>
                  <input
                    type="number"
                    value={batchInput}
                    onChange={e => setBatchInput(e.target.value)}
                    style={{
                      width: 80,
                      textAlign: 'center',
                      fontSize: 16,
                      fontWeight: 700,
                      background: 'var(--bg-primary)',
                      border: '1px solid var(--border-color)',
                      color: 'var(--accent-primary)',
                      padding: '6px 8px',
                      borderRadius: 8
                    }}
                  />
                  <button type="button" className="btn btn-primary btn-sm" onClick={saveBatch}>
                    💾 Save Batch
                  </button>
                  <div className="batch-draw-date-badge">
                    <span>📅 <b>ထွက်ရက်စွဲ:</b> {liveResults.result_date || gloResult?.drawDate || '16-09-2026'}</span>
                    <span>&bull;</span>
                    <span>⏭️ <b>နောက်ထွက်မည့်ရက်:</b> {liveResults.target_draw_date || '01-10-2026'}</span>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
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

            {/* Real-Time Thai 3D / GLO Live Scraper Card */}
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
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  {gloResult?.threeD && (
                    <span className={`sync-status-indicator ${gloResult.threeD === liveResults.winning_number ? 'synced' : 'out-of-sync'}`}>
                      {gloResult.threeD === liveResults.winning_number
                        ? `🟢 IN SYNC: Live App has ${gloResult.threeD}`
                        : `ℹ️ Feed: ${gloResult.threeD} (Live App: ${liveResults.winning_number || 'Undeclared / Waiting'})`}
                    </span>
                  )}
                  <button
                    type="button"
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
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                    <div className="glo-stat-grid">
                      <div className="glo-stat-box">
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>1st Prize (รางวัลที่ 1)</div>
                        <div style={{ fontSize: 'clamp(18px, 2.5vw, 22px)', fontWeight: 800, fontFamily: 'monospace', color: 'var(--text-primary)', marginTop: 4 }}>
                          {gloResult.firstPrize || '—'}
                        </div>
                      </div>
                      <div className="glo-stat-box" style={{ borderColor: 'rgba(0, 200, 151, 0.4)', background: 'rgba(0, 200, 151, 0.08)' }}>
                        <div style={{ fontSize: 11, color: 'var(--accent-success)', fontWeight: 800, textTransform: 'uppercase' }}>
                          3D Winning (နောက် ၃ လုံး)
                        </div>
                        <div style={{ fontSize: 'clamp(22px, 3.5vw, 30px)', fontWeight: 900, fontFamily: 'monospace', color: 'var(--accent-success)', letterSpacing: 2, marginTop: 2 }}>
                          {gloResult.threeD || '—'}
                        </div>
                      </div>
                      {gloResult.twoD && (
                        <div className="glo-stat-box">
                          <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>2D (အောက် ၂ လုံး)</div>
                          <div style={{ fontSize: 'clamp(16px, 2.2vw, 20px)', fontWeight: 700, fontFamily: 'monospace', color: 'var(--text-primary)', marginTop: 4 }}>
                            {gloResult.twoD}
                          </div>
                        </div>
                      )}
                      {gloResult.date && (
                        <div className="glo-stat-box">
                          <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Draw Date (ရက်စွဲ)</div>
                          <div style={{ fontSize: 'clamp(12px, 1.6vw, 14px)', fontWeight: 700, color: 'var(--text-secondary)', marginTop: 6 }}>
                            {gloResult.date}
                          </div>
                        </div>
                      )}
                      <div className="glo-stat-box" style={{ borderColor: 'rgba(99, 102, 241, 0.35)', background: 'rgba(99, 102, 241, 0.08)' }}>
                        <div style={{ fontSize: 11, color: '#818cf8', textTransform: 'uppercase', fontWeight: 700 }}>
                          Feed Source (ရင်းမြစ်)
                        </div>
                        <div style={{ fontSize: 'clamp(11px, 1.5vw, 12px)', fontWeight: 800, color: 'var(--text-primary)', marginTop: 6, display: 'flex', alignItems: 'center', gap: 6 }}>
                          <span>{gloResult.source?.includes('Sanook') ? '⚡' : '🏛️'}</span>
                          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{gloResult.source || gloResult.session || 'Live Fast Feed'}</span>
                        </div>
                      </div>
                    </div>

                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
                      <button
                        type="button"
                        className="btn btn-warning"
                        style={{ fontWeight: 800, padding: '10px 18px' }}
                        onClick={applyGloDirectlyToFirebase}
                      >
                        ⚡ Apply 3D Result to Live App & Telegram
                      </button>
                      <button
                        type="button"
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

            {/* Manual Lottery Result Setting Card */}
            <div className="card">
              <div className="card-header">
                <h2>🎯 Manual 3D Result Declaration (လက်စွဲ ရလဒ် ကြေညာချက်)</h2>
              </div>
              <div className="card-body">
                <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                  <div className="form-group">
                    <label>3D Winning Number (ပေါက်ဂဏန်း ၃ လုံး)</label>
                    <input
                      type="text"
                      maxLength={3}
                      value={manualNumber}
                      onChange={e => setManualNumber(e.target.value.replace(/\D/g, ''))}
                      placeholder="e.g. 640"
                      style={{ textAlign: 'center', fontSize: 24, fontWeight: 900, letterSpacing: 6 }}
                    />
                  </div>

                  <div className="form-group">
                    <label>Draw Date (ထွက်သည့် ရက်စွဲ)</label>
                    <input
                      type="text"
                      value={manualDate}
                      onChange={e => setManualDate(e.target.value)}
                      placeholder="e.g. 16-09-2026"
                    />
                  </div>

                  <div className="form-group">
                    <label>State (အခြေအနေ)</label>
                    <select
                      className="select-input"
                      value={manualStatus}
                      onChange={e => setManualStatus(e.target.value)}
                    >
                      <option value="waiting">⏳ Waiting for Draw (စောင့်ဆိုင်းဆဲ)</option>
                      <option value="finished">✅ Result Declared (ထွက်ပြီး)</option>
                    </select>
                  </div>

                  <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13 }}>
                    <input
                      type="checkbox"
                      checked={updateBatchWithResult}
                      onChange={e => setUpdateBatchWithResult(e.target.checked)}
                    />
                    <span>Auto-advance batch number after declaration (အကြိမ် နံပါတ် အလိုအလျောက် တိုးမည်)</span>
                  </label>

                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={applyManualResult}
                    style={{ padding: '12px', fontWeight: 700 }}
                  >
                    🚀 Publish 3D Result (ရလဒ် ထုတ်ပြန်မည်)
                  </button>

                  {/* Active Tut Breakdown Preview */}
                  {activeNumber.length === 3 && (
                    <div style={{ background: 'var(--bg-primary)', padding: 14, borderRadius: 10, border: '1px solid var(--border-color)', marginTop: 8 }}>
                      <h4 style={{ margin: '0 0 8px 0', fontSize: 13, color: 'var(--accent-warning)' }}>
                        🔢 Tut Breakdown for {activeNumber} ({tutInfo.allTut.length} Numbers):
                      </h4>
                      <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 4 }}>
                        Permutations (အပြန်): {tutInfo.permutations.join(', ') || 'မရှိပါ'}
                      </div>
                      <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                        Near Miss (ကပ်သီး): {tutInfo.nearMisses.join(', ')}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ========================================================
            SECTION 4: APP DISTRIBUTION & TELEGRAM RELEASE
            ======================================================== */}
        {activeSection === 'release' && (
          <div style={{ maxWidth: 840, margin: '0 auto' }}>
            <div className="card" style={{ border: '1px solid rgba(99, 102, 241, 0.3)', background: 'linear-gradient(180deg, rgba(99, 102, 241, 0.04) 0%, var(--bg-card) 100%)' }}>
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
                    {appRelease?.file_id ? '🟢 Telegram Hosting Active' : '🟡 Pending Upload'}
                  </span>
                </div>
              </div>

              <div className="card-body">
                {appRelease ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                    <div style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                      gap: 12,
                      background: 'var(--bg-primary)',
                      padding: 16,
                      borderRadius: 12,
                      border: '1px solid var(--border-color)'
                    }}>
                      <div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Active Version</div>
                        <div style={{ fontSize: 18, fontWeight: 800, color: 'var(--accent-success)', marginTop: 4 }}>
                          v{appRelease.version_name || '1.0.0'} ({appRelease.version_code || 1})
                        </div>
                      </div>
                      <div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>File Size</div>
                        <div style={{ fontSize: 18, fontWeight: 800, color: 'var(--text-primary)', marginTop: 4 }}>
                          {appRelease.file_size ? `${(appRelease.file_size / (1024 * 1024)).toFixed(1)} MB` : 'Unknown'}
                        </div>
                      </div>
                      <div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Update Mode</div>
                        <div style={{ fontSize: 18, fontWeight: 800, color: appRelease.force_update ? '#ef4444' : '#10b981', marginTop: 4 }}>
                          {appRelease.force_update ? '🔴 Forced (မဖြစ်မနေ)' : '🟢 Optional (ရွေးချယ်နိုင်)'}
                        </div>
                      </div>
                      <div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Release Date</div>
                        <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-secondary)', marginTop: 6 }}>
                          {appRelease.released_at ? new Date(appRelease.released_at).toLocaleString() : '—'}
                        </div>
                      </div>
                    </div>

                    <div style={{ background: 'var(--bg-primary)', padding: 14, borderRadius: 10, border: '1px solid var(--border-color)' }}>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600, marginBottom: 6 }}>Release Notes (ဗားရှင်း ပြောင်းလဲမှု မှတ်စု)</div>
                      <div style={{ fontSize: 13, color: 'var(--text-primary)', whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>
                        {appRelease.release_notes || 'ဗားရှင်း အသစ် ထွက်ရှိပါပြီ။'}
                      </div>
                    </div>
                  </div>
                ) : (
                  <div style={{ fontSize: 13, color: 'var(--text-muted)', padding: 20, textAlign: 'center' }}>
                    No active Telegram release published yet. Use the Telegram Admin Bot or publish script to push an APK directly.
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* ========================================================
            SECTION 5: SALE PLANS & PRICING
            ======================================================== */}
        {activeSection === 'plans' && (
          <div style={{ maxWidth: 840, margin: '0 auto' }}>
            <div className="card">
              <div className="card-header">
                <h2>💰 Sale Plans & Pricing (ရောင်းချမည့် အစီအစဉ်များနှင့် စျေးနှုန်း)</h2>
              </div>
              <div className="card-body">
                {SYNCED_PLAN_IDS.map((planId) => {
                  const plan = salePlans[planId] || DEFAULT_SALE_PLANS[planId];
                  return (
                    <div key={planId} className="plan-editor-card">
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6, flexWrap: 'wrap', gap: 6 }}>
                        <span style={{ fontWeight: 700, fontSize: 14, color: 'var(--text-primary)' }}>
                          {plan.name}
                        </span>
                        <span className={`device-badge ${plan.device_changeable ? 'changeable' : 'locked'}`}>
                          {plan.device_changeable ? '🔄 Changeable' : '🔒 1-Device Locked'}
                        </span>
                      </div>
                      <p style={{ fontSize: 12, color: 'var(--text-muted)', margin: '0 0 10px 0' }}>
                        {plan.description}
                      </p>
                      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                        <input
                          type="number"
                          defaultValue={plan.price}
                          key={plan.price}
                          placeholder="Price in MMK"
                          onChange={e => setEditingPlanPrice(prev => ({ ...prev, [planId]: e.target.value }))}
                          style={{ width: 140, fontWeight: 700 }}
                        />
                        <span style={{ fontSize: 13, color: 'var(--text-muted)' }}>Ks</span>
                        <button
                          type="button"
                          className="btn btn-outline btn-sm"
                          onClick={() => savePlanPrice(planId)}
                        >
                          💾 Save
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        )}

        {/* ========================================================
            SECTION 6: TUT CALCULATOR / FORMULA CHECKER
            ======================================================== */}
        {activeSection === 'calculator' && (
          <div style={{ maxWidth: 760, margin: '0 auto' }}>
            <div className="card">
              <div className="card-header">
                <h2>🧪 3D Tut Simulator (တွတ် စမ်းသပ်တွက်စက်)</h2>
              </div>
              <div className="card-body">
                <div className="form-group">
                  <label>Enter Any 3D Number to Test (ဂဏန်း ၃ လုံး စမ်းသပ်ရန်)</label>
                  <input
                    type="text"
                    maxLength={3}
                    value={calcTestNumber}
                    onChange={e => setCalcTestNumber(e.target.value.replace(/\D/g, ''))}
                    placeholder="e.g. 212, 123, 640"
                    style={{ textAlign: 'center', fontSize: 24, fontWeight: 900, letterSpacing: 6 }}
                  />
                </div>

                {calcTestNumber.length === 3 ? (
                  <div style={{ background: 'var(--bg-primary)', padding: 16, borderRadius: 10, border: '1px solid var(--border-color)' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                      <span style={{ fontSize: 14, fontWeight: 700, color: 'var(--accent-success)' }}>
                        တွတ် စုစုပေါင်း ({playgroundTut.allTut.length} ဂဏန်း)
                      </span>
                      <button
                        type="button"
                        className={`copy-btn ${copiedId === 'play-tut-all' ? 'copied' : ''}`}
                        style={{ fontSize: 11, padding: '4px 10px' }}
                        onClick={() => copyToClipboard(playgroundTut.allTut.join(', '), 'play-tut-all')}
                      >
                        {copiedId === 'play-tut-all' ? '✅ Copied All' : '📋 Copy All'}
                      </button>
                    </div>

                    <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 6 }}>
                      အပြန် ({playgroundTut.permutations.length}): {playgroundTut.permutations.join(', ') || 'မရှိပါ'}
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 12 }}>
                      ကပ်သီး ({playgroundTut.nearMisses.length}): {playgroundTut.nearMisses.join(', ')}
                    </div>

                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                      {playgroundTut.allTut.map(n => (
                        <span
                          key={n}
                          style={{
                            background: 'rgba(0, 200, 151, 0.15)',
                            color: 'var(--accent-success)',
                            padding: '4px 10px',
                            borderRadius: 6,
                            fontFamily: 'monospace',
                            fontSize: 13,
                            fontWeight: 800
                          }}
                        >
                          {n}
                        </span>
                      ))}
                    </div>
                  </div>
                ) : (
                  <div style={{ fontSize: 13, color: 'var(--text-muted)', lineHeight: 1.6 }}>
                    ဂဏန်း ၃ လုံး ရိုက်ထည့်ပြီး တွတ်ဂဏန်းများ (အပြန် ၅ လုံး + ကပ်သီး ၂ လုံး) ကို စမ်းသပ်ကြည့်ရှုနိုင်ပါသည်။
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
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
