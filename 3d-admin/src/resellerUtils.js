/**
 * Reseller & Key filtering utilities for 3D Admin
 */

/**
 * Builds a unified list of resellers from database objects and any keys referencing resellers.
 */
export function buildResellerList(resellersMap = {}, keyEntries = []) {
  const list = Object.values(resellersMap || {});
  const existingIds = new Set(list.map(r => String(r.telegram_id)));

  keyEntries.forEach(([_, k]) => {
    if (k?.generated_by_reseller_id && !existingIds.has(String(k.generated_by_reseller_id))) {
      existingIds.add(String(k.generated_by_reseller_id));
      list.push({
        telegram_id: String(k.generated_by_reseller_id),
        name: k.reseller_name || `Reseller ${k.generated_by_reseller_id}`,
        username: k.reseller_username || '',
        total_generated: 0,
        total_activated: 0,
        total_commission: 0,
        total_due: 0,
        total_paid: 0,
        created_at: k.created_at || Date.now()
      });
    }
  });

  return list;
}

/**
 * Computes counts of keys per reseller, direct keys, and total keys.
 */
export function computeResellerKeyCounts(keyEntries = []) {
  const counts = { all: keyEntries.length, direct: 0 };
  keyEntries.forEach(([_, k]) => {
    if (k?.generated_by_reseller_id) {
      const id = String(k.generated_by_reseller_id);
      counts[id] = (counts[id] || 0) + 1;
    } else {
      counts.direct = (counts.direct || 0) + 1;
    }
  });
  return counts;
}

/**
 * Filters keys based on reseller separation, status/plan filters, and search text.
 */
export function filterKeysByResellerAndCriteria(keyEntries = [], {
  selectedResellerId = 'all',
  currentSelectedReseller = null,
  keyFilter = 'all',
  keySearch = ''
} = {}) {
  return keyEntries
    .filter(([keyId, keyData]) => {
      if (!keyData) return false;

      // Reseller separation
      if (selectedResellerId === 'direct') {
        if (keyData.generated_by_reseller_id) return false;
      } else if (selectedResellerId !== 'all') {
        const matchesId = String(keyData.generated_by_reseller_id) === String(selectedResellerId);
        const matchesName = Boolean(currentSelectedReseller && keyData.reseller_name && keyData.reseller_name === currentSelectedReseller.name);
        if (!matchesId && !matchesName) return false;
      }

      // Status & plan filters
      if (keyFilter === 'available' && keyData.status !== 'available') return false;
      if (keyFilter === 'claimed' && keyData.status !== 'claimed' && keyData.status !== 'active') return false;
      if (keyFilter === 'revoked' && keyData.status !== 'revoked') return false;
      if (keyFilter === 'changeable' && keyData.device_changeable !== true) return false;
      if (keyFilter === 'locked' && keyData.device_changeable === true) return false;
      if (keyFilter === 'trial_3d' && keyData.plan_id !== 'trial_3d' && keyData.duration !== 'trial') return false;
      if (keyFilter === 'one_year' && keyData.plan_id !== 'one_year' && keyData.duration !== 365) return false;
      if (keyFilter === 'lifetime' && keyData.plan_id !== 'lifetime' && keyData.duration !== 'lifetime') return false;

      // Search query
      if (keySearch && keySearch.trim()) {
        const q = keySearch.toLowerCase().trim();
        const matchKey = (keyId || '').toLowerCase().includes(q);
        const matchDevice = (keyData.claimed_by || keyData.device_model || keyData.device_fingerprint || '').toLowerCase().includes(q);
        const matchPlan = (keyData.plan_id || keyData.duration_label || '').toLowerCase().includes(q);
        const matchReseller = (keyData.reseller_name || keyData.reseller_username || '').toLowerCase().includes(q);
        return matchKey || matchDevice || matchPlan || matchReseller;
      }

      return true;
    })
    .sort((a, b) => (b[1]?.generated_at || b[1]?.created_at || 0) - (a[1]?.generated_at || a[1]?.created_at || 0));
}

/**
 * Normalizes telegram username without leading @
 */
export function formatTelegramUsername(username = '') {
  if (!username) return '';
  return username.replace(/^@+/, '').trim();
}

/**
 * Returns direct https://t.me/ URL for a telegram username
 */
export function getTelegramChatUrl(username = '') {
  const clean = formatTelegramUsername(username);
  return clean ? `https://t.me/${clean}` : '';
}
