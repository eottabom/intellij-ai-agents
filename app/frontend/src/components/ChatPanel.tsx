import { useEffect, useRef, useState } from 'react'
import { ChatMode, CliName, ProjectRef, bridge } from '../bridge'
import MessageList from './MessageList'
import InputBar from './InputBar'
import AgentIcon, { getAgentMeta } from './AgentIcon'

export interface Message {
  id: number
  role: 'user' | 'assistant'
  content: string
  cli?: CliName
  isStreaming?: boolean
  variant?: 'system' | 'summary'
}

interface Props {
  installedClis: CliName[]
}

type Target = CliName | 'all'

interface PlanCommandResult {
  modeChanged: ChatMode | null
  prompt: string
}

const CONTEXT_TURNS = 4
const CONTEXT_CHARS_PER_MSG = 220

function parseAgentCommand(raw: string, fallbackCli: CliName) {
  const match = raw.match(/^@(claude|gemini|codex|all)\b\s*/i)
  if (!match) {
    return { target: fallbackCli as Target, prompt: raw, switched: false }
  }

  const target = match[1].toLowerCase() as Target
  const prompt = raw.slice(match[0].length).trim()
  return { target, prompt, switched: target !== fallbackCli && target !== 'all' }
}

function parsePlanCommand(raw: string): PlanCommandResult {
  const trimmed = raw.trim()
  const match = trimmed.match(/^\/plan(?:\s+(on|off))?(?:\s+([\s\S]*))?$/i)
  if (!match) return { modeChanged: null, prompt: raw }

  const modeToken = match[1]?.toLowerCase()
  const remainder = (match[2] ?? '').trim()
  if (modeToken === 'off') return { modeChanged: 'normal', prompt: remainder }
  return { modeChanged: 'plan', prompt: remainder }
}

function parseSessionCommand(raw: string) {
  const trimmed = raw.trim()
  if (/^\/clearall(?:\s+sessions?)?$/i.test(trimmed)) {
    return { clearAllSessions: true, prompt: '' }
  }
  return { clearAllSessions: false, prompt: raw }
}

