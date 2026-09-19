// Reading, validating and bundling the knowledge base.
//
// Deliberately dependency-free so the CLI works before `npm install`, and so the
// MCP server and the CLI can never disagree about what a valid document is.

import { readFileSync, writeFileSync, readdirSync, mkdirSync, existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
export const ROOT = resolve(here, '..', '..')
export const KNOWLEDGE = join(ROOT, 'knowledge')
export const BUNDLE = join(KNOWLEDGE, 'knowledge-base.json')

const KINDS = {
  exercises: { dir: 'exercises', required: ['id', 'name', 'primaryMuscles', 'cues'] },
  foods: { dir: 'foods', required: ['id', 'name', 'portion', 'per'] },
  plans: { dir: 'plans', required: ['id', 'name', 'days'] },
}

const CONFIDENCE = ['strong', 'moderate', 'limited', 'estimate']
const JOINTS = ['head', 'neck', 'shoulder', 'elbow', 'wrist', 'hip', 'knee', 'ankle', 'foot']

export function kinds() {
  return Object.keys(KINDS)
}

function dirFor(kind) {
  const spec = KINDS[kind]
  if (!spec) throw new Error(`Unknown kind "${kind}". Expected one of: ${kinds().join(', ')}`)
  return join(KNOWLEDGE, spec.dir)
}

export function listDocs(kind) {
  const dir = dirFor(kind)
  if (!existsSync(dir)) return []
  return readdirSync(dir)
    .filter((f) => f.endsWith('.json'))
    .map((f) => JSON.parse(readFileSync(join(dir, f), 'utf8')))
    .sort((a, b) => a.id.localeCompare(b.id))
}

export function readDoc(kind, id) {
  const path = join(dirFor(kind), `${id}.json`)
  if (!existsSync(path)) return null
  return JSON.parse(readFileSync(path, 'utf8'))
}

/** Returns a list of problems; empty means the document is publishable. */
export function validateDoc(kind, doc) {
  const problems = []
  const spec = KINDS[kind]
  if (!spec) return [`unknown kind "${kind}"`]
  if (!doc || typeof doc !== 'object') return ['document must be a JSON object']

  for (const field of spec.required) {
    const value = doc[field]
    const empty = value === undefined || value === null || value === '' ||
      (Array.isArray(value) && value.length === 0)
    if (empty) problems.push(`missing required field "${field}"`)
  }

  if (doc.id && !/^[a-z0-9_]+$/.test(doc.id)) {
    problems.push(`id "${doc.id}" must be lower case letters, digits and underscores`)
  }

  if (kind === 'exercises') {
    for (const entry of doc.evidence ?? []) {
      if (!entry.claim) problems.push('an evidence entry has no claim')
      if (entry.confidence && !CONFIDENCE.includes(entry.confidence)) {
        problems.push(`evidence confidence "${entry.confidence}" must be one of ${CONFIDENCE.join(', ')}`)
      }
    }
    for (const entry of doc.mistakes ?? []) {
      if (!entry.mistake || !entry.fix) problems.push('every mistake needs both "mistake" and "fix"')
    }
    problems.push(...validateDemo(doc.demo))
  }

  if (kind === 'foods') {
    const per = doc.per ?? {}
    for (const macro of ['kcal', 'protein', 'carbs', 'fat']) {
      if (typeof per[macro] !== 'number' || per[macro] < 0) {
        problems.push(`per.${macro} must be a number of ${macro === 'kcal' ? 'kilocalories' : 'grams'}`)
      }
    }
    if (!doc.portion?.label) problems.push('portion.label is what you actually eat, e.g. "1 katori"')
    // A rough sanity check: macros should roughly explain the calories.
    if (typeof per.kcal === 'number' && typeof per.protein === 'number') {
      const fromMacros = per.protein * 4 + (per.carbs ?? 0) * 4 + (per.fat ?? 0) * 9
      if (fromMacros > 0 && Math.abs(fromMacros - per.kcal) > Math.max(60, per.kcal * 0.35)) {
        problems.push(
          `per.kcal ${per.kcal} does not match the macros (${Math.round(fromMacros)} kcal from ` +
          `${per.protein}p/${per.carbs ?? 0}c/${per.fat ?? 0}f)`,
        )
      }
    }
  }

  if (kind === 'plans') {
    for (const day of doc.days ?? []) {
      if (!day.day) problems.push('every day needs a "day" name')
      for (const item of day.items ?? []) {
        if (!item.exercise) problems.push(`a ${day.day} item has no exercise id`)
      }
    }
  }

  return problems
}

function validateDemo(demo) {
  if (!demo) return []
  const problems = []
  if (!Array.isArray(demo.frames) || demo.frames.length < 2) {
    problems.push('demo needs at least two frames')
    return problems
  }
  if (demo.view && !['side', 'front'].includes(demo.view)) {
    problems.push(`demo view "${demo.view}" must be "side" or "front"`)
  }
  demo.frames.forEach((frame, index) => {
    if (typeof frame.at !== 'number' || frame.at < 0 || frame.at > 1) {
      problems.push(`frame ${index}: "at" must be between 0 and 1`)
    }
    for (const [joint, point] of Object.entries(frame.joints ?? {})) {
      if (!JOINTS.includes(joint)) problems.push(`frame ${index}: unknown joint "${joint}"`)
      if (!Array.isArray(point) || point.length !== 2 || point.some((n) => typeof n !== 'number')) {
        problems.push(`frame ${index}: joint "${joint}" must be [x, y]`)
      }
    }
  })
  if (demo.frames[0].at !== 0) problems.push('the first frame must be at 0')
  const first = Object.keys(demo.frames[0].joints ?? {})
  for (const joint of JOINTS) {
    if (!first.includes(joint)) problems.push(`the first frame must place every joint - missing "${joint}"`)
  }
  return problems
}

/** Writes a document after validating it. Throws with every problem listed. */
export function writeDoc(kind, doc) {
  const problems = validateDoc(kind, doc)
  if (problems.length) {
    throw new Error(`${kind}/${doc?.id ?? 'document'} is not publishable:\n- ${problems.join('\n- ')}`)
  }
  const dir = dirFor(kind)
  mkdirSync(dir, { recursive: true })
  const path = join(dir, `${doc.id}.json`)
  const existed = existsSync(path)
  writeFileSync(path, `${JSON.stringify(doc, null, 2)}\n`)
  return { path, created: !existed }
}

export function deleteDoc(kind, id) {
  const path = join(dirFor(kind), `${id}.json`)
  if (!existsSync(path)) return false
  // Kept deliberately simple: the repo is the undo history.
  writeFileSync(path, '')
  return true
}

export function validateAll() {
  const problems = []
  for (const kind of kinds()) {
    for (const doc of listDocs(kind)) {
      for (const problem of validateDoc(kind, doc)) {
        problems.push(`${kind}/${doc.id ?? '?'}: ${problem}`)
      }
    }
  }
  // Plans may only point at exercises that exist.
  const exerciseIds = new Set(listDocs('exercises').map((d) => d.id))
  for (const plan of listDocs('plans')) {
    for (const day of plan.days ?? []) {
      for (const item of day.items ?? []) {
        if (!exerciseIds.has(item.exercise)) {
          problems.push(`plans/${plan.id}: ${day.day} refers to unknown exercise "${item.exercise}"`)
        }
      }
    }
  }
  return problems
}

export function buildBundle() {
  const problems = validateAll()
  if (problems.length) {
    throw new Error(`The knowledge base has problems:\n- ${problems.join('\n- ')}`)
  }
  const bundle = {
    version: 1,
    builtAt: new Date().toISOString(),
    exercises: listDocs('exercises'),
    foods: listDocs('foods'),
    plans: listDocs('plans'),
  }
  writeFileSync(BUNDLE, `${JSON.stringify(bundle, null, 2)}\n`)
  return {
    path: BUNDLE,
    counts: {
      exercises: bundle.exercises.length,
      foods: bundle.foods.length,
      plans: bundle.plans.length,
    },
  }
}
