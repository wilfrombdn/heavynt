// Daily reminders on workout days (native only). Re-schedules the next 30 days whenever data changes.
import { Capacitor } from '@capacitor/core';
import { LocalNotifications } from '@capacitor/local-notifications';
import { state } from './store.js';
import { dateKey, addDays, planForDate, parseKey } from './logic.js';
import { todaysLog } from './workout.js';

let timer;
export function scheduleReminders() {
  if (!Capacitor.isNativePlatform()) return;
  clearTimeout(timer);
  timer = setTimeout(run, 1500);
}

async function run() {
  try {
    const pending = await LocalNotifications.getPending();
    if (pending.notifications.length) await LocalNotifications.cancel({ notifications: pending.notifications.map((n) => ({ id: n.id })) });
    if (!state.settings.reminderOn) return;
    let perm = await LocalNotifications.checkPermissions();
    if (perm.display !== 'granted') perm = await LocalNotifications.requestPermissions();
    if (perm.display !== 'granted') return;

    const [hh, mm] = state.settings.reminderTime.split(':').map(Number);
    const out = [];
    for (let i = 0; i < 30; i++) {
      const key = addDays(dateKey(), i);
      const plan = planForDate(state, key);
      if (!plan) continue;
      if (i === 0 && todaysLog(plan.id)) continue;
      const at = parseKey(key);
      at.setHours(hh, mm, 0, 0);
      if (at.getTime() <= Date.now()) continue;
      out.push({
        id: i + 1,
        title: `Today: ${plan.name}`,
        body: `${plan.items.length} exercises are ready. Tap to start your workout.`,
        schedule: { at, allowWhileIdle: true },
        smallIcon: 'ic_stat_workout',
      });
    }
    if (out.length) await LocalNotifications.schedule({ notifications: out });
  } catch (e) { /* notifications unavailable: ignore */ }
}
