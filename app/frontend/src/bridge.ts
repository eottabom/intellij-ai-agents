/**
 * Java ↔ JS 브릿지 래퍼
 * window.__bridge (JsBridge.java에서 주입)를 통해 Java와 통신
 */

export type CliName = 'claude' | 'gemini' | 'codex'
export type ChatMode = 'normal' | 'plan'
export interface ProjectRef { symbol: string; path: string; kind: 'class' | 'file' }
export interface AiModel { id: string; displayName: string }
export interface ModelsPayload { cli: CliName; models: AiModel[]; selected: string }

export type BridgeMessageType = 'chat' | 'cancel' | 'getSession' | 'clearSession' | 'clearAllSessions' | 'getProjectRefs' | 'getModels' | 'setModel' | 'openSettings'

interface BridgePayload {
  type: BridgeMessageType
  cli?: CliName
  prompt?: string
  mode?: ChatMode
  model?: string
}

declare global {
  interface Window {
    __bridge?: { send: (json: string) => void }
    __onInstalledClis?: (clis: unknown) => void
    __onChunk?: ((text: string) => void) & ((cli: CliName, text: string) => void)
    __onProgress?: ((text: string) => void) & ((cli: CliName, text: string) => void)
    __onDone?: (() => void) & ((cli: CliName) => void)
    __onError?: ((error: string) => void) & ((cli: CliName, error: string) => void)
    __onSession?: (cli: CliName, sessionId: string) => void
    __onSessionCleared?: (cli: CliName) => void
    __onProjectRefs?: (refs: ProjectRef[]) => void
    __onModels?: (payload: ModelsPayload) => void
    __onModelChanged?: (cli: CliName, modelId: string) => void
  }
}

function send(payload: BridgePayload) {
  const bridge = window.__bridge
  if (!bridge || typeof bridge.send !== 'function') {
    throw new Error('Bridge is not ready')
  }
  bridge.send(JSON.stringify(payload))
}

export const bridge = {
  /** 채팅 메시지 전송 */
  chat: (cli: CliName, prompt: string, mode: ChatMode = 'normal') =>
    send({ type: 'chat', cli, prompt, mode }),

  /** 현재 실행 취소 */
  cancel: (cli: CliName) =>
    send({ type: 'cancel', cli }),

  /** 세션 ID 조회 */
  getSession: (cli: CliName) =>
    send({ type: 'getSession', cli }),

  /** 세션 초기화 */
  clearSession: (cli: CliName) =>
    send({ type: 'clearSession', cli }),

  /** 모든 세션 초기화 */
  clearAllSessions: () =>
    send({ type: 'clearAllSessions' }),

  /** 프로젝트 참조 목록 조회 (# 자동완성용) */
  getProjectRefs: () =>
    send({ type: 'getProjectRefs' }),

  /** 프로바이더별 모델 목록 조회 */
  getModels: (cli: CliName) =>
    send({ type: 'getModels', cli }),

  /** 모델 변경 */
  setModel: (cli: CliName, model: string) =>
    send({ type: 'setModel', cli, model }),

  /** Settings 열기 */
  openSettings: () =>
    send({ type: 'openSettings' }),
}
