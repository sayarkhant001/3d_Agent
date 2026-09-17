/**
 * တွတ် (Tut) Generator: Permutations + Near-Misses (+1, -1)
 *
 * Rules in Myanmar 3D lottery:
 * 1. Winning Number: Exact 3-digit number (ဒဲ့)
 * 2. Permutations (အပြန်): all other unique anagrams of the winning digits (up to 5, less if repeating digits like 212, 222)
 * 3. Near-misses (ကပ်သီး): +1 and -1 from the winning number (with 000-999 cyclic boundary)
 * 4. Combined Tut (တွတ်): all permutations + near-misses (strictly excluding the exact winning number)
 */
export function calculateTutNumbers(winningNumber) {
  if (!/^\d{3}$/.test(winningNumber)) {
    return { permutations: [], nearMisses: [], allTut: [] };
  }

  // 1. Permutations (အပြန်များ)
  const digits = winningNumber.split('');
  const perms = new Set();
  const permute = (arr, m = []) => {
    if (arr.length === 0) {
      perms.add(m.join(''));
    } else {
      for (let i = 0; i < arr.length; i++) {
        const curr = arr.slice();
        const next = curr.splice(i, 1);
        permute(curr.slice(), m.concat(next));
      }
    }
  };
  permute(digits);
  perms.delete(winningNumber);
  const permutations = Array.from(perms).sort();

  // 2. Near-misses (ကပ်သီး +1, -1)
  const numInt = parseInt(winningNumber, 10);
  const minus1 = String(numInt === 0 ? 999 : numInt - 1).padStart(3, '0');
  const plus1 = String(numInt === 999 ? 0 : numInt + 1).padStart(3, '0');
  const nearMisses = [minus1, plus1].filter(n => n !== winningNumber);

  // 3. Combined Tut (တွတ်)
  const allTut = Array.from(new Set([...permutations, ...nearMisses])).sort();

  return { permutations, nearMisses, allTut };
}
