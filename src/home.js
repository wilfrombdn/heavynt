// Home tab: today's plan + anatomy strength map.
import { state } from './store.js';
import { planForDate, regionScores, scoreLabel } from './logic.js';
import { REGIONS } from './data.js';
import { bodyMap } from './bodymap.js';
import { h } from './ui.js';
import { startWorkout, todaysLog, nextWorkout, dayInfo } from './workout.js';
import { openSettings } from './settings.js';

let selected = null;

export function renderHome(c, go) {
  const plan = planForDate(state);
  const scores = state.settings.bodyweight ? regionScores(state) : {};
  const hasData = Object.keys(scores).length > 0;

  c.append(h('div', { class: 'page-head row' },
    h('div', { class: 'grow' }, h('h1', null, 'Strength'), h('div', { class: 'muted' }, new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' }))),
    h('button', { class: 'icon-btn', 'aria-label': 'Settings', onClick: openSettings, html: '⚙' })));

  // Today card
  const { idx, len } = dayInfo();
  let today;
  if (state.active) {
    today = h('div', { class: 'card row' },
      h('div', { class: 'grow' }, h('div', { class: 'eyebrow' }, 'In progress'), h('div', { class: 'big' }, state.active.planName)),
      h('button', { class: 'btn primary slim', onClick: () => go('workout') }, 'Resume'));
  } else if (plan) {
    const done = todaysLog(plan.id);
    today = h('div', { class: 'card row' },
      h('div', { class: 'grow' }, h('div', { class: 'eyebrow' }, `Today · day ${idx + 1} of ${len}`),
        h('div', { class: 'big' }, plan.name), h('div', { class: 'muted' }, `${plan.items.length} exercises`)),
      done ? h('span', { class: 'tag' }, 'Done ✓')
        : h('button', { class: 'btn primary slim', onClick: async () => { await startWorkout(plan.id); go('workout'); } }, 'Start'));
  } else {
    const n = nextWorkout();
    today = h('div', { class: 'card' },
      h('div', { class: 'eyebrow' }, `Rest day${len ? ` · day ${idx + 1} of ${len}` : ''}`),
      h('div', { class: 'muted' }, n ? `Next: ${n.plan.name} on ${new Date(n.key + 'T00:00').toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })}` : 'Nothing scheduled'));
  }
  c.append(today);

  // Map
  const mapCard = h('div', { class: 'card map-card' });
  mapCard.append(bodyMap(scores, selected, (r) => { selected = selected === r ? null : r; rerender(); }));
  mapCard.append(h('div', { class: 'legend' }, h('span', null, 'Weak'), h('div', { class: 'bar' }), h('span', null, 'Strong')));
  c.append(mapCard);

  if (!state.settings.bodyweight) {
    c.append(h('button', { class: 'card note', onClick: openSettings },
      h('b', null, 'Set your bodyweight'), h('div', { class: 'muted' }, 'Strength is scored relative to bodyweight. Tap to set it.')));
  } else if (!hasData) {
    c.append(h('div', { class: 'card note muted' }, 'Finish a workout with weights and the map will colour in: blue = strong, red = weak, grey = average.'));
  }

  if (selected) c.append(regionCard(selected, scores[selected]));
  else if (hasData) c.append(summary(scores));

  function rerender() { c.replaceChildren(); renderHome(c, go); }
}

function regionCard(key, sc) {
  const card = h('div', { class: 'card' }, h('div', { class: 'row' },
    h('div', { class: 'grow big' }, REGIONS[key]),
    sc ? h('span', { class: 'tag' }, scoreLabel(sc.score)) : h('span', { class: 'tag' }, 'No data')));
  if (!sc) { card.append(h('p', { class: 'muted' }, 'No logged lifts hit this area in the last 90 days.')); return card; }
  card.append(h('div', { class: 'muted' }, `${Math.round(sc.score * 100)}% of intermediate strength`));
  sc.lifts.slice(0, 5).forEach((l) => card.append(
    h('div', { class: 'line' }, h('span', null, l.name + (l.primary ? '' : ' (assist)')), h('span', { class: 'muted' }, `${Math.round(l.score * 100)}%`))));
  return card;
}

function summary(scores) {
  const arr = Object.entries(scores).sort((a, b) => a[1].score - b[1].score);
  const list = (items) => items.map(([k]) => REGIONS[k]).join(', ') || '–';
  const untracked = Object.keys(REGIONS).filter((k) => !scores[k]);
  return h('div', { class: 'card' },
    h('div', { class: 'line' }, h('span', { class: 'muted' }, 'Weakest'), h('span', null, list(arr.slice(0, 3)))),
    h('div', { class: 'line' }, h('span', { class: 'muted' }, 'Strongest'), h('span', null, list(arr.slice(-3).reverse()))),
    untracked.length ? h('div', { class: 'line' }, h('span', { class: 'muted' }, 'Untrained'), h('span', null, untracked.map((k) => REGIONS[k]).join(', '))) : null);
}
