// Static data: body regions and the built-in exercise library.
// std = "intermediate" estimated 1-rep-max as a multiple of bodyweight (rough, adjustable per exercise).
// bw  = fraction of bodyweight moved by the exercise itself (0 = external load only).

export const REGIONS = {
  upperChest: 'Upper chest',
  chest: 'Chest',
  shoulders: 'Front delts',
  sideDelts: 'Side delts',
  rearDelts: 'Rear delts',
  traps: 'Traps',
  upperBack: 'Mid back',
  lats: 'Lats',
  lowerBack: 'Lower back',
  biceps: 'Biceps',
  triceps: 'Triceps',
  forearms: 'Forearms',
  abs: 'Abs',
  obliques: 'Obliques',
  glutes: 'Glutes',
  quads: 'Quads',
  hamstrings: 'Hamstrings',
  calves: 'Calves',
};

export const REGION_GROUPS = {
  Upper: ['upperChest', 'chest', 'shoulders', 'sideDelts', 'rearDelts', 'traps', 'upperBack', 'lats', 'lowerBack', 'biceps', 'triceps', 'forearms'],
  Core: ['abs', 'obliques'],
  Lower: ['glutes', 'quads', 'hamstrings', 'calves'],
};

export const CATEGORIES = ['Chest', 'Back', 'Shoulders', 'Arms', 'Core', 'Legs', 'Olympic'];

const E = (id, name, cat, primary, secondary, std, bw = 0) => ({
  id, name, cat, primary, secondary, std, bw, custom: false,
});

