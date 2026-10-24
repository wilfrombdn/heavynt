// Pure logic: dates, schedule, strength scoring, plateau detection. No DOM here.
import { REGIONS } from './data.js';

// ---------- dates ----------
const pad = (n) => String(n).padStart(2, '0');
export const dateKey = (d = new Date()) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
export const parseKey = (k) => { const [y, m, d] = k.split('-').map(Number); return new Date(y, m - 1, d); };
export const dayDiff = (a, b) => Math.round((parseKey(a) - parseKey(b)) / 86400000);
export const addDays = (key, n) => { const d = parseKey(key); d.setDate(d.getDate() + n); return dateKey(d); };

// Monday-based weekday: Mon = 0 ... Sun = 6
export const weekdayIdx = (key) => (parseKey(key).getDay() + 6) % 7;
export const mondayOf = (key) => addDays(key, -weekdayIdx(key));
export const WEEKDAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

// ---------- schedule ----------
// schedule = { start: 'YYYY-MM-DD' (a Monday = first day of "week 1"), days: [planId|null, ...] }
// days holds 7 entries per week (Mon..Sun) and the whole pattern repeats every days.length/7 weeks.
export function cycleIndex(schedule, key = dateKey()) {
  const len = schedule.days.length;
  if (!len) return -1;
  return ((dayDiff(key, schedule.start) % len) + len) % len;
}

// What the repeating schedule says for a date (ignores one-off switches).
export function scheduledPlan(state, key = dateKey()) {
  const i = cycleIndex(state.schedule, key);
  if (i < 0) return null;
  const id = state.schedule.days[i];
  return id ? state.plans.find((p) => p.id === id) || null : null;
}

// What you are actually doing on a date: a one-off switch wins over the schedule.
// state.overrides[key] = { plan: planId | null (rest), skipped: planId | null }
export function planForDate(state, key = dateKey()) {
  const o = state.overrides && state.overrides[key];
  if (o) return o.plan ? state.plans.find((p) => p.id === o.plan) || null : null;
  return scheduledPlan(state, key);
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
export const LB_PER_KG = 2.20462;
const REF_KG = 82; // the bodyweight the library's "std" ratios are based on
export const LEVELS = { novice: 0.7, intermediate: 1, advanced: 1.35 };

// What a lifter with this profile "should" lift for std = 1:
// strength scales with bodyweight^0.67, excess weight (BMI > 27) counts half,
// then adjust for sex, age and the chosen target level.
export function strengthFactors(st) {
  const unit = st.unit || 'lb';
  const kg = unit === 'lb' ? (st.bodyweight || 0) / LB_PER_KG : st.bodyweight || 0;
  let eff = kg;
  if (st.heightCm) {
    const m = st.heightCm / 100, cap = 27 * m * m;
    if (kg > cap) eff = cap + 0.5 * (kg - cap);
  }
  const age = st.age || 30;
  const sexF = st.sex === 'female' ? 0.7 : 1;
  const ageF = age < 18 ? 0.85 : age <= 35 ? 1 : Math.max(0.6, 1 - 0.01 * (age - 35));
  const baseKg = REF_KG * Math.pow(eff / REF_KG, 0.67) * sexF * ageF * (LEVELS[st.level] || 1);
  return { baseKg, unitPerKg: unit === 'lb' ? LB_PER_KG : 1 };
}

// Target estimated 1RM for an exercise, in the user's unit.
export function targetFor(ex, st) {
  const f = strengthFactors(st);
  return ex.std * f.baseKg * f.unitPerKg;
}

export function entryScore(ex, entry, st) {
  const bw = st.bodyweight;
  if (!bw) return null;
  if (!ex.bw && !(entry.weight > 0)) return null;
  const load = (ex.bw || 0) * bw + (entry.weight || 0);
  if (load <= 0) return null;
  const e = est1RM(load, entry.reps);
  return { score: e / targetFor(ex, st), e1rm: e, target: targetFor(ex, st) };
}

const SECONDARY_WEIGHT = 0.35;
const WINDOW_DAYS = 90;

// Returns { regionKey: { score, lifts:[{name, score, e1rm, target, primary}] } } for regions with data.
export function regionScores(state, now = new Date()) {
  const st = state.settings;
  const cutoff = now.getTime() - WINDOW_DAYS * 86400000;
  const best = new Map(); // exId -> best result in window
  for (const log of state.logs) {
    if (new Date(log.start).getTime() < cutoff) continue;
    for (const e of log.entries) {
      const ex = state.exercises.find((x) => x.id === e.ex);
      if (!ex) continue;
      const r = entryScore(ex, e, st);
      if (r && r.score > (best.get(ex.id)?.score ?? 0)) best.set(ex.id, r);
    }
  }
  const acc = {};
  for (const [exId, r] of best) {
    const ex = state.exercises.find((x) => x.id === exId);
    const add = (reg, w) => {
      if (!REGIONS[reg]) return;
      (acc[reg] ||= { sum: 0, w: 0, lifts: [] });
      acc[reg].sum += r.score * w; acc[reg].w += w;
      acc[reg].lifts.push({ name: ex.name, ...r, primary: w === 1 });
    };
    ex.primary.forEach((reg) => add(reg, 1));
    ex.secondary.forEach((reg) => add(reg, SECONDARY_WEIGHT));
  }
  const out = {};
  for (const [reg, a] of Object.entries(acc)) {
    out[reg] = { score: a.sum / a.w, lifts: a.lifts.sort((x, y) => y.score - x.score) };
  }
  return out;
}

// Under target = red (deeper the further under), at/above target = blue (brighter the further above).
const RED_LO = [150, 62, 66], RED_HI = [240, 64, 70], BLUE_LO = [58, 118, 230], BLUE_HI = [110, 175, 255];
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
export function scoreRGB(score) {
  return score < 1
    ? mix(RED_LO, RED_HI, Math.min(1, (1 - score) / 0.5))
    : mix(BLUE_LO, BLUE_HI, Math.min(1, (score - 1) / 0.4));
}

export function scoreColor(score) {
  if (score == null) return null;
  return `rgb(${scoreRGB(score).join(',')})`;
}

// Distance from target (0..1) -> how strongly the region glows.
export const scoreStrength = (score) => Math.min(1, Math.abs(score - 1) / 0.5);

export function scoreLabel(score) {
  if (score < 0.6) return 'Well below target';
  if (score < 0.85) return 'Below target';
  if (score < 1) return 'Almost there';
  if (score < 1.15) return 'On target';
  if (score < 1.4) return 'Above target';
  return 'Well above target';
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
