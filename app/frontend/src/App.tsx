import { useEffect, useState } from 'react'
import { CliName } from './bridge'
import ChatPanel from './components/ChatPanel'
import './index.css'

export default function App() {
  const [installedClis, setInstalledClis] = useState<CliName[]>([])

  useEffect(() => {
    window.__onInstalledClis = (clis) => {
      setInstalledClis(clis)
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
