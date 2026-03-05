import { useEffect, useRef, useState } from 'react'
import { ChatMode, CliName, ProjectRef, bridge } from '../bridge'
import { parseAgentCommand, parsePlanCommand, parseSessionCommand } from '../utils/commandParser'
import { useBridgeCallbacks } from '../hooks/useBridgeCallbacks'
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

const CONTEXT_TURNS = 4
const CONTEXT_CHARS_PER_MSG = 220

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
  const chatModeRef = useRef<ChatMode>(chatMode)
  const pendingSessionsRef = useRef<{ remaining: Set<CliName>; results: Partial<Record<CliName, string>> } | null>(null)
  const isLoading = runningClis.length > 0

  useEffect(() => {
    messagesRef.current = messages
  }, [messages])

  useEffect(() => {
    chatModeRef.current = chatMode
  }, [chatMode])

  useEffect(() => {
    if (!activeCli && installedClis.length > 0) {
      setActiveCli(installedClis[0])
    }
    if (activeCli && !installedClis.includes(activeCli)) {
      setActiveCli(installedClis[0] ?? null)
    }
  }, [installedClis, activeCli])

  const appendAssistant = (content: string, cli?: CliName, variant?: Message['variant']) => {
    setMessages((prev) => [...prev, { id: ++msgIdRef.current, role: 'assistant', content, cli, variant }])
  }

  const { clearBuffers } = useBridgeCallbacks({
    activeCli,
    installedClis,
    msgIdRef,
    pendingResponseCliRef,
    pendingSessionsRef,
    setMessages,
    setRunningClis,
    setProgressByCli,
    setProjectRefs,
    appendAssistant,
  })

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
    mode: ChatMode = chatModeRef.current,
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

    const failedClis: CliName[] = []
    targetClis.forEach((cli) => {
      try {
        const finalPrompt = promptFactory ? promptFactory(cli) : composePromptWithContext(userVisibleText, snapshot)
        bridge.chat(cli, finalPrompt, mode)
      } catch {
        failedClis.push(cli)
      }
    })
    if (failedClis.length > 0) {
      setRunningClis((prev) => prev.filter((c) => !failedClis.includes(c)))
      setProgressByCli((prev) => {
        const next = { ...prev }
        failedClis.forEach((c) => { delete next[c] })
        return next
      })
      if (pendingResponseCliRef.current && failedClis.includes(pendingResponseCliRef.current)) {
        const remaining = targetClis.filter((c) => !failedClis.includes(c))
        pendingResponseCliRef.current = remaining[0] ?? null
      }
    }
  }

  const handleSend = (prompt: string) => {
    if (!prompt.trim() || isLoading || installedClis.length === 0 || !activeCli) return

    const parsed = parseAgentCommand(prompt, activeCli)
    if (parsed.target !== 'all' && !installedClis.includes(parsed.target)) {
      setMessages((prev) => [
        ...prev,
        {
          id: ++msgIdRef.current,
          role: 'assistant',
          variant: 'system',
          content: `@${parsed.target} is not installed.`,
        },
      ])
      return
    }
    const planCmd = parsePlanCommand(parsed.prompt)
    const sessionCmd = parseSessionCommand(planCmd.prompt)
    let nextMode = chatModeRef.current
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
      const failedClis: CliName[] = []
      installedClis.forEach((cli) => {
        try {
          bridge.getSession(cli)
        } catch {
          failedClis.push(cli)
          pendingSessionsRef.current?.remaining.delete(cli)
          pendingSessionsRef.current!.results[cli] = ''
        }
      })
      if (failedClis.length > 0) {
        appendAssistant(`Failed to fetch session for: ${failedClis.map((cli) => `@${cli}`).join(', ')}`, undefined, 'system')
      }
      if (pendingSessionsRef.current && pendingSessionsRef.current.remaining.size === 0) {
        const results = { ...pendingSessionsRef.current.results }
        pendingSessionsRef.current = null
        const lines = [
          '🗂 **Session Status**',
          '',
          ...installedClis.map((cli) => {
            const sessionId = results[cli]
            return `- **@${cli}**: ${sessionId ? `\`${sessionId}\`` : 'no active session'}`
          }),
        ]
        appendAssistant(lines.join('\n'), undefined, 'system')
      }
      return
    }

    if (/^\/doctor$/i.test(parsedPrompt.trim())) {
      const targetClis = parsed.target === 'all' ? [...installedClis] : [parsed.target]
      const doctorFailedClis: CliName[] = []
      targetClis.forEach((cli) => {
        try {
          bridge.chat(cli, '/doctor', 'normal')
        } catch {
          doctorFailedClis.push(cli)
        }
      })
      const succeededClis = targetClis.filter((c) => !doctorFailedClis.includes(c))
      setMessages((prev) => [
        ...prev,
        {
          id: ++msgIdRef.current,
          role: 'user',
          content: '/doctor' + (targetClis.length > 1 ? ' (@all)' : ` (@${targetClis[0]})`),
        },
      ])
      setRunningClis(succeededClis)
      setProgressByCli(Object.fromEntries(succeededClis.map((cli) => [cli, 'checking...'])) as Partial<Record<CliName, string>>)
      pendingResponseCliRef.current = succeededClis[0] ?? null
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
    clearBuffers()
    const failedClis: CliName[] = []
    runningClis.forEach((cli) => {
      try {
        bridge.cancel(cli)
      } catch {
        failedClis.push(cli)
      }
    })
    setRunningClis([])
    setProgressByCli({})
    setMessages((previousMessages) =>
      previousMessages.map((message) => (message.isStreaming ? { ...message, isStreaming: false } : message)),
    )
    pendingResponseCliRef.current = null
    if (failedClis.length > 0) {
      appendAssistant(`Failed to cancel: ${failedClis.map((cli) => `@${cli}`).join(', ')}`, undefined, 'system')
    }
  }

  const handleTogglePlanMode = () => {
    const nextChatMode = chatModeRef.current === 'plan' ? 'normal' : 'plan'
    chatModeRef.current = nextChatMode
    setChatMode(nextChatMode)
    setMessages((messages) => [
      ...messages,
      {
        id: ++msgIdRef.current,
        role: 'assistant',
        variant: 'system',
        content: `Plan mode ${nextChatMode === 'plan' ? 'enabled' : 'disabled'}.`,
      },
    ])
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
