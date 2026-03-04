import { memo, useEffect, useRef, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism'
import { Message } from './ChatPanel'
import AgentIcon, { getAgentMeta } from './AgentIcon'

interface ParsedUserMention {
  mentionKey: string | null
  isDebate: boolean
  rest: string
}

function parseUserMention(content: string): ParsedUserMention {
  const match = content.match(/^\[@(all|claude|gemini|codex)(?:\s+(debate))?\]\s*/)
  if (!match) return { mentionKey: null, isDebate: false, rest: content }
  return {
    mentionKey: match[1],
    isDebate: match[2] === 'debate',
    rest: content.slice(match[0].length),
  }
}

interface Props {
  messages: Message[]
}

interface CodeBlockProps {
  code: string
  language: string
}

function CodeBlock({ code, language }: CodeBlockProps) {
  const [copied, setCopied] = useState(false)
  const timerRef = useRef<number | null>(null)

  useEffect(() => {
    return () => {
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current)
      }
    }
  }, [])

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current)
      }
      timerRef.current = window.setTimeout(() => {
        setCopied(false)
        timerRef.current = null
      }, 1200)
    } catch {
      setCopied(false)
    }
  }

  return (
    <div className="code-block-shell">
      <div className="code-block-toolbar">
        <span className="code-lang-badge">{language}</span>
        <button type="button" className={`code-copy-btn ${copied ? 'copied' : ''}`} onClick={handleCopy}>
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>
      <SyntaxHighlighter
        style={vscDarkPlus}
        language={language}
        PreTag="div"
      >
        {code}
      </SyntaxHighlighter>
    </div>
  )
}

const markdownComponents: React.ComponentProps<typeof ReactMarkdown>['components'] = {
  code({ className, children, ...props }) {
    const match = /language-(\w+)/.exec(className || '')
    const code = String(children).replace(/\n$/, '')
    return match ? (
      <CodeBlock code={code} language={match[1]} />
    ) : (
      <code className={className} {...props}>
        {children}
      </code>
    )
  },
}

interface MessageBubbleProps {
  msg: Message
}

const MessageBubble = memo(function MessageBubble({ msg }: MessageBubbleProps) {
  const meta = msg.cli ? getAgentMeta(msg.cli) : null
  const userMention = msg.role === 'user' ? parseUserMention(msg.content) : null
  const displayContent = userMention?.mentionKey ? userMention.rest : msg.content

  return (
    <div
      className={`message ${msg.role} ${msg.cli ? `agent-${msg.cli}` : ''} ${msg.variant ? `variant-${msg.variant}` : ''} ${msg.isStreaming ? 'is-streaming' : ''}`}
    >
      {userMention?.mentionKey && (
        <div className="message-user-meta">
          <span className={`user-mention-tag user-mention-${userMention.mentionKey}`}>
            @{userMention.mentionKey}
          </span>
          {userMention.isDebate && <span className="user-debate-label">debate</span>}
        </div>
      )}
      {msg.cli && meta && (
        <div className="message-head">
          <div className={`agent-badge agent-${meta.className}`}>
            <span className="agent-badge-icon" aria-hidden="true"><AgentIcon cli={msg.cli} size={12} /></span>
            <span className="agent-badge-label">{meta.label}</span>
          </div>
          <div className="message-cli">@{msg.cli}</div>
          {msg.isStreaming && (
            <div className="thinking-chip" aria-live="polite">
              <span>working</span>
              <span className="thinking-dots" aria-hidden="true"><i /><i /><i /></span>
            </div>
          )}
        </div>
      )}
      <ReactMarkdown components={markdownComponents}>
        {displayContent}
      </ReactMarkdown>
      {msg.isStreaming && <span className="cursor" />}
    </div>
  )
}, (prev, next) => {
  return prev.msg.id === next.msg.id
    && prev.msg.role === next.msg.role
    && prev.msg.cli === next.msg.cli
    && prev.msg.variant === next.msg.variant
    && prev.msg.content === next.msg.content
    && prev.msg.isStreaming === next.msg.isStreaming
})

export default function MessageList({ messages }: Props) {
	const bottomRef = useRef<HTMLDivElement>(null)
	const prevLengthRef = useRef(0)
	const lastStreamingScrollAtRef = useRef(0)
	const streamingScrollTimerRef = useRef<number | null>(null)

  // 새 메시지 추가 시에만 자동 스크롤 (chunk 업데이트 시에는 스킵)
  useEffect(() => {
    if (messages.length !== prevLengthRef.current) {
      prevLengthRef.current = messages.length
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [messages])

	// 스트리밍 중 마지막 메시지 업데이트 시 부드럽지 않은 즉시 스크롤
	useEffect(() => {
		const last = messages[messages.length - 1]
		if (last?.isStreaming) {
			const now = Date.now()
			const elapsed = now - lastStreamingScrollAtRef.current
			if (elapsed >= 120) {
				lastStreamingScrollAtRef.current = now
				bottomRef.current?.scrollIntoView({ behavior: 'auto' })
				return
			}
			if (streamingScrollTimerRef.current !== null) {
				window.clearTimeout(streamingScrollTimerRef.current)
			}
			const waitMs = 120 - elapsed
			streamingScrollTimerRef.current = window.setTimeout(() => {
				lastStreamingScrollAtRef.current = Date.now()
				bottomRef.current?.scrollIntoView({ behavior: 'auto' })
				streamingScrollTimerRef.current = null
			}, waitMs)
		}
	}, [messages])

	useEffect(() => {
		return () => {
			if (streamingScrollTimerRef.current !== null) {
				window.clearTimeout(streamingScrollTimerRef.current)
			}
		}
	}, [])

  if (messages.length === 0) {
    return (
      <div className="message-list empty">
        <p>Ask anything. Your conversation is preserved across sessions.</p>
      </div>
    )
  }

  return (
    <div className="message-list">
      {messages.map((msg) => (
        <MessageBubble key={msg.id} msg={msg} />
      ))}
      <div ref={bottomRef} />
    </div>
  )
}
