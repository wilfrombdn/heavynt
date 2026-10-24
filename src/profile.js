// Profile sheet: the numbers every strength target is calculated from.
import { state, save } from './store.js';
import { h, openSheet, stepper, toast } from './ui.js';

const LEVEL_INFO = {
  novice: 'Novice: about 6 months of training',
  intermediate: 'Intermediate: a couple of solid years',
  advanced: 'Advanced: serious, multi-year lifter',
};

export function profileSummary() {
  const s = state.settings;
  if (!s.bodyweight) return 'Not set up yet';
  const imperial = s.unit === 'lb';
  const ht = s.heightCm
    ? imperial ? ` · ${Math.floor(s.heightCm / 2.54 / 12)}'${Math.round((s.heightCm / 2.54) % 12) % 12}"` : ` · ${Math.round(s.heightCm)} cm`
    : '';
  return `${s.sex === 'female' ? 'Female' : 'Male'} · ${s.age} · ${s.bodyweight} ${s.unit}${ht} · ${s.level}`;
}

export function openProfile() {
  const s = state.settings;
  const imperial = s.unit === 'lb';
  const draft = { sex: s.sex, age: s.age, bodyweight: s.bodyweight, heightCm: s.heightCm || (imperial ? 177.8 : 175), level: s.level };
  let ft = Math.floor(draft.heightCm / 2.54 / 12), inch = Math.round((draft.heightCm / 2.54) % 12);
  if (inch === 12) { ft += 1; inch = 0; }

  openSheet('Your profile', (body, close) => {
    const seg = (key, opts) => {
      const wrap = h('div', { class: 'seg' });
      const draw = () => {
        wrap.replaceChildren(...opts.map(([v, label]) => h('button', {
          type: 'button', class: draft[key] === v ? 'on' : '',
          onClick: () => { draft[key] = v; draw(); if (key === 'level') hint.textContent = LEVEL_INFO[v]; },
        }, label)));
      };
      draw();
      return wrap;
    };
    const hint = h('div', { class: 'hint' }, LEVEL_INFO[draft.level]);

    body.append(
      h('p', { class: 'muted' }, 'Targets are worked out from your sex, age, height and weight, then compared to what you actually lift. Under target shows red, on or above target shows blue.'),
      h('div', { class: 'field' }, h('label', null, 'Sex'), seg('sex', [['male', 'Male'], ['female', 'Female']])),
      stepper({ value: draft.age, min: 12, max: 90, label: 'Age', onChange: (v) => { draft.age = v; } }),
      stepper({ value: draft.bodyweight, min: 0, max: 700, step: 1, label: `Bodyweight (${s.unit})`, onChange: (v) => { draft.bodyweight = v; } }),
      imperial
        ? h('div', { class: 'grid2' },
          stepper({ value: ft, min: 3, max: 8, label: 'Height (ft)', onChange: (v) => { ft = v; } }),
          stepper({ value: inch, min: 0, max: 11, label: 'Height (in)', onChange: (v) => { inch = v; } }))
        : stepper({ value: Math.round(draft.heightCm), min: 120, max: 230, label: 'Height (cm)', onChange: (v) => { draft.heightCm = v; } }),
      h('div', { class: 'field' }, h('label', null, 'Where should your strength be?'),
        seg('level', [['novice', 'Novice'], ['intermediate', 'Intermediate'], ['advanced', 'Advanced']]), hint),
      h('div', { class: 'btn-col' }, h('button', { class: 'btn primary', onClick: () => {
        if (!(draft.bodyweight > 0)) return toast('Enter your bodyweight');
        Object.assign(s, { sex: draft.sex, age: draft.age, bodyweight: draft.bodyweight, level: draft.level, profileAsked: true,
          heightCm: imperial ? Math.round((ft * 12 + inch) * 2.54 * 10) / 10 : draft.heightCm });
        save(); close(); toast('Profile saved');
      } }, 'Save')));
  }, { onClose: () => { if (!s.profileAsked) { s.profileAsked = true; save(); } } });
}
