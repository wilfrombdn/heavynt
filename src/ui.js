// Tiny DOM helpers: element builder, bottom sheets, dialogs, steppers, formatting.
export function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'html') el.innerHTML = v;
    else if (v === true) el.setAttribute(k, '');
    else el.setAttribute(k, v);
  }
  for (const kid of kids.flat()) {
    if (kid == null || kid === false) continue;
    el.append(kid.nodeType ? kid : document.createTextNode(kid));
  }
  return el;
}

// ---------- formatting ----------
export const fmtNum = (n) => (Math.round(n * 100) / 100).toString();
export const fmtDate = (iso) => {
  const d = new Date(iso);
  return d.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' }) +
    ' · ' + d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
};
export const fmtShort = (iso) => new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
export const fmtDuration = (a, b) => {
  const m = Math.max(1, Math.round((new Date(b) - new Date(a)) / 60000));
  return m >= 60 ? `${Math.floor(m / 60)}h ${m % 60}m` : `${m}m`;
};

// ---------- sheets & dialogs ----------
const root = () => document.getElementById('overlay');
const stack = [];

export function openSheet(title, build, { onClose } = {}) {
  const body = h('div', { class: 'sheet-body' });
  const sheet = h('div', { class: 'sheet', role: 'dialog' },
    h('div', { class: 'grab' }),
    title ? h('h2', null, title) : null,
    body);
  const back = h('div', { class: 'backdrop' }, sheet);
  let closed = false;
  const entry = {
    close(result) {
      if (closed) return;
      closed = true;
      stack.splice(stack.indexOf(entry), 1);
      back.classList.remove('show');
      setTimeout(() => back.remove(), 180);
      onClose && onClose(result);
    },
  };
  back.addEventListener('pointerdown', (e) => { if (e.target === back) entry.close(); });
  sheet.addEventListener('pointerdown', (e) => e.stopPropagation());
  root().append(back);
  stack.push(entry);
  build(body, entry.close);
  requestAnimationFrame(() => back.classList.add('show'));
  return entry;
}

export const closeTopSheet = () => { if (!stack.length) return false; stack[stack.length - 1].close(); return true; };

// Promise-based choice dialog. buttons: [{label, value, primary}]
export function ask(title, message, buttons) {
  return new Promise((resolve) => {
    let answered = false;
    openSheet(title, (body, close) => {
      if (message) body.append(h('p', { class: 'muted' }, message));
      const row = h('div', { class: 'btn-col' });
      for (const b of buttons) {
        row.append(h('button', {
          class: 'btn' + (b.primary ? ' primary' : ''),
          onClick: () => { answered = true; close(); resolve(b.value); },
        }, b.label));
      }
      body.append(row);
    }, { onClose: () => { if (!answered) resolve(null); } });
  });
}

let toastTimer;
export function toast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), 2200);
}

export const buzz = (ms = 25) => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) { /* unsupported */ } };

// ---------- form controls ----------
export function stepper({ value, min = 0, max = 9999, step = 1, label, onChange }) {
  let v = value;
  const input = h('input', { type: 'number', inputmode: 'decimal', value: fmtNum(v), step: String(step) });
  const set = (n) => {
    v = Math.min(max, Math.max(min, Math.round(n * 100) / 100));
    input.value = fmtNum(v);
    onChange && onChange(v);
  };
  input.addEventListener('change', () => set(parseFloat(input.value) || 0));
  input.addEventListener('focus', () => input.select());
  return h('div', { class: 'field' },
    label ? h('label', null, label) : null,
    h('div', { class: 'stepper' },
      h('button', { type: 'button', class: 'step-btn', 'aria-label': 'decrease', onClick: () => set(v - step) }, '−'),
      input,
      h('button', { type: 'button', class: 'step-btn', 'aria-label': 'increase', onClick: () => set(v + step) }, '+')));
}

