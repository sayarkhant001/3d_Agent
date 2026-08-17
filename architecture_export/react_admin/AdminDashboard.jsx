/**
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