export default function ChatPanel({ installedClis }: Props) {
  const [messages, setMessages] = useState<Message[]>([])
  const [runningClis, setRunningClis] = useState<CliName[]>([])
  const [progressByCli, setProgressByCli] = useState<Partial<Record<CliName, string>>>({})
  const [activeCli, setActiveCli] = useState<CliName | null>(null)
  const [chatMode, setChatMode] = useState<ChatMode>('normal')
  const [projectRefs, setProjectRefs] = useState<ProjectRef[]>([])
  const msgIdRef = useRef(0)
  const pendingResponseCliRef = useRef<CliName | null>(null)
  const messagesRef = useRef<Message[]>([])
  const installedClisRef = useRef<CliName[]>([])
  const pendingSessionsRef = useRef<{ remaining: Set<CliName>; results: Partial<Record<CliName, string>> } | null>(null)
  const isLoading = runningClis.length > 0

  useEffect(() => {
    messagesRef.current = messages
  }, [messages])

  useEffect(() => {
    installedClisRef.current = installedClis
  }, [installedClis])

  useEffect(() => {
    if (!activeCli && installedClis.length > 0) {
      setActiveCli(installedClis[0])
    }
    if (activeCli && !installedClis.includes(activeCli)) {
      setActiveCli(installedClis[0] ?? null)
    }
  }, [installedClis, activeCli])

  useEffect(() => {
    window.__onChunk = ((arg1: CliName | string, arg2?: string) => {
      const cli = arg2 !== undefined ? (arg1 as CliName) : (pendingResponseCliRef.current ?? activeCli ?? undefined)
      const text = arg2 !== undefined ? arg2 : String(arg1)
      setMessages((prev) => {
        const last = prev[prev.length - 1]
        if (last?.role === 'assistant' && last.isStreaming && last.cli === cli) {
          return [...prev.slice(0, -1), { ...last, content: last.content + text }]
        }
        return [...prev, {
          id: ++msgIdRef.current,
          role: 'assistant',
          cli,
          content: text,
          isStreaming: true,
        }]
      })
    }) as typeof window.__onChunk

    window.__onProgress = ((arg1: CliName | string, arg2?: string) => {
      const text = arg2 !== undefined ? arg2 : String(arg1)
      const cli = arg2 !== undefined ? (arg1 as CliName) : (pendingResponseCliRef.current ?? activeCli ?? undefined)
      if (!cli) return
      setProgressByCli((prev) => ({ ...prev, [cli]: text }))
    }) as typeof window.__onProgress

    window.__onDone = ((cliArg?: CliName) => {
      if (cliArg) {
        setRunningClis((prev) => prev.filter((c) => c !== cliArg))
        setProgressByCli((prev) => {
          const next = { ...prev }
          delete next[cliArg]
          return next
        })
        setMessages((prev) => {
          const idx = [...prev].map((m, i) => ({ m, i })).reverse()
            .find(({ m }) => m.isStreaming && m.cli === cliArg)?.i
          if (idx === undefined) return prev
          const next = [...prev]
          next[idx] = { ...next[idx], isStreaming: false }
          return next
        })
      } else {
        setRunningClis([])
        setProgressByCli({})
        setMessages((prev) => {
          const idx = [...prev].map((m, i) => ({ m, i })).reverse()
            .find(({ m }) => m.isStreaming)?.i
          if (idx === undefined) return prev
          const next = [...prev]
          next[idx] = { ...next[idx], isStreaming: false }
          return next
        })
      }
      pendingResponseCliRef.current = null
    }) as typeof window.__onDone

    window.__onError = ((arg1: CliName | string, arg2?: string) => {
      const cli = arg2 !== undefined ? (arg1 as CliName) : (pendingResponseCliRef.current ?? activeCli ?? undefined)
      const error = arg2 !== undefined ? arg2 : String(arg1)
      if (cli) {
        setRunningClis((prev) => prev.filter((c) => c !== cli))
        setProgressByCli((prev) => {
          const next = { ...prev }
          delete next[cli]
          return next
        })
      } else {
        setRunningClis([])
        setProgressByCli({})
      }
      setMessages((prev) => [
        ...prev,
        {
          id: ++msgIdRef.current,
          role: 'assistant',
          cli,
          variant: 'system',
          content: `⚠️ Error: ${error}`,
        },
      ])
      pendingResponseCliRef.current = null
    }) as typeof window.__onError

    window.__onProjectRefs = ((refs: ProjectRef[]) => {
      setProjectRefs(Array.isArray(refs) ? refs : [])
    }) as typeof window.__onProjectRefs

    window.__onSession = ((cli: CliName, sessionId: string) => {
      if (!pendingSessionsRef.current) return
      pendingSessionsRef.current.results[cli] = sessionId
      pendingSessionsRef.current.remaining.delete(cli)
      if (pendingSessionsRef.current.remaining.size === 0) {
        const results = { ...pendingSessionsRef.current.results }
        pendingSessionsRef.current = null
        const clis = installedClisRef.current
        const lines = [
          '🗂 **Session Status**',
          '',
          ...clis.map((c) => {
            const id = results[c]
            return `- **@${c}**: ${id ? `\`${id.slice(0, 24)}…\`` : 'no active session'}`
          }),
        ]
        appendAssistant(lines.join('\n'), undefined, 'system')
      }
    }) as typeof window.__onSession
  }, [activeCli])

  useEffect(() => {
    bridge.getProjectRefs()
  }, [])

  const appendAssistant = (content: string, cli?: CliName, variant?: Message['variant']) => {
    setMessages((prev) => [...prev, { id: ++msgIdRef.current, role: 'assistant', content, cli, variant }])
  }

  const clip = (text: string, max = CONTEXT_CHARS_PER_MSG) =>
    text.replace(/\s+/g, ' ').trim().slice(0, max)

  const buildCompactContext = (baseMessages: Message[]) => {
    const recent = baseMessages
      .filter((m) => !m.isStreaming)
      .filter((m) => m.variant !== 'system')
      .slice(-CONTEXT_TURNS)

    if (recent.length === 0) return ''
    const lines = recent.map((m) => {
      const speaker =
        m.role === 'user'
          ? 'USER'
          : (m.cli ? m.cli.toUpperCase() : (m.variant === 'summary' ? 'ORCHESTRATOR' : 'ASSISTANT'))
      return `${speaker}: ${clip(m.content)}`
    })

    return [
      'Context (recent turns, compressed):',
      ...lines,
      'Answer the latest request only. Keep it concise.',
      '',
    ].join('\n')
  }

  const composePromptWithContext = (userPrompt: string, snapshot: Message[]) => {
    const context = buildCompactContext(snapshot)
    return context ? `${context}User request:\n${userPrompt}` : userPrompt
  }

  const startDispatch = (
    targetClis: CliName[],
    userVisibleText: string,
    userCli?: CliName,
    promptFactory?: (cli: CliName) => string,
    mode: ChatMode = chatMode,
  ) => {
    const snapshot = messagesRef.current
    setMessages((prev) => [
      ...prev,
      {
        id: ++msgIdRef.current,
        role: 'user',
        cli: userCli,
        content: userVisibleText,
      },
    ])
    setRunningClis(targetClis)
    setProgressByCli(Object.fromEntries(targetClis.map((cli) => [cli, 'working...'])) as Partial<Record<CliName, string>>)
    pendingResponseCliRef.current = targetClis[0] ?? null

    targetClis.forEach((cli) => {
      const finalPrompt = promptFactory ? promptFactory(cli) : composePromptWithContext(userVisibleText, snapshot)
      bridge.chat(cli, finalPrompt, mode)
    })
  }

  const handleSend = (prompt: string) => {
    if (!prompt.trim() || isLoading || installedClis.length === 0 || !activeCli) return

    const parsed = parseAgentCommand(prompt, activeCli)
    const planCmd = parsePlanCommand(parsed.prompt)
    const sessionCmd = parseSessionCommand(planCmd.prompt)
    let nextMode = chatMode
    if (planCmd.modeChanged) {
      nextMode = planCmd.modeChanged
      setChatMode(planCmd.modeChanged)
      setMessages((prev) => [
        ...prev,
        {
          id: ++msgIdRef.current,
          role: 'assistant',
          variant: 'system',
          content: `Plan mode ${planCmd.modeChanged === 'plan' ? 'enabled' : 'disabled'}.`,
        },
      ])
    }
    const parsedPrompt = sessionCmd.prompt

    if (sessionCmd.clearAllSessions) {
      bridge.clearAllSessions()
      appendAssistant('Cleared saved sessions for all CLIs.', undefined, 'system')
      return
    }

    if (!parsedPrompt) {
      const targetLabel = parsed.target === 'all' ? '@all' : `@${parsed.target}`
      if (!planCmd.modeChanged) {
        setMessages((prev) => [
          ...prev,
          {
            id: ++msgIdRef.current,
            role: 'assistant',
            content: `Use \`${targetLabel} <message>\` to send a prompt.`,
          },
        ])
      }
      if (parsed.target !== 'all' && parsed.switched) setActiveCli(parsed.target)
      return
    }

    if (/^\/session$/i.test(parsedPrompt.trim())) {
      if (installedClis.length === 0) return
      pendingSessionsRef.current = { remaining: new Set(installedClis), results: {} }
      installedClis.forEach((cli) => bridge.getSession(cli))
      return
    }

    if (parsed.target !== 'all' && parsed.switched) {
      setActiveCli(parsed.target)
      setMessages((prev) => [
        ...prev,
        {
          id: ++msgIdRef.current,
          role: 'assistant',
          variant: 'system',
          content: `Switched target to @${parsed.target}`,
        },
      ])
    }

    const targetClis = parsed.target === 'all' ? [...installedClis] : [parsed.target]
    startDispatch(
      targetClis,
      parsed.target === 'all' ? `[@all] ${parsedPrompt}` : parsedPrompt,
      parsed.target === 'all' ? undefined : parsed.target,
      (cli) => composePromptWithContext(parsedPrompt, messagesRef.current),
      nextMode,
    )
  }

  const handleCancel = () => {
    runningClis.forEach((cli) => bridge.cancel(cli))
    setRunningClis([])
    setProgressByCli({})
    pendingResponseCliRef.current = null
  }

  const handleTogglePlanMode = () => {
    setChatMode((prev) => {
      const next = prev === 'plan' ? 'normal' : 'plan'
      setMessages((messages) => [
        ...messages,
        {
          id: ++msgIdRef.current,
          role: 'assistant',
          variant: 'system',
          content: `Plan mode ${next === 'plan' ? 'enabled' : 'disabled'}.`,
        },
      ])
      return next
    })
  }

  const progressEntries = Object.entries(progressByCli) as Array<[CliName, string]>

  return (
    <div className="chat-panel">
      <div className="chat-header">
        <div className="chat-header-title">CLI Agents</div>
        <div className="chat-header-meta">
          <span className="agent-pill active">Current: @{activeCli ?? 'none'}</span>
          <span className={`agent-pill ${chatMode === 'plan' ? 'active' : ''}`}>Plan: {chatMode === 'plan' ? 'ON' : 'OFF'}</span>
          <span className="agent-hint">Use @claude / @gemini / @codex / @all / /plan</span>
        </div>
      </div>
      <MessageList messages={messages} />

      {progressEntries.length > 0 && (
        <div className="progress-bar">
          {progressEntries.map(([cli, text]) => {
            const meta = getAgentMeta(cli)
            return (
              <div key={cli} className={`progress-chip agent-${meta.className}`}>
                <span className="progress-chip-badge" aria-hidden="true"><AgentIcon cli={cli} size={11} /></span>
                <span className="progress-chip-label">@{cli}</span>
                <span className="progress-chip-text">{text}</span>
                <span className="thinking-dots" aria-hidden="true"><i /><i /><i /></span>
              </div>
            )
          })}
        </div>
      )}

      <InputBar
        onSend={handleSend}
        onCancel={handleCancel}
        onTogglePlanMode={handleTogglePlanMode}
        isLoading={isLoading}
        chatMode={chatMode}
        activeCli={activeCli}
        installedClis={installedClis}
        projectRefs={projectRefs}
      />
    </div>
  )
}
