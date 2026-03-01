import { ChatMode, CliName } from '../bridge'

export type Target = CliName | 'all'

export interface AgentCommandResult {
  target: Target
  prompt: string
  switched: boolean
}

export interface PlanCommandResult {
  modeChanged: ChatMode | null
  prompt: string
}

export interface SessionCommandResult {
  clearAllSessions: boolean
  prompt: string
}

export function parseAgentCommand(raw: string, fallbackCli: CliName): AgentCommandResult {
  const match = raw.match(/^@(claude|gemini|codex|all)\b\s*/i)
  if (!match) {
    return { target: fallbackCli as Target, prompt: raw, switched: false }
  }

  const target = match[1].toLowerCase() as Target
  const prompt = raw.slice(match[0].length).trim()
  return { target, prompt, switched: target !== fallbackCli && target !== 'all' }
}

export function parsePlanCommand(raw: string): PlanCommandResult {
  const trimmed = raw.trim()
  const match = trimmed.match(/^\/plan(?:\s+(on|off))?(?:\s+([\s\S]*))?$/i)
  if (!match) return { modeChanged: null, prompt: raw }

  const modeToken = match[1]?.toLowerCase()
  const remainder = (match[2] ?? '').trim()
  if (modeToken === 'off') return { modeChanged: 'normal', prompt: remainder }
  return { modeChanged: 'plan', prompt: remainder }
}

export function parseSessionCommand(raw: string): SessionCommandResult {
  const trimmed = raw.trim()
  if (/^\/clearall(?:\s+sessions?)?$/i.test(trimmed)) {
    return { clearAllSessions: true, prompt: '' }
  }
  return { clearAllSessions: false, prompt: raw }
}
