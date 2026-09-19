# Architecture

## The one-sentence version

Room is the source of truth on the device, Health Connect fills in what the phone already
knows, and the AI layer is a pure function from a JSON snapshot to a *proposal* that you
approve.

## Module layout

Single Gradle module (`:app`). One user, one process; a multi-module split would cost more
than it returns at this size. Packages carry the boundaries instead:

```
com.ug911.myfitness
  data/model        domain types; no Android imports
  data/local        Room entities, DAOs, converters, default tracker set
  data/repository   the only writers; LogRepository is the single writer for entries
  analysis          pure aggregation and pattern code (PeriodStats, PatternAnalysis, PlanProgress)
  ai                snapshot builder, prompts, provider clients, review service
  health            Health Connect reads, sync coordinator, WorkManager worker
  settings          DataStore-backed preferences
  ui/*              Compose screens + ViewModels, one package per screen
  di                AppContainer: the whole dependency graph, by hand
```

`analysis` and most of `ai` are plain Kotlin with no Android dependencies, which is why the
unit tests run on the JVM with no emulator and no Robolectric.

## Data model

```
Tracker        id, name, category, type, unit, active, sortOrder,
               options, ratingMax, healthMetric, direction, aggregation
Entry          id, trackerId, date, value (TEXT), notes, source, updatedAt
JournalEntry   date (PK), text, updatedAt
Plan           id, startDate, endDate, generatedBy, status, note, analysisId, createdAt
PlanTarget     id, planId, trackerId, targetValue, targetFrequency, note
AiAnalysis     id, periodStart, periodEnd, inputSnapshot, review, insights,
               planRationale, provider, model, createdAt
```

Four decisions worth stating:

**1. Trackers are data, so `Entry.value` is text.** Every tracker type serialises through
`TrackerValue.encode()/decode()`, so adding `SELECT` or a new type is a row, not a
migration. The cost is that a value has to be decoded with knowledge of its tracker's
type; repositories do that in one place, and `TrackerValueTest` pins the format down,
including what happens to a corrupt or blank stored value (it decodes to nothing rather
than throwing).

**2. `aggregation` and `direction` live on the tracker.** Without them, every new habit
would need code somewhere deciding whether to sum it, average it, or count days, and
whether "more" is good. With them, the snapshot builder, the plan progress bars and the
pattern ranking all work on trackers they have never heard of.

**3. Actual and planned are separate tables.** `Entry` is the historical record;
`Plan`/`PlanTarget` is intent. Nothing in the app writes an entry from a plan, and an
approved plan does not backfill anything. This is what makes the AI safe to point at the
data: the worst it can do is propose a bad week.

**4. `AiAnalysis.inputSnapshot` stores the exact JSON that was sent.** A review you read
three months later can be judged against what the model actually saw, and a prompt or
builder change does not silently rewrite the past.

`Entry` has a unique index on `(trackerId, date)`: one value per tracker per day. The
finer-grained tables sketched for later — `Meal`, `FoodItem`, `Workout`, `ExerciseSet`,
`BodyMeasurement`, `LabResult`, `Medication`, `Supplement` — hang off their own tables with
their own timestamps and roll *up* into daily entries. That keeps Today, History and the
snapshot builder working unchanged when they arrive.

## The AI boundary

The interesting constraint is not what the AI may read, it is what it may write. Two
narrow interfaces (`ai/PeriodData.kt`) are the whole surface:

```kotlin
interface HistorySource   { suspend fun load(start: LocalDate, end: LocalDate): PeriodData }
interface PlanProposalSink { suspend fun propose(plan: Plan, targets: List<PlanTarget>): Long }
```

`WeeklyReviewService` is constructed from those two plus `AnalysisRepository`. It never
sees `LogRepository`, so "the AI must not rewrite your historical records" is a compile
error rather than a code review comment. `ProposalOnlyPlanSink` forces
`PlanStatus.PROPOSED` regardless of what it is handed, and `PlanDao.approve` — reachable
only from the Plan screen's Accept button — is the single place a proposal becomes
`ACTIVE`.

Flow of one review:

```
ReviewPeriod (Mon-Sun)
  -> HistorySource.load(period)         entries + journal + active plan
  -> HistorySource.load(previous week)  for "up from three last week"
  -> AiContextBuilder.build()           JSON aggregates; free text kept separate
  -> AiClient.complete(system, user)    Anthropic / Gemini / OpenAI
  -> AiReviewParser.parse()             tolerant envelope, strict shape
  -> AnalysisRepository.save()          snapshot + review + insights
  -> PlanProposalSink.propose()         PROPOSED plan, app-chosen dates
  -> (user) Accept  ->  PlanDao.approve()  ->  ACTIVE, previous plan ARCHIVED
```

Failure at any step returns `ReviewOutcome.Failed` carrying the snapshot, and writes
nothing — no half-saved analysis, no orphan proposal. `WeeklyReviewServiceTest` covers
the transport failure, the unparseable reply and the empty-week cases.

Target names from the model are matched against tracker names by slug, and anything
unmatched is reported back to the UI (`unmatchedTargets`) rather than dropped, so a
proposal never looks like it was accepted in full when part of it was ignored.

