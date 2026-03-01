import { ChatMode, CliName, ProjectRef } from '../bridge'
import { KeyboardEvent, useRef, useState } from 'react'
import { useAutocomplete } from '../hooks/useAutocomplete'
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

export default function InputBar({ onSend, onCancel, onTogglePlanMode, isLoading, chatMode, activeCli, installedClis, projectRefs }: Props) {
  const [text, setText] = useState('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const {
    mention, mentionIndex, mentionOptions, mentionItemRefs,
    slash, slashIndex, slashOptions, slashItemRefs,
    hash, hashIndex, hashOptions, hashItemRefs,
    openMention,
    applyMention, applySlash, applyHash,
    setMentionIndex, setSlashIndex, setHashIndex,
    setMention, setSlash, setHash,
  } = useAutocomplete(installedClis, projectRefs)

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
        applyHash(hashOptions[hashIndex] ?? hashOptions[0], text, setText, textareaRef)
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
        applySlash(slashOptions[slashIndex] ?? slashOptions[0], text, setText, textareaRef)
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
        applyMention(mentionOptions[mentionIndex] ?? mentionOptions[0], text, setText, textareaRef)
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
                  applyMention(target, text, setText, textareaRef)
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
                  applySlash(option, text, setText, textareaRef)
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
                  applyHash(ref, text, setText, textareaRef)
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
