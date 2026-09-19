#!/usr/bin/env node
// The same operations as the MCP server, for a terminal or a CI step.
//
//   node src/cli.js validate
//   node src/cli.js build
//   node src/cli.js list exercises
//   cat doc.json | node src/cli.js publish foods

import { buildBundle, listDocs, validateAll, writeDoc } from './knowledge.js'

const [command, ...rest] = process.argv.slice(2)

async function readStdin() {
  const chunks = []
  for await (const chunk of process.stdin) chunks.push(chunk)
  return Buffer.concat(chunks).toString('utf8')
}

switch (command) {
  case 'validate': {
    const problems = validateAll()
    if (problems.length) {
      console.error(`${problems.length} problem(s):`)
      for (const problem of problems) console.error(`  - ${problem}`)
      process.exit(1)
    }
    console.log('Knowledge base is valid.')
    break
  }
  case 'build': {
    try {
      const { path, counts } = buildBundle()
      console.log(`Wrote ${path}`)
      console.log(`  ${counts.exercises} exercises, ${counts.foods} foods, ${counts.plans} plans`)
    } catch (error) {
      console.error(error.message)
      process.exit(1)
    }
    break
  }
  case 'list': {
    const kind = rest[0] ?? 'exercises'
    for (const doc of listDocs(kind)) console.log(`${doc.id}\t${doc.name}`)
    break
  }
  case 'publish': {
    const kind = rest[0]
    const doc = JSON.parse(await readStdin())
    try {
      const { path, created } = writeDoc(kind, doc)
      console.log(`${created ? 'Created' : 'Updated'} ${path}`)
    } catch (error) {
      console.error(error.message)
      process.exit(1)
    }
    break
  }
  default:
    console.log(`Usage:
  node src/cli.js validate            check every document
  node src/cli.js build               validate and write knowledge-base.json
  node src/cli.js list <kind>         list exercises, foods or plans
  node src/cli.js publish <kind>      read one document from stdin and write it`)
    process.exit(command ? 1 : 0)
}
