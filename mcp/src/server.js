#!/usr/bin/env node
// MCP server for the My Fitness knowledge base.
//
// Point Claude Desktop, Claude Code or any MCP-capable client at this and the
// assistant can research an exercise, write it up, and publish it straight into
// the app's knowledge base - with the schema enforced on the way in, so a bad
// document is rejected rather than shipped.

import { Server } from '@modelcontextprotocol/sdk/server/index.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js'
import {
  buildBundle, kinds, listDocs, readDoc, validateAll, validateDoc, writeDoc,
} from './knowledge.js'

const server = new Server(
  { name: 'my-fitness-knowledge', version: '0.1.0' },
  { capabilities: { tools: {} } },
)

const KIND_ENUM = kinds()

const TOOLS = [
  {
    name: 'list_knowledge',
    description:
      'List the documents already in the knowledge base. Use this before writing, so you ' +
      'extend what is there instead of duplicating it.',
    inputSchema: {
      type: 'object',
      properties: {
        kind: { type: 'string', enum: KIND_ENUM, description: 'exercises, foods or plans' },
      },
      required: ['kind'],
    },
  },
  {
    name: 'read_knowledge',
    description: 'Read one document in full, including its demo keyframes.',
    inputSchema: {
      type: 'object',
      properties: {
        kind: { type: 'string', enum: KIND_ENUM },
        id: { type: 'string', description: 'Document id, e.g. "push_ups"' },
      },
      required: ['kind', 'id'],
    },
  },
  {
    name: 'publish_exercise',
    description:
      'Create or replace an exercise document: coaching cues, common mistakes and their ' +
      'fixes, evidence notes with an honest confidence level, progressions, and optional ' +
      'stick-figure demo keyframes. Only claim what the training literature broadly agrees ' +
      'on, and never invent a citation - leave "source" out rather than guess it.',
    inputSchema: {
      type: 'object',
      properties: {
        document: {
          type: 'object',
          description: 'The full exercise document. See knowledge/README.md for the shape.',
        },
      },
      required: ['document'],
    },
  },
  {
    name: 'publish_food',
    description:
      'Create or replace a food document: the portion as it is actually eaten (a katori, a ' +
      'plate, two eggs) and the macros for that portion. Household estimates are expected - ' +
      'mark them "estimate" rather than implying lab precision.',
    inputSchema: {
      type: 'object',
      properties: { document: { type: 'object' } },
      required: ['document'],
    },
  },
  {
    name: 'publish_plan',
    description: 'Create or replace a training week: days, the exercises in each, sets and reps.',
    inputSchema: {
      type: 'object',
      properties: { document: { type: 'object' } },
      required: ['document'],
    },
  },
  {
    name: 'check_knowledge',
    description:
      'Validate a document without writing it, or validate the whole base when no document ' +
      'is given. Returns the list of problems.',
    inputSchema: {
      type: 'object',
      properties: {
        kind: { type: 'string', enum: KIND_ENUM },
        document: { type: 'object' },
      },
    },
  },
  {
    name: 'build_bundle',
    description:
      'Validate everything and write knowledge/knowledge-base.json - the single file the ' +
      'phone downloads. Run this after publishing.',
    inputSchema: { type: 'object', properties: {} },
  },
]

server.setRequestHandler(ListToolsRequestSchema, async () => ({ tools: TOOLS }))

function text(value) {
  return {
    content: [
      { type: 'text', text: typeof value === 'string' ? value : JSON.stringify(value, null, 2) },
    ],
  }
}

function failure(message) {
  return { content: [{ type: 'text', text: message }], isError: true }
}

function publish(kind, document) {
  try {
    const { path, created } = writeDoc(kind, document)
    return text(`${created ? 'Created' : 'Updated'} ${path}\n\nRun build_bundle when you are done publishing.`)
  } catch (error) {
    return failure(error.message)
  }
}

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args = {} } = request.params
  switch (name) {
    case 'list_knowledge': {
      const docs = listDocs(args.kind).map((d) => ({ id: d.id, name: d.name }))
      return text(docs.length ? docs : `Nothing published under ${args.kind} yet.`)
    }
    case 'read_knowledge': {
      const doc = readDoc(args.kind, args.id)
      return doc ? text(doc) : failure(`No ${args.kind} document with id "${args.id}".`)
    }
    case 'publish_exercise':
      return publish('exercises', args.document)
    case 'publish_food':
      return publish('foods', args.document)
    case 'publish_plan':
      return publish('plans', args.document)
    case 'check_knowledge': {
      const problems = args.document
        ? validateDoc(args.kind ?? 'exercises', args.document)
        : validateAll()
      return text(problems.length ? problems : 'No problems found.')
    }
    case 'build_bundle': {
      try {
        const { path, counts } = buildBundle()
        return text(
          `Wrote ${path}\n` +
          `${counts.exercises} exercises, ${counts.foods} foods, ${counts.plans} plans.\n\n` +
          'Commit and push it, and the phone will pick it up on its next knowledge sync.',
        )
      } catch (error) {
        return failure(error.message)
      }
    }
    default:
      return failure(`Unknown tool "${name}".`)
  }
})

const transport = new StdioServerTransport()
await server.connect(transport)
