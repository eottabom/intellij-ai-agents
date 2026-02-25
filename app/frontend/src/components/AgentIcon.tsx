import { CliName } from '../bridge'
import geminiIcon from '../assets/agent-icons/gemini.svg'
import claudeIcon from '../assets/agent-icons/claude.svg'
import codexIcon from '../assets/agent-icons/codex.svg'

type AgentMeta = {
  label: string
  className: 'claude' | 'gemini' | 'codex'
}

export function getAgentMeta(cli: CliName): AgentMeta {
  switch (cli) {
    case 'claude':
      return { label: 'Claude', className: 'claude' }
    case 'gemini':
      return { label: 'Gemini', className: 'gemini' }
    case 'codex':
      return { label: 'Codex', className: 'codex' }
  }
}

interface Props {
  cli: CliName
  size?: number
}

export default function AgentIcon({ cli, size = 14 }: Props) {
  const src =
    cli === 'gemini' ? geminiIcon :
    cli === 'claude' ? claudeIcon :
    codexIcon

  return (
    <img
      src={src}
      alt=""
      className="agent-icon-img"
      width={size}
      height={size}
      aria-hidden="true"
    />
  )
}
