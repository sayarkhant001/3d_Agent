import { useState, useEffect, useCallback, useMemo } from 'react';
import { signInWithEmailAndPassword, signOut, onAuthStateChanged } from 'firebase/auth';
import { ref, onValue, set, update } from 'firebase/database';
import { auth, db } from './firebase';
import './index.css';

import { calculateTutNumbers } from './tutLogic';

function App() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loginError, setLoginError] = useState('');
  const [keys, setKeys] = useState({});
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
  const [toast, setToast] = useState(null);
  const [loggingIn, setLoggingIn] = useState(false);

  // Key filtering & search
  const [keySearch, setKeySearch] = useState('');
  const [keyFilter, setKeyFilter] = useState('all'); // all, available, claimed, revoked

  // Tut playground test number
  const [calcTestNumber, setCalcTestNumber] = useState('');

  // GLO Thailand Official Lottery Scraper State
  const [gloResult, setGloResult] = useState(null);
  const [fetchingGlo, setFetchingGlo] = useState(false);
  const [gloError, setGloError] = useState('');

  // Key generation options
  const [keyType, setKeyType] = useState('trial');
  const [customDays, setCustomDays] = useState(7);

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

    const batchRef = ref(db, '3d_lottery_config/current_batch');
    unsubs.push(onValue(batchRef, (snap) => {
      const b = snap.val() || 1;
      setCurrentBatch(b);
      setBatchInput(String(b));
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

  // ── Fetch official Thai GLO result with multi-tier failover ─────────────────
  const fetchOfficialGlo = async () => {
    setFetchingGlo(true);
    setGloError('');
    try {
      let data = null;

      // 1. Primary Scraper Worker (User's Cloudflare Worker)
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
                date: date
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
                date: date
              };
            }
          }
        } catch (_) {}
      }

      if (data) {
        setGloResult(data);
        showToast(`GLO Result: 3D = ${data.threeD} (1st = ${data.firstPrize})`);
      } else {
        setGloError('Unable to fetch GLO data. You can enter manually.');
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
    showToast(`Set 3D to ${gloResult.threeD} with status "declared"`);
  };

  // ── CD-Key Generation ────────────────────────────────────────────────────────
  const generateKey = async () => {
    const array = new Uint8Array(16);
    window.crypto.getRandomValues(array);
    const key = Array.from(array, byte => byte.toString(16).padStart(2, '0').toUpperCase())
      .join('').match(/.{1,4}/g).join('-');

    let durationValue = keyType;
    if (keyType === 'custom') durationValue = parseInt(customDays, 10);

    await set(ref(db, `3d_licenses/keys/${key}`), {
      status: 'available',
      duration: durationValue,
      generated_at: Date.now()
    });

    setGeneratedKey(key);
    showToast('New CD-Key generated successfully!');
  };

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

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
    showToast('Copied to clipboard!');
  };

  const formatDuration = (duration) => {
    if (duration === 'trial') return '3 Days Trial';
    if (duration === 'lifetime') return 'Lifetime ♾️';
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
  const claimedKeys = keyEntries.filter(([, v]) => v.status === 'claimed').length;
  const revokedKeys = keyEntries.filter(([, v]) => v.status === 'revoked').length;

  const filteredKeys = useMemo(() => {
    return keyEntries
      .filter(([keyId, keyData]) => {
        if (keyFilter !== 'all' && keyData.status !== keyFilter) return false;
        if (keySearch.trim()) {
          const q = keySearch.toLowerCase().trim();
          const matchKey = keyId.toLowerCase().includes(q);
          const matchDevice = (keyData.claimed_by || '').toLowerCase().includes(q);
          return matchKey || matchDevice;
        }
        return true;
      })
      .sort((a, b) => (b[1].generated_at || 0) - (a[1].generated_at || 0));
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

        {/* GLO Official Lottery Live Scraper Card */}
        <div className="card" style={{ marginBottom: 20 }}>
          <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 20 }}>🇹🇭</span>
              <h2 style={{ fontSize: 16, margin: 0 }}>Official Thai GLO Lottery (ထိုင်း အစိုးရ ထီပေါက်ဂဏန်း)</h2>
            </div>
            <button
              className="btn btn-primary btn-sm"
              onClick={fetchOfficialGlo}
              disabled={fetchingGlo}
            >
              {fetchingGlo ? '⏳ Fetching GLO...' : '🔄 Check GLO Live'}
            </button>
          </div>
          <div className="card-body">
            {gloError && (
              <div style={{ color: 'var(--accent-secondary)', fontSize: 13, marginBottom: 10 }}>
                ⚠️ {gloError}
              </div>
            )}
            {gloResult ? (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase' }}>1st Prize (รางวัลที่ 1)</div>
                    <div style={{ fontSize: 22, fontWeight: 800, fontFamily: 'monospace', color: 'var(--text-primary)' }}>
                      {gloResult.firstPrize || '—'}
                    </div>
                  </div>
                  <div>
                    <div style={{ fontSize: 11, color: 'var(--accent-success)', fontWeight: 700, textTransform: 'uppercase' }}>
                      3D Winning (နောက် ၃ လုံး)
                    </div>
                    <div style={{ fontSize: 28, fontWeight: 900, fontFamily: 'monospace', color: 'var(--accent-success)' }}>
                      {gloResult.threeD || '—'}
                    </div>
                  </div>
                  {gloResult.twoD && (
                    <div>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase' }}>2D (အောက် ၂ လုံး)</div>
                      <div style={{ fontSize: 20, fontWeight: 700, fontFamily: 'monospace', color: 'var(--text-primary)' }}>
                        {gloResult.twoD}
                      </div>
                    </div>
                  )}
                  {gloResult.date && (
                    <div>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Draw Date (ထွက်သည့် ရက်စွဲ)</div>
                      <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)', marginTop: 4 }}>
                        {gloResult.date}
                      </div>
                    </div>
                  )}
                </div>

                <button className="btn btn-warning" onClick={applyGloToManual}>
                  ⚡ Apply GLO Result to 3D Ledger
                </button>
              </div>
            ) : (
              <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
                Click "Check GLO Live" to fetch the official 1st prize and 3D winning number directly from the Thai Government Lottery Office.
              </div>
            )}
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
                      className="btn btn-outline btn-sm"
                      style={{ fontSize: 11, padding: '2px 8px' }}
                      onClick={() => copyToClipboard(tutInfo.allTut.join(', '))}
                    >
                      📋 Copy All Tut
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
                          style={{ fontSize: 10, color: 'var(--accent-primary)', cursor: 'pointer', textDecoration: 'underline' }}
                          onClick={() => copyToClipboard(tutInfo.permutations.join(', '))}
                        >
                          Copy အပြန်
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
                          style={{ fontSize: 10, color: 'var(--accent-warning)', cursor: 'pointer', textDecoration: 'underline' }}
                          onClick={() => copyToClipboard(tutInfo.nearMisses.join(', '))}
                        >
                          Copy ကပ်သီး
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

          {/* Right Column: Key Generation + Tut Playground */}
          <div>
            {/* Key Generation Panel */}
            <div className="card">
              <div className="card-header">
                <h2>✨ Generate CD-Key (လိုင်စင်ကုဒ် ထုတ်ရန်)</h2>
              </div>
              <div className="card-body">
                <div className="form-group">
                  <label>Key Type (အမျိုးအစား)</label>
                  <select
                    className="select-input"
                    value={keyType}
                    onChange={e => setKeyType(e.target.value)}
                  >
                    <option value="trial">⏱️ 3-Day Trial (၃ ရက် စမ်းသပ်ခွင့်)</option>
                    <option value="lifetime">♾️ Lifetime (သက်တမ်း အကန့်အသတ်မရှိ)</option>
                    <option value="custom">📅 Custom Duration (ရက်သတ်မှတ်ရန်)</option>
                  </select>
                </div>

                {keyType === 'custom' && (
                  <div className="form-group">
                    <label>Number of Days (ရက်ပေါင်း)</label>
                    <input
                      type="number"
                      min="1"
                      value={customDays}
                      onChange={e => setCustomDays(e.target.value)}
                      placeholder="Days"
                    />
                  </div>
                )}

                <button className="btn btn-primary btn-full" onClick={generateKey} style={{ marginTop: 8 }}>
                  ✨ Generate Key (ကုဒ် အသစ်ထုတ်မည်)
                </button>

                <div style={{ marginTop: 16, fontSize: 12, color: 'var(--text-muted)', textAlign: 'center' }}>
                  {keyType === 'trial' && 'Key will expire 3 days after device activation'}
                  {keyType === 'lifetime' && 'Key will never expire for this device'}
                  {keyType === 'custom' && `Key will expire ${customDays} days after activation`}
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
                        className="btn btn-outline btn-sm"
                        style={{ fontSize: 10, padding: '2px 6px' }}
                        onClick={() => copyToClipboard(playgroundTut.allTut.join(', '))}
                      >
                        Copy All
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
                <option value="claimed">🔴 Claimed Only</option>
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
                    <th>Duration</th>
                    <th>Status</th>
                    <th>Device</th>
                    <th>Date</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredKeys.map(([keyId, keyData]) => (
                    <tr key={keyId}>
                      <td>
                        <span
                          className="key-code"
                          style={{ cursor: 'pointer' }}
                          onClick={() => copyToClipboard(keyId)}
                          title="Click to copy"
                        >
                          {keyId}
                        </span>
                      </td>
                      <td>
                        <span className={`duration-badge ${keyData.duration === 'lifetime' ? 'lifetime' : keyData.duration === 'trial' ? 'trial' : 'custom'}`}>
                          {formatDuration(keyData.duration)}
                        </span>
                      </td>
                      <td>
                        <span className={`status-badge ${keyData.status}`}>
                          {keyData.status === 'available' ? '🟢' : keyData.status === 'claimed' ? '🔴' : '⚪'} {keyData.status}
                        </span>
                      </td>
                      <td>
                        <span className="device-text">
                          {keyData.claimed_by || '—'}
                        </span>
                      </td>
                      <td style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                        {keyData.generated_at
                          ? new Date(keyData.generated_at).toLocaleDateString()
                          : '—'}
                      </td>
                      <td>
                        <div style={{ display: 'flex', gap: 6 }}>
                          {keyData.status === 'claimed' && (
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

      {/* Generated Key Popup */}
      {generatedKey && (
        <div className="key-popup-overlay" onClick={() => setGeneratedKey(null)}>
          <div className="key-popup" onClick={e => e.stopPropagation()}>
            <div className="popup-icon">🎉</div>
            <h3>Key Generated! (လိုင်စင်ကုဒ် ထွက်ပါပြီ)</h3>
            <p style={{ color: 'var(--text-secondary)', fontSize: 13, marginBottom: 4 }}>
              Type: <strong>{keyType === 'trial' ? '3-Day Trial' : keyType === 'lifetime' ? 'Lifetime' : `${customDays} Days`}</strong>
            </p>
            <div
              className="generated-key"
              onClick={() => copyToClipboard(generatedKey)}
            >
              {generatedKey}
            </div>
            <p className="hint">Click the key to copy it to clipboard</p>
            <div className="popup-actions">
              <button className="btn btn-primary" onClick={() => copyToClipboard(generatedKey)}>
                📋 Copy
              </button>
              <button className="btn btn-outline" onClick={() => setGeneratedKey(null)}>
                Close
              </button>
            </div>
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
