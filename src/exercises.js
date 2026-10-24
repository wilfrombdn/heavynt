// Exercises tab: library, per-exercise weight history chart, add/edit custom exercises.
import { state, save, uid, exById } from './store.js';
import { exerciseHistory } from './logic.js';
import { REGIONS, CATEGORIES, DEFAULT_STD } from './data.js';
import { h, openSheet, ask, toast, selectify, fmtNum, fmtDate, fmtShort } from './ui.js';
import { unit } from './workout.js';

let query = '';

function sparkline(points) {
  if (points.length < 2) return null;
  const w = 56, ht = 18, min = Math.min(...points), max = Math.max(...points), r = max - min || 1;
  const d = points.map((v, i) => `${i ? 'L' : 'M'}${((i / (points.length - 1)) * w).toFixed(1)} ${(ht - ((v - min) / r) * (ht - 2) - 1).toFixed(1)}`).join('');
  return h('span', { class: 'spark', html: `<svg width="${w}" height="${ht}" viewBox="0 0 ${w} ${ht}"><path d="${d}" fill="none" stroke="currentColor" stroke-width="1.5"/></svg>` });
}

export function renderExercises(c) {
  const list = h('div');
  const draw = () => {
    list.replaceChildren();
    const q = query.trim().toLowerCase();
    for (const cat of [...new Set(state.exercises.map((e) => e.cat))]) {
      const exs = state.exercises.filter((e) => e.cat === cat && e.name.toLowerCase().includes(q))
        .sort((a, b) => a.name.localeCompare(b.name));
      if (!exs.length) continue;
      list.append(h('div', { class: 'section-title' }, cat));
      for (const ex of exs) {
        const hist = exerciseHistory(state, ex.id);
        const last = hist[hist.length - 1];
        list.append(h('button', { class: 'card tap row', onClick: () => detail(ex) },
          h('div', { class: 'grow' }, h('div', { class: 'name' }, ex.name),
            h('div', { class: 'muted small' }, last ? `${fmtNum(last.weight)} ${unit()}${ex.pair ? ' total' : ''} · ${fmtShort(last.at)}` : 'Not logged yet')),
          sparkline(hist.map((x) => x.weight))));
      }
    }
  };
  const search = h('input', { class: 'text', type: 'search', placeholder: 'Search exercises', value: query });
  search.addEventListener('input', () => { query = search.value; draw(); });
  c.append(
    h('div', { class: 'page-head' }, h('h1', null, 'Exercises')),
    search,
    h('button', { class: 'btn primary', onClick: () => editExercise() }, '+ Add exercise'),
    list);
  draw();
}

function chart(hist) {
  const W = 320, H = 130, P = 22;
  const ws = hist.map((x) => x.weight);
  const min = Math.min(...ws), max = Math.max(...ws), r = max - min || 1;
  const t0 = new Date(hist[0].at).getTime(), t1 = new Date(hist[hist.length - 1].at).getTime(), tr = t1 - t0 || 1;
  const pts = hist.map((x) => [P + ((new Date(x.at).getTime() - t0) / tr) * (W - 2 * P), H - P - ((x.weight - min) / r) * (H - 2 * P)]);
  const line = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join('');
  return h('div', { class: 'chart', html:
    `<svg viewBox="0 0 ${W} ${H}"><path d="${line}" fill="none" stroke="#fff" stroke-width="1.6"/>` +
    pts.map((p) => `<circle cx="${p[0].toFixed(1)}" cy="${p[1].toFixed(1)}" r="3" fill="#000" stroke="#fff" stroke-width="1.4"/>`).join('') +
    `<text x="4" y="12" class="ct">${fmtNum(max)} ${unit()}</text><text x="4" y="${H - 4}" class="ct">${fmtNum(min)}</text></svg>` });
}

