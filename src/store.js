// App state, persistence (localStorage) and first-run seeding.
import { LIBRARY, STARTER_PLANS, PAIRED, STARTER_ON_DAYS, STARTER_OFF_DAYS, STARTER_TODAY_IS_DAY } from './data.js';
import { dateKey, addDays } from './logic.js';

const KEY = 'workout-tracker-v1';
export const DEFAULT_SETTINGS = {
  unit: 'lb', bodyweight: 0, heightCm: 0, age: 30, sex: 'male', level: 'intermediate',
  plateauSessions: 3, reminderOn: false, reminderTime: '07:00',
};

export const uid = () => Math.random().toString(36).slice(2, 9) + Date.now().toString(36).slice(-4);

const makePlans = () => STARTER_PLANS.map((p) => ({
  id: uid(), name: p.name, day: p.day,
  items: p.items.map(([ex, sets, reps, note]) => ({ ex, sets, reps, weight: 0, note })),
}));

// Day 1..N of the on-block get their plan, everything else is rest; today lands on STARTER_TODAY_IS_DAY.
function starterSchedule(plans) {
  const days = Array(STARTER_ON_DAYS + STARTER_OFF_DAYS).fill(null);
  plans.forEach((p) => { days[p.day - 1] = p.id; });
  return { start: addDays(dateKey(), -(STARTER_TODAY_IS_DAY - 1)), days };
}

function seed() {
  const plans = makePlans();
  return {
    v: 3,
    settings: { ...DEFAULT_SETTINGS },
    exercises: LIBRARY.map((e) => ({ ...e })),
    plans,
    schedule: starterSchedule(plans),
    logs: [],
    active: null,
    snooze: {},
  };
}

// v1 -> v2: dumbbell lifts used to store ONE dumbbell; they now store both combined.
function migrate(s) {
  const lib = new Map(LIBRARY.map((e) => [e.id, e]));
  for (const e of s.exercises) { if (e.pair == null) e.pair = !!(lib.get(e.id) || {}).pair; }
  if ((s.v || 1) < 2) {
    for (const e of s.exercises) if (PAIRED.has(e.id)) { e.pair = true; e.std = Math.round(e.std * 2 * 100) / 100; }
    const dbl = (o) => { if (o.weight > 0 && PAIRED.has(o.ex)) o.weight *= 2; };
    (s.plans || []).forEach((p) => p.items.forEach(dbl));
    (s.logs || []).forEach((l) => l.entries.forEach(dbl));
    if (s.active) s.active.items.forEach(dbl);
    s.v = 2;
  }
  if (s.v < 3) {
    // v2 -> v3: swap the untouched generic starter plans for the real programme.
    const old = new Set(['Push', 'Pull', 'Legs']);
    const untouched = (p) => old.has(p.name) && p.items.every((i) => !i.weight) && !s.logs.some((l) => l.planId === p.id);
    const removed = new Set(s.plans.filter(untouched).map((p) => p.id));
    s.plans = s.plans.filter((p) => !removed.has(p.id));
    const fresh = makePlans().filter((p) => !s.plans.some((q) => q.name === p.name));
    s.plans.push(...fresh);
    const usingOnlyOld = s.schedule.days.every((d) => !d || removed.has(d));
    if (usingOnlyOld) {
      const all = makePlans().map((p) => s.plans.find((q) => q.name === p.name) || p);
      all.forEach((p, i) => { p.day = STARTER_PLANS[i].day; });
      s.schedule = starterSchedule(all);
    }
    s.plans.forEach((p) => delete p.day);
    s.v = 3;
  }
  return s;
}

function load() {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) {
      const s = migrate(JSON.parse(raw)); // migrate first: only already-saved exercises need converting
      // Pick up library additions shipped in newer app versions without touching user data.
      const have = new Set(s.exercises.map((e) => e.id));
      for (const e of LIBRARY) if (!have.has(e.id)) s.exercises.push({ ...e });
      s.snooze ||= {};
      s.settings = { ...DEFAULT_SETTINGS, ...s.settings };
      return s;
    }
  } catch (e) { /* corrupted storage: fall through to a fresh state */ }
  return seed();
}

export const state = load();
const listeners = new Set();

export function save() {
  try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) { /* storage full/blocked */ }
  listeners.forEach((fn) => fn());
}
export const onChange = (fn) => listeners.add(fn);

export const exById = (id) => state.exercises.find((e) => e.id === id);
export const planById = (id) => state.plans.find((p) => p.id === id);

export function exportData() { return JSON.stringify(state, null, 2); }
export function importData(text) {
  const s = JSON.parse(text);
  if (!s || !Array.isArray(s.exercises) || !Array.isArray(s.logs)) throw new Error('Not a workout backup');
  Object.keys(state).forEach((k) => delete state[k]);
  Object.assign(state, migrate(s));
  state.settings = { ...DEFAULT_SETTINGS, ...state.settings };
  state.snooze ||= {};
  save();
}
