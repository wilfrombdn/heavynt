import test from 'node:test';
import assert from 'node:assert/strict';
import { weekdayIdx, mondayOf, targetFor, cycleIndex, buildOnOff, planForDate, regionScores, scoreColor, plateauInfo, addDays, dateKey } from '../src/logic.js';
import { LIBRARY } from '../src/data.js';

const mk = (over = {}) => ({
  settings: { unit: 'lb', bodyweight: 180, plateauSessions: 3 },
  exercises: LIBRARY.map((e) => ({ ...e })),
  plans: [{ id: 'a', name: 'A', items: [] }, { id: 'b', name: 'B', items: [] }],
  schedule: { start: '2026-01-01', days: buildOnOff(7, 7, ['a', 'b']) },
  logs: [], snooze: {}, ...over,
});

test('7 on / 7 off cycle repeats', () => {
  const s = mk();
  assert.equal(s.schedule.days.length, 14);
  assert.equal(planForDate(s, '2026-01-01').id, 'a');
  assert.equal(planForDate(s, '2026-01-02').id, 'b');
  assert.equal(planForDate(s, '2026-01-08'), null);   // day 8 = rest
  assert.equal(planForDate(s, '2026-01-14'), null);   // day 14 = rest
  assert.equal(planForDate(s, '2026-01-15').id, 'a'); // cycle restarts
  assert.equal(cycleIndex(s.schedule, '2025-12-31'), 13); // before start still works
});

test('strength map: strong chest -> blue, weak legs -> red', () => {
  const log = (ex, weight, reps) => ({ start: new Date().toISOString(), entries: [{ ex, sets: 3, reps, weight }] });
  const s = mk({ logs: [log('bench', 270, 5), log('squat', 95, 5)] });
  const r = regionScores(s);
  assert.ok(r.chest.score > 1.2, 'chest strong');
  assert.ok(r.quads.score < 0.7, 'quads weak');
  const rr = scoreColor(0.5).match(/\d+/g).map(Number);
  const rb = scoreColor(1.5).match(/\d+/g).map(Number);
  assert.ok(rr[0] > rr[2], 'red'); assert.ok(rb[2] > rb[0], 'blue');
  assert.equal(r.calves, undefined);
});

test('plateau detection and snooze', () => {
  const d = (n) => new Date(Date.now() - n * 86400000).toISOString();
  const e = (n, w) => ({ start: d(n), entries: [{ ex: 'bench', sets: 3, reps: 8, weight: w, at: d(n) }] });
  let s = mk({ logs: [e(20, 130), e(13, 135), e(6, 135)] });
  assert.equal(plateauInfo(s, 'bench', 135), null); // only 2 sessions at 135, <28 days
  s = mk({ logs: [e(20, 135), e(13, 135), e(6, 135)] });
  assert.equal(plateauInfo(s, 'bench', 135).sessions, 3);
  assert.equal(plateauInfo(s, 'bench', 140), null); // already bumped in the plan
  s.snooze.bench = 3;
  assert.equal(plateauInfo(s, 'bench', 135), null); // snoozed
  s = mk({ logs: [e(40, 100), e(30, 100)] });
  assert.ok(plateauInfo(s, 'bench', 100), 'time-based plateau (2 sessions, 30 days)');
});

test('targets personalise: older, female, heavier-with-fat lifters get different bars', () => {
  const ex = LIBRARY.find((e) => e.id === 'bench');
  const base = { unit: 'lb', bodyweight: 180, heightCm: 180, age: 30, sex: 'male', level: 'intermediate' };
  const t = (o) => targetFor(ex, { ...base, ...o });
  assert.ok(Math.abs(t({}) - 1.25 * 82 * 2.20462) < 15, 'about 1.25x bodyweight near the reference weight');
  assert.ok(t({ sex: 'female' }) < t({}));
  assert.ok(t({ age: 55 }) < t({}));
  assert.ok(t({ level: 'advanced' }) > t({}) && t({ level: 'novice' }) < t({}));
  assert.ok(t({ bodyweight: 260 }) > t({}) && t({ bodyweight: 260 }) < t({}) * (260 / 180), 'heavier = higher bar, but not proportionally');
  const big = t({ bodyweight: 260, heightCm: 170 }); // short + heavy: excess weight counts half
  assert.ok(big < t({ bodyweight: 260, heightCm: 195 }));
});

test('colour: under target is red, on/above target is blue', () => {
  const c = (n) => scoreColor(n).match(/\d+/g).map(Number);
  assert.ok(c(0.9)[0] > c(0.9)[2], 'red-ish at 0.9');
  assert.ok(c(1.0)[2] > c(1.0)[0], 'blue at exactly target');
  assert.ok(c(1.3)[2] > c(1.3)[0]);
});

test('weekly schedule: Wed-Sun of week 1, week 2 off, repeats every 2 weeks across month ends', () => {
  const days = Array(14).fill(null);
  ['a', 'b', 'a', 'b', 'a'].forEach((id, i) => { days[2 + i] = id; });
  const s = mk({ schedule: { start: '2026-10-19', days } }); // Monday of the week containing Fri Oct 23
  assert.equal(planForDate(s, '2026-10-23').id, 'a');   // Friday, week 1
  assert.equal(planForDate(s, '2026-10-21').id, 'a');   // Wednesday = first workout
  assert.equal(planForDate(s, '2026-10-20'), null);     // Tuesday = rest
  assert.equal(planForDate(s, '2026-10-28'), null);     // week 2 Wednesday = off
  assert.equal(planForDate(s, '2026-11-04').id, 'a');   // two weeks later, next month: on again
  assert.equal(planForDate(s, '2026-11-11'), null);     // off week
  assert.equal(planForDate(s, '2026-12-02').id, 'a');   // still on the same rhythm
  assert.equal(weekdayIdx('2026-10-19'), 0);
  assert.equal(mondayOf('2026-10-23'), '2026-10-19');
});
