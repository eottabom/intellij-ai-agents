import { useEffect, useRef, useState } from 'react'
import type { MutableRefObject, RefObject } from 'react'
import { CliName, ProjectRef } from '../bridge'

type MentionTarget = CliName | 'all'

interface MentionState {
  start: number
  end: number
  query: string
}

type SlashTarget = CliName | 'all' | null

interface SlashState {
  start: number
  end: number
  query: string
  command?: 'plan'
  target: SlashTarget
}

interface SlashOption {
  value: string
  label: string
  meta: string
}

interface HashState {
  start: number
  end: number
  query: string
}

const COMMON_SLASH_COMMANDS: SlashOption[] = [
  { value: '/plan', label: '/plan', meta: 'plan mode on (+ prompt optional)' },
  { value: '/clearall', label: '/clearall', meta: 'clear all saved sessions' },
  { value: '/doctor', label: '/doctor', meta: 'CLI health check (sent to active agent)' },
  { value: '/session', label: '/session', meta: 'show active sessions' },
]

const MAX_HASH_OPTIONS = 20

function detectMention(text: string, caret: number): MentionState | null {
  const before = text.slice(0, caret)
  const match = before.match(/(^|\s)@([a-z]*)$/i)
  if (!match) return null
  const query = match[2] ?? ''
  const start = caret - query.length - 1
  return { start, end: caret, query }
}

function detectLeadingTarget(text: string): SlashTarget {
  const match = text.trimStart().match(/^@(claude|gemini|codex|all)\b/i)
  return (match?.[1]?.toLowerCase() as SlashTarget) ?? null
}