const RAW = [
  // Chest
  E('bench', 'Barbell Bench Press', 'Chest', ['chest'], ['shoulders', 'triceps'], 1.25),
  E('incline-bench', 'Incline Barbell Bench Press', 'Chest', ['upperChest'], ['shoulders', 'triceps'], 1.0),
  E('db-bench', 'Dumbbell Bench Press', 'Chest', ['chest'], ['shoulders', 'triceps'], 0.5),
  E('incline-db', 'Incline Dumbbell Press', 'Chest', ['upperChest'], ['shoulders', 'triceps'], 0.42),
  E('chest-fly', 'Cable Chest Fly', 'Chest', ['chest'], ['shoulders'], 0.3),
  E('pushup', 'Push-Up', 'Chest', ['chest'], ['shoulders', 'triceps'], 1.0, 0.64),
  E('dip', 'Dip', 'Chest', ['chest', 'triceps'], ['shoulders'], 1.5, 1),
  // Back
  E('deadlift', 'Deadlift', 'Back', ['lowerBack', 'glutes', 'hamstrings'], ['traps', 'lats', 'forearms', 'quads'], 1.75),
  E('row', 'Barbell Row', 'Back', ['lats', 'upperBack'], ['rearDelts', 'biceps', 'lowerBack', 'forearms'], 0.9),
  E('pullup', 'Pull-Up', 'Back', ['lats'], ['upperBack', 'biceps', 'forearms'], 1.3, 1),
  E('chinup', 'Chin-Up', 'Back', ['lats', 'biceps'], ['upperBack', 'forearms'], 1.4, 1),
  E('lat-pulldown', 'Lat Pulldown', 'Back', ['lats'], ['upperBack', 'biceps'], 0.9),
  E('cable-row', 'Seated Cable Row', 'Back', ['upperBack', 'lats'], ['rearDelts', 'biceps'], 0.9),
  E('db-row', 'Dumbbell Row', 'Back', ['lats'], ['upperBack', 'biceps', 'forearms'], 0.45),
  E('tbar-row', 'T-Bar Row', 'Back', ['upperBack', 'lats'], ['rearDelts', 'biceps', 'lowerBack'], 0.8),
  E('back-ext', 'Back Extension', 'Back', ['lowerBack'], ['glutes', 'hamstrings'], 0.75, 0.5),
  E('shrug', 'Barbell Shrug', 'Back', ['traps'], ['forearms'], 1.1),
  // Shoulders
  E('ohp', 'Overhead Press', 'Shoulders', ['shoulders'], ['sideDelts', 'triceps', 'traps'], 0.75),
  E('db-press', 'Dumbbell Shoulder Press', 'Shoulders', ['shoulders'], ['sideDelts', 'triceps'], 0.34),
  E('arnold', 'Arnold Press', 'Shoulders', ['shoulders', 'sideDelts'], ['triceps'], 0.3),
  E('lateral', 'Lateral Raise', 'Shoulders', ['sideDelts'], ['traps'], 0.16),
  E('rear-delt', 'Rear Delt Fly', 'Shoulders', ['rearDelts'], ['upperBack'], 0.16),
  E('face-pull', 'Face Pull', 'Shoulders', ['rearDelts', 'upperBack'], ['traps'], 0.35),
  // Arms
  E('bb-curl', 'Barbell Curl', 'Arms', ['biceps'], ['forearms'], 0.6),
  E('db-curl', 'Dumbbell Curl', 'Arms', ['biceps'], ['forearms'], 0.28),
  E('hammer', 'Hammer Curl', 'Arms', ['biceps', 'forearms'], [], 0.3),
  E('pushdown', 'Triceps Pushdown', 'Arms', ['triceps'], [], 0.65),
  E('skull', 'Skull Crusher', 'Arms', ['triceps'], [], 0.55),
  E('oh-tri', 'Overhead Triceps Extension', 'Arms', ['triceps'], [], 0.45),
  E('cgbp', 'Close-Grip Bench Press', 'Arms', ['triceps'], ['chest', 'shoulders'], 1.05),
  E('wrist-curl', 'Wrist Curl', 'Arms', ['forearms'], [], 0.35),
  // Core
  E('cable-crunch', 'Cable Crunch', 'Core', ['abs'], [], 0.9),
  E('leg-raise', 'Hanging Leg Raise', 'Core', ['abs'], ['obliques', 'forearms'], 0.6, 0.4),
  E('crunch', 'Crunch', 'Core', ['abs'], [], 0.5, 0.3),
  E('russian', 'Russian Twist', 'Core', ['obliques'], ['abs'], 0.25),
  // Legs
  E('squat', 'Back Squat', 'Legs', ['quads', 'glutes'], ['hamstrings', 'lowerBack'], 1.5),
  E('front-squat', 'Front Squat', 'Legs', ['quads'], ['glutes', 'abs'], 1.2),
  E('leg-press', 'Leg Press', 'Legs', ['quads', 'glutes'], ['hamstrings'], 3.0),
  E('rdl', 'Romanian Deadlift', 'Legs', ['hamstrings', 'glutes'], ['lowerBack', 'forearms'], 1.5),
  E('lunge', 'Dumbbell Lunge', 'Legs', ['quads', 'glutes'], ['hamstrings'], 0.4),
  E('split-squat', 'Bulgarian Split Squat', 'Legs', ['quads', 'glutes'], ['hamstrings'], 0.4),
  E('leg-ext', 'Leg Extension', 'Legs', ['quads'], [], 1.0),
  E('leg-curl', 'Leg Curl', 'Legs', ['hamstrings'], [], 0.8),
  E('hip-thrust', 'Hip Thrust', 'Legs', ['glutes'], ['hamstrings'], 1.75),
  E('calf-raise', 'Standing Calf Raise', 'Legs', ['calves'], [], 1.75),
  E('seated-calf', 'Seated Calf Raise', 'Legs', ['calves'], [], 1.0),

  // ---- Extended library ----
  // Chest
  E('decline-bench', 'Decline Barbell Bench Press', 'Chest', ['chest'], ['triceps', 'shoulders'], 1.3),
  E('decline-db', 'Decline Dumbbell Press', 'Chest', ['chest'], ['triceps', 'shoulders'], 0.5),
  E('smith-bench', 'Smith Machine Bench Press', 'Chest', ['chest'], ['triceps', 'shoulders'], 1.2),
  E('smith-incline', 'Smith Machine Incline Press', 'Chest', ['upperChest'], ['shoulders', 'triceps'], 1.0),
  E('machine-chest', 'Machine Chest Press', 'Chest', ['chest'], ['triceps', 'shoulders'], 1.0),
  E('floor-press', 'Floor Press', 'Chest', ['chest'], ['triceps'], 1.1),
  E('pec-deck', 'Pec Deck Fly', 'Chest', ['chest'], [], 0.6),
  E('db-fly', 'Dumbbell Fly', 'Chest', ['chest'], ['shoulders'], 0.2),
  E('cable-crossover', 'Cable Crossover', 'Chest', ['chest'], ['shoulders'], 0.3),
  E('low-cable-fly', 'Low-to-High Cable Fly', 'Chest', ['upperChest'], ['shoulders'], 0.2),
  E('db-pullover', 'Dumbbell Pullover', 'Chest', ['chest', 'lats'], ['triceps'], 0.4),
  E('landmine-press', 'Landmine Press', 'Chest', ['shoulders', 'upperChest'], ['triceps'], 0.4),
  E('wide-pushup', 'Wide Push-Up', 'Chest', ['chest'], ['shoulders', 'triceps'], 1.0, 0.64),
  E('decline-pushup', 'Decline Push-Up', 'Chest', ['upperChest', 'shoulders'], ['triceps'], 1.1, 0.7),
  E('diamond-pushup', 'Diamond Push-Up', 'Chest', ['triceps'], ['chest', 'shoulders'], 0.9, 0.64),
  // Back
  E('pendlay-row', 'Pendlay Row', 'Back', ['lats', 'upperBack'], ['rearDelts', 'biceps', 'lowerBack'], 0.85),
  E('chest-row', 'Chest-Supported Row', 'Back', ['upperBack', 'lats'], ['rearDelts', 'biceps'], 0.7),
  E('machine-row', 'Machine Row', 'Back', ['upperBack', 'lats'], ['rearDelts', 'biceps'], 0.9),
  E('meadows-row', 'Meadows Row', 'Back', ['lats'], ['upperBack', 'biceps', 'forearms'], 0.5),
  E('seal-row', 'Seal Row', 'Back', ['upperBack', 'lats'], ['rearDelts', 'biceps'], 0.7),
  E('inverted-row', 'Inverted Row', 'Back', ['upperBack', 'lats'], ['rearDelts', 'biceps'], 0.9, 0.6),
  E('one-arm-cable-row', 'One-Arm Cable Row', 'Back', ['lats'], ['upperBack', 'biceps'], 0.4),
  E('wide-pulldown', 'Wide-Grip Lat Pulldown', 'Back', ['lats'], ['upperBack', 'biceps'], 0.9),
  E('close-pulldown', 'Close-Grip Lat Pulldown', 'Back', ['lats'], ['upperBack', 'biceps'], 0.9),
  E('straight-arm', 'Straight-Arm Pulldown', 'Back', ['lats'], ['triceps'], 0.4),
  E('neutral-pullup', 'Neutral-Grip Pull-Up', 'Back', ['lats'], ['upperBack', 'biceps', 'forearms'], 1.35, 1),
  E('wide-pullup', 'Wide-Grip Pull-Up', 'Back', ['lats'], ['upperBack', 'biceps', 'forearms'], 1.25, 1),
  E('rack-pull', 'Rack Pull', 'Back', ['traps', 'lowerBack'], ['upperBack', 'glutes', 'forearms'], 2.0),
  E('sumo-deadlift', 'Sumo Deadlift', 'Back', ['glutes', 'quads', 'hamstrings'], ['lowerBack', 'traps', 'forearms'], 1.75),
  E('trap-bar-deadlift', 'Trap Bar Deadlift', 'Back', ['quads', 'glutes', 'lowerBack'], ['hamstrings', 'traps', 'forearms'], 1.9),
  E('good-morning', 'Good Morning', 'Back', ['lowerBack', 'hamstrings'], ['glutes'], 0.8),
  E('db-shrug', 'Dumbbell Shrug', 'Back', ['traps'], ['forearms'], 0.5),
  E('reverse-hyper', 'Reverse Hyperextension', 'Back', ['lowerBack', 'glutes'], ['hamstrings'], 0.7),
  // Shoulders
  E('seated-bb-press', 'Seated Barbell Shoulder Press', 'Shoulders', ['shoulders'], ['sideDelts', 'triceps'], 0.7),
  E('push-press', 'Push Press', 'Shoulders', ['shoulders'], ['sideDelts', 'triceps', 'quads'], 0.9),
  E('machine-shoulder', 'Machine Shoulder Press', 'Shoulders', ['shoulders'], ['sideDelts', 'triceps'], 0.8),
  E('z-press', 'Z Press', 'Shoulders', ['shoulders'], ['sideDelts', 'triceps', 'abs'], 0.5),
  E('kb-press', 'Kettlebell Press', 'Shoulders', ['shoulders'], ['sideDelts', 'triceps'], 0.35),
  E('cable-lateral', 'Cable Lateral Raise', 'Shoulders', ['sideDelts'], [], 0.12),
  E('front-raise', 'Front Raise', 'Shoulders', ['shoulders'], ['upperChest'], 0.15),
  E('upright-row', 'Upright Row', 'Shoulders', ['sideDelts', 'traps'], ['shoulders', 'biceps'], 0.6),
  E('reverse-pec-deck', 'Reverse Pec Deck', 'Shoulders', ['rearDelts'], ['upperBack'], 0.5),
  E('hspu', 'Handstand Push-Up', 'Shoulders', ['shoulders'], ['sideDelts', 'triceps', 'traps'], 1.3, 0.9),
  E('pike-pushup', 'Pike Push-Up', 'Shoulders', ['shoulders'], ['triceps', 'upperChest'], 1.0, 0.6),
  // Arms
  E('ez-curl', 'EZ-Bar Curl', 'Arms', ['biceps'], ['forearms'], 0.6),
  E('preacher', 'Preacher Curl', 'Arms', ['biceps'], [], 0.5),
  E('incline-curl', 'Incline Dumbbell Curl', 'Arms', ['biceps'], [], 0.22),
  E('cable-curl', 'Cable Curl', 'Arms', ['biceps'], ['forearms'], 0.45),
  E('bayesian', 'Bayesian Cable Curl', 'Arms', ['biceps'], [], 0.2),
  E('spider-curl', 'Spider Curl', 'Arms', ['biceps'], [], 0.4),
  E('concentration', 'Concentration Curl', 'Arms', ['biceps'], [], 0.25),
  E('drag-curl', 'Drag Curl', 'Arms', ['biceps'], ['forearms'], 0.5),
  E('zottman', 'Zottman Curl', 'Arms', ['biceps', 'forearms'], [], 0.2),
  E('reverse-curl', 'Reverse Curl', 'Arms', ['forearms'], ['biceps'], 0.4),
  E('rope-pushdown', 'Rope Pushdown', 'Arms', ['triceps'], [], 0.6),
  E('oh-rope', 'Overhead Rope Extension', 'Arms', ['triceps'], [], 0.45),
  E('jm-press', 'JM Press', 'Arms', ['triceps'], ['chest'], 0.9),
  E('tate-press', 'Tate Press', 'Arms', ['triceps'], [], 0.25),
  E('kickback', 'Triceps Kickback', 'Arms', ['triceps'], [], 0.15),
  E('bench-dip', 'Bench Dip', 'Arms', ['triceps'], ['chest', 'shoulders'], 0.9, 0.5),
  E('rev-wrist-curl', 'Reverse Wrist Curl', 'Arms', ['forearms'], [], 0.2),
  E('farmers-walk', "Farmer's Walk", 'Arms', ['forearms', 'traps'], ['abs', 'obliques', 'glutes'], 0.75),
  // Core
  E('ab-wheel', 'Ab Wheel Rollout', 'Core', ['abs'], ['lats', 'shoulders'], 0.8, 0.5),
  E('knee-raise', 'Hanging Knee Raise', 'Core', ['abs'], ['forearms'], 0.5, 0.3),
  E('toes-to-bar', 'Toes to Bar', 'Core', ['abs'], ['obliques', 'forearms', 'lats'], 0.7, 0.4),
  E('dragon-flag', 'Dragon Flag', 'Core', ['abs'], ['obliques'], 0.9, 0.6),
  E('decline-situp', 'Decline Sit-Up', 'Core', ['abs'], [], 0.6, 0.35),
  E('machine-crunch', 'Machine Crunch', 'Core', ['abs'], [], 0.8),
  E('woodchop', 'Cable Woodchop', 'Core', ['obliques'], ['abs'], 0.3),
  E('pallof', 'Pallof Press', 'Core', ['obliques', 'abs'], [], 0.2),
  E('side-bend', 'Dumbbell Side Bend', 'Core', ['obliques'], [], 0.4),
  // Legs
  E('hack-squat', 'Hack Squat', 'Legs', ['quads'], ['glutes'], 1.8),
  E('smith-squat', 'Smith Machine Squat', 'Legs', ['quads', 'glutes'], ['hamstrings'], 1.4),
  E('goblet', 'Goblet Squat', 'Legs', ['quads', 'glutes'], ['abs'], 0.45),
  E('box-squat', 'Box Squat', 'Legs', ['quads', 'glutes'], ['hamstrings', 'lowerBack'], 1.5),
  E('pause-squat', 'Pause Squat', 'Legs', ['quads', 'glutes'], ['hamstrings', 'lowerBack'], 1.4),
  E('zercher', 'Zercher Squat', 'Legs', ['quads', 'glutes'], ['abs', 'biceps'], 1.1),
  E('safety-squat', 'Safety Bar Squat', 'Legs', ['quads', 'glutes'], ['hamstrings', 'upperBack'], 1.45),
  E('belt-squat', 'Belt Squat', 'Legs', ['quads', 'glutes'], [], 1.5),
  E('walking-lunge', 'Walking Lunge', 'Legs', ['quads', 'glutes'], ['hamstrings'], 0.4),
  E('reverse-lunge', 'Reverse Lunge', 'Legs', ['quads', 'glutes'], ['hamstrings'], 0.4),
  E('step-up', 'Dumbbell Step-Up', 'Legs', ['quads', 'glutes'], [], 0.4),
  E('sldl', 'Stiff-Leg Deadlift', 'Legs', ['hamstrings', 'lowerBack'], ['glutes', 'forearms'], 1.5),
  E('db-rdl', 'Dumbbell Romanian Deadlift', 'Legs', ['hamstrings', 'glutes'], ['lowerBack', 'forearms'], 0.6),
  E('sl-rdl', 'Single-Leg Romanian Deadlift', 'Legs', ['hamstrings', 'glutes'], ['lowerBack'], 0.35),
  E('nordic', 'Nordic Hamstring Curl', 'Legs', ['hamstrings'], ['calves'], 1.0, 0.6),
  E('seated-leg-curl', 'Seated Leg Curl', 'Legs', ['hamstrings'], [], 0.75),
  E('lying-leg-curl', 'Lying Leg Curl', 'Legs', ['hamstrings'], [], 0.75),
  E('glute-bridge', 'Barbell Glute Bridge', 'Legs', ['glutes'], ['hamstrings'], 1.2),
  E('pull-through', 'Cable Pull-Through', 'Legs', ['glutes', 'hamstrings'], ['lowerBack'], 0.7),
  E('kb-swing', 'Kettlebell Swing', 'Legs', ['glutes', 'hamstrings'], ['lowerBack', 'shoulders'], 0.5),
  E('hip-abduction', 'Hip Abduction Machine', 'Legs', ['glutes'], [], 1.0),
  E('donkey-calf', 'Donkey Calf Raise', 'Legs', ['calves'], [], 1.8),
  E('leg-press-calf', 'Leg Press Calf Raise', 'Legs', ['calves'], [], 2.5),
  E('db-calf', 'Single-Leg Dumbbell Calf Raise', 'Legs', ['calves'], [], 0.5),
  E('machine-lateral', 'Machine Lateral Raise', 'Shoulders', ['sideDelts'], [], 0.4),
  E('cable-rear-fly', 'Rear-Delt Cable Fly', 'Shoulders', ['rearDelts'], ['upperBack'], 0.2),
  // Olympic / power
  E('power-clean', 'Power Clean', 'Olympic', ['traps', 'glutes', 'hamstrings'], ['quads', 'lowerBack', 'upperBack'], 1.0),
  E('hang-clean', 'Hang Clean', 'Olympic', ['traps', 'glutes', 'hamstrings'], ['quads', 'upperBack'], 0.9),
  E('snatch', 'Snatch', 'Olympic', ['shoulders', 'glutes', 'quads'], ['traps', 'upperBack', 'hamstrings'], 0.75),
  E('clean-jerk', 'Clean & Jerk', 'Olympic', ['shoulders', 'quads', 'glutes'], ['traps', 'triceps'], 0.9),
];

