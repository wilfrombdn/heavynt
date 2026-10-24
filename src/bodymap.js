// Front/back anatomy figure as inline SVG. Each region is a group tinted by strength score.
import { scoreRGB, scoreStrength } from './logic.js';

const NS = 'http://www.w3.org/2000/svg';
const M = true; // also draw a mirrored copy on the right side

// rounded rectangle as a path
const rr = (x, y, w, h, r) =>
  `M${x + r} ${y}h${w - 2 * r}a${r} ${r} 0 0 1 ${r} ${r}v${h - 2 * r}a${r} ${r} 0 0 1 -${r} ${r}h-${w - 2 * r}a${r} ${r} 0 0 1 -${r} -${r}v-${h - 2 * r}a${r} ${r} 0 0 1 ${r} -${r}z`;

// shared by both views
const DELT = 'M72 68C62 62 48 66 45 80C43 91 47 99 54 100C58 91 64 83 72 78C75 75 75 71 72 68Z';
const UPPER_ARM = 'M47 101C41 115 40 129 43 142C48 145 54 145 58 142C62 128 62 113 58 102C54 104 50 104 47 101Z';
const FOREARM = 'M43 147C38 163 36 181 36 198C41 202 47 201 52 198C56 181 58 163 58 147C53 149 48 149 43 147Z';
const CALF_F = 'M78 264C74 286 76 320 82 346C86 348 91 348 94 346C99 320 100 286 96 264C90 267 84 267 78 264Z';

const FRONT = {
  chest: [['M98 66C88 64 78 65 72 70C70 80 71 90 77 96C85 102 94 102 98 100Z', M]],
  shoulders: [[DELT, M]],
  biceps: [[UPPER_ARM, M]],
  forearms: [[FOREARM, M]],
  abs: [
    [rr(84, 105, 14, 14, 4) + rr(84, 122, 14, 14, 4) + rr(84, 139, 14, 14, 4), M],
    ['M72 103C69 121 72 141 80 159L82 159C83 141 83 123 82 107C78 107 75 106 72 103Z', M],
  ],
  quads: [['M81 166C70 188 68 228 77 256C84 260 93 259 97 255C101 232 101 196 98 168C92 166 86 165 81 166Z', M]],
  calves: [[CALF_F, M]],
};

const BACK = {
  upperBack: [
    ['M100 52C92 56 84 60 76 66C80 76 90 90 100 100C110 90 120 76 124 66C116 60 108 56 100 52Z', false],
    ['M76 84C70 102 72 124 82 137C90 139 96 137 98 135L99 108C90 102 82 94 76 84Z', M],
  ],
  shoulders: [[DELT, M]],
  triceps: [[UPPER_ARM, M]],
  forearms: [[FOREARM, M]],
  lowerBack: [['M84 139C90 141 96 141 99 139L99 162C92 164 86 163 83 160C82 153 82 146 84 139Z', M]],
  glutes: [['M82 166C72 172 70 190 78 203C88 209 96 205 99 197L99 168C94 166 88 165 82 166Z', M]],
  hamstrings: [['M79 208C72 226 73 244 78 258C85 261 93 260 97 257C101 242 100 224 98 208C92 210 85 210 79 208Z', M]],
  calves: [['M78 264C72 284 74 318 82 344C86 346 91 346 94 344C100 320 100 286 96 264C90 267 84 267 78 264Z', M]],
};

// Outline-only parts (not tracked): head, neck, trap line, hands, feet.
const OUTLINE = [
  ['M100 12C109 12 115 20 115 29C115 40 108 47 100 47C92 47 85 40 85 29C85 20 91 12 100 12Z', false],
  ['M93 46C93 52 92 55 90 57M107 46C107 52 108 55 110 57', false],
  ['M91 57C82 60 70 62 60 67', M],
  ['M37 201C32 207 34 217 41 219C47 217 49 208 47 201', M],
  ['M77 350C73 362 76 372 84 373C90 373 94 369 94 360L94 350', M],
];

const el = (tag, attrs) => {
  const n = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, v);
  return n;
};

function drawView(parent, x, parts, label, scores, selected) {
  const g = el('g', { transform: `translate(${x} 0)` });
  for (const [d, mirror] of OUTLINE) {
    g.append(el('path', { d, class: 'outline' }));
    if (mirror) g.append(el('path', { d, class: 'outline', transform: 'translate(200 0) scale(-1 1)' }));
  }
  for (const [region, shapes] of Object.entries(parts)) {
    const sc = scores[region];
    const rg = el('g', { class: 'region' + (selected === region ? ' sel' : ''), 'data-region': region });
    if (sc) {
      // Ambient look: translucent tint, hairline edge in the same hue, layered glow that grows with the gap to target.
      const c = scoreRGB(sc.score).join(',');
      const k = scoreStrength(sc.score);
      rg.style.fill = `rgba(${c},${(0.3 + 0.28 * k).toFixed(2)})`;
      rg.style.setProperty('--rs', `rgba(${c},1)`);
      rg.style.filter = `drop-shadow(0 0 ${(2 + 2 * k).toFixed(1)}px rgba(${c},${(0.85 + 0.15 * k).toFixed(2)})) ` +
        `drop-shadow(0 0 ${(7 + 6 * k).toFixed(1)}px rgba(${c},${(0.55 + 0.25 * k).toFixed(2)}))`;
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
  const t = el('text', { x: 100, y: 392, class: 'view-label', 'text-anchor': 'middle' });
  t.textContent = label;
  g.append(t);
  parent.append(g);
}

export function bodyMap(scores, selected, onPick) {
  const svg = el('svg', { viewBox: '0 0 420 400', class: 'bodymap', role: 'img', 'aria-label': 'Body strength map' });
  drawView(svg, 0, FRONT, 'FRONT', scores, selected);
  drawView(svg, 220, BACK, 'BACK', scores, selected);
  svg.addEventListener('click', (e) => {
    const r = e.target.closest('[data-region]');
    if (r) onPick(r.getAttribute('data-region'));
  });
  return svg;
}
