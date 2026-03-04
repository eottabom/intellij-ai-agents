import { useCallback, useEffect, useRef } from 'react'
import type { Dispatch, MutableRefObject, SetStateAction } from 'react'
import { CliName, ProjectRef, bridge } from '../bridge'
import type { Message } from '../components/ChatPanel'

function findLastStreamingIndex(messages: Message[], cli?: CliName): number | undefined {
  for (let i = messages.length - 1; i >= 0; i--) {
    const m = messages[i]
    if (m.isStreaming && (cli === undefined || m.cli === cli)) {
      return i
    }
  }
  return undefined
}

interface UseBridgeCallbacksParams {
  activeCli: CliName | null
  installedClis: CliName[]
  msgIdRef: MutableRefObject<number>
  pendingResponseCliRef: MutableRefObject<CliName | null>
  pendingSessionsRef: MutableRefObject<{
    remaining: Set<CliName>
    results: Partial<Record<CliName, string>>
  } | null>
  setMessages: Dispatch<SetStateAction<Message[]>>
  setRunningClis: Dispatch<SetStateAction<CliName[]>>
  setProgressByCli: Dispatch<SetStateAction<Partial<Record<CliName, string>>>>
  setProjectRefs: Dispatch<SetStateAction<ProjectRef[]>>
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
  const activeCliRef = useRef<CliName | null>(activeCli)
  const installedClisRef = useRef<CliName[]>(installedClis)
	const appendAssistantRef = useRef(appendAssistant)
	const chunkBufferRef = useRef<Partial<Record<CliName, string>>>({})
	const chunkFlushTimerRef = useRef<number | null>(null)
	const progressBufferRef = useRef<Partial<Record<CliName, string>>>({})
	const progressFlushTimerRef = useRef<number | null>(null)

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
        const targetMessageIndex = findLastStreamingIndex(nextMessages, cli)

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
		}, 80)
	}, [flushBufferedChunks])

	const flushBufferedProgress = useCallback(() => {
		if (progressFlushTimerRef.current !== null) {
			window.clearTimeout(progressFlushTimerRef.current)
			progressFlushTimerRef.current = null
		}
		const bufferedProgressEntries = Object.entries(progressBufferRef.current) as Array<[CliName, string]>
		if (bufferedProgressEntries.length === 0) {
			return
		}
		progressBufferRef.current = {}

		setProgressByCli((previousProgressByCli) => {
			let changed = false
			const nextProgressByCli = { ...previousProgressByCli }
			for (const [cli, text] of bufferedProgressEntries) {
				if (nextProgressByCli[cli] === text) {
					continue
				}
				nextProgressByCli[cli] = text
				changed = true
			}
			return changed ? nextProgressByCli : previousProgressByCli
		})
	}, [setProgressByCli])

	const scheduleProgressFlush = useCallback(() => {
		if (progressFlushTimerRef.current !== null) {
			return
		}
		progressFlushTimerRef.current = window.setTimeout(() => {
			flushBufferedProgress()
		}, 120)
	}, [flushBufferedProgress])

  useEffect(() => {
    activeCliRef.current = activeCli
  }, [activeCli])

  useEffect(() => {
    installedClisRef.current = installedClis
  }, [installedClis])

  useEffect(() => {
    appendAssistantRef.current = appendAssistant
  }, [appendAssistant])

  useEffect(() => {
    const prevOnChunk = window.__onChunk
    const prevOnProgress = window.__onProgress
    const prevOnDone = window.__onDone
    const prevOnError = window.__onError
    const prevOnProjectRefs = window.__onProjectRefs
    const prevOnSession = window.__onSession

    window.__onChunk = ((arg1: CliName | string, arg2?: string) => {
      const cli = arg2 !== undefined ? (arg1 as CliName) : (pendingResponseCliRef.current ?? activeCliRef.current ?? undefined)
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
		const cli = arg2 !== undefined ? (arg1 as CliName) : (pendingResponseCliRef.current ?? activeCliRef.current ?? undefined)
		if (!cli) return
		progressBufferRef.current[cli] = text
		scheduleProgressFlush()
	}) as typeof window.__onProgress

	window.__onDone = ((cliArg?: CliName) => {
		flushBufferedChunks()
		flushBufferedProgress()
		if (cliArg) {
			delete progressBufferRef.current[cliArg]
			setRunningClis((prev) => prev.filter((c) => c !== cliArg))
			setProgressByCli((prev) => {
				const next = { ...prev }
          delete next[cliArg]
          return next
        })
        setMessages((prev) => {
          const idx = findLastStreamingIndex(prev, cliArg)
          if (idx === undefined) return prev
          const next = [...prev]
          next[idx] = { ...next[idx], isStreaming: false }
          return next
        })
		} else {
			progressBufferRef.current = {}
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
      if (!cliArg || pendingResponseCliRef.current === cliArg) {
        pendingResponseCliRef.current = null
      }
    }) as typeof window.__onDone

	window.__onError = ((arg1: CliName | string, arg2?: string) => {
		flushBufferedChunks()
		flushBufferedProgress()
		const cli = arg2 !== undefined ? (arg1 as CliName) : (pendingResponseCliRef.current ?? activeCliRef.current ?? undefined)
		const error = arg2 !== undefined ? arg2 : String(arg1)
		if (cli) {
			delete progressBufferRef.current[cli]
			setRunningClis((prev) => prev.filter((c) => c !== cli))
			setProgressByCli((prev) => {
				const next = { ...prev }
          delete next[cli]
          return next
        })
        setMessages((previousMessages) => {
          const streamingMessageIndex = findLastStreamingIndex(previousMessages, cli)
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
			progressBufferRef.current = {}
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
      if (!cli || pendingResponseCliRef.current === cli) {
        pendingResponseCliRef.current = null
      }
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
		flushBufferedProgress()
		window.__onChunk = prevOnChunk
		window.__onProgress = prevOnProgress
		window.__onDone = prevOnDone
		window.__onError = prevOnError
		window.__onProjectRefs = prevOnProjectRefs
		window.__onSession = prevOnSession
	}
	}, [flushBufferedChunks, flushBufferedProgress, msgIdRef, scheduleChunkFlush, scheduleProgressFlush, setMessages, setProgressByCli, setProjectRefs, setRunningClis])

  useEffect(() => {
    const requestProjectRefs = () => bridge.getProjectRefs()
    requestProjectRefs()
    window.addEventListener('bridgeReady', requestProjectRefs)
    return () => {
      window.removeEventListener('bridgeReady', requestProjectRefs)
    }
  }, [])

  const clearBuffers = useCallback(() => {
    chunkBufferRef.current = {}
    progressBufferRef.current = {}
    if (chunkFlushTimerRef.current !== null) {
      window.clearTimeout(chunkFlushTimerRef.current)
      chunkFlushTimerRef.current = null
    }
    if (progressFlushTimerRef.current !== null) {
      window.clearTimeout(progressFlushTimerRef.current)
      progressFlushTimerRef.current = null
    }
  }, [])

  return { clearBuffers }
}
