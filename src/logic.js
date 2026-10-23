// Pure logic: dates, schedule, strength scoring, plateau detection. No DOM here.
import { REGIONS } from './data.js';

// ---------- dates ----------
const pad = (n) => String(n).padStart(2, '0');
export const dateKey = (d = new Date()) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
export const parseKey = (k) => { const [y, m, d] = k.split('-').map(Number); return new Date(y, m - 1, d); };
export const dayDiff = (a, b) => Math.round((parseKey(a) - parseKey(b)) / 86400000);
export const addDays = (key, n) => { const d = parseKey(key); d.setDate(d.getDate() + n); return dateKey(d); };

// ---------- schedule ----------
// schedule = { start: 'YYYY-MM-DD', days: [planId|null, ...] }  (cycle repeats forever)
export function cycleIndex(schedule, key = dateKey()) {
  const len = schedule.days.length;
  if (!len) return -1;
  return ((dayDiff(key, schedule.start) % len) + len) % len;
}

export function planForDate(state, key = dateKey()) {
  const i = cycleIndex(state.schedule, key);
  if (i < 0) return null;
  const id = state.schedule.days[i];
  return id ? state.plans.find((p) => p.id === id) || null : null;
}

// Build an "X on / Y off" cycle, rotating through the given plan ids on the "on" days.
export function buildOnOff(on, off, planIds) {
  const days = [];
  for (let i = 0; i < on; i++) days.push(planIds.length ? planIds[i % planIds.length] : null);
  for (let i = 0; i < off; i++) days.push(null);
  return days;
}

// ---------- strength ----------
export const est1RM = (weight, reps) => (reps <= 1 ? weight : weight * (1 + reps / 30)); // Epley

export function loadOf(ex, weight, bodyweight) {
  return (ex.bw || 0) * bodyweight + (weight || 0);
}

// Weight 0 is valid for bodyweight moves; otherwise no weight = no data.
export function entryScore(ex, entry, bodyweight) {
  if (!bodyweight) return null;
  if (!ex.bw && !(entry.weight > 0)) return null;
  const load = loadOf(ex, entry.weight, bodyweight);
  if (load <= 0) return null;
  return est1RM(load, entry.reps) / (bodyweight * ex.std);
}

const SECONDARY_WEIGHT = 0.35;
const WINDOW_DAYS = 90;

// Returns { regionKey: { score, lifts:[{name, score}] } } for regions with data.
export function regionScores(state, now = new Date()) {
  const bw = state.settings.bodyweight;
  const cutoff = now.getTime() - WINDOW_DAYS * 86400000;
  const best = new Map(); // exId -> best score in window
  for (const log of state.logs) {
    if (new Date(log.start).getTime() < cutoff) continue;
    for (const e of log.entries) {
      const ex = state.exercises.find((x) => x.id === e.ex);
      if (!ex) continue;
      const s = entryScore(ex, e, bw);
      if (s != null && s > (best.get(ex.id) ?? 0)) best.set(ex.id, s);
    }
  }
  const acc = {};
  for (const [exId, s] of best) {
    const ex = state.exercises.find((x) => x.id === exId);
    const add = (r, w) => {
      if (!REGIONS[r]) return;
      (acc[r] ||= { sum: 0, w: 0, lifts: [] });
      acc[r].sum += s * w; acc[r].w += w;
      acc[r].lifts.push({ name: ex.name, score: s, primary: w === 1 });
    };
    ex.primary.forEach((r) => add(r, 1));
    ex.secondary.forEach((r) => add(r, SECONDARY_WEIGHT));
  }
  const out = {};
  for (const [r, a] of Object.entries(acc)) {
    out[r] = { score: a.sum / a.w, lifts: a.lifts.sort((x, y) => y.score - x.score) };
  }
  return out;
}

// Map a score to a colour: red (weak) -> grey (average) -> blue (strong).
const RED = [229, 72, 77], GREY = [110, 110, 110], BLUE = [59, 130, 246];
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
export function scoreColor(score) {
  if (score == null) return null;
  const rgb = score < 1
    ? mix(GREY, RED, Math.min(1, (1 - score) / 0.45))
    : mix(GREY, BLUE, Math.min(1, (score - 1) / 0.5));
  return `rgb(${rgb.join(',')})`;
}

export function scoreLabel(score) {
  if (score < 0.6) return 'Weak';
  if (score < 0.85) return 'Below average';
  if (score < 1.15) return 'Average';
  if (score < 1.4) return 'Strong';
  return 'Very strong';
}

// ---------- history helpers ----------
export function exerciseHistory(state, exId) {
  const out = [];
  for (const log of state.logs) {
    for (const e of log.entries) if (e.ex === exId) out.push({ ...e, at: e.at || log.start });
  }
  return out.sort((a, b) => new Date(a.at) - new Date(b.at));
}

// Plateau: latest weight has not gone up for `sessions` sessions (or 4+ weeks over 2+ sessions),
// and we haven't already asked within the snooze window. Returns null or a description.
export function plateauInfo(state, exId, currentWeight) {
  const hist = exerciseHistory(state, exId);
  if (!hist.length || !(currentWeight > 0)) return null;
  const n = state.settings.plateauSessions;
  const last = hist[hist.length - 1].weight;
  if (last !== currentWeight) return null; // plan weight already moved on
  let streak = 0;
  for (let i = hist.length - 1; i >= 0 && hist[i].weight === last; i--) streak++;
  const since = hist[hist.length - streak].at;
  const days = Math.floor((Date.now() - new Date(since).getTime()) / 86400000);
  const hit = streak >= n || (streak >= 2 && days >= 28);
  if (!hit) return null;
  const snoozed = state.snooze[exId];
  if (snoozed != null && hist.length - snoozed < n) return null;
  return { sessions: streak, since, days, weight: last, count: hist.length };
}
