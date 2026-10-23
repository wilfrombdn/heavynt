// Static data: body regions and the built-in exercise library.
// std = "intermediate" estimated 1-rep-max as a multiple of bodyweight (rough, adjustable per exercise).
// bw  = fraction of bodyweight moved by the exercise itself (0 = external load only).

export const REGIONS = {
  chest: 'Chest',
  shoulders: 'Shoulders',
  upperBack: 'Upper back',
  lowerBack: 'Lower back',
  biceps: 'Biceps',
  triceps: 'Triceps',
  forearms: 'Forearms',
  abs: 'Core',
  glutes: 'Glutes',
  quads: 'Quads',
  hamstrings: 'Hamstrings',
  calves: 'Calves',
};

export const REGION_GROUPS = {
  Upper: ['chest', 'shoulders', 'upperBack', 'lowerBack', 'biceps', 'triceps', 'forearms'],
  Core: ['abs'],
  Lower: ['glutes', 'quads', 'hamstrings', 'calves'],
};

export const CATEGORIES = ['Chest', 'Back', 'Shoulders', 'Arms', 'Core', 'Legs'];

const E = (id, name, cat, primary, secondary, std, bw = 0) => ({
  id, name, cat, primary, secondary, std, bw, custom: false,
});

export const LIBRARY = [
  // Chest
  E('bench', 'Barbell Bench Press', 'Chest', ['chest'], ['shoulders', 'triceps'], 1.25),
  E('incline-bench', 'Incline Barbell Bench Press', 'Chest', ['chest'], ['shoulders', 'triceps'], 1.0),
  E('db-bench', 'Dumbbell Bench Press', 'Chest', ['chest'], ['shoulders', 'triceps'], 0.5),
  E('incline-db', 'Incline Dumbbell Press', 'Chest', ['chest'], ['shoulders', 'triceps'], 0.42),
  E('chest-fly', 'Cable Chest Fly', 'Chest', ['chest'], ['shoulders'], 0.3),
  E('pushup', 'Push-Up', 'Chest', ['chest'], ['shoulders', 'triceps'], 1.0, 0.64),
  E('dip', 'Dip', 'Chest', ['chest', 'triceps'], ['shoulders'], 1.5, 1),
  // Back
  E('deadlift', 'Deadlift', 'Back', ['lowerBack', 'glutes', 'hamstrings'], ['upperBack', 'forearms', 'quads'], 1.75),
  E('row', 'Barbell Row', 'Back', ['upperBack'], ['biceps', 'lowerBack', 'forearms'], 0.9),
  E('pullup', 'Pull-Up', 'Back', ['upperBack'], ['biceps', 'forearms'], 1.3, 1),
  E('chinup', 'Chin-Up', 'Back', ['upperBack', 'biceps'], ['forearms'], 1.4, 1),
  E('lat-pulldown', 'Lat Pulldown', 'Back', ['upperBack'], ['biceps'], 0.9),
  E('cable-row', 'Seated Cable Row', 'Back', ['upperBack'], ['biceps'], 0.9),
  E('db-row', 'Dumbbell Row', 'Back', ['upperBack'], ['biceps', 'forearms'], 0.45),
  E('tbar-row', 'T-Bar Row', 'Back', ['upperBack'], ['biceps', 'lowerBack'], 0.8),
  E('back-ext', 'Back Extension', 'Back', ['lowerBack'], ['glutes', 'hamstrings'], 0.75, 0.5),
  E('shrug', 'Barbell Shrug', 'Back', ['upperBack'], ['forearms'], 1.1),
  // Shoulders
  E('ohp', 'Overhead Press', 'Shoulders', ['shoulders'], ['triceps', 'upperBack'], 0.75),
  E('db-press', 'Dumbbell Shoulder Press', 'Shoulders', ['shoulders'], ['triceps'], 0.34),
  E('arnold', 'Arnold Press', 'Shoulders', ['shoulders'], ['triceps'], 0.3),
  E('lateral', 'Lateral Raise', 'Shoulders', ['shoulders'], [], 0.16),
  E('rear-delt', 'Rear Delt Fly', 'Shoulders', ['shoulders'], ['upperBack'], 0.16),
  E('face-pull', 'Face Pull', 'Shoulders', ['shoulders', 'upperBack'], [], 0.35),
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
  E('leg-raise', 'Hanging Leg Raise', 'Core', ['abs'], ['forearms'], 0.6, 0.4),
  E('crunch', 'Crunch', 'Core', ['abs'], [], 0.5, 0.3),
  E('russian', 'Russian Twist', 'Core', ['abs'], [], 0.25),
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
];

// Fallback benchmark for custom exercises when the user leaves it blank.
export const DEFAULT_STD = {
  Chest: 1.0, Back: 0.9, Shoulders: 0.5, Arms: 0.5, Core: 0.5, Legs: 1.4,
};

// Starter plans (weights start at 0 so the app asks you to set them on first use).
export const STARTER_PLANS = [
  { name: 'Push', items: [['bench', 4, 8], ['incline-db', 3, 10], ['ohp', 3, 8], ['lateral', 3, 15], ['pushdown', 3, 12]] },
  { name: 'Pull', items: [['deadlift', 3, 5], ['pullup', 3, 8], ['row', 3, 8], ['face-pull', 3, 15], ['bb-curl', 3, 10]] },
  { name: 'Legs', items: [['squat', 4, 6], ['rdl', 3, 8], ['leg-press', 3, 12], ['leg-curl', 3, 12], ['calf-raise', 4, 15]] },
];
