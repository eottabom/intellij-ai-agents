import { useCallback, useEffect, useRef } from 'react'
import { CliName, ProjectRef, bridge } from '../bridge'
import type { Message } from '../components/ChatPanel'

interface UseBridgeCallbacksParams {
  activeCli: CliName | null
  installedClis: CliName[]
  msgIdRef: React.MutableRefObject<number>
  pendingResponseCliRef: React.MutableRefObject<CliName | null>
  pendingSessionsRef: React.MutableRefObject<{
    remaining: Set<CliName>
    results: Partial<Record<CliName, string>>
  } | null>
  setMessages: React.Dispatch<React.SetStateAction<Message[]>>
  setRunningClis: React.Dispatch<React.SetStateAction<CliName[]>>
  setProgressByCli: React.Dispatch<React.SetStateAction<Partial<Record<CliName, string>>>>
  setProjectRefs: React.Dispatch<React.SetStateAction<ProjectRef[]>>
  appendAssistant: (content: string, cli?: CliName, variant?: Message['variant']) => void
}

export function useBridgeCallbacks({
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
}: UseBridgeCallbacksParams) {
  const installedClisRef = useRef<CliName[]>([])
  const appendAssistantRef = useRef(appendAssistant)
  const chunkBufferRef = useRef<Partial<Record<CliName, string>>>({})
  const chunkFlushTimerRef = useRef<number | null>(null)

  const flushBufferedChunks = useCallback(() => {
    if (chunkFlushTimerRef.current !== null) {
      window.clearTimeout(chunkFlushTimerRef.current)
      chunkFlushTimerRef.current = null
    }
    const bufferedEntries = Object.entries(chunkBufferRef.current) as Array<[CliName, string]>
    if (bufferedEntries.length === 0) {
      return
    }
    chunkBufferRef.current = {}

    setMessages((previousMessages) => {
      let nextMessages = previousMessages
      for (const [cli, text] of bufferedEntries) {
        const targetMessageIndex = [...nextMessages]
          .map((message, messageIndex) => ({ message, messageIndex }))
          .reverse()
          .find(({ message }) => (
            message.role === 'assistant'
            && message.isStreaming
            && message.cli === cli
          ))?.messageIndex

        if (targetMessageIndex !== undefined) {
          const targetMessage = nextMessages[targetMessageIndex]
          const updatedMessages = [...nextMessages]
          updatedMessages[targetMessageIndex] = {
            ...targetMessage,
            content: targetMessage.content + text,
          }
          nextMessages = updatedMessages
          continue
        }
        nextMessages = [...nextMessages, {
          id: ++msgIdRef.current,
          role: 'assistant',
          cli,
          content: text,
          isStreaming: true,
        }]
      }
      return nextMessages
    })
  }, [msgIdRef, setMessages])

  const scheduleChunkFlush = useCallback(() => {
    if (chunkFlushTimerRef.current !== null) {
      return
    }
    chunkFlushTimerRef.current = window.setTimeout(() => {
      flushBufferedChunks()
    }, 33)
  }, [flushBufferedChunks])

  useEffect(() => {
    installedClisRef.current = installedClis
  }, [installedClis])

  useEffect(() => {
    appendAssistantRef.current = appendAssistant
  }, [appendAssistant])

  useEffect(() => {
    window.__onChunk = ((arg1: CliName | string, arg2?: string) => {
      const cli = arg2 !== undefined ? (arg1 as CliName) : (pendingResponseCliRef.current ?? activeCli ?? undefined)
      const text = arg2 !== undefined ? arg2 : String(arg1)
      if (!cli) {
        setMessages((previousMessages) => [
          ...previousMessages,
          {
            id: ++msgIdRef.current,
            role: 'assistant',
            content: text,
            isStreaming: true,
          },
        ])
        return
      }
      const previousBufferedText = chunkBufferRef.current[cli] ?? ''
      chunkBufferRef.current[cli] = previousBufferedText + text
      scheduleChunkFlush()
    }) as typeof window.__onChunk

    window.__onProgress = ((arg1: CliName | string, arg2?: string) => {
      const text = arg2 !== undefined ? arg2 : String(arg1)
      const cli = arg2 !== undefined ? (arg1 as CliName) : (pendingResponseCliRef.current ?? activeCli ?? undefined)
      if (!cli) return
      setProgressByCli((prev) => ({ ...prev, [cli]: text }))
    }) as typeof window.__onProgress

    window.__onDone = ((cliArg?: CliName) => {
      flushBufferedChunks()
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
        setMessages((previousMessages) =>
          previousMessages.map((message) => (
            message.isStreaming
              ? { ...message, isStreaming: false }
              : message
          )),
        )
      }
      pendingResponseCliRef.current = null
    }) as typeof window.__onDone

    window.__onError = ((arg1: CliName | string, arg2?: string) => {
      flushBufferedChunks()
      const cli = arg2 !== undefined ? (arg1 as CliName) : (pendingResponseCliRef.current ?? activeCli ?? undefined)
      const error = arg2 !== undefined ? arg2 : String(arg1)
      if (cli) {
        setRunningClis((prev) => prev.filter((c) => c !== cli))
        setProgressByCli((prev) => {
          const next = { ...prev }
          delete next[cli]
          return next
        })
        setMessages((previousMessages) => {
          const streamingMessageIndex = [...previousMessages]
            .map((message, messageIndex) => ({ message, messageIndex }))
            .reverse()
            .find(({ message }) => message.isStreaming && message.cli === cli)?.messageIndex
          if (streamingMessageIndex === undefined) {
            return previousMessages
          }
          const nextMessages = [...previousMessages]
          nextMessages[streamingMessageIndex] = {
            ...nextMessages[streamingMessageIndex],
            isStreaming: false,
          }
          return nextMessages
        })
      } else {
        setRunningClis([])
        setProgressByCli({})
        setMessages((previousMessages) =>
          previousMessages.map((message) => (
            message.isStreaming
              ? { ...message, isStreaming: false }
              : message
          )),
        )
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
            return `- **@${c}**: ${id ? `\`${id}\`` : 'no active session'}`
          }),
        ]
        appendAssistantRef.current(lines.join('\n'), undefined, 'system')
      }
    }) as typeof window.__onSession

    return () => {
      flushBufferedChunks()
      window.__onChunk = (() => {}) as typeof window.__onChunk
      window.__onProgress = (() => {}) as typeof window.__onProgress
      window.__onDone = (() => {}) as typeof window.__onDone
      window.__onError = (() => {}) as typeof window.__onError
      window.__onProjectRefs = (() => {}) as typeof window.__onProjectRefs
      window.__onSession = (() => {}) as typeof window.__onSession
    }
  }, [activeCli, flushBufferedChunks, msgIdRef, scheduleChunkFlush, setMessages, setProgressByCli, setProjectRefs, setRunningClis])

  useEffect(() => {
    const requestProjectRefs = () => bridge.getProjectRefs()
    requestProjectRefs()
    window.addEventListener('bridgeReady', requestProjectRefs)
    return () => {
      window.removeEventListener('bridgeReady', requestProjectRefs)
    }
  }, [])
}
