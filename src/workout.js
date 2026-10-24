// Workout tab: start today's plan, tap a section to complete it, hold to edit, plateau prompts.
import { state, save, uid, exById, planById } from './store.js';
import { dateKey, planForDate, scheduledPlan, exerciseHistory, plateauInfo, addDays, cycleIndex } from './logic.js';
import { h, ask, openSheet, stepper, exerciseSelect, toast, icon, buzz, fmtNum, fmtDate, fmtShort } from './ui.js';

export const weightStep = () => (state.settings.unit === 'kg' ? 2.5 : 5);
export const unit = () => state.settings.unit;
export const weightLabel = (ex) => (ex && ex.pair ? `Weight (${unit()}) · both dumbbells combined` : `Weight (${unit()})`);
export const PAIR_NOTE = 'Add both dumbbells together, e.g. two 40s = 80.';

const lastEntry = (exId) => exerciseHistory(state, exId).slice(-1)[0];
export const todaysLog = (planId) =>
  state.logs.find((l) => dateKey(new Date(l.start)) === dateKey() && (!planId || l.planId === planId));

export function describe(it) {
  const ex = exById(it.ex);
  const w = it.weight > 0 ? ` @ ${fmtNum(it.weight)} ${unit()}${ex && ex.pair ? ' total' : ''}` : ex && ex.bw ? ' · bodyweight' : '';
  return `${it.sets} × ${it.reps}${w}`;
}

// ---------- starting ----------
export async function startWorkout(planId) {
  if (state.active) return;
  const plan = planId ? planById(planId) : null;
  state.active = {
    id: uid(),
    start: new Date().toISOString(),
    planId: plan ? plan.id : null,
    planName: plan ? plan.name : 'Quick workout',
    items: plan ? plan.items.map((i) => ({ ...i, done: false, at: null })) : [],
  };
  save();
  await promptPlateaus(state.active.items);
}

// Offer a weight increase for lifts that have been stuck at the same weight.
async function promptPlateaus(items) {
  for (const it of items) {
    const info = plateauInfo(state, it.ex, it.weight);
    if (!info) continue;
    const ex = exById(it.ex);
    const choice = await new Promise((resolve) => {
      let target = info.weight + weightStep();
      let answered = false;
      openSheet('Time to level up?', (body, close) => {
        body.append(
          h('p', null, h('b', null, ex.name), ` has been ${fmtNum(info.weight)} ${unit()} for ${info.sessions} sessions` +
            ` (since ${fmtShort(info.since)}). Ready to go heavier?`),
          stepper({ value: target, min: 0, step: weightStep(), label: `New weight (${unit()})`, onChange: (v) => { target = v; } }),
          h('div', { class: 'btn-col' },
            h('button', { class: 'btn primary', onClick: () => { answered = true; close(); resolve({ up: target }); } }, 'Increase weight'),
            h('button', { class: 'btn', onClick: () => { answered = true; close(); resolve({ up: null }); } }, 'Not yet')));
      }, { onClose: () => { if (!answered) resolve({ up: null }); } });
    });
    if (choice.up != null && choice.up !== info.weight) {
      it.weight = choice.up;
      const pi = state.active && planById(state.active.planId)?.items.find((p) => p.ex === it.ex);
      if (pi) pi.weight = choice.up;
      toast(`${ex.name} → ${fmtNum(choice.up)} ${unit()}`);
    } else {
      state.snooze[it.ex] = info.count; // ask again after N more sessions
    }
    save();
  }
}

// ---------- tap / hold ----------
function pressable(el, { onTap, onHold }) {
  let timer, sx, sy, held = false;
  const cancel = () => { clearTimeout(timer); el.classList.remove('pressing'); };
  el.addEventListener('pointerdown', (e) => {
    held = false; sx = e.clientX; sy = e.clientY;
    el.classList.add('pressing');
    timer = setTimeout(() => { held = true; el.classList.remove('pressing'); buzz(40); onHold(); }, 480);
  });
  el.addEventListener('pointermove', (e) => { if (Math.hypot(e.clientX - sx, e.clientY - sy) > 10) cancel(); });
  el.addEventListener('pointerup', cancel);
  el.addEventListener('pointercancel', cancel);
  el.addEventListener('pointerleave', cancel);
  // A click is only delivered for a real tap (not a scroll); swallow the one that follows a hold.
  el.addEventListener('click', () => { if (held) { held = false; return; } onTap(); });
  el.addEventListener('contextmenu', (e) => e.preventDefault());
}