// Fallback benchmark for custom exercises when the user leaves it blank.
export const DEFAULT_STD = {
  Chest: 1.0, Back: 0.9, Shoulders: 0.5, Arms: 0.5, Core: 0.5, Legs: 1.4, Olympic: 0.9,
};

// Starter programme: [exercise id, sets, reps, note]. Weights start at 0 so you set your own.
export const STARTER_PLANS = [
  { weekday: 2, name: 'Push', items: [
    ['bench', 5, 5, 'Heavy · 4–6 reps · rest 3 min'],
    ['incline-db', 3, 8, 'A1 · superset with lateral raise'],
    ['cable-lateral', 3, 8, 'A2 · 8–10 per side · rest 75 s'],
    ['dip', 3, 8, 'B1 · weighted · superset with triceps extension'],
    ['oh-rope', 3, 8, 'B2 · overhead cable · 8–10 reps · rest 75 s'],
    ['pec-deck', 1, 20, 'Finisher · triple drop: 20 → drop 25% → max → drop 25% → max'],
    ['machine-lateral', 2, 20, 'Finisher · 20–25 reps · rest 45 s'],
  ] },
  { weekday: 3, name: 'Pull', items: [
    ['pullup', 5, 5, 'Heavy 1 · weighted pull-up (or heavy lat pulldown) · 4–6 reps · rest 3 min'],
    ['row', 3, 5, 'Heavy 2 · rest 2.5 min'],
    ['one-arm-cable-row', 3, 8, 'A1 · per side · superset with face pull'],
    ['face-pull', 3, 10, 'A2 · 10–12 reps · rest 75 s'],
    ['incline-curl', 3, 8, 'B1 · superset with reverse pec deck'],
    ['reverse-pec-deck', 3, 10, 'B2 · rest 75 s'],
    ['lat-pulldown', 1, 15, 'Finisher · myo-reps: 15–20, then 4–5 mini-sets of 5 with 15 s rest'],
  ] },
  { weekday: 4, name: 'Legs + Abs', items: [
    ['leg-press', 5, 5, 'Heavy · 5–6 reps · rest 3 min'],
    ['rdl', 3, 8, 'A1 · superset with calf raise'],
    ['db-calf', 3, 8, 'A2 · single-leg · 8–10 per side · rest 90 s'],
    ['seated-leg-curl', 3, 8, 'B1 · seated or lying · superset with cable crunch'],
    ['cable-crunch', 3, 10, 'B2 · rest 75 s'],
    ['leg-ext', 1, 20, 'Finisher · 20 + one drop to failure'],
  ] },
  { weekday: 5, name: 'Shoulders + Arms', items: [
    ['ohp', 5, 5, 'Heavy · standing · 4–6 reps · rest 3 min'],
    ['cgbp', 3, 8, 'A1 · superset with EZ-bar curl'],
    ['ez-curl', 3, 8, 'A2 · rest 90 s'],
    ['lateral', 4, 10, 'B1 · superset with rear-delt fly'],
    ['cable-rear-fly', 3, 10, 'B2 · rest 60 s'],
    ['cable-curl', 2, 25, 'Finisher · arm burnout: cable curl 25 → rope pushdown 25, no rest · rest 60 s'],
    ['rope-pushdown', 2, 25, 'Finisher · arm burnout, straight after the curls'],
    ['cable-lateral', 1, 12, 'Finisher · drop set per side: to failure, drop, to failure'],
  ] },
  { weekday: 6, name: 'Upper Volume', items: [
    ['incline-bench', 5, 5, 'Heavy 1 · 4–6 reps · rest 3 min'],
    ['row', 3, 5, 'Heavy 2 · rest 2.5 min'],
    ['chest-row', 3, 8, 'A1 · chest-supported T-bar row · superset with machine chest press'],
    ['machine-chest', 3, 8, 'A2 · rest 90 s'],
    ['close-pulldown', 3, 8, 'B1 · neutral-grip lat pulldown · superset with cable fly'],
    ['low-cable-fly', 3, 8, 'B2 · low-to-high · 8–10 reps · rest 75 s'],
    ['straight-arm', 1, 25, 'Finisher'],
    ['pushup', 2, 15, 'Finisher · max reps · rest 60 s'],
  ] },
];

// Schedule: weekday index (Mon = 0) for each plan. Wed-Sun of "week 1"; week 2 is the week off (repeats every 2 weeks).
export const STARTER_WEEKS = 2;

// Dumbbell lifts done with a pair: the app takes the COMBINED weight (both dumbbells added up).
// Their `std` is therefore the combined-weight benchmark.
export const PAIRED = new Set(['db-bench', 'incline-db', 'decline-db', 'db-press', 'arnold', 'lateral', 'rear-delt', 'db-curl',
  'hammer', 'incline-curl', 'zottman', 'db-fly', 'front-raise', 'kickback', 'lunge', 'split-squat', 'walking-lunge',
  'reverse-lunge', 'step-up', 'db-rdl', 'db-shrug', 'farmers-walk']);

export const LIBRARY = RAW.map((e) => (PAIRED.has(e.id)
  ? { ...e, pair: true, std: Math.round(e.std * 2 * 100) / 100 }
  : { ...e, pair: false }));
