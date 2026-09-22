import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import {
  buildResellerList,
  computeResellerKeyCounts,
  filterKeysByResellerAndCriteria,
  formatTelegramUsername,
  getTelegramChatUrl,
  computeKeyStats,
  groupKeysByReseller
} from '../src/resellerUtils.js';

describe('Reseller Separation & License Key Filtering', () => {
  const mockResellers = {
    '11111': {
      telegram_id: '11111',
      name: 'Ko Aung',
      username: 'koaung3d',
      total_due: 15000,
      total_commission: 50000,
      total_paid: 35000
    },
    '22222': {
      telegram_id: '22222',
      name: 'Daw Mya',
      username: '@dawmya_agent',
      total_due: 0,
      total_commission: 20000,
      total_paid: 20000
    }
  };

  const mockKeys = [
    ['KEY-ADMIN-01', { status: 'available', plan_id: 'lifetime', duration: 'lifetime', generated_at: 100 }],
    ['KEY-AUNG-01', {
      status: 'available',
      plan_id: 'one_year',
      duration: 365,
      generated_by_reseller_id: '11111',
      reseller_name: 'Ko Aung',
      reseller_username: 'koaung3d',
      generated_at: 200
    }],
    ['KEY-AUNG-02', {
      status: 'claimed',
      plan_id: 'trial_3d',
      duration: 'trial',
      generated_by_reseller_id: '11111',
      reseller_name: 'Ko Aung',
      reseller_username: 'koaung3d',
      claimed_by: 'phone-09123456789',
      generated_at: 300
    }],
    ['KEY-MYA-01', {
      status: 'available',
      plan_id: 'lifetime',
      duration: 'lifetime',
      generated_by_reseller_id: '22222',
      reseller_name: 'Daw Mya',
      reseller_username: 'dawmya_agent',
      generated_at: 400
    }]
  ];

  test('buildResellerList discovers all registered resellers', () => {
    const list = buildResellerList(mockResellers, mockKeys);
    assert.equal(list.length, 2);
    const aung = list.find(r => r.telegram_id === '11111');
    assert.ok(aung);
    assert.equal(aung.name, 'Ko Aung');
    assert.equal(aung.username, 'koaung3d');
  });

  test('computeResellerKeyCounts correctly partitions keys', () => {
    const counts = computeResellerKeyCounts(mockKeys);
    assert.equal(counts.all, 4);
    assert.equal(counts.direct, 1);
    assert.equal(counts['11111'], 2);
    assert.equal(counts['22222'], 1);
  });

  test('filters keys strictly when selecting a specific reseller', () => {
    const aungKeys = filterKeysByResellerAndCriteria(mockKeys, {
      selectedResellerId: '11111',
      currentSelectedReseller: mockResellers['11111'],
      keyFilter: 'all'
    });
    assert.equal(aungKeys.length, 2);
    assert.deepEqual(aungKeys.map(([id]) => id), ['KEY-AUNG-02', 'KEY-AUNG-01']); // Sorted newest first

    const myaKeys = filterKeysByResellerAndCriteria(mockKeys, {
      selectedResellerId: '22222',
      currentSelectedReseller: mockResellers['22222'],
      keyFilter: 'all'
    });
    assert.equal(myaKeys.length, 1);
    assert.equal(myaKeys[0][0], 'KEY-MYA-01');
  });

  test('filters direct admin keys (without reseller attribution)', () => {
    const directKeys = filterKeysByResellerAndCriteria(mockKeys, {
      selectedResellerId: 'direct',
      keyFilter: 'all'
    });
    assert.equal(directKeys.length, 1);
    assert.equal(directKeys[0][0], 'KEY-ADMIN-01');
  });

  test('shows all keys when selectedResellerId is all', () => {
    const allKeys = filterKeysByResellerAndCriteria(mockKeys, {
      selectedResellerId: 'all',
      keyFilter: 'all'
    });
    assert.equal(allKeys.length, 4);
  });

  test('combines reseller separation with status filter', () => {
    const aungAvailable = filterKeysByResellerAndCriteria(mockKeys, {
      selectedResellerId: '11111',
      currentSelectedReseller: mockResellers['11111'],
      keyFilter: 'available'
    });
    assert.equal(aungAvailable.length, 1);
    assert.equal(aungAvailable[0][0], 'KEY-AUNG-01');

    const aungClaimed = filterKeysByResellerAndCriteria(mockKeys, {
      selectedResellerId: '11111',
      currentSelectedReseller: mockResellers['11111'],
      keyFilter: 'claimed'
    });
    assert.equal(aungClaimed.length, 1);
    assert.equal(aungClaimed[0][0], 'KEY-AUNG-02');
  });

  test('correctly normalizes telegram usernames and URLs', () => {
    assert.equal(formatTelegramUsername('@koaung3d'), 'koaung3d');
    assert.equal(formatTelegramUsername('dawmya_agent'), 'dawmya_agent');
    assert.equal(formatTelegramUsername(''), '');

    assert.equal(getTelegramChatUrl('@koaung3d'), 'https://t.me/koaung3d');
    assert.equal(getTelegramChatUrl('dawmya_agent'), 'https://t.me/dawmya_agent');
    assert.equal(getTelegramChatUrl(''), '');
  });

  test('computeKeyStats accurately breaks down key statuses', () => {
    const stats = computeKeyStats(mockKeys);
    assert.equal(stats.total, 4);
    assert.equal(stats.available, 3);
    assert.equal(stats.claimed, 1);
    assert.equal(stats.revoked, 0);
  });

  test('groupKeysByReseller separates direct keys and individual reseller sections', () => {
    const resellerList = buildResellerList(mockResellers, mockKeys);
    const grouped = groupKeysByReseller(resellerList, mockKeys);

    assert.equal(grouped.direct.keys.length, 1);
    assert.equal(grouped.direct.keys[0][0], 'KEY-ADMIN-01');
    assert.equal(grouped.direct.stats.available, 1);

    assert.equal(grouped.resellerSections.length, 2);
    const aungSection = grouped.resellerSections.find(s => s.reseller.telegram_id === '11111');
    assert.ok(aungSection);
    assert.equal(aungSection.keys.length, 2);
    assert.equal(aungSection.stats.total, 2);
    assert.equal(aungSection.stats.available, 1);
    assert.equal(aungSection.stats.claimed, 1);

    const myaSection = grouped.resellerSections.find(s => s.reseller.telegram_id === '22222');
    assert.ok(myaSection);
    assert.equal(myaSection.keys.length, 1);
    assert.equal(myaSection.stats.available, 1);
  });
});