function detail(ex) {
  openSheet(ex.name, (body, close) => {
    const hist = exerciseHistory(state, ex.id);
    body.append(h('div', { class: 'muted' }, `${ex.cat} · ${ex.primary.map((r) => REGIONS[r]).join(', ')}`));
    if (hist.length) {
      const best = Math.max(...hist.map((x) => x.weight));
      body.append(chart(hist.length > 1 ? hist : [hist[0], hist[0]]),
        h('div', { class: 'line' }, h('span', { class: 'muted' }, 'Best weight'), h('span', null, `${fmtNum(best)} ${unit()}`)),
        h('div', { class: 'line' }, h('span', { class: 'muted' }, 'Sessions'), h('span', null, hist.length)),
        h('div', { class: 'section-title' }, 'History'),
        ...hist.slice().reverse().slice(0, 30).map((x) => h('div', { class: 'line' },
          h('span', { class: 'muted' }, fmtDate(x.at)), h('span', null, `${x.sets} × ${x.reps} @ ${fmtNum(x.weight)} ${unit()}${ex.pair ? ' total' : ''}`))));
    } else body.append(h('p', { class: 'muted' }, 'No sets logged yet.'));
    body.append(h('div', { class: 'btn-col' },
      h('button', { class: 'btn', onClick: () => { close(); editExercise(ex); } }, 'Edit exercise'),
      ex.custom ? h('button', { class: 'btn danger', onClick: async () => {
        const ok = await ask('Delete exercise?', 'Past workouts keep their records but plans using it will lose it.',
          [{ label: 'Delete', value: true, primary: true }, { label: 'Cancel', value: false }]);
        if (!ok) return;
        state.exercises = state.exercises.filter((e) => e !== ex);
        state.plans.forEach((p) => { p.items = p.items.filter((i) => i.ex !== ex.id); });
        save(); close();
      } }, 'Delete exercise') : null));
  });
}

function chips(selected, onToggle) {
  const wrap = h('div', { class: 'chips' });
  for (const [k, label] of Object.entries(REGIONS)) {
    const b = h('button', { type: 'button', class: 'chip' + (selected.has(k) ? ' on' : '') }, label);
    b.addEventListener('click', () => { onToggle(k); b.classList.toggle('on', selected.has(k)); });
    wrap.append(b);
  }
  return wrap;
}

export function editExercise(ex) {
  const isNew = !ex;
  const d = ex ? { ...ex } : { name: '', cat: 'Chest', primary: [], secondary: [], std: 0, bw: 0, pair: false };
  const prim = new Set(d.primary), sec = new Set(d.secondary);
  openSheet(isNew ? 'New exercise' : 'Edit exercise', (body, close) => {
    const name = h('input', { class: 'text', value: d.name, placeholder: 'e.g. Incline Cable Fly' });
    const catNative = h('select', null, ...CATEGORIES.map((c) => h('option', { value: c }, c)));
    catNative.value = d.cat;
    const cat = selectify(catNative);
    const std = h('input', { class: 'text', type: 'number', inputmode: 'decimal', step: '0.05', value: d.std || '', placeholder: `Auto (${DEFAULT_STD[d.cat]}× bodyweight)` });
    const bw = h('input', { type: 'checkbox' });
    bw.checked = d.bw > 0;
    const pair = h('input', { type: 'checkbox' });
    pair.checked = !!d.pair;
    body.append(
      h('div', { class: 'field' }, h('label', null, 'Name'), name),
      h('div', { class: 'field' }, h('label', null, 'Category'), cat),
      h('div', { class: 'field' }, h('label', null, 'Main muscles worked'), chips(prim, (k) => { prim.has(k) ? prim.delete(k) : prim.add(k); })),
      h('div', { class: 'field' }, h('label', null, 'Helper muscles'), chips(sec, (k) => { sec.has(k) ? sec.delete(k) : sec.add(k); })),
      h('div', { class: 'field' }, h('label', null, 'Intermediate lift (× bodyweight, optional)'), std,
        h('div', { class: 'hint' }, 'How heavy a solid intermediate lifts this for one rep. Used to colour the body map.')),
      h('label', { class: 'check-row' }, pair, h('span', null, 'Done with a pair of dumbbells (enter both added together)')),
      h('label', { class: 'check-row' }, bw, h('span', null, 'Bodyweight movement (pull-ups, dips…) – weight is extra added load')),
      h('div', { class: 'btn-col' }, h('button', { class: 'btn primary', onClick: () => {
        if (!name.value.trim()) return toast('Give it a name');
        if (!prim.size) return toast('Pick at least one main muscle');
        const out = {
          name: name.value.trim(), cat: cat.value, primary: [...prim], secondary: [...sec].filter((k) => !prim.has(k)),
          std: parseFloat(std.value) || DEFAULT_STD[cat.value], bw: bw.checked ? (d.bw || 1) : 0, pair: pair.checked,
        };
        if (isNew) state.exercises.push({ id: 'c-' + uid(), custom: true, ...out });
        else Object.assign(ex, out);
        save(); close(); toast(isNew ? 'Exercise added' : 'Saved');
      } }, 'Save')));
  });
}
