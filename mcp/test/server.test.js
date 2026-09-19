import { test } from 'node:test'
import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const mcpRoot = join(dirname(fileURLToPath(import.meta.url)), '..')

/**
 * Talks to the server the way a client does - raw JSON-RPC over stdio - so "the MCP
 * server works" is verified rather than assumed.
 */
class TestClient {
  constructor() {
    this.process = spawn('node', ['src/server.js'], { cwd: mcpRoot })
    this.replies = new Map()
    this.buffer = ''
    this.nextId = 1
    this.process.stdout.on('data', (chunk) => this.#consume(chunk.toString()))
  }

  #consume(chunk) {
    this.buffer += chunk
    let index
    while ((index = this.buffer.indexOf('\n')) >= 0) {
      const line = this.buffer.slice(0, index).trim()
      this.buffer = this.buffer.slice(index + 1)
      if (!line) continue
      const message = JSON.parse(line)
      if (message.id != null) this.replies.set(message.id, message)
    }
  }

  #send(message) {
    this.process.stdin.write(`${JSON.stringify(message)}\n`)
  }

  async request(method, params) {
    const id = this.nextId++
    this.#send({ jsonrpc: '2.0', id, method, params })
    for (let attempt = 0; attempt < 200; attempt++) {
      if (this.replies.has(id)) return this.replies.get(id)
      await new Promise((resolve) => setTimeout(resolve, 25))
    }
    throw new Error(`no reply to ${method}`)
  }

  async start() {
    const init = await this.request('initialize', {
      protocolVersion: '2024-11-05',
      capabilities: {},
      clientInfo: { name: 'test', version: '1' },
    })
    this.#send({ jsonrpc: '2.0', method: 'notifications/initialized' })
    return init
  }

  call(name, args = {}) {
    return this.request('tools/call', { name, arguments: args })
  }

  stop() {
    this.process.kill()
  }
}

test('the server introduces itself and lists its tools', async (t) => {
  const client = new TestClient()
  t.after(() => client.stop())

  const init = await client.start()
  assert.equal(init.result.serverInfo.name, 'my-fitness-knowledge')

  const { result } = await client.request('tools/list')
  const names = result.tools.map((tool) => tool.name)
  for (const expected of ['list_knowledge', 'publish_exercise', 'publish_food', 'build_bundle']) {
    assert.ok(names.includes(expected), `missing tool ${expected}`)
  }
})

test('an assistant can read what is already published', async (t) => {
  const client = new TestClient()
  t.after(() => client.stop())
  await client.start()

  const listed = await client.call('list_knowledge', { kind: 'exercises' })
  const exercises = JSON.parse(listed.result.content[0].text)
  assert.ok(exercises.length >= 11)

  const read = await client.call('read_knowledge', { kind: 'exercises', id: 'push_ups' })
  const doc = JSON.parse(read.result.content[0].text)
  assert.equal(doc.name, 'Push-ups')
  assert.ok(doc.demo.frames.length >= 2)
})

test('a document that fails validation is refused, with the reason', async (t) => {
  const client = new TestClient()
  t.after(() => client.stop())
  await client.start()

  const response = await client.call('publish_food', {
    document: {
      id: 'wrong_numbers', name: 'Wrong', portion: { label: '1' },
      per: { kcal: 900, protein: 1, carbs: 1, fat: 1 },
    },
  })

  assert.equal(response.result.isError, true)
  assert.match(response.result.content[0].text, /does not match the macros/)
})

test('reading a document that does not exist is an error, not an empty success', async (t) => {
  const client = new TestClient()
  t.after(() => client.stop())
  await client.start()

  const response = await client.call('read_knowledge', { kind: 'exercises', id: 'nope' })

  assert.equal(response.result.isError, true)
})

test('checking the whole base reports it clean', async (t) => {
  const client = new TestClient()
  t.after(() => client.stop())
  await client.start()

  const response = await client.call('check_knowledge', {})

  assert.match(response.result.content[0].text, /No problems found/)
})
