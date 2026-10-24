// App state, persistence (localStorage) and first-run seeding.
import { LIBRARY, STARTER_PLANS } from './data.js';
import { dateKey, buildOnOff } from './logic.js';

const KEY = 'workout-tracker-v1';
export const DEFAULT_SETTINGS = {
  unit: 'lb', bodyweight: 0, heightCm: 0, age: 30, sex: 'male', level: 'intermediate',
  plateauSessions: 3, reminderOn: false, reminderTime: '07:00',
};

export const uid = () => Math.random().toString(36).slice(2, 9) + Date.now().toString(36).slice(-4);

function seed() {
  const plans = STARTER_PLANS.map((p) => ({
    id: uid(),
    name: p.name,
    items: p.items.map(([ex, sets, reps]) => ({ ex, sets, reps, weight: 0 })),
  }));
  return {
    v: 1,
    settings: { ...DEFAULT_SETTINGS },
    exercises: LIBRARY.map((e) => ({ ...e })),
    plans,
    schedule: { start: dateKey(), days: buildOnOff(7, 7, plans.map((p) => p.id)) },
    logs: [],
    active: null,
    snooze: {},
  };
}

function load() {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) {
      const s = JSON.parse(raw);
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
  Object.assign(state, s);
  state.settings = { ...DEFAULT_SETTINGS, ...state.settings };
  state.snooze ||= {};
  save();
}
