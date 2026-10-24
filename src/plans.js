// Plans tab: schedule (any on/off cycle) and plan editor with exercise drop-downs.
import { state, save, uid, exById } from './store.js';
import { dateKey, addDays, cycleIndex, buildOnOff, parseKey } from './logic.js';
import { h, openSheet, ask, stepper, exerciseSelect, selectify, toast } from './ui.js';
import { unit, weightStep } from './workout.js';

const dayName = (key) => parseKey(key).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
const planName = (id) => (state.plans.find((p) => p.id === id) || {}).name;

function cycleSummary() {
  const d = state.schedule.days;
  const on = d.filter(Boolean).length;
  return `${d.length}-day cycle · ${on} on / ${d.length - on} off`;
}

export function renderPlans(c) {
  const today = dateKey();
  const idx = cycleIndex(state.schedule, today);
  const strip = h('div', { class: 'strip' });
  for (let i = 0; i < 14; i++) {
    const k = addDays(today, i);
    const p = planName(state.schedule.days[cycleIndex(state.schedule, k)]);
    strip.append(h('div', { class: 'cell' + (i === 0 ? ' today' : '') + (p ? ' on' : '') },
      h('div', { class: 'd' }, parseKey(k).toLocaleDateString(undefined, { weekday: 'narrow' })),
      h('div', { class: 'n' }, parseKey(k).getDate()),
      h('div', { class: 'p' }, p ? p.slice(0, 4) : '–')));
  }

  c.append(
    h('div', { class: 'page-head' }, h('h1', null, 'Plans')),
    h('div', { class: 'card' },
      h('div', { class: 'row' }, h('div', { class: 'grow' }, h('div', { class: 'eyebrow' }, 'Schedule'),
        h('div', { class: 'muted' }, cycleSummary() + (idx >= 0 ? ` · today is day ${idx + 1}` : ''))),
        h('button', { class: 'btn slim', onClick: editSchedule }, 'Edit')),
      h('div', { class: 'strip-wrap' }, strip)),
    h('div', { class: 'section-title' }, 'Workout plans'),
    ...state.plans.map((p) => h('button', { class: 'card tap', onClick: () => editPlan(p) },
      h('div', { class: 'row' }, h('div', { class: 'grow' }, h('div', { class: 'big' }, p.name),
        h('div', { class: 'muted' }, p.items.map((i) => (exById(i.ex) || {}).name).filter(Boolean).slice(0, 4).join(' · ') || 'Empty')),
        h('span', { class: 'chev' }, '›')))),
    h('button', { class: 'btn primary', onClick: newPlan }, '+ New plan'));
}

function newPlan() {
  const p = { id: uid(), name: `Plan ${state.plans.length + 1}`, items: [] };
  state.plans.push(p);
  save();
  editPlan(p);
}

