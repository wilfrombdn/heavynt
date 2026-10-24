// Plans tab: schedule (any on/off cycle) and plan editor with exercise drop-downs.
import { state, save, uid, exById } from './store.js';
import { dateKey, addDays, cycleIndex, mondayOf, parseKey, WEEKDAYS } from './logic.js';
import { h, openSheet, ask, stepper, exerciseSelect, selectify, toast } from './ui.js';
import { unit, weightStep, PAIR_NOTE } from './workout.js';

const dayName = (key) => parseKey(key).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
const planName = (id) => (state.plans.find((p) => p.id === id) || {}).name;

function cycleSummary() {
  const d = state.schedule.days;
  const weeks = d.length / 7;
  const on = d.filter(Boolean).length;
  return `Repeats ${weeks === 1 ? 'every week' : `every ${weeks} weeks`} · ${on} workout${on === 1 ? '' : 's'} per cycle`;
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
        h('div', { class: 'muted' }, cycleSummary() + (idx >= 0 && state.schedule.days.length > 7 ? ` · this is week ${Math.floor(idx / 7) + 1}` : ''))),
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
        const note = h('input', { class: 'text', placeholder: 'Note (rest time, superset, finisher…)', value: it.note || '' });
        note.addEventListener('input', () => { it.note = note.value; save(); });
        body.append(h('div', { class: 'plan-row' },
          exerciseSelect(state.exercises, { value: it.ex, onChange: (v) => { it.ex = v; save(); draw(); } }),
          exById(it.ex) && exById(it.ex).pair ? h('div', { class: 'hint note' }, `Weight is both dumbbells combined. ${PAIR_NOTE}`) : null,
          h('div', { class: 'minis' }, num('Sets', 'sets', 1), num('Reps', 'reps', 1), num(exById(it.ex) && exById(it.ex).pair ? `Total (${unit()})` : `Weight (${unit()})`, 'weight', weightStep()),
            h('div', { class: 'row-actions' },
              h('button', { class: 'icon-btn', 'aria-label': 'Move up', onClick: () => { move(i, -1); } }, '↑'),
              h('button', { class: 'icon-btn', 'aria-label': 'Move down', onClick: () => { move(i, 1); } }, '↓'),
              h('button', { class: 'icon-btn', 'aria-label': 'Remove', onClick: () => { plan.items.splice(i, 1); save(); draw(); } }, '✕'))),
          note));
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
// 1) put plans on weekdays  2) say how many weeks it repeats  3) say which week this is.
function editSchedule() {
  openSheet('Schedule', (body, close) => {
    const s = state.schedule;
    const weeks = () => s.days.length / 7;
    const thisWeek = () => Math.floor(cycleIndex(s, dateKey()) / 7);
    let tab = Math.max(0, thisWeek());

    const resize = (n) => {
      const len = n * 7;
      while (s.days.length < len) s.days.push(null);
      s.days.length = len;
      if (tab >= n) tab = n - 1;
      save(); draw();
    };

    const draw = () => {
      body.replaceChildren();
      const n = weeks();
      const today = dateKey();
      const len = s.days.length;

      const repeat = h('div', { class: 'seg wrap' }, ...[1, 2, 3, 4].map((k) => h('button', {
        class: n === k ? 'on' : '', onClick: () => resize(k) }, k === 1 ? 'Every week' : `Every ${k} weeks`)));

      body.append(
        h('p', { class: 'muted' }, 'Pick what you train on each weekday. If your pattern spans more than one week, choose how often it repeats.'),
        h('div', { class: 'field' }, h('label', null, 'How often does this repeat?'), repeat));

      if (n > 1) {
        const which = h('div', { class: 'seg' }, ...Array.from({ length: n }, (_, k) => h('button', {
          class: thisWeek() === k ? 'on' : '',
          onClick: () => { s.start = addDays(mondayOf(today), -7 * k); save(); tab = k; draw(); } }, `Week ${k + 1}`)));
        const tabs = h('div', { class: 'seg' }, ...Array.from({ length: n }, (_, k) => h('button', {
          class: tab === k ? 'on' : '', onClick: () => { tab = k; draw(); } }, `Week ${k + 1}`)));
        body.append(
          h('div', { class: 'field' }, h('label', null, 'Which week is THIS week?'), which),
          h('div', { class: 'section-title' }, 'Set up each week'), tabs);
      } else {
        body.append(h('div', { class: 'section-title' }, 'Your week'));
      }

      for (let d = 0; d < 7; d++) {
        const i = tab * 7 + d;
        const base = addDays(s.start, i);
        const gap = Math.ceil((parseKey(today) - parseKey(base)) / 86400000 / len);
        const next = gap > 0 ? addDays(base, gap * len) : base;
        const isToday = next === today;
        const native = h('select', null, h('option', { value: '' }, 'Rest'),
          ...state.plans.map((p) => h('option', { value: p.id }, p.name)));
        native.value = s.days[i] || '';
        const sel = selectify(native);
        sel.addEventListener('change', () => { s.days[i] = sel.value || null; save(); });
        body.append(h('div', { class: 'day-row' },
          h('div', { class: 'dl' }, h('b', null, WEEKDAYS[d]),
            h('span', { class: 'muted' }, isToday ? 'Today' : `Next: ${parseKey(next).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}`)),
          sel));
      }

      body.append(h('div', { class: 'btn-col' },
        h('button', { class: 'btn', onClick: () => { for (let d = 0; d < 7; d++) s.days[tab * 7 + d] = null; save(); draw(); } }, 'Clear this week'),
        h('button', { class: 'btn primary', onClick: close }, 'Done')));
    };
    draw();
  });
}