function toggleDone(it) {
  it.done = !it.done;
  it.at = it.done ? new Date().toISOString() : null;
  buzz(it.done ? 20 : 10);
  save();
}

function editItem(it) {
  const ex = exById(it.ex);
  const a = state.active;
  const draft = { sets: it.sets, reps: it.reps, weight: it.weight };
  openSheet(ex.name, (body, close) => {
    const last = lastEntry(it.ex);
    if (last) body.append(h('p', { class: 'muted' }, `Last time: ${last.sets} × ${last.reps} @ ${fmtNum(last.weight)} ${unit()}${ex.pair ? ' total' : ''} · ${fmtShort(last.at)}`));
    body.append(
      h('div', { class: 'grid2' },
        stepper({ value: draft.sets, min: 1, max: 20, label: 'Sets', onChange: (v) => { draft.sets = v; } }),
        stepper({ value: draft.reps, min: 1, max: 100, label: 'Reps', onChange: (v) => { draft.reps = v; } })),
      stepper({ value: draft.weight, min: 0, step: weightStep(), label: weightLabel(ex), onChange: (v) => { draft.weight = v; } }),
      ex.pair ? h('div', { class: 'hint note' }, PAIR_NOTE) : null,
      h('div', { class: 'btn-col' },
        h('button', { class: 'btn primary', onClick: async () => {
          Object.assign(it, draft);
          close();
          if (!it.done) { it.done = true; it.at = new Date().toISOString(); buzz(20); }
          save();
          await offerPlanUpdate(it, a);
        } }, it.done ? 'Save' : 'Save & complete'),
        h('button', { class: 'btn danger', onClick: () => { close(); a.items.splice(a.items.indexOf(it), 1); save(); } }, 'Remove from this workout')));
  });
}

async function offerPlanUpdate(it, a) {
  const plan = planById(a.planId);
  const pi = plan && plan.items.find((p) => p.ex === it.ex);
  if (!pi || (pi.sets === it.sets && pi.reps === it.reps && pi.weight === it.weight)) return;
  const yes = await ask('Update the plan?',
    `Change ${exById(it.ex).name} from ${describe(pi)} to ${describe(it)} permanently in "${plan.name}"?`,
    [{ label: 'Yes, update the plan', value: true, primary: true }, { label: 'Just this workout', value: false }]);
  if (yes) { Object.assign(pi, { sets: it.sets, reps: it.reps, weight: it.weight }); save(); toast('Plan updated'); }
}

function addExtra() {
  openSheet('Add exercise', (body, close) => {
    let exId = '';
    const draft = { sets: 3, reps: 10, weight: 0 };
    const holder = h('div');
    const fields = () => {
      holder.replaceChildren(
        h('div', { class: 'grid2' },
          stepper({ value: draft.sets, min: 1, max: 20, label: 'Sets', onChange: (v) => { draft.sets = v; } }),
          stepper({ value: draft.reps, min: 1, max: 100, label: 'Reps', onChange: (v) => { draft.reps = v; } })),
        stepper({ value: draft.weight, min: 0, step: weightStep(), label: weightLabel(exById(exId)), onChange: (v) => { draft.weight = v; } }),
        exId && exById(exId).pair ? h('div', { class: 'hint note' }, PAIR_NOTE) : null);
    };
    fields();
    body.append(
      exerciseSelect(state.exercises, { onChange: (v) => {
        exId = v;
        const l = v && lastEntry(v);
        if (l) Object.assign(draft, { sets: l.sets, reps: l.reps, weight: l.weight });
        fields();
      } }),
      holder,
      h('div', { class: 'btn-col' },
        h('button', { class: 'btn primary', onClick: () => {
          if (!exId) return toast('Pick an exercise');
          close();
          state.active.items.push({ ex: exId, ...draft, done: false, at: null, extra: true });
          save();
        } }, 'Add')));
  });
}

