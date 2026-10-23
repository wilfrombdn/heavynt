// History tab: every workout with its automatic date & time.
import { state, save, exById } from './store.js';
import { h, ask, fmtDate, fmtDuration, fmtNum } from './ui.js';
import { unit } from './workout.js';

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
        h('span', { class: 'muted' }, `${e.sets} × ${e.reps} @ ${fmtNum(e.weight)} ${unit()}`)));
    }
    body.append(h('button', { class: 'btn danger slim', onClick: async () => {
      const ok = await ask('Delete workout?', 'This also removes it from your strength map.',
        [{ label: 'Delete', value: true, primary: true }, { label: 'Cancel', value: false }]);
      if (ok) { state.logs = state.logs.filter((l) => l !== log); save(); }
    } }, 'Delete'));
    c.append(h('div', { class: 'card tap' },
      h('button', { class: 'log-head', onClick: () => { body.hidden = !body.hidden; } },
        h('div', { class: 'grow' }, h('div', { class: 'big' }, log.planName), h('div', { class: 'muted' }, fmtDate(log.start))),
        h('div', { class: 'muted right' }, `${log.entries.length} exercises`, h('br'), log.end ? fmtDuration(log.start, log.end) : '')),
      body));
  }
}
