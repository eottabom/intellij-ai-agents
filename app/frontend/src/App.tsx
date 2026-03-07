import { useEffect, useState } from 'react'
import { CliName } from './bridge'
import ChatPanel from './components/ChatPanel'
import './index.css'

function isCliName(value: unknown): value is CliName {
  return value === 'claude' || value === 'gemini' || value === 'codex'
}

function sanitizeInstalledClis(value: unknown): CliName[] {
  if (!Array.isArray(value)) {
    return []
  }
  return value.filter(isCliName)
}

export default function App() {
  const [installedClis, setInstalledClis] = useState<CliName[]>([])

  useEffect(() => {
    window.__onInstalledClis = (clis) => {
      setInstalledClis(sanitizeInstalledClis(clis))
    }

    window.dispatchEvent(new Event('webviewReady'))

    return () => {
      window.__onInstalledClis = undefined
    }
  }, [])

  if (installedClis.length === 0) {
    return (
      <div className="empty-state">
        <p>No AI CLI tools detected.</p>
        <p>Install <code>claude</code>, <code>gemini</code>, or <code>codex</code> and restart.</p>
      </div>
    )
  }

  return (
    <div className="app">
      <ChatPanel installedClis={installedClis} />
    </div>
  )
}