async function finish() {
  const a = state.active;
  const done = a.items.filter((i) => i.done);
  if (!done.length) {
    const r = await ask('Nothing completed', 'No exercises were checked off yet.', [
      { label: 'Keep going', value: 'keep', primary: true },
      { label: 'Discard workout', value: 'discard' }]);
    if (r === 'discard') { state.active = null; save(); }
    return;
  }
  const left = a.items.length - done.length;
  if (left) {
    const r = await ask('Finish workout?', `${left} exercise${left > 1 ? 's' : ''} not completed will be skipped.`, [
      { label: 'Finish & save', value: 'ok', primary: true }, { label: 'Keep going', value: 'keep' }]);
    if (r !== 'ok') return;
  }
  state.logs.push({
    id: a.id, start: a.start, end: new Date().toISOString(), planId: a.planId, planName: a.planName,
    entries: done.map((i) => ({ ex: i.ex, sets: i.sets, reps: i.reps, weight: i.weight, at: i.at })),
  });
  state.active = null;
  save();
  toast('Workout saved');
  document.dispatchEvent(new CustomEvent('navigate', { detail: 'home' }));
}

// ---------- view ----------
function exerciseCard(it, interactive) {
  const ex = exById(it.ex);
  const last = lastEntry(it.ex);
  const plateau = interactive && plateauInfo(state, it.ex, it.weight);
  const card = h('div', { class: 'item' + (it.done ? ' done' : '') },
    h('div', { class: 'check' }, it.done ? '✓' : ''),
    h('div', { class: 'grow' },
      h('div', { class: 'name' }, ex.name),
      h('div', { class: 'sub' }, describe(it) + (interactive && !ex.bw && !(it.weight > 0) ? ' · hold to set weight' : '')),
      it.note ? h('div', { class: 'hint note-line' }, it.note) : null,
      last && interactive ? h('div', { class: 'hint' }, `Last: ${last.sets} × ${last.reps} @ ${fmtNum(last.weight)} ${unit()}${ex.pair ? ' total' : ''}`) : null),
    plateau ? h('span', { class: 'tag' }, 'Plateau') : null);
  if (interactive) pressable(card, { onTap: () => toggleDone(it), onHold: () => editItem(it) });
  return card;
}

export function nextWorkout() {
  for (let i = 1; i <= state.schedule.days.length; i++) {
    const key = addDays(dateKey(), i);
    const p = planForDate(state, key);
    if (p) return { key, plan: p };
  }
  return null;
}

