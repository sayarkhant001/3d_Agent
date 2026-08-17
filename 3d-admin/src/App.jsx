import { useState, useEffect, useCallback } from 'react';
import { signInWithEmailAndPassword, signOut, onAuthStateChanged } from 'firebase/auth';
import { ref, onValue, set, update } from 'firebase/database';
import { auth, db } from './firebase';
import './index.css';

function App() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loginError, setLoginError] = useState('');
  const [keys, setKeys] = useState({});
  const [mode, setMode] = useState('auto');
  const [lotteryStatus, setLotteryStatus] = useState('normal');
  const [liveResults, setLiveResults] = useState({});
  const [manualNumber, setManualNumber] = useState('');
  const [manualDate, setManualDate] = useState('');
  const [manualStatus, setManualStatus] = useState('waiting');
  const [generatedKey, setGeneratedKey] = useState(null);
  const [toast, setToast] = useState(null);
  const [loggingIn, setLoggingIn] = useState(false);

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
      setLoginError('Invalid credentials. Please try again.');
    }
    setLoggingIn(false);
  };

  const handleLogout = () => {
    signOut(auth);
  };

  const generateKey = async () => {
    // Generate secure 32-char hex key: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX
    const array = new Uint8Array(16);
    window.crypto.getRandomValues(array);
    const key = Array.from(array, byte => byte.toString(16).padStart(2, '0').toUpperCase())
      .join('').match(/.{1,4}/g).join('-');

    let durationValue = keyType;
    if (keyType === 'custom') durationValue = parseInt(customDays);

    await set(ref(db, `3d_licenses/keys/${key}`), {
      status: 'available',
      duration: durationValue, // "trial", "lifetime", or integer (days)
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
    showToast('Key revoked and made available again');
  };

  const deleteKey = async (keyId) => {
    await set(ref(db, `3d_licenses/keys/${keyId}`), null);
    showToast('Key deleted', 'error');
  };

  const toggleMode = async (newMode) => {
    await set(ref(db, '3d_lottery_config/mode'), newMode);
    showToast(`Switched to ${newMode.toUpperCase()} mode`);
  };

  const pushManualResult = async () => {
    if (!/^\d{3}$/.test(manualNumber)) {
      showToast('Must be exactly 3 digits', 'error');
      return;
    }

    const updates = {
      '3d_live_results/winning_number': manualNumber,
      '3d_lottery_status/state': manualStatus
    };

    if (manualDate) {
      updates['3d_live_results/target_draw_date'] = manualDate;
    }

    await update(ref(db), updates);
    showToast(`Result ${manualNumber} pushed as "${manualStatus}"!`);
    setManualNumber('');
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

  // Stats
  const keyEntries = Object.entries(keys);
  const totalKeys = keyEntries.length;
  const availableKeys = keyEntries.filter(([, v]) => v.status === 'available').length;
  const claimedKeys = keyEntries.filter(([, v]) => v.status === 'claimed').length;
  const revokedKeys = keyEntries.filter(([, v]) => v.status === 'revoked').length;

  if (loading) {
    return (
      <div className="login-container">
        <div className="loading" style={{ fontSize: 24, color: 'var(--text-secondary)' }}>
          Loading...
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
          <p className="subtitle">Sign in to manage your lottery system</p>

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
        <h1>🎰 <span>3D Lottery Admin</span></h1>
        <div className="header-right">
          <span className="user-info">👤 {user.email}</span>
          <button className="btn btn-outline btn-sm" onClick={handleLogout}>Logout</button>
        </div>
      </header>

      <main className="dashboard-content">
        {/* Stats */}
        <div className="stats-row">
          <div className="stat-card purple">
            <div className="stat-icon">🔑</div>
            <div className="stat-value">{totalKeys}</div>
            <div className="stat-label">Total Keys</div>
          </div>
          <div className="stat-card green">
            <div className="stat-icon">✅</div>
            <div className="stat-value">{availableKeys}</div>
            <div className="stat-label">Available</div>
          </div>
          <div className="stat-card red">
            <div className="stat-icon">📱</div>
            <div className="stat-value">{claimedKeys}</div>
            <div className="stat-label">Claimed</div>
          </div>
          <div className="stat-card orange">
            <div className="stat-icon">🎯</div>
            <div className="stat-value">{liveResults.winning_number || '---'}</div>
            <div className="stat-label">Latest Result</div>
          </div>
        </div>

        {/* Two Column Layout */}
        <div className="two-col-grid">

          {/* Lottery Control Panel */}
          <div className="card">
            <div className="card-header">
              <h2>🎱 Lottery Control</h2>
              <span className={`mode-badge ${mode}`}>
                {mode === 'auto' ? '🤖' : '✋'} {mode}
              </span>
            </div>
            <div className="card-body">
              <div className="control-row" style={{ marginBottom: 16 }}>
                <span style={{ fontSize: 13, color: 'var(--text-secondary)', fontWeight: 600 }}>System Mode:</span>
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
                  <option value="auto">AUTO (Cloudflare Scraper)</option>
                  <option value="manual">MANUAL (Override)</option>
                </select>
              </div>

              <div style={{ fontSize: 13, color: 'var(--text-muted)', marginBottom: 8 }}>
                Status: <strong style={{ color: lotteryStatus === 'declared' ? 'var(--accent-success)' : 'var(--text-secondary)' }}>
                  {lotteryStatus}
                </strong>
                {liveResults.target_draw_date && (
                  <span> · Draw: {liveResults.target_draw_date}</span>
                )}
              </div>

              {mode === 'manual' && (
                <div className="manual-override-panel">
                  <h3 style={{ fontSize: 14, fontWeight: 700, color: 'var(--accent-warning)', marginBottom: 12 }}>
                    ✋ Manual Override Panel
                  </h3>

                  <div className="form-group">
                    <label>3-Digit Result</label>
                    <input
                      type="text"
                      maxLength={3}
                      value={manualNumber}
                      onChange={e => setManualNumber(e.target.value.replace(/\D/g, ''))}
                      placeholder="000"
                      style={{ textAlign: 'center', fontSize: 20, letterSpacing: 6, fontWeight: 700 }}
                    />
                  </div>

                  <div className="form-group">
                    <label>Draw Date</label>
                    <input
                      type="date"
                      value={manualDate}
                      onChange={e => setManualDate(e.target.value)}
                    />
                  </div>

                  <div className="form-group">
                    <label>Display Status</label>
                    <select
                      className="select-input"
                      value={manualStatus}
                      onChange={e => setManualStatus(e.target.value)}
                    >
                      <option value="waiting">⏳ Waiting (Show countdown)</option>
                      <option value="pending">🔄 Pending (Draw happening now)</option>
                      <option value="declared">✅ Declared (Results out)</option>
                      <option value="delayed">🔴 Delayed (Show red warning)</option>
                    </select>
                  </div>

                  <button className="btn btn-warning btn-full" onClick={pushManualResult}>
                    📤 Push Live Update
                  </button>
                </div>
              )}
            </div>
          </div>

          {/* Key Generation Panel */}
          <div className="card">
            <div className="card-header">
              <h2>✨ Generate CD-Key</h2>
            </div>
            <div className="card-body">
              <div className="form-group">
                <label>Key Type</label>
                <select
                  className="select-input"
                  value={keyType}
                  onChange={e => setKeyType(e.target.value)}
                >
                  <option value="trial">⏱️ 3-Day Trial</option>
                  <option value="lifetime">♾️ Lifetime</option>
                  <option value="custom">📅 Custom Duration</option>
                </select>
              </div>

              {keyType === 'custom' && (
                <div className="form-group">
                  <label>Number of Days</label>
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
                ✨ Generate Key
              </button>

              <div style={{ marginTop: 16, fontSize: 12, color: 'var(--text-muted)', textAlign: 'center' }}>
                {keyType === 'trial' && 'Key will expire 3 days after activation'}
                {keyType === 'lifetime' && 'Key will never expire'}
                {keyType === 'custom' && `Key will expire ${customDays} days after activation`}
              </div>
            </div>
          </div>

        </div>

        {/* Licenses Table */}
        <div className="card">
          <div className="card-header">
            <h2>🔑 License Keys ({totalKeys})</h2>
            <div style={{ display: 'flex', gap: 8, fontSize: 12 }}>
              <span className="status-badge available">🟢 {availableKeys}</span>
              <span className="status-badge claimed">🔴 {claimedKeys}</span>
              {revokedKeys > 0 && <span className="status-badge revoked">⚪ {revokedKeys}</span>}
            </div>
          </div>
          <div className="card-body" style={{ padding: 0 }}>
            {totalKeys === 0 ? (
              <div className="empty-state">
                <div className="empty-icon">🔐</div>
                <p>No license keys yet. Generate your first key above!</p>
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
                  {keyEntries
                    .sort((a, b) => (b[1].generated_at || 0) - (a[1].generated_at || 0))
                    .map(([keyId, keyData]) => (
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
            <h3>Key Generated!</h3>
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
