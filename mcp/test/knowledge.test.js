import { test } from 'node:test'
import assert from 'node:assert/strict'
import { listDocs, validateAll, validateDoc } from '../src/knowledge.js'

const goodFood = {
  id: 'idli', name: 'Idli', meals: ['breakfast'],
  portion: { label: '2 idli', grams: 100 },
  per: { kcal: 120, protein: 4, carbs: 25, fat: 0.5, fibre: 1 },
  confidence: 'estimate',
}

test('the shipped knowledge base validates', () => {
  assert.deepEqual(validateAll(), [])
})

test('every exercise carries coaching and evidence', () => {
  const exercises = listDocs('exercises')
  assert.ok(exercises.length >= 11)
  for (const exercise of exercises) {
    assert.ok(exercise.cues.length > 0, `${exercise.id} has no cues`)
    assert.ok(exercise.mistakes.length > 0, `${exercise.id} has no mistakes`)
    assert.ok(exercise.evidence.length > 0, `${exercise.id} has no evidence notes`)
    for (const note of exercise.evidence) {
      assert.ok(['strong', 'moderate', 'limited'].includes(note.confidence),
        `${exercise.id} evidence needs an honest confidence level`)
    }
  }
})

test('every demo starts from a complete pose', () => {
  for (const exercise of listDocs('exercises')) {
    if (!exercise.demo) continue
    assert.deepEqual(validateDoc('exercises', exercise), [], `${exercise.id} demo is broken`)
    assert.equal(exercise.demo.frames[0].at, 0)
  }
})

test('a food whose calories contradict its macros is rejected', () => {
  const problems = validateDoc('foods', { ...goodFood, per: { ...goodFood.per, kcal: 600 } })
  assert.ok(problems.some((p) => p.includes('does not match the macros')), problems.join('; '))
})

test('a good food passes', () => {
  assert.deepEqual(validateDoc('foods', goodFood), [])
})

test('missing required fields are named', () => {
  const problems = validateDoc('exercises', { id: 'x', name: 'X' })
  assert.ok(problems.some((p) => p.includes('primaryMuscles')))
  assert.ok(problems.some((p) => p.includes('cues')))
})

test('ids must be machine-safe', () => {
  const problems = validateDoc('foods', { ...goodFood, id: 'Idli Sambar' })
  assert.ok(problems.some((p) => p.includes('lower case')))
})

test('a demo with an unknown joint is rejected', () => {
  const doc = listDocs('exercises').find((e) => e.demo)
  const broken = structuredClone(doc)
  broken.demo.frames[1].joints.tail = [0.1, 0.1]
  assert.ok(validateDoc('exercises', broken).some((p) => p.includes('unknown joint')))
})

test('a plan may only point at exercises that exist', () => {
  const problems = validateDoc('plans', {
    id: 'test_week', name: 'Test', days: [{ day: 'MONDAY', items: [{ sets: 3 }] }],
  })
  assert.ok(problems.some((p) => p.includes('no exercise id')))
})
