import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { calculateTutNumbers } from '../src/tutLogic.js';

describe('Myanmar 3D Tut Calculation Engine', () => {
  test('handles 3 distinct digits (e.g. 108): 5 permutations + 2 near misses', () => {
    const result = calculateTutNumbers('108');
    // Digits: 1, 0, 8. Total permutations = 6 - 1 (self) = 5.
    assert.equal(result.permutations.length, 5);
    assert.deepEqual(result.permutations, ['018', '081', '180', '801', '810']);

    // Near misses: 108 - 1 = 107, 108 + 1 = 109
    assert.deepEqual(result.nearMisses, ['107', '109']);

    // All Tut combines both without duplicates and without winning number
    assert.equal(result.allTut.includes('108'), false);
    assert.equal(result.allTut.length, 7);
  });

  test('handles double repeating digits (e.g. 212): 2 permutations + 2 near misses', () => {
    const result = calculateTutNumbers('212');
    // Digits: 2, 1, 2. Permutations: '122', '221' (only 2 distinct permutations)
    assert.deepEqual(result.permutations, ['122', '221']);

    // Near misses: 211, 213
    assert.deepEqual(result.nearMisses, ['211', '213']);

    assert.equal(result.allTut.includes('212'), false);
    assert.equal(result.allTut.length, 4);
  });

  test('handles triple repeating digits (e.g. 222): 0 permutations + 2 near misses', () => {
    const result = calculateTutNumbers('222');
    // No distinct permutations other than 222
    assert.deepEqual(result.permutations, []);

    // Near misses: 221, 223
    assert.deepEqual(result.nearMisses, ['221', '223']);

    assert.equal(result.allTut.includes('222'), false);
    assert.deepEqual(result.allTut, ['221', '223']);
  });

  test('handles boundary cyclic cases: 000 and 999', () => {
    const res000 = calculateTutNumbers('000');
    assert.deepEqual(res000.nearMisses, ['999', '001']);
    assert.equal(res000.allTut.includes('000'), false);

    const res999 = calculateTutNumbers('999');
    assert.deepEqual(res999.nearMisses, ['998', '000']);
    assert.equal(res999.allTut.includes('999'), false);
  });

  test('handles invalid inputs gracefully', () => {
    const res1 = calculateTutNumbers('');
    assert.deepEqual(res1, { permutations: [], nearMisses: [], allTut: [] });

    const res2 = calculateTutNumbers('12');
    assert.deepEqual(res2, { permutations: [], nearMisses: [], allTut: [] });

    const res3 = calculateTutNumbers('abc');
    assert.deepEqual(res3, { permutations: [], nearMisses: [], allTut: [] });
  });
});
