// Synthesized Web Audio FX for 3D Admin Portal (zero external assets)

const SOUND_PREF_KEY = '3d_admin_sound_enabled';
let audioCtx = null;

function getAudioContext() {
  if (typeof window === 'undefined') return null;
  const Ctx = window.AudioContext || window.webkitAudioContext;
  if (!Ctx) return null;
  if (!audioCtx) {
    try {
      audioCtx = new Ctx();
    } catch {
      return null;
    }
  }
  if (audioCtx.state === 'suspended') {
    audioCtx.resume().catch(() => {});
  }
  return audioCtx;
}

export function isSoundEnabled() {
  if (typeof window === 'undefined') return true;
  try {
    return window.localStorage.getItem(SOUND_PREF_KEY) !== 'false';
  } catch {
    return true;
  }
}

export function setSoundEnabled(enabled) {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(SOUND_PREF_KEY, enabled ? 'true' : 'false');
  } catch {}
}

function playToneSequence(steps) {
  if (!isSoundEnabled()) return;
  const ctx = getAudioContext();
  if (!ctx) return;

  const baseTime = ctx.currentTime;
  for (const step of steps) {
    try {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      const startAt = baseTime + (step.delay || 0);
      const dur = step.duration || 0.06;

      osc.type = step.type || 'triangle';
      osc.frequency.setValueAtTime(step.freq, startAt);
      if (step.endFreq) {
        osc.frequency.exponentialRampToValueAtTime(step.endFreq, startAt + dur);
      }

      gain.gain.setValueAtTime(0.0001, startAt);
      gain.gain.linearRampToValueAtTime(step.gain || 0.09, startAt + 0.004);
      gain.gain.exponentialRampToValueAtTime(0.0001, startAt + dur);

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(startAt);
      osc.stop(startAt + dur + 0.01);
    } catch {}
  }
}

export const soundFx = {
  playClick() {
    playToneSequence([{ freq: 720, endFreq: 320, duration: 0.032, gain: 0.08, type: 'triangle' }]);
  },
  playSoftTap() {
    playToneSequence([{ freq: 540, endFreq: 420, duration: 0.04, gain: 0.06, type: 'sine' }]);
  },
  playCopy() {
    playToneSequence([
      { freq: 660, duration: 0.055, gain: 0.075, type: 'sine', delay: 0 },
      { freq: 990, duration: 0.075, gain: 0.075, type: 'sine', delay: 0.045 }
    ]);
  },
  playSuccess() {
    playToneSequence([
      { freq: 523.25, duration: 0.09, gain: 0.08, type: 'triangle', delay: 0 },
      { freq: 659.25, duration: 0.09, gain: 0.08, type: 'triangle', delay: 0.055 },
      { freq: 783.99, duration: 0.12, gain: 0.085, type: 'triangle', delay: 0.11 },
      { freq: 1046.5, duration: 0.15, gain: 0.07, type: 'sine', delay: 0.165 }
    ]);
  },
  playError() {
    playToneSequence([
      { freq: 220, endFreq: 170, duration: 0.09, gain: 0.11, type: 'triangle', delay: 0 },
      { freq: 165, endFreq: 130, duration: 0.12, gain: 0.11, type: 'triangle', delay: 0.09 }
    ]);
  }
};
