import { CliName } from '../bridge'
import geminiIcon from '../assets/agent-icons/gemini.svg'
import claudeIcon from '../assets/agent-icons/claude.svg'
import codexIcon from '../assets/agent-icons/codex.svg'

type AgentConfig = {
  label: string
  className: 'claude' | 'gemini' | 'codex'
  icon: string
}

const AGENT_CONFIG: Record<CliName, AgentConfig> = {
  claude: { label: 'Claude', className: 'claude', icon: claudeIcon },
  gemini: { label: 'Gemini', className: 'gemini', icon: geminiIcon },
  codex:  { label: 'Codex',  className: 'codex',  icon: codexIcon  },
}

export function getAgentMeta(cli: CliName): { label: string; className: string } {
  return AGENT_CONFIG[cli]
}

interface Props {
  cli: CliName
  size?: number
}

export default function AgentIcon({ cli, size = 14 }: Props) {
  return (
    <img
      src={AGENT_CONFIG[cli].icon}
      alt=""
      className="agent-icon-img"
      width={size}
      height={size}
      aria-hidden="true"
    />
  )
}
