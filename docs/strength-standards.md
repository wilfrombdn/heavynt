# Strength standards

How Heavyn't decides what a person "should" lift, and where the numbers come from.
Everything below lives in `src/data.js` (`STANDARDS`, `FEMALE_STD`, each exercise's `std`) and is generated
into `native/.../LibraryData.kt` by `node scripts/gen-kotlin-data.mjs`. Replace those with your own database later.

## Source

[Strength Level](https://strengthlevel.com/strength-standards) standards tables, read 2026-10-24. They come from
lifts self-reported by Strength Level users, so "average" means the average *lifter*, not the general population.
Levels mean: Beginner beats 5% of lifters, Novice 20%, Intermediate 50%, Advanced 80%, Elite 95%.

## The formula

    target = std × refKg × (bodyweight / refKg) ^ exponent × age factor × level factor

| | Men | Women |
|---|---|---|
| Reference bodyweight | 82 kg (181 lb) | 65 kg (143 lb) |
| Bodyweight exponent | 0.85 | 0.72 |
| Level factors (beginner…elite) | 0.52, 0.74, 1, 1.30, 1.63 | 0.37, 0.64, 1, 1.44, 1.94 |

- `std` = intermediate one-rep max ÷ bodyweight at the reference weight. Women use `FEMALE_STD` where measured,
  otherwise `std × femaleRatio` for the exercise's first main muscle (pressing ≈ 0.62, pulling/arms ≈ 0.68,
  legs ≈ 0.72, glutes/hips ≈ 0.85; measured ratios ranged 0.59–0.87).
- Bodyweight above BMI 27 counts half (needs height).
- Age: Strength Level's age table, flat from 25 to 40, then about 0.887 at 50, 0.745 at 60, 0.608 at 70, 0.49 at 80.
- Bodyweight moves (pull-ups, dips…) never ask a woman for less than 1.1 × the body part moved.
- Dumbbell lifts are entered as both dumbbells added together, so their `std` is doubled from the per-dumbbell table.

## Checked against the tables (unit test `targetsMatchPublishedStandards`, within 5–6%)

| Lift | Profile | Strength Level | App |
|---|---|---|---|
| Bench | Man, 180 lb | 220 lb | ✓ |
| Bench | Man, 150 / 250 lb | 183 / 295 lb | ✓ |
| Squat / Deadlift / Press | Man, 180 lb | 298 / 348 / 140 lb | ✓ |
| Bench | Woman, 140 / 180 lb | 108 / 131 lb | ✓ |
| Squat / Deadlift | Woman, 150 lb | 174 / 205 lb | ✓ |
| Squat at 50 vs 30 | Man | × 0.887 | ✓ |

Measured exercises (men at 180 lb / women at 140 lb, intermediate): bench, incline bench, dumbbell bench, squat,
front squat, deadlift, Romanian deadlift, hip thrust, leg press, leg extension, seated leg curl, calf raise,
hip adduction, overhead press, dumbbell press, lateral raise, barbell curl, dumbbell curl, triceps pushdown,
barbell row, lat pulldown, seated cable row. Other exercises are scaled from the nearest measured lift.
Neck and tibialis benchmarks are rough estimates with no published table.

## Muscles on the map

23 regions: upper chest, chest, front/side/rear delts, traps, mid back, lats, lower back, biceps, triceps,
forearms, abs, obliques, hip flexors, glutes, outer hips, inner thighs, quads, hamstrings, calves, shins, neck.
Deep muscles you can't see or train on their own (rotator cuff, serratus, brachialis…) are left out on purpose.
