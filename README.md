# My Fitness

A local-first personal health experiment journal for Android. Not a fitness tracker and
not a calorie counter: log the day in under a minute, then use weeks of your own data to
find out what actually works for you.

The design constraint that shapes everything else: **logging takes under 30 seconds a day,
analysis can be as sophisticated as you like later.**

## What it does

| Screen | What it is for |
| --- | --- |
| **Today** | Every active tracker on one scroll, grouped by Exercise / Food / Behaviour / Journal / Health, plus a free-text note. The screen you use 90% of the time. |
| **History** | Calendar heatmap of the last 18 weeks. Tap a day to see everything logged, including which values came from Health Connect. |
| **Insights** | 7 / 30 / 90-day trends, local "when I do X, what happens to Y?" splits, and the **Review my week** button. |
| **Plan** | This week's approved targets with live progress, and any AI-proposed plan waiting for your approval. |

Steps, workout minutes, sleep, resting heart rate, active calories and weight come from
**Health Connect** so you do not type them in. A manual entry always beats a synced one:
if you corrected a value by hand, no later sync overwrites it.

## Trackers, not fields

Nothing loggable is hardcoded. A tracker is a row with a type:

| Type | Example |
| --- | --- |
| `BOOLEAN` | Strength training: yes/no |
| `NUMBER` | Weight: 74.2 kg |
| `DURATION` | Exercise: 45 min |
| `RATING` | Energy: 4/5 |
| `TEXT` | Meal note |
| `SELECT` | Alcohol: None / 1 / 2 / 3+ |

Each tracker also carries a category, a direction (`more is better`, `less is better`,
`just observe`) and an aggregation (`days done`, `total`, `average`, `latest`), which is
how the app knows that "Junk food 3/7 days" is a worse week than "1/7" without anything
being special-cased. Add, edit, reorder or switch off trackers from the Trackers screen;
the database never changes.

The app ships with a sensible starter set (see `DefaultTrackers.kt`) and all of it is
editable.

## The AI loop

The model never touches the database. Pressing **Review my week** builds a compact JSON
snapshot of aggregates plus your own journal text, and that snapshot is the only thing
sent:

```json
{
  "period": "2026-09-07/2026-09-13",
  "days": 7,
  "exercise": { "strength_training": "3/7 days", "cardio": "1/7 days", "steps": 7120, "exercise_duration": 185 },
  "nutrition": { "protein_target": "5/7 days", "vegetables": "6/7 days", "junk_food": "3/7 days" },
  "behaviour": { "bed_before_11_30": "3/7 days", "meditation": "4/7 days" },
  "subjective": { "energy": 3.4, "hunger": 2.8, "stress": 3.1 },
  "journal": ["Mon 07 Sep: Good session, slept well"],
  "plan": { "targets": [{ "tracker": "Strength training", "target_frequency": 4, "actual_days": 3, "met": false }] },
  "previous_period": { "exercise": { "strength_training": "2/7 days" } }
}
```

You can see the exact snapshot before sending it with **Preview data** on the Insights tab.

The reply must be one JSON object with three separate outputs:

- **Weekly review** — what happened, with numbers, compared to last week.
- **Insights** — possible patterns, each stating its own uncertainty.
- **Plan** — concrete targets for next week.

Then the loop closes: `TRACK → MEASURE → AI REVIEW → PATTERNS → EXPERIMENT → NEXT WEEK'S PLAN → TRACK`.

### What the AI is not allowed to do

This is enforced by the type system, not by convention (`ai/PeriodData.kt`,
`data/repository/AiAccess.kt`):

- The review service is built from a read-only `HistorySource` and a `PlanProposalSink`.
  It has no reference it could use to write an entry, and **no path to rewrite history**.
- Plans it creates are `PROPOSED`. They are not goals, do not appear as targets and do not
  affect progress until you press **Accept plan**. You can adjust the target days first,
  or reject the plan outright — a rejected proposal is kept, so the suggestion history
  stays honest.
- Plan dates come from the app, not the model: a proposal always covers the week after the
  reviewed period.
- Every review stores the exact snapshot that was sent, so a past review can be re-read
  against the data it actually saw.

Bring your own key (Claude, Gemini or OpenAI) in Settings. It is stored in the app's
private storage and is only used when you press the button.

## Local analysis, no key needed

Insights answers the app's core question locally: it splits your days by a habit and
reports the two averages, same-day and next-day — "Energy averages 2.0 lower the next day
you log Late-night eating (3 vs 3 days)". It refuses to show a split with fewer than three
days on either side, and it describes rather than claims causation.

## Architecture

```
Jetpack Compose UI  (Today / History / Insights / Plan / Trackers / Settings)
        |
ViewModels (StateFlow)
        |
Repositories  ---- Room (local source of truth, single writer for entries)
        |
        +-- Health Connect (read-only: steps, exercise, sleep, HR, calories, weight)
        +-- WorkManager (6-hourly catch-up sync, re-reads the last 7 days)
        +-- AI layer (snapshot in, review + proposed plan out; no DB access)
```

Data model: `Tracker`, `Entry`, `JournalEntry`, `Plan`, `PlanTarget`, `AiAnalysis`. What
happened (`Entry`) and what you intended (`Plan`/`PlanTarget`) are deliberately separate
tables. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the reasoning and for where
`Meal`, `Workout`, `ExerciseSet`, `BodyMeasurement` and friends would slot in later.

## Build

Requires JDK 17+ and the Android SDK (compileSdk 35, minSdk 28).

```bash
# point Gradle at your SDK
echo "sdk.dir=/path/to/android-sdk" > local.properties

./gradlew :app:assembleDebug        # build the APK
./gradlew :app:testDebugUnitTest    # run the unit tests
./gradlew :app:installDebug         # install on a connected device
```

Health Connect is part of the OS on Android 14+; on Android 13 and below install the
Health Connect app from the Play Store. Without it the app still works — every automatic
tracker just becomes a manual one.

## Status

V1 is the four screens above plus tracker configuration and settings. Deliberately not in
V1: per-gram macro tracking, meal photos, cloud sync and multi-device. The data model
leaves room for all of them.