// Native <select> grouped by category, so building a workout is a simple drop-down.
// Replaces a native <select> with a themed button + bottom-sheet picker (searchable when long).
// The returned button keeps `.value` and fires `change`, so it is a drop-in replacement.
export function selectify(sel) {
  const btn = h('button', { type: 'button', class: 'select' });
  const label = () => {
    const o = sel.options[sel.selectedIndex];
    btn.textContent = o ? o.textContent : '';
    btn.classList.toggle('placeholder', !sel.value);
  };
  Object.defineProperty(btn, 'value', { get: () => sel.value, set: (v) => { sel.value = v; label(); } });
  label();
  btn.addEventListener('click', () => {
    openSheet(null, (body, close) => {
      const items = [];
      const list = h('div', { class: 'opts' });
      const add = (o) => {
        if (o.dataset.placeholder) return;
        const row = h('button', { type: 'button', class: 'opt' + (o.value === sel.value ? ' on' : '') },
          h('span', null, o.textContent), o.value === sel.value ? icon('check') : null);
        row.addEventListener('click', () => {
          sel.value = o.value; label(); close();
          btn.dispatchEvent(new Event('change'));
        });
        items.push({ row, text: o.textContent.toLowerCase() });
        return row;
      };
      const groups = [];
      for (const child of sel.children) {
        if (child.tagName === 'OPTGROUP') {
          const title = h('div', { class: 'section-title' }, child.label);
          list.append(title);
          const rows = [...child.children].map(add);
          rows.forEach((r) => list.append(r));
          groups.push({ title, rows });
        } else { const r = add(child); if (r) list.append(r); }
      }
      if (sel.options.length > 12) {
        const q = h('input', { class: 'text', type: 'search', placeholder: 'Search…' });
        q.addEventListener('input', () => {
          const t = q.value.trim().toLowerCase();
          items.forEach((i) => { i.row.hidden = !i.text.includes(t); });
          groups.forEach((g) => { g.title.hidden = g.rows.every((r) => r.hidden); });
        });
        body.append(q);
      }
      body.append(list);
      requestAnimationFrame(() => { const on = list.querySelector('.on'); if (on) on.scrollIntoView({ block: 'center' }); });
    });
  });
  return btn;
}

export function exerciseSelect(exercises, { value, placeholder = 'Choose exercise…', onChange } = {}) {
  const sel = h('select', null, h('option', { value: '', 'data-placeholder': '1' }, placeholder));
  const cats = [...new Set(exercises.map((e) => e.cat))];
  for (const c of cats) {
    const g = h('optgroup', { label: c });
    exercises.filter((e) => e.cat === c).sort((a, b) => a.name.localeCompare(b.name))
      .forEach((e) => g.append(h('option', { value: e.id }, e.name)));
    sel.append(g);
  }
  if (value) sel.value = value;
  const btn = selectify(sel);
  btn.addEventListener('change', () => onChange && onChange(btn.value));
  return btn;
}

export const icon = (name) => h('span', { class: 'ico', html: ICONS[name] });
const svg = (d) => `<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">${d}</svg>`;
export const ICONS = {
  home: svg('<circle cx="12" cy="5" r="2.2"/><path d="M12 8v6m0 0-3 7m3-7 3 7M7 10h10"/>'),
  workout: svg('<path d="M3 9v6M6 7v10M18 7v10M21 9v6M6 12h12"/>'),
  plans: svg('<rect x="4" y="4" width="16" height="16" rx="2"/><path d="M8 9h8M8 13h8M8 17h5"/>'),
  exercises: svg('<path d="M4 6h16M4 12h16M4 18h10"/>'),
  history: svg('<path d="M4 12a8 8 0 1 0 3-6.2M4 4v4h4M12 8v4l3 2"/>'),
  gear: svg('<circle cx="12" cy="12" r="3"/><path d="M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M5.6 18.4l1.8-1.8M16.6 7.4l1.8-1.8"/>'),
  check: svg('<path d="M5 12.5l4.5 4.5L19 7.5"/>'),
};