## Health Connect

Read-only, via `HealthConnectManager`. `HealthMetric` on a tracker is what links a row to
a record type, so "fill this from Health Connect" is a per-tracker setting rather than a
fixed list of synced fields.

- Aggregates are read per calendar day. Sleep is attributed to the day you **woke up**
  (window: previous 18:00 to noon), which is how you would answer "how did I sleep?" when
  logging in the morning.
- `LogRepository.setAutomaticValue` routes through `EntryDao.upsertKeepingManual`: a
  `MANUAL` entry is never overwritten by a sync.
- `HealthSyncWorker` runs every 6 hours and re-reads the last 7 days, so a missed window
  costs nothing and no persistent background service is needed.
- No Health Connect on the device? `availability()` reports it and every automatic tracker
  behaves as a manual one.

## UI

Compose + Navigation, four bottom-bar destinations (Today, History, Insights, Plan) with
Trackers and Settings in the app bar. ViewModels expose a single immutable state object
via `StateFlow` and are built by `viewModelFactory` initializers from `AppContainer` — no
DI framework, no generated code.

Today is deliberately dumb: no dialogs, no navigation, one tap per boolean, inline fields
for numbers. Values write on change; there is no save button except for the day's note.

## Testing

JVM unit tests only, aimed at the logic that would be expensive to get wrong:

- `PeriodStatsTest` — aggregation per type, "no data" instead of zero, weekly series.
- `PatternAnalysisTest` — same-day vs next-day splits, refusal below the data threshold.
- `PlanProgressTest` — frequency and value targets, `Direction.DOWN`, deleted trackers.
- `AiContextBuilderTest` — snapshot shape, free text kept out of aggregates, plan vs
  actual, previous-period comparison, empty week.
- `AiReviewParserTest` — fenced and prose-wrapped replies, braces inside strings, unknown
  trackers, app-chosen plan dates.
- `WeeklyReviewServiceTest` — the whole pipeline with a fake client: proposal not
  activation, nothing written on failure, no model call for an empty week.
- `TrackerValueTest` — storage round trip and corrupt-value handling.

Room DAO behaviour (`upsertKeepingManual`, `approve`) and Compose screens are not covered;
those want instrumented tests on a device and are the obvious next thing to add.

## The knowledge layer

Three things arrive from outside the phone and none of them are the person's own data:
exercise coaching, food macros, and the training week. They live in `knowledge/` as JSON,
are built into a single bundle, and reach the app two ways - shipped as an asset so a
fresh install works offline, and fetched from a URL when a newer one is published.

```
knowledge/*.json ──build──▶ knowledge-base.json ──▶ app/src/main/assets  (offline default)
       ▲                            │
       │ MCP                        └─▶ any URL ──▶ Settings ▸ Knowledge base ▸ Sync
   Claude / ChatGPT
```

Documents are stored in `knowledge_docs` **as the JSON they arrived as**, with only `kind`,
`id` and `name` pulled out for indexing. A new field in a published document therefore
needs no migration: the app either understands it or ignores it (`ignoreUnknownKeys`).
That is what makes an assistant publishing into the base safe to do without shipping an
app update.

Corrections live apart from the thing they correct. `food_overrides` holds your edits to
a food's numbers and is re-applied on read, so a rebuild of the bundle never silently
overwrites the calories you fixed by hand. The same rule as everywhere else in this app:
published data and personal data never share a row.

`extractKnowledgeJson` accepts a page as well as a file, because a wiki publishes JSON
wrapped in HTML. It is a pure function with its own tests rather than a regex buried in a
network call.

## Training and eating

`workout_sets` and `food_log` are historical records in the same sense as `entries`:
written by the person, never by the AI layer. A set carries its weight, reps and index
within the session; `WorkoutDao.addSet` allocates the next index inside a transaction so
two quick taps cannot produce two set threes.

The gym screen's one real idea is that the logger opens pre-filled with the last session's
numbers (`GymRow.suggestedWeight/suggestedReps`), falling back to the bottom of the
planned rep range on a first attempt. Progression is then a decision rather than a
memory exercise.

Nutrition is deliberately not a food database. Sixteen documents cover what this person
eats, priced in portions rather than grams, and `totalMacros` folds a day's log against
them. A food the bundle no longer carries is skipped rather than counted as zero.

## Motion

Animation is in `ui/common/Motion.kt` and `ui/common/ExerciseDemo.kt`, and follows one
rule: movement explains a change, it never decorates one. Numbers count rather than jump
so a change is noticed; progress settles with a spring because it is physical; cards
arrive in sequence so the eye gets an order to read them in. The exercise demo interpolates
between keyframes with a smoothstep inside each segment, and ping-pongs by default because
a lift is a there-and-back movement.

## Deliberately not built yet

Cloud sync (Cloud Run + Firestore), a general food database, meal photos, Whoop or other
non-Health-Connect sources, planned-exercise write-back to Health Connect, widgets,
notifications, and a rest timer between sets. The sketch in the original design keeps working: sync would sit behind the
repositories, and additional sources become more `HealthMetric`-style mappings feeding the
same daily entries.