export function renderWorkout(c) {
  const a = state.active;
  if (a) {
    const done = a.items.filter((i) => i.done).length;
    c.append(
      h('div', { class: 'page-head' },
        h('h1', null, a.planName),
        h('div', { class: 'muted' }, `Started ${fmtDate(a.start)}`)),
      h('div', { class: 'progress' }, h('div', { style: `width:${a.items.length ? (done / a.items.length) * 100 : 0}%` })),
      h('p', { class: 'muted small' }, `${done}/${a.items.length} done · tap to complete · hold to edit`),
      ...a.items.map((it) => exerciseCard(it, true)),
      h('button', { class: 'btn', onClick: addExtra }, '+ Add exercise'),
      h('button', { class: 'btn primary', onClick: finish }, 'Finish workout'));
    return;
  }

  const plan = planForDate(state);
  const doneToday = plan && todaysLog(plan.id);
  const o = state.overrides[dateKey()];
  const skipped = o && o.skipped && state.plans.find((p) => p.id === o.skipped);
  c.append(h('div', { class: 'page-head' }, h('h1', null, 'Workout'),
    h('div', { class: 'muted' }, new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' }))));

  if (plan) {
    c.append(h('div', { class: 'card' },
      h('div', { class: 'row' }, h('div', { class: 'grow' }, h('div', { class: 'eyebrow' }, o ? 'Today · switched' : 'Today'), h('div', { class: 'big' }, plan.name),
        skipped ? h('div', { class: 'muted small' }, `Skipped ${skipped.name}`) : null),
        doneToday ? h('span', { class: 'tag' }, 'Done ✓') : null)),
      ...plan.items.map((it) => exerciseCard({ ...it, done: false }, false)),
      h('button', { class: 'btn primary', onClick: () => startWorkout(plan.id) }, doneToday ? 'Start again' : 'Start workout'),
      h('button', { class: 'btn', onClick: switchToday }, "Switch today's workout"));
  } else {
    const n = nextWorkout();
    c.append(h('div', { class: 'card center' },
      h('div', { class: 'big' }, 'Rest day'),
      h('div', { class: 'muted' }, n ? `Next: ${n.plan.name} on ${new Date(n.key + 'T00:00').toLocaleDateString(undefined, { weekday: 'long', month: 'short', day: 'numeric' })}` : 'No workouts scheduled'),
      skipped ? h('div', { class: 'muted small' }, `Skipped ${skipped.name}`) : null),
      h('button', { class: 'btn primary', onClick: switchToday }, 'Train anyway: choose a workout'));
  }
}

// Pick a different workout for today. Skipping the scheduled one drops it entirely:
// tomorrow carries on with whatever the schedule says next.
function switchToday() {
  const key = dateKey();
  const sched = scheduledPlan(state, key);
  const current = planForDate(state, key);
  const set = (plan, skippedId) => {
    if (sched && plan && plan.id === sched.id) delete state.overrides[key]; // back to the normal plan
    else state.overrides[key] = { plan: plan ? plan.id : null, skipped: skippedId || null };
    save();
  };
  openSheet("Switch today's workout", (body, close) => {
    body.append(h('p', { class: 'muted' }, sched ? `Scheduled today: ${sched.name}. Pick what you want to do instead.` : 'Nothing is scheduled today. Pick a workout.'));
    const list = h('div', { class: 'opts' });
    const row = (label, on, fn) => list.append(h('button', { type: 'button', class: 'opt' + (on ? ' on' : ''), onClick: async () => { close(); await fn(); } },
      h('span', null, label), on ? icon('check') : null));

    for (const p of state.plans) {
      const isSched = sched && p.id === sched.id;
      row(p.name + (isSched ? ' · scheduled' : ''), current && p.id === current.id, async () => {
        if (current && p.id === current.id) return;
        if (!sched || isSched) { set(p); toast(`Today: ${p.name}`); return; }
        const choice = await ask(`Skip ${sched.name}?`,
          `${sched.name} is scheduled for today. If you skip it, it's dropped completely and tomorrow carries on with the next day in your schedule.`,
          [{ label: `Yes, skip ${sched.name}`, value: 'skip', primary: true },
            { label: 'No, do both (this one as an extra)', value: 'both' }]);
        if (choice === 'skip') { set(p, sched.id); toast(`Skipped ${sched.name}. Today: ${p.name}`); }
        else if (choice === 'both') await startWorkout(p.id);
      });
    }
    if (sched || current) {
      row('Rest day (skip today)', !current, async () => {
        if (!current && !sched) return;
        const choice = sched ? await ask(`Skip ${sched.name}?`, 'Make today a rest day. Tomorrow carries on with the next day in your schedule.',
          [{ label: `Yes, skip ${sched.name}`, value: 'skip', primary: true }, { label: 'Cancel', value: null }]) : 'skip';
        if (choice === 'skip') { set(null, sched && sched.id); toast('Today is a rest day'); }
      });
    }
    row('Empty workout (nothing from a plan)', false, () => startWorkout(null));
    body.append(list);
    if (state.overrides[key]) body.append(h('button', { class: 'btn', onClick: () => { close(); delete state.overrides[key]; save(); toast('Back to your schedule'); } }, 'Reset to scheduled plan'));
  });
}

export const dayInfo = () => ({ idx: cycleIndex(state.schedule), len: state.schedule.days.length });
