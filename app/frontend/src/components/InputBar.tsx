import { AiModel, ChatMode, CliName, ProjectRef } from '../bridge'
import { KeyboardEvent, useEffect, useRef, useState } from 'react'
import { useAutocomplete } from '../hooks/useAutocomplete'
import AgentIcon, { getAgentMeta } from './AgentIcon'

interface Props {
  onSend: (prompt: string) => void
  onCancel: () => void
  onTogglePlanMode: () => void
  onAgentChange: (cli: CliName) => void
  onModelChange: (cli: CliName, modelId: string) => void
  isLoading: boolean
  chatMode: ChatMode
  activeCli: CliName | null
  installedClis: CliName[]
  projectRefs: ProjectRef[]
  modelsByCli: Partial<Record<CliName, AiModel[]>>
  selectedModelByCli: Partial<Record<CliName, string>>
}

export default function InputBar({ onSend, onCancel, onTogglePlanMode, onAgentChange, onModelChange, isLoading, chatMode, activeCli, installedClis, projectRefs, modelsByCli, selectedModelByCli }: Props) {
  const [text, setText] = useState('')
  const [showAgentDropdown, setShowAgentDropdown] = useState(false)
  const [modelDropdownCli, setModelDropdownCli] = useState<CliName | null>(null)
  const agentDropdownRef = useRef<HTMLDivElement>(null)
  const modelDropdownRef = useRef<HTMLDivElement>(null)
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

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (agentDropdownRef.current && !agentDropdownRef.current.contains(e.target as Node)) {
        setShowAgentDropdown(false)
      }
      if (modelDropdownRef.current && !modelDropdownRef.current.contains(e.target as Node)) {
        setModelDropdownCli(null)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const getActiveModelLabel = (cli: CliName): string => {
    const selectedId = selectedModelByCli[cli]
    if (!selectedId) return 'default'
    const models = modelsByCli[cli] ?? []
    const found = models.find((m) => m.id === selectedId)
    return found ? found.displayName : selectedId
  }

  const handleMenuKeys = <T,>(
    e: KeyboardEvent<HTMLTextAreaElement>,
    options: T[],
    index: number,
    setIndex: (fn: (prev: number) => number) => void,
    apply: (item: T) => void,
    close: () => void,
  ): boolean => {
    if (options.length === 0) {
      return false
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setIndex((prev) => (prev + 1) % options.length)
      return true
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      setIndex((prev) => (prev - 1 + options.length) % options.length)
      return true
    }
    if (e.key === 'Tab' || e.key === 'Enter') {
      e.preventDefault()
      apply(options[index] ?? options[0])
      return true
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      close()
      return true
    }
    return false
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    const isImeComposing = e.nativeEvent.isComposing
      || e.nativeEvent.keyCode === 229
      || e.key === 'Process'
    if (isImeComposing) {
      return
    }

    if (handleMenuKeys(e, hashOptions, hashIndex, setHashIndex,
      (item) => applyHash(item, text, setText, textareaRef), () => setHash(null))) {
      return
    }
    if (handleMenuKeys(e, slashOptions, slashIndex, setSlashIndex,
      (item) => applySlash(item, text, setText, textareaRef), () => setSlash(null))) {
      return
    }
    if (handleMenuKeys(e, mentionOptions, mentionIndex, setMentionIndex,
      (item) => applyMention(item, text, setText, textareaRef), () => setMention(null))) {
      return
    }

    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const handleSend = () => {
    const prompt = text.trim()
    if (!prompt || isLoading) return
    onSend(prompt)
    setText('')
    setMention(null)
    setSlash(null)
    setHash(null)
    setMentionIndex(0)
    setSlashIndex(0)
    setHashIndex(0)
    textareaRef.current?.focus()
  }

  return (
    <div className="input-bar">
      <div className="input-status">
        <div className="agent-selector-wrapper" ref={agentDropdownRef}>
          <button
            type="button"
            className="agent-pill agent-selector-pill"
            onClick={() => setShowAgentDropdown(!showAgentDropdown)}
          >
            {activeCli && <AgentIcon cli={activeCli} size={12} />}
            <span>@{activeCli ?? 'none'}</span>
          </button>
          {showAgentDropdown && installedClis.length > 0 && (
            <div className="agent-dropdown" role="listbox" aria-label="Agent selection">
              {installedClis.map((cli) => {
                const isActive = cli === activeCli
                const meta = getAgentMeta(cli)
                return (
                  <button
                    key={cli}
                    type="button"
                    className={`agent-dropdown-item ${isActive ? 'active' : ''}`}
                    onClick={() => {
                      onAgentChange(cli)
                      setShowAgentDropdown(false)
                    }}
                    role="option"
                    aria-selected={isActive}
                  >
                    <AgentIcon cli={cli} size={14} />
                    <span className="agent-dropdown-label">@{cli}</span>
                    <span className="agent-dropdown-meta">{meta.label}</span>
                  </button>
                )
              })}
            </div>
          )}
        </div>
        {activeCli && (
          <div className="model-selector-wrapper" ref={modelDropdownRef}>
            <button
              type="button"
              className="agent-pill model-pill"
              onClick={() => setModelDropdownCli(modelDropdownCli === activeCli ? null : activeCli)}
            >
              Model: {getActiveModelLabel(activeCli)}
            </button>
            {modelDropdownCli === activeCli && (
              <div className="model-dropdown" role="listbox" aria-label="Model selection">
                <button
                  type="button"
                  className={`model-dropdown-item ${!selectedModelByCli[activeCli] ? 'active' : ''}`}
                  onClick={() => {
                    onModelChange(activeCli, '')
                    setModelDropdownCli(null)
                  }}
                  role="option"
                  aria-selected={!selectedModelByCli[activeCli]}
                >
                  (default)
                </button>
                {(modelsByCli[activeCli] ?? []).map((model) => {
                  const isSelected = selectedModelByCli[activeCli] === model.id
                  return (
                    <button
                      key={model.id}
                      type="button"
                      className={`model-dropdown-item ${isSelected ? 'active' : ''}`}
                      onClick={() => {
                        onModelChange(activeCli, model.id)
                        setModelDropdownCli(null)
                      }}
                      role="option"
                      aria-selected={isSelected}
                    >
                      {model.displayName}
                    </button>
                  )
                })}
              </div>
            )}
          </div>
        )}
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
          <button type="button" className="btn cancel" onClick={onCancel}>
            Stop
          </button>
        ) : (
          <button
            type="button"
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
