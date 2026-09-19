# My Fitness

A local-first personal health experiment journal for Android. Not a fitness tracker and
not a calorie counter: log the day in under a minute, then use weeks of your own data to
find out what actually works for you.

The design constraint that shapes everything else: **logging takes under 30 seconds a day,
analysis can be as sophisticated as you like later.**

## What it does

The home screen is the day in the order it happens, not a set of abstract categories:

| Section | What it holds |
| --- | --- |
| **Morning** | Woke up (aiming at 05:00), gym, treadmill, and the exercise checklist |
| **School run** | Dropped Vihaan at school |
| **Breakfast** | Chicken / eggs / dosa / poha, coffee hot or cold, before or after the drop |
| **Office** | Reached, left, ate at the office, glasses of water (aiming at 8), lunch |
| **Evening** | Played with Vihaan, dinner, dinner food, late meetings, Instagram (after the drop / after office / late night) |
| **Night** | Slept at (aiming at 23:00), then energy, mood and stress for the day |
| **Body** | Weight, steps, sleep duration - mostly filled in by Health Connect |

| Screen | What it is for |
| --- | --- |
| **Today** | Every section on one scroll, with the gym and the kitchen summarised at the top. Habits are one tap, times are one tap on a suggested chip, the gym list is a tap per exercise. |
| **Gym** | Today's session from the training plan. Each exercise expands into a set logger that already knows what you lifted last time. |
| **Food** | Calories and protein for the foods you actually eat, counted in katoris and plates. |
| **History** | Calendar heatmap of the last 18 weeks with a streak, plus any day broken down by section. |
| **Insights** | 7 / 30 / 90-day trends per section, local "when I do X, what happens to Y?" splits, and **Review my week**. |
| **Plan** | This week's approved targets with progress, and any AI-proposed plan waiting for approval. |

## The coach

Tap **How to** on any exercise and you get the knowledge base's page for it: an animated
demo, the setup and execution, coaching cues, the mistakes that actually happen and their
fixes, what the training literature supports (with an honest confidence level on each
claim, and no invented citations), and the ladder of progressions and regressions.

The demos are drawn, not filmed - a stick figure moving between keyframes stored in the
exercise document. That is the honest trade: it shows the movement pattern, the joint
angles and the tempo rather than what a real body looks like doing it, and in exchange it
is a few hundred bytes that an assistant can publish through the MCP server.

Nothing on that screen is medical advice, and the app says so.

## Logging a session

The Gym tab reads the training week from the knowledge base and shows today's session.
Each exercise expands into a logger pre-filled with **what you did last time** - the one
number that makes progression visible. Accept it, or nudge the weight and reps, and tap
Log set. Sets appear as chips; tap one to remove it.

## Nutrition, only for what you eat

Sixteen foods, priced the way you actually eat them: one katori of dal, two eggs, a plate
of poha. Add a food to a meal, adjust portions in halves, and the day totals against the
protein and calorie targets in your plan.

Every number is a household estimate rather than a lab value, and the app labels it as
one. Tap a food to correct it - your numbers outrank the published ones and survive the
next knowledge sync.

## The knowledge base, and publishing to it from Claude or ChatGPT

Everything the coach knows - exercises, foods, the training week - lives in
[`knowledge/`](knowledge/README.md) as plain JSON, and ships inside the app so a fresh
install works offline.

[`mcp/`](mcp/README.md) is an MCP server that lets Claude or ChatGPT publish into it:

> Add an incline dumbbell curl to my knowledge base - cues, the usual mistakes, what the
> evidence says about stretch-position training, and a demo animation. Then build the bundle.

The schema is enforced on the way in, so a food whose calories contradict its macros, a
demo missing a joint, or a plan pointing at an exercise that does not exist is refused
rather than shipped to the phone. Point **Settings ▸ Knowledge base** at the published
bundle - a raw file in this repository, or a wiki page holding the JSON - and the app
pulls it in.

Two of the times fill themselves in: if anything writes sleep to Health Connect, the
start and end of the night become "Slept at" and "Woke up". A value you type always
wins - a sync never overwrites a manual entry.

## Trackers, not fields

Nothing loggable is hardcoded. A tracker is a row with a type:

| Type | Example |
| --- | --- |
| `BOOLEAN` | Gym: yes/no |
| `NUMBER` | Water at office: 8 glasses |
| `DURATION` | Gym minutes: 45 |
| `RATING` | Energy: 4/5 |
| `TEXT` | A note |
| `SELECT` | Coffee: None / Hot / Cold |
| `TIME` | Woke up: 05:10 |
| `MULTI_SELECT` | Exercises: shoulders, biceps, abs... |

The tick-lists - the food at each meal, the exercises - are editable in the app:
**Settings > Food and checklists**. Adding an item there is all it takes for it to show
up on Today, and removing one leaves the days you already logged it untouched.

Each tracker also carries the part of the day it belongs to, a direction (`more is
better`, `less is better`, `just observe`), an aggregation (`days done`, `total`,
`average`, `latest`) and an optional standing target - which is how the app knows that
waking at 04:50 beats a 05:00 target while a 00:20 bedtime *misses* an 23:00 one.
Add, edit, reorder or switch off trackers from the Trackers screen; the database does
not change.

## The AI loop

The model never touches the database. Pressing **Review my week** builds a compact JSON
snapshot of aggregates plus your own journal text, and that snapshot is the only thing
sent:

```json
{
  "period": "2026-09-07/2026-09-13",
  "days": 7,
  "morning": {
    "woke_up": "05:24",
    "gym": "4/7 days",
    "exercises": "4/7 days",
    "exercises_items_per_day": 3.2,
    "exercises_breakdown": { "abs": 4, "shoulders": 2, "lats": 1 }
  },
  "school_run": { "dropped_vihaan_at_school": "5/7 days" },
  "breakfast": { "breakfast": "6/7 days", "coffee": "5/7 days" },
  "office": { "reached_office": "09:40", "water_at_office": 6.4, "ate_at_office": "3/7 days" },
  "evening": { "played_with_vihaan": "5/7 days", "dinner": "7/7 days", "late_meetings": "2/7 days" },
  "night": { "slept_at": "23:35", "energy": 3.4, "stress": 3.1 },
  "journal": ["Mon 07 Sep: Good session, Vihaan slept early"],
  "plan": { "targets": [{ "tracker": "Gym", "target_frequency": 5, "actual_days": 4, "met": false }] },
  "previous_period": { "morning": { "gym": "3/7 days" } }
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

For a release build, put a `keystore.properties` in the repository root:

```properties
storeFile=/absolute/path/to/my-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

then `./gradlew :app:assembleRelease`. R8 shrinking takes the APK from ~63 MB to
~3.5 MB. Both the properties file and `*.jks` are gitignored - a signing key does
not belong in the repository. Without the file the release build is unsigned and
only `assembleDebug` is usable.

Keep the keystore: Android only allows an update to install over an existing app
if it is signed with the same key. Losing it means uninstalling first, and since
all data is local, uninstalling means losing your history.

Health Connect is part of the OS on Android 14+; on Android 13 and below install the
Health Connect app from the Play Store. Without it the app still works — every automatic
tracker just becomes a manual one.

## Status

V1 is the four screens above plus tracker configuration and settings. Deliberately not in
V1: per-gram macro tracking, meal photos, cloud sync and multi-device. The data model
leaves room for all of them.