// ---------- plan editor ----------
function editPlan(plan) {
  openSheet('Edit plan', (body, close) => {
    const draw = () => {
      body.replaceChildren();
      const name = h('input', { class: 'text', value: plan.name, placeholder: 'Plan name' });
      name.addEventListener('input', () => { plan.name = name.value; save(); });
      body.append(h('div', { class: 'field' }, h('label', null, 'Name'), name));

      plan.items.forEach((it, i) => {
        const num = (label, key, step) => {
          const inp = h('input', { type: 'number', inputmode: 'decimal', value: it[key], step: String(step), min: '0' });
          inp.addEventListener('focus', () => inp.select());
          inp.addEventListener('input', () => { it[key] = parseFloat(inp.value) || 0; save(); });
          return h('div', { class: 'mini' }, h('label', null, label), inp);
        };
        body.append(h('div', { class: 'plan-row' },
          exerciseSelect(state.exercises, { value: it.ex, onChange: (v) => { it.ex = v; save(); } }),
          h('div', { class: 'minis' }, num('Sets', 'sets', 1), num('Reps', 'reps', 1), num(`Weight (${unit()})`, 'weight', weightStep()),
            h('div', { class: 'row-actions' },
              h('button', { class: 'icon-btn', 'aria-label': 'Move up', onClick: () => { move(i, -1); } }, '↑'),
              h('button', { class: 'icon-btn', 'aria-label': 'Move down', onClick: () => { move(i, 1); } }, '↓'),
              h('button', { class: 'icon-btn', 'aria-label': 'Remove', onClick: () => { plan.items.splice(i, 1); save(); draw(); } }, '✕')))));
      });

      body.append(
        h('button', { class: 'btn', onClick: () => { plan.items.push({ ex: '', sets: 3, reps: 10, weight: 0 }); save(); draw(); } }, '+ Add exercise'),
        h('div', { class: 'btn-col' },
          h('button', { class: 'btn primary', onClick: close }, 'Done'),
          h('button', { class: 'btn', onClick: () => {
            const copy = { id: uid(), name: plan.name + ' copy', items: plan.items.map((x) => ({ ...x })) };
            state.plans.push(copy); save(); close(); toast('Plan duplicated');
          } }, 'Duplicate'),
          h('button', { class: 'btn danger', onClick: async () => {
            const ok = await ask('Delete plan?', `"${plan.name}" will be removed from your schedule too.`,
              [{ label: 'Delete', value: true, primary: true }, { label: 'Cancel', value: false }]);
            if (!ok) return;
            state.plans = state.plans.filter((p) => p !== plan);
            state.schedule.days = state.schedule.days.map((d) => (d === plan.id ? null : d));
            save(); close();
          } }, 'Delete plan')));
    };
    const move = (i, dir) => {
      const j = i + dir;
      if (j < 0 || j >= plan.items.length) return;
      [plan.items[i], plan.items[j]] = [plan.items[j], plan.items[i]];
      save(); draw();
    };
    draw();
  }, { onClose: () => { plan.items = plan.items.filter((i) => i.ex); save(); } });
}

// ---------- schedule editor ----------
function editSchedule() {
  openSheet('Schedule', (body, close) => {
    const s = state.schedule;
    let on = Math.min(7, s.days.length) || 7;
    let off = Math.max(0, s.days.length - on);
    const draw = () => {
      body.replaceChildren();
      const start = h('input', { type: 'date', class: 'text', value: s.start });
      start.addEventListener('change', () => { if (start.value) { s.start = start.value; save(); draw(); } });
      body.append(
        h('p', { class: 'muted' }, 'Your plans repeat in a cycle. Pick a pattern, or set each day yourself.'),
        h('div', { class: 'grid2' },
          stepper({ value: on, min: 0, max: 30, label: 'Days on', onChange: (v) => { on = v; } }),
          stepper({ value: off, min: 0, max: 30, label: 'Days off', onChange: (v) => { off = v; } })),
        h('button', { class: 'btn', onClick: () => {
          if (on + off < 1) return toast('Need at least one day');
          s.days = buildOnOff(on, off, state.plans.map((p) => p.id));
          save(); draw(); toast('Pattern applied, plans rotate across the on-days');
        } }, `Apply ${on} on / ${off} off`),
        h('div', { class: 'field' }, h('label', null, 'Cycle day 1 is'), start),
        h('div', { class: 'section-title' }, `Cycle days (${s.days.length})`));

      s.days.forEach((pid, i) => {
        const native = h('select', null, h('option', { value: '' }, 'Rest'),
          ...state.plans.map((p) => h('option', { value: p.id }, p.name)));
        native.value = pid || '';
        const sel = selectify(native);
        sel.addEventListener('change', () => { s.days[i] = sel.value || null; save(); });
        body.append(h('div', { class: 'day-row' },
          h('div', { class: 'dl' }, h('b', null, `Day ${i + 1}`), h('span', { class: 'muted' }, dayName(addDays(s.start, i)))), sel));
      });

      body.append(
        h('div', { class: 'grid2' },
          h('button', { class: 'btn', onClick: () => { s.days.push(null); save(); draw(); } }, '+ Add day'),
          h('button', { class: 'btn', onClick: () => { if (s.days.length > 1) { s.days.pop(); save(); draw(); } } }, '− Remove last')),
        h('button', { class: 'btn primary', onClick: close }, 'Done'));
    };
    draw();
  });
}
