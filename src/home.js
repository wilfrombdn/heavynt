// Home tab: today's plan + anatomy strength map.
import { state } from './store.js';
import { planForDate, regionScores, scoreLabel } from './logic.js';
import { REGIONS } from './data.js';
import { bodyMap } from './bodymap.js';
import { h } from './ui.js';
import { startWorkout, todaysLog, nextWorkout, dayInfo } from './workout.js';
import { openSettings } from './settings.js';
import { openProfile } from './profile.js';

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
  mapCard.append(h('div', { class: 'legend' }, h('span', null, 'Under target'), h('div', { class: 'bar' }), h('span', null, 'On / above')));
  c.append(mapCard);

  if (!state.settings.bodyweight) {
    c.append(h('button', { class: 'card note', onClick: openProfile },
      h('b', null, 'Set up your profile'), h('div', { class: 'muted' }, 'Add your age, height and weight so the map knows what you should be lifting. Tap to start.')));
  } else if (!hasData) {
    c.append(h('div', { class: 'card note muted' }, 'Finish a workout with weights and the map colours in: red = under your target, blue = on target or better.'));
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
  card.append(h('div', { class: 'muted' }, `${Math.round(sc.score * 100)}% of your target`));
  const u = state.settings.unit;
  sc.lifts.slice(0, 5).forEach((l) => card.append(
    h('div', { class: 'line' },
      h('span', null, l.name + (l.primary ? '' : ' (assist)'), h('div', { class: 'hint' }, `est. max ${Math.round(l.e1rm)} ${u} · target ${Math.round(l.target)} ${u}`)),
      h('span', { class: l.score >= 1 ? 'good' : 'bad' }, `${Math.round(l.score * 100)}%`))));
  return card;
}

function summary(scores) {
  const arr = Object.entries(scores).sort((a, b) => a[1].score - b[1].score);
  const names = (items) => items.map(([k]) => REGIONS[k]).join(', ');
  const behind = arr.filter(([, v]) => v.score < 1);
  const ok = arr.filter(([, v]) => v.score >= 1).reverse();
  const untracked = Object.keys(REGIONS).filter((k) => !scores[k]);
  return h('div', { class: 'card' },
    behind.length ? h('div', { class: 'line' }, h('span', { class: 'muted' }, 'Behind target'), h('span', { class: 'bad' }, names(behind.slice(0, 4)))) : null,
    ok.length ? h('div', { class: 'line' }, h('span', { class: 'muted' }, 'On target or better'), h('span', { class: 'good' }, names(ok.slice(0, 4)))) : null,
    untracked.length ? h('div', { class: 'line' }, h('span', { class: 'muted' }, 'Untrained'), h('span', null, names(untracked.map((k) => [k])))) : null);
}
