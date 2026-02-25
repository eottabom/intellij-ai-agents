import { ChatMode, CliName, ProjectRef } from '../bridge'
import { KeyboardEvent, useEffect, useRef, useState } from 'react'
import AgentIcon, { getAgentMeta } from './AgentIcon'

interface Props {
  onSend: (prompt: string) => void
  onCancel: () => void
  onTogglePlanMode: () => void
  isLoading: boolean
  chatMode: ChatMode
  activeCli: CliName | null
  installedClis: CliName[]
  projectRefs: ProjectRef[]
}

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

export default function InputBar({ onSend, onCancel, onTogglePlanMode, isLoading, chatMode, activeCli, installedClis, projectRefs }: Props) {
  const [text, setText] = useState('')
  const [mention, setMention] = useState<MentionState | null>(null)
  const [mentionIndex, setMentionIndex] = useState(0)
  const [slash, setSlash] = useState<SlashState | null>(null)
  const [slashIndex, setSlashIndex] = useState(0)
  const [hash, setHash] = useState<HashState | null>(null)
  const [hashIndex, setHashIndex] = useState(0)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
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

  const applyMention = (target: MentionTarget) => {
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

  const applySlash = (option: SlashOption) => {
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

  const applyHash = (ref: ProjectRef) => {
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

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (hashOptions.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        setHashIndex((prev) => (prev + 1) % hashOptions.length)
        return
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault()
        setHashIndex((prev) => (prev - 1 + hashOptions.length) % hashOptions.length)
        return
      }
      if (e.key === 'Tab' || e.key === 'Enter') {
        e.preventDefault()
        applyHash(hashOptions[hashIndex] ?? hashOptions[0])
        return
      }
      if (e.key === 'Escape') {
        e.preventDefault()
        setHash(null)
        return
      }
    }

    if (slashOptions.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        setSlashIndex((prev) => (prev + 1) % slashOptions.length)
        return
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault()
        setSlashIndex((prev) => (prev - 1 + slashOptions.length) % slashOptions.length)
        return
      }
      if (e.key === 'Tab' || e.key === 'Enter') {
        e.preventDefault()
        applySlash(slashOptions[slashIndex] ?? slashOptions[0])
        return
      }
      if (e.key === 'Escape') {
        e.preventDefault()
        setSlash(null)
        return
      }
    }

    if (mentionOptions.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        setMentionIndex((prev) => (prev + 1) % mentionOptions.length)
        return
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault()
        setMentionIndex((prev) => (prev - 1 + mentionOptions.length) % mentionOptions.length)
        return
      }
      if (e.key === 'Tab' || e.key === 'Enter') {
        e.preventDefault()
        applyMention(mentionOptions[mentionIndex] ?? mentionOptions[0])
        return
      }
      if (e.key === 'Escape') {
        e.preventDefault()
        setMention(null)
        return
      }
    }

    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const handleSend = () => {
    if (!text.trim() || isLoading) return
    onSend(text.trim())
    setText('')
    textareaRef.current?.focus()
  }

  return (
    <div className="input-bar">
      <div className="input-status">
        <span className="agent-pill">Current: @{activeCli ?? 'none'}</span>
        <button
          type="button"
          className={`agent-pill plan-toggle ${chatMode === 'plan' ? 'active' : ''}`}
          onClick={onTogglePlanMode}
        >
          Plan: {chatMode === 'plan' ? 'ON' : 'OFF'}
        </button>
        <span className="agent-hint">
          Available: {installedClis.map((cli) => `@${cli}`).join(', ')}, @all, /plan
        </span>
      </div>
      <textarea
        ref={textareaRef}
        className="input-textarea"
        value={text}
        onChange={(e) => {
          setText(e.target.value)
          openMention(e.target.value, e.target.selectionStart)
        }}
        onKeyDown={handleKeyDown}
        onClick={(e) => openMention(e.currentTarget.value, e.currentTarget.selectionStart)}
        onKeyUp={(e) => {
          if (['ArrowUp', 'ArrowDown', 'Enter', 'Tab', 'Escape'].includes(e.key)) return
          openMention(e.currentTarget.value, e.currentTarget.selectionStart)
        }}
        placeholder="Ask anything... /plan, @codex /plan, @all (Enter to send)"
        rows={3}
        disabled={isLoading || installedClis.length === 0}
      />
      {mentionOptions.length > 0 && (
        <div className="mention-menu" role="listbox" aria-label="CLI mentions">
          {mentionOptions.map((target, index) => {
            const selected = index === mentionIndex
            const isCli = target !== 'all'
            return (
              <button
                key={target}
                ref={(el) => { mentionItemRefs.current[index] = el }}
                type="button"
                className={`mention-item ${selected ? 'active' : ''}`}
                onMouseDown={(e) => {
                  e.preventDefault()
                  applyMention(target)
                }}
                role="option"
                aria-selected={selected}
              >
                <span className={`mention-item-icon ${target === 'all' ? 'all' : getAgentMeta(target).className}`} aria-hidden="true">
                  {isCli ? <AgentIcon cli={target} size={12} /> : 'ALL'}
                </span>
                <span className="mention-item-label">@{target}</span>
                <span className="mention-item-meta">
                  {target === 'all' ? 'multi-agent' : 'cli'}
                </span>
              </button>
            )
          })}
        </div>
      )}
      {slashOptions.length > 0 && (
        <div className="mention-menu" role="listbox" aria-label="Slash commands">
          {slashOptions.map((option, index) => {
            const selected = index === slashIndex
            const target = slash?.target
            const targetCli = target && target !== 'all' ? target : null
            return (
              <button
                key={`${option.label}:${option.value}`}
                ref={(el) => { slashItemRefs.current[index] = el }}
                type="button"
                className={`mention-item ${selected ? 'active' : ''}`}
                onMouseDown={(e) => {
                  e.preventDefault()
                  applySlash(option)
                }}
                role="option"
                aria-selected={selected}
              >
                <span className={`mention-item-icon slash ${targetCli ? getAgentMeta(targetCli).className : ''}`} aria-hidden="true">
                  {targetCli ? <AgentIcon cli={targetCli} size={12} /> : '/'}
                </span>
                <span className="mention-item-label mention-item-label-code">{option.label}</span>
                <span className="mention-item-meta">
                  {target ? `@${target} • ${option.meta}` : option.meta}
                </span>
              </button>
            )
          })}
        </div>
      )}
      {hashOptions.length > 0 && (
        <div className="mention-menu" role="listbox" aria-label="Project references">
          {hashOptions.map((ref, index) => {
            const selected = index === hashIndex
            return (
              <button
                key={`${ref.path}:${ref.symbol}`}
                ref={(el) => { hashItemRefs.current[index] = el }}
                type="button"
                className={`mention-item ${selected ? 'active' : ''}`}
                onMouseDown={(e) => {
                  e.preventDefault()
                  applyHash(ref)
                }}
                role="option"
                aria-selected={selected}
              >
                <span className={`mention-item-icon slash ${ref.kind === 'class' ? 'ref-class' : 'ref-file'}`} aria-hidden="true">
                  {ref.kind === 'class' ? 'C' : 'F'}
                </span>
                <span className="mention-item-label mention-item-label-code">#{ref.symbol}</span>
                <span className="mention-item-meta">{ref.path}</span>
              </button>
            )
          })}
        </div>
      )}
      <div className="input-actions">
        {isLoading ? (
          <button className="btn cancel" onClick={onCancel}>
            Stop
          </button>
        ) : (
          <button
            className="btn send"
            onClick={handleSend}
            disabled={!text.trim() || installedClis.length === 0}
          >
            Send
          </button>
        )}
      </div>
    </div>
  )
}
