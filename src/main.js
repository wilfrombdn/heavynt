// App shell: tab navigation, rendering, Android back button, launch behaviour.
import { Capacitor } from '@capacitor/core';
import { App } from '@capacitor/app';
import { state, onChange } from './store.js';
import { planForDate } from './logic.js';
import { h, icon, closeTopSheet } from './ui.js';
import { renderHome } from './home.js';
import { renderWorkout, todaysLog } from './workout.js';
import { renderPlans } from './plans.js';
import { renderExercises } from './exercises.js';
import { renderHistory } from './history.js';
import { scheduleReminders } from './reminders.js';
import { openProfile } from './profile.js';

const TABS = [
  { id: 'home', label: 'Home', render: renderHome },
  { id: 'workout', label: 'Workout', render: renderWorkout },
  { id: 'plans', label: 'Plans', render: renderPlans },
  { id: 'exercises', label: 'Exercises', render: renderExercises },
  { id: 'history', label: 'History', render: renderHistory },
];

let current = 'home';
const view = document.getElementById('view');
const nav = document.getElementById('nav');

function go(id) {
  if (current !== id) view.scrollTop = 0;
  current = id;
  render();
}

function render() {
  const top = view.scrollTop;
  view.replaceChildren();
  const tab = TABS.find((t) => t.id === current);
  tab.render(view, go);
  view.scrollTop = top;
  nav.replaceChildren(...TABS.map((t) => h('button', { class: 'tab' + (t.id === current ? ' on' : ''), onClick: () => go(t.id) },
    icon(t.id), h('span', null, t.label))));
  // Little dot on the Workout tab while a session is running.
  if (state.active) nav.children[1].classList.add('live');
}

onChange(() => { render(); scheduleReminders(); });
document.addEventListener('navigate', (e) => go(e.detail));
document.addEventListener('visibilitychange', () => { if (!document.hidden) render(); });

// Android back: close sheet -> back to Home -> exit.
if (Capacitor.isNativePlatform()) {
  App.addListener('backButton', () => {
    if (closeTopSheet()) return;
    if (current !== 'home') go('home');
    else App.exitApp();
  });
}

// Launch: jump to the workout if one is running or due today.
const plan = planForDate(state);
if (state.active || (plan && !todaysLog(plan.id) && state.settings.openOnLaunch !== false)) current = 'workout';
const hashTab = location.hash.slice(1);
if (TABS.some((t) => t.id === hashTab)) current = hashTab; // dev shortcut: index.html#plans
render();
scheduleReminders();
if (!state.settings.bodyweight && !state.settings.profileAsked) setTimeout(openProfile, 350);
