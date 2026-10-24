// Front/back anatomy outline as inline SVG. Each region is a group coloured by strength score.
import { scoreRGB, scoreStrength } from './logic.js';

const NS = 'http://www.w3.org/2000/svg';
const sh = 'M46 74 a13 14 0 1 0 26 0 a13 14 0 1 0 -26 0';
const arm = 'M45 90 Q40 114 42 138 L57 140 Q62 114 62 92 Z';
const fore = 'M41 144 Q35 172 36 198 L49 200 Q57 172 57 144 Z';
const M = true; // draw mirrored copy on the right side

const FRONT = {
  chest: [['M100 64 L78 66 Q66 72 68 86 Q72 100 100 102 Z', M]],
  shoulders: [[sh, M]],
  biceps: [[arm, M]],
  forearms: [[fore, M]],
  abs: [
    ['M84 104 H98 V120 H84 Z M84 123 H98 V139 H84 Z M84 142 H98 V158 H84 Z', M],
    ['M70 104 Q76 108 81 106 L81 158 Q73 150 71 128 Z', M],
  ],
  quads: [['M78 166 Q70 206 76 256 L96 256 Q100 210 98 168 Z', M]],
  calves: [['M77 262 Q72 300 79 344 L92 344 Q98 300 96 262 Z', M]],
};

const BACK = {
  upperBack: [
    ['M74 82 Q68 106 80 132 L98 136 L100 104 L82 86 Z', M],
    ['M100 54 L82 62 L78 74 L100 98 Z', M],
  ],
  shoulders: [[sh, M]],
  triceps: [[arm, M]],
  forearms: [[fore, M]],
  lowerBack: [['M84 138 H98 V162 H82 Z', M]],
  glutes: [['M80 166 Q72 178 76 198 Q90 206 98 198 L98 166 Z', M]],
  hamstrings: [['M77 204 Q72 232 77 256 L96 256 Q99 232 98 204 Z', M]],
  calves: [['M77 262 Q70 296 79 342 L93 342 Q100 298 96 262 Z', M]],
};

// Outline-only parts (not tracked): head, neck, hands, feet.
const OUTLINE = [
  ['M100 12 a16 16 0 1 0 0.01 0 Z', false],
  ['M92 44 V58 M108 44 V58', false],
  ['M35 202 a7 9 0 1 0 14 0 a7 9 0 1 0 -14 0', M],
  ['M76 350 Q73 366 80 371 H96 L94 350 Z', M],
];

const el = (tag, attrs) => {
  const n = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, v);
  return n;
};

// [x, y, rotation, text] per region, one label each.
const LABELS_FRONT = { chest: [100, 86, 0, 'Chest'], shoulders: [59, 75, 0, 'Delts'], biceps: [51, 114, -90, 'Biceps'],
  forearms: [46, 171, -90, 'Forearms'], abs: [100, 131, 0, 'Core'], quads: [87, 211, -90, 'Quads'], calves: [86, 303, -90, 'Calves'] };
const LABELS_BACK = { upperBack: [100, 94, 0, 'Upper back'], shoulders: [59, 75, 0, 'Delts'], triceps: [51, 114, -90, 'Triceps'],
  forearms: [46, 171, -90, 'Forearms'], lowerBack: [100, 150, 0, 'Low back'], glutes: [100, 186, 0, 'Glutes'],
  hamstrings: [87, 230, -90, 'Hamstrings'], calves: [86, 302, -90, 'Calves'] };

function drawView(parent, x, parts, label, scores, selected, labels) {
  const g = el('g', { transform: `translate(${x} 0)` });
  for (const [d, mirror] of OUTLINE) {
    g.append(el('path', { d, class: 'outline' }));
    if (mirror) g.append(el('path', { d, class: 'outline', transform: 'translate(200 0) scale(-1 1)' }));
  }
  for (const [region, shapes] of Object.entries(parts)) {
    const sc = scores[region];
    const rg = el('g', { class: 'region' + (selected === region ? ' sel' : ''), 'data-region': region });
    if (sc) {
      // Ambient look: translucent tint, hairline edge in the same hue, soft glow that grows with the gap to target.
      const c = scoreRGB(sc.score).join(',');
      const k = scoreStrength(sc.score);
      rg.style.fill = `rgba(${c},${(0.26 + 0.24 * k).toFixed(2)})`;
      rg.style.setProperty('--rs', `rgba(${c},0.95)`);
      rg.style.filter = `drop-shadow(0 0 ${(2.5 + 3 * k).toFixed(1)}px rgba(${c},${(0.55 + 0.3 * k).toFixed(2)}))`;
      rg.classList.add('lit');
    } else {
      rg.style.fill = 'rgba(255,255,255,0.025)';
    }
    for (const [d, mirror] of shapes) {
      rg.append(el('path', { d }));
      if (mirror) rg.append(el('path', { d, transform: 'translate(200 0) scale(-1 1)' }));
    }
    g.append(rg);
  }
  for (const [lx, ly, rot, text] of Object.values(labels)) {
    const tx = el('text', { transform: `translate(${lx} ${ly}) rotate(${rot})`, class: 'region-label', 'text-anchor': 'middle', 'dominant-baseline': 'central' });
    tx.textContent = text;
    g.append(tx);
  }
  const t = el('text', { x: 100, y: 392, class: 'view-label', 'text-anchor': 'middle' });
  t.textContent = label;
  g.append(t);
  parent.append(g);
}

export function bodyMap(scores, selected, onPick) {
  const svg = el('svg', { viewBox: '0 0 420 400', class: 'bodymap', role: 'img', 'aria-label': 'Body strength map' });
  drawView(svg, 0, FRONT, 'FRONT', scores, selected, LABELS_FRONT);
  drawView(svg, 220, BACK, 'BACK', scores, selected, LABELS_BACK);
  svg.addEventListener('click', (e) => {
    const r = e.target.closest('[data-region]');
    if (r) onPick(r.getAttribute('data-region'));
  });
  return svg;
}