function detectHash(text: string, caret: number): HashState | null {
  const before = text.slice(0, caret)
  const match = before.match(/(^|\s)#([a-zA-Z0-9_./-]*)$/)
  if (!match) return null
  const query = match[2] ?? ''
  const start = caret - query.length - 1
  return { start, end: caret, query }
}

function detectSlash(text: string, caret: number): SlashState | null {
  const before = text.slice(0, caret)
  const target = detectLeadingTarget(before)

  const planArgMatch = before.match(/(^|\s)\/plan\s+([a-z]*)$/i)
  if (planArgMatch) {
    const query = planArgMatch[2] ?? ''
    const start = caret - query.length
    return { start, end: caret, query, command: 'plan', target }
  }

  const commandMatch = before.match(/(^|\s)\/([a-z]*)$/i)
  if (!commandMatch) return null
  const query = commandMatch[2] ?? ''
  const start = caret - query.length - 1
  return { start, end: caret, query, target }
}

export interface UseAutocompleteResult {
  mention: MentionState | null
  mentionIndex: number
  mentionOptions: MentionTarget[]
  mentionItemRefs: MutableRefObject<Array<HTMLButtonElement | null>>
  slash: SlashState | null
  slashIndex: number
  slashOptions: SlashOption[]
  slashItemRefs: MutableRefObject<Array<HTMLButtonElement | null>>
  hash: HashState | null
  hashIndex: number
  hashOptions: ProjectRef[]
  hashItemRefs: MutableRefObject<Array<HTMLButtonElement | null>>
  openMention: (nextText: string, caret: number | null) => void
  applyMention: (target: MentionTarget, text: string, setText: (v: string) => void, textareaRef: RefObject<HTMLTextAreaElement | null>) => void
  applySlash: (option: SlashOption, text: string, setText: (v: string) => void, textareaRef: RefObject<HTMLTextAreaElement | null>) => void
  applyHash: (ref: ProjectRef, text: string, setText: (v: string) => void, textareaRef: RefObject<HTMLTextAreaElement | null>) => void
  setMentionIndex: (v: number | ((prev: number) => number)) => void
  setSlashIndex: (v: number | ((prev: number) => number)) => void
  setHashIndex: (v: number | ((prev: number) => number)) => void
  setMention: (v: MentionState | null) => void
  setSlash: (v: SlashState | null) => void
  setHash: (v: HashState | null) => void
}

export function useAutocomplete(
  installedClis: CliName[],
  projectRefs: ProjectRef[],
): UseAutocompleteResult {
  const [mention, setMention] = useState<MentionState | null>(null)
  const [mentionIndex, setMentionIndex] = useState(0)
  const [slash, setSlash] = useState<SlashState | null>(null)
  const [slashIndex, setSlashIndex] = useState(0)
  const [hash, setHash] = useState<HashState | null>(null)
  const [hashIndex, setHashIndex] = useState(0)
  const mentionItemRefs = useRef<Array<HTMLButtonElement | null>>([])
  const slashItemRefs = useRef<Array<HTMLButtonElement | null>>([])
  const hashItemRefs = useRef<Array<HTMLButtonElement | null>>([])
  const mentionTargets: MentionTarget[] = [...installedClis, 'all']

  const mentionOptions = mention
    ? mentionTargets.filter((target) => target.startsWith(mention.query.toLowerCase()))
    : []

  const slashOptions: SlashOption[] = (() => {
    if (!slash) return []
    if (slash.command === 'plan') {
      return ['on', 'off']
        .filter((value) => value.startsWith(slash.query.toLowerCase()))
        .map((value) => ({
          value,
          label: value,
          meta: '/plan option',
        }))
    }
    return COMMON_SLASH_COMMANDS.filter((cmd) => cmd.value.slice(1).startsWith(slash.query.toLowerCase()))
  })()

  const hashOptions = hash
    ? projectRefs
        .filter((ref) => {
          const q = hash.query.toLowerCase()
          if (!q) return true
          return ref.symbol.toLowerCase().includes(q) || ref.path.toLowerCase().includes(q)
        })
        .slice(0, MAX_HASH_OPTIONS)
    : []

  useEffect(() => {
    const el = mentionItemRefs.current[mentionIndex]
    el?.scrollIntoView({ block: 'nearest' })
  }, [mentionIndex, mentionOptions.length])

  useEffect(() => {
    const el = slashItemRefs.current[slashIndex]
    el?.scrollIntoView({ block: 'nearest' })
  }, [slashIndex, slashOptions.length])

  useEffect(() => {
    const el = hashItemRefs.current[hashIndex]
    el?.scrollIntoView({ block: 'nearest' })
  }, [hashIndex, hashOptions.length])

  const openMention = (nextText: string, caret: number | null) => {
    if (caret == null) {
      setMention(null)
      setSlash(null)
      setHash(null)
      return
    }
    const nextHash = detectHash(nextText, caret)
    setHash(nextHash)
    setHashIndex(0)
    if (nextHash) {
      setMention(null)
      setSlash(null)
      return
    }
    const nextSlash = detectSlash(nextText, caret)
    setSlash(nextSlash)
    setSlashIndex(0)

    if (nextSlash) {
      setMention(null)
      return
    }
    const nextMention = detectMention(nextText, caret)
    setMention(nextMention)
    setMentionIndex(0)
  }

  const applyMention = (
    target: MentionTarget,
    text: string,
    setText: (v: string) => void,
    textareaRef: RefObject<HTMLTextAreaElement | null>,
  ) => {
    if (!mention) return
    const next = `${text.slice(0, mention.start)}@${target} ${text.slice(mention.end)}`
    setText(next)
    setMention(null)
    setMentionIndex(0)
    queueMicrotask(() => {
      const el = textareaRef.current
      if (!el) return
      const caret = mention.start + target.length + 2
      el.focus()
      el.setSelectionRange(caret, caret)
    })
  }

  const applySlash = (
    option: SlashOption,
    text: string,
    setText: (v: string) => void,
    textareaRef: RefObject<HTMLTextAreaElement | null>,
  ) => {
    if (!slash) return
    const replacement = option.value
    const shouldAddTrailingSpace = slash.command !== 'plan'
    const inserted = shouldAddTrailingSpace ? `${replacement} ` : replacement
    const next = `${text.slice(0, slash.start)}${inserted}${text.slice(slash.end)}`
    setText(next)
    setSlash(null)
    setSlashIndex(0)
    queueMicrotask(() => {
      const el = textareaRef.current
      if (!el) return
      const caret = slash.start + inserted.length
      el.focus()
      el.setSelectionRange(caret, caret)
    })
  }

  const applyHash = (
    ref: ProjectRef,
    text: string,
    setText: (v: string) => void,
    textareaRef: RefObject<HTMLTextAreaElement | null>,
  ) => {
    if (!hash) return
    const token = `#${ref.symbol} `
    const next = `${text.slice(0, hash.start)}${token}${text.slice(hash.end)}`
    setText(next)
    setHash(null)
    setHashIndex(0)
    queueMicrotask(() => {
      const el = textareaRef.current
      if (!el) return
      const caret = hash.start + token.length
      el.focus()
      el.setSelectionRange(caret, caret)
    })
  }

  return {
    mention,
    mentionIndex,
    mentionOptions,
    mentionItemRefs,
    slash,
    slashIndex,
    slashOptions,
    slashItemRefs,
    hash,
    hashIndex,
    hashOptions,
    hashItemRefs,
    openMention,
    applyMention,
    applySlash,
    applyHash,
    setMentionIndex,
    setSlashIndex,
    setHashIndex,
    setMention,
    setSlash,
    setHash,
  }
}

export type { MentionTarget, SlashOption, MentionState, SlashState, HashState }
