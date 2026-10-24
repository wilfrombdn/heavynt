// Settings sheet: units, bodyweight, plateau rule, reminders, backup.
import { Capacitor } from '@capacitor/core';
import { state, save, exportData, importData } from './store.js';
import { h, openSheet, ask, stepper, toast } from './ui.js';
import { openProfile, profileSummary } from './profile.js';

const LB = 2.20462;
const round = (n) => Math.round(n * 2) / 2;

async function switchUnit(to) {
  if (to === state.settings.unit) return;
  const convert = await ask('Convert saved weights?', `Switch to ${to}. Convert your plans, history and bodyweight too?`, [
    { label: 'Convert everything', value: true, primary: true },
    { label: 'Just change the label', value: false }]);
  if (convert === null) return;
  if (convert) {
    const k = to === 'kg' ? 1 / LB : LB;
    const cv = (w) => (w ? round(w * k) : w);
    state.plans.forEach((p) => p.items.forEach((i) => { i.weight = cv(i.weight); }));
    state.logs.forEach((l) => l.entries.forEach((e) => { e.weight = cv(e.weight); }));
    state.settings.bodyweight = Math.round(state.settings.bodyweight * k * 10) / 10;
    state.active && state.active.items.forEach((i) => { i.weight = cv(i.weight); });
  }
  state.settings.unit = to;
  save();
}

export function openSettings() {
  openSheet('Settings', (body, close) => {
    const s = state.settings;
    const draw = () => {
      body.replaceChildren();
      const seg = h('div', { class: 'seg' },
        ...['lb', 'kg'].map((u) => h('button', { class: s.unit === u ? 'on' : '', onClick: async () => { await switchUnit(u); draw(); } }, u)));

      const time = h('input', { type: 'time', class: 'text', value: s.reminderTime });
      time.addEventListener('change', () => { s.reminderTime = time.value || '07:00'; save(); });
      const remind = h('input', { type: 'checkbox' });
      remind.checked = s.reminderOn;
      remind.addEventListener('change', () => { s.reminderOn = remind.checked; save(); });

      const open = h('input', { type: 'checkbox' });
      open.checked = s.openOnLaunch !== false;
      open.addEventListener('change', () => { s.openOnLaunch = open.checked; save(); });

      body.append(
        h('div', { class: 'field' }, h('label', null, 'Units'), seg),
        h('button', { class: 'card tap', onClick: () => { close(); openProfile(); } },
          h('div', { class: 'eyebrow' }, 'Profile & targets'), h('div', null, profileSummary())),
        stepper({ value: s.plateauSessions, min: 2, max: 12, label: 'Suggest a heavier weight after this many sessions at the same weight', onChange: (v) => { s.plateauSessions = v; save(); } }),
        h('label', { class: 'check-row' }, open, h('span', null, "Open to today's workout on launch")),
        h('label', { class: 'check-row' }, remind, h('span', null, 'Remind me on workout days')),
        h('div', { class: 'field' }, h('label', null, 'Reminder time'), time),
        Capacitor.isNativePlatform() ? null : h('div', { class: 'hint' }, 'Reminders work in the installed Android app.'),
        h('div', { class: 'section-title' }, 'Backup'),
        h('div', { class: 'grid2' },
          h('button', { class: 'btn', onClick: async () => {
            try { await navigator.clipboard.writeText(exportData()); toast('Backup copied to clipboard'); } catch (e) { toast('Could not copy'); }
          } }, 'Copy backup'),
          h('button', { class: 'btn', onClick: restore }, 'Restore')),
        h('button', { class: 'btn danger', onClick: async () => {
          const ok = await ask('Erase everything?', 'All workouts, plans and settings are deleted from this device.',
            [{ label: 'Erase all data', value: true, primary: true }, { label: 'Cancel', value: false }]);
          if (ok) { localStorage.clear(); location.reload(); }
        } }, 'Erase all data'),
        h('div', { class: 'hint center' }, 'Strength scores are estimates: your best set in the last 90 days → estimated 1-rep max ÷ bodyweight, compared to a typical intermediate lifter.'),
        h('button', { class: 'btn primary', onClick: close }, 'Done'));
    };
    draw();
  });
}

function restore() {
  openSheet('Restore backup', (body, close) => {
    const ta = h('textarea', { class: 'text', rows: '8', placeholder: 'Paste backup text here' });
    body.append(ta, h('button', { class: 'btn primary', onClick: () => {
      try { importData(ta.value); close(); toast('Backup restored'); } catch (e) { toast('That is not a valid backup'); }
    } }, 'Restore (replaces current data)'));
  });
}
