# Knowledge base

Everything the app knows that is *not* your own logged data: how to do an exercise,
what a portion of poha contains, what this week's training looks like.

It lives here as plain JSON so it can be written by hand, by Claude, or by ChatGPT -
see [`../mcp/README.md`](../mcp/README.md) for the MCP server that lets an assistant
publish into it.

```
knowledge/
  exercises/*.json   one file per exercise: coaching, evidence, demo keyframes
  foods/*.json       one file per food: portion and macros
  plans/*.json       a training week
  knowledge-base.json  built bundle - what the app actually reads
```

## Building the bundle

```bash
cd mcp && npm run build          # validates every doc, writes knowledge/knowledge-base.json
```

The app ships the bundle as an asset so it works offline on first run, and can also
pull a newer one from a URL (Settings > Knowledge base).

## Exercise document

```json
{
  "id": "push_ups",
  "name": "Push-ups",
  "aliases": ["press-up"],
  "pattern": "horizontal_push",
  "primaryMuscles": ["Chest", "Triceps", "Front delts"],
  "secondaryMuscles": ["Core"],
  "equipment": "bodyweight",
  "difficulty": "beginner",
  "setup": ["Hands under shoulders, slightly wider."],
  "execution": ["Lower until the chest is a fist off the floor."],
  "cues": ["Squeeze the glutes so the hips do not sag."],
  "mistakes": [{ "mistake": "Hips sagging", "fix": "Brace the ribs down." }],
  "breathing": "Inhale down, exhale up.",
  "evidence": [
    { "claim": "...", "detail": "...", "confidence": "strong" }
  ],
  "progressions": ["Feet elevated"],
  "regressions": ["Hands on a bench"],
  "repRange": { "min": 6, "max": 20 },
  "restSeconds": 120,
  "tempo": "2-0-1",
  "demo": {
    "durationMillis": 2600,
    "loop": "pingpong",
    "frames": [ { "at": 0.0, "joints": { "hip": [0.5, 0.52] } } ]
  }
}
```

`evidence[].confidence` is one of `strong`, `moderate`, `limited`. Entries describe
what training literature broadly agrees on; they are general guidance, not medical
advice, and deliberately carry no invented citations. Add your own references with
`"source"` when you have a paper you trust.

## Demo keyframes

A demo is a stick figure animated between keyframes. Joints are normalised `[x, y]`
in a unit box, `y` down. Named joints:

`head, neck, shoulder, elbow, wrist, hip, knee, ankle, foot`

Each frame gives the joints that move; anything omitted holds its previous value.
`loop` is `pingpong` (down-and-up movements) or `restart`.

## Food document

```json
{
  "id": "poha",
  "name": "Poha",
  "meals": ["breakfast"],
  "portion": { "label": "1 plate", "grams": 200 },
  "per": { "kcal": 270, "protein": 6, "carbs": 48, "fat": 6, "fibre": 3 },
  "note": "Home-style, cooked with a little oil and peanuts.",
  "confidence": "estimate"
}
```

Every number is a rounded household estimate for how *you* eat it, not a lab value.
Edit any of them in the app (Nutrition > tap a food) - your edit wins over the bundle.
