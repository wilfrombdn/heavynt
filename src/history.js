// History tab: every workout with its automatic date & time.
import { state, save, exById } from './store.js';
import { h, ask, fmtDate, fmtDuration, fmtNum, openSheet, exerciseSelect, toast } from './ui.js';
import { unit, weightStep, PAIR_NOTE } from './workout.js';

export function renderHistory(c) {
  c.append(h('div', { class: 'page-head' }, h('h1', null, 'History')));
  if (!state.logs.length) {
    c.append(h('div', { class: 'card center muted' }, 'No workouts yet. Finish one and it shows up here with the date and time.'));
    return;
  }
  for (const log of state.logs.slice().sort((a, b) => new Date(b.start) - new Date(a.start))) {
    const body = h('div', { class: 'log-body' });
    body.hidden = true;
    for (const e of log.entries) {
      const ex = exById(e.ex);
      body.append(h('div', { class: 'line' }, h('span', null, ex ? ex.name : 'Removed exercise'),
        h('span', { class: 'muted' }, `${e.sets} × ${e.reps} @ ${fmtNum(e.weight)} ${unit()}${ex && ex.pair ? ' total' : ''}`)));
    }
    body.append(h('div', { class: 'grid2' }, h('button', { class: 'btn slim', onClick: () => editLog(log) }, 'Edit'), h('button', { class: 'btn danger slim', onClick: async () => {
      const ok = await ask('Delete workout?', 'This also removes it from your strength map.',
        [{ label: 'Delete', value: true, primary: true }, { label: 'Cancel', value: false }]);
      if (ok) { state.logs = state.logs.filter((l) => l !== log); save(); }
    } }, 'Delete')));
    c.append(h('div', { class: 'card tap' },
      h('button', { class: 'log-head', onClick: () => { body.hidden = !body.hidden; } },
        h('div', { class: 'grow' }, h('div', { class: 'big' }, log.planName), h('div', { class: 'muted' }, fmtDate(log.start))),
        h('div', { class: 'muted right' }, `${log.entries.length} exercises`, h('br'), log.end ? fmtDuration(log.start, log.end) : '')),
      body));
  }
}

const toLocalInput = (iso) => {
  const d = new Date(iso), p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
};

// Edit a finished workout: when it happened, its name, and every exercise's sets / reps / weight.
function editLog(log) {
  const draft = { name: log.planName, when: toLocalInput(log.start), entries: log.entries.map((e) => ({ ...e })) };
  openSheet('Edit workout', (body, close) => {
    const draw = () => {
      body.replaceChildren();
      const name = h('input', { class: 'text', value: draft.name });
      name.addEventListener('input', () => { draft.name = name.value; });
      const when = h('input', { class: 'text', type: 'datetime-local', value: draft.when });
      when.addEventListener('change', () => { if (when.value) draft.when = when.value; });
      body.append(
        h('div', { class: 'field' }, h('label', null, 'Name'), name),
        h('div', { class: 'field' }, h('label', null, 'Date & time'), when),
        h('div', { class: 'section-title' }, 'Exercises'));

      draft.entries.forEach((e, i) => {
        const ex = exById(e.ex);
        const num = (label, key, step) => {
          const inp = h('input', { type: 'number', inputmode: 'decimal', value: e[key], step: String(step), min: '0' });
          inp.addEventListener('focus', () => inp.select());
          inp.addEventListener('input', () => { e[key] = parseFloat(inp.value) || 0; });
          return h('div', { class: 'mini' }, h('label', null, label), inp);
        };
        body.append(h('div', { class: 'plan-row' },
          exerciseSelect(state.exercises, { value: e.ex, onChange: (v) => { e.ex = v; draw(); } }),
          ex && ex.pair ? h('div', { class: 'hint note' }, `Weight is both dumbbells combined. ${PAIR_NOTE}`) : null,
          h('div', { class: 'minis' }, num('Sets', 'sets', 1), num('Reps', 'reps', 1),
            num(ex && ex.pair ? `Total (${unit()})` : `Weight (${unit()})`, 'weight', weightStep()),
            h('div', { class: 'row-actions' },
              h('button', { class: 'icon-btn', 'aria-label': 'Remove', onClick: () => { draft.entries.splice(i, 1); draw(); } }, '✕')))));
      });

      body.append(
        h('button', { class: 'btn', onClick: () => { draft.entries.push({ ex: '', sets: 3, reps: 10, weight: 0, at: null }); draw(); } }, '+ Add exercise'),
        h('div', { class: 'btn-col' }, h('button', { class: 'btn primary', onClick: () => {
          const entries = draft.entries.filter((e) => e.ex);
          if (!entries.length) return toast('Add at least one exercise');
          const start = new Date(draft.when);
          const shift = start - new Date(log.start);
          log.start = start.toISOString();
          if (log.end) log.end = new Date(new Date(log.end).getTime() + shift).toISOString();
          log.planName = draft.name.trim() || 'Workout';
          log.entries = entries.map((e) => ({ ...e, at: e.at ? new Date(new Date(e.at).getTime() + shift).toISOString() : log.start }));
          save(); close(); toast('Workout updated');
        } }, 'Save changes'), h('button', { class: 'btn', onClick: close }, 'Cancel')));
    };
    draw();
  });
}
