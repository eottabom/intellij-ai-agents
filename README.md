# IntelliJ AI Agents

IntelliJ IDEA 플러그인으로, **Claude, Gemini, Codex** CLI를 IDE 내부에서 직접 사용할 수 있는 멀티 AI 에이전트 채팅 도구입니다.

## Features

- **멀티 AI 에이전트 지원** — Claude, Gemini, Codex CLI를 하나의 인터페이스에서 전환하며 사용
- **`@mention` 시스템** — `@claude`, `@gemini`, `@codex`로 특정 에이전트 지정, `@all`로 모든 에이전트에 동시 질문
- **Plan 모드** — `/plan` 명령으로 실행 계획만 요청하는 모드 전환
- **스트리밍 응답** — 각 CLI의 JSON 스트림 출력을 실시간 파싱하여 표시
- **세션 관리** — CLI별 세션 ID를 저장하여 대화 컨텍스트 유지 (`--resume`)
- **프로젝트 참조 자동완성** — `#`으로 프로젝트 내 파일/클래스를 자동완성
- **`/doctor` 헬스체크** — CLI 설치 및 인증 상태 확인
- **마크다운 렌더링** — 코드 블록 구문 강조, 복사 버튼 지원
- **JCEF 기반 UI** — React + TypeScript 프론트엔드를 IntelliJ 내장 브라우저에서 렌더링
- **CLI 자동 감지** — 시작 시 설치된 CLI를 자동 감지하여 사용 가능한 에이전트만 표시

## Architecture

```
┌─────────────────────────────────────────────┐
│  IntelliJ IDEA Plugin                       │
│                                             │
│  ┌─────────────┐     ┌────────────────────┐ │
│  │ ToolWindow  │     │  Settings          │ │
│  │ Factory     │     │  (refs config,     │ │
│  │             │     │   ignored dirs,    │ │
│  │             │     │   CLI flags,       │ │
│  │             │     │   timeouts)        │ │
│  └──────┬──────┘     └────────────────────┘ │
│         │                                   │
│  ┌──────▼──────┐     ┌────────────────────┐ │
│  │ AiAgentPanel│────▶│ JsBridge           │ │
│  │ (JCEF)      │     │ (Java ↔ JS)        │ │
│  └─────────────┘     └────────┬───────────┘ │
│                               │             │
│                      ┌────────▼───────────┐ │
│                      │ ChatCommandHandler │ │
│                      └────────┬───────────┘ │
│                               │             │
│  ┌────────────────────────────▼───────────┐ │
│  │ AiProvider (enum)                      │ │
│  │  ├─ CLAUDE  ─┐                         │ │
│  │  ├─ GEMINI  ─┼─▶ ProcessRunner         │ │
│  │  └─ CODEX   ─┘   (subprocess mgmt)     │ │
│  └────────────────────────────────────────┘ │
│                                             │
│  ┌────────────────────────────────────────┐ │
│  │ React Frontend (JCEF WebView)          │ │
│  │  ChatPanel → MessageList + InputBar    │ │
│  │  bridge.ts ↔ window.__bridge           │ │
│  └────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| IDE Platform | IntelliJ Platform SDK 2025.1 |
| Backend | Java 21 |
| Frontend | React 18, TypeScript, Vite |
| UI Rendering | JCEF (Chromium Embedded Framework) |
| Serialization | Gson |
| Build | Gradle (Kotlin DSL) |

## Prerequisites

각 AI CLI 도구가 시스템에 설치되어 있어야 합니다:

- [Claude Code](https://docs.anthropic.com/en/docs/claude-code) — `npm install -g @anthropic-ai/claude-code`
- [Gemini CLI](https://github.com/google-gemini/gemini-cli) — `npm install -g @anthropic-ai/gemini-cli`
- [Codex CLI](https://github.com/openai/codex) — OpenAI Codex CLI

## Build & Run

```bash
# 프론트엔드 빌드 + 플러그인 빌드
./gradlew buildPlugin

# IntelliJ sandbox에서 실행
./gradlew runIde
```

## Usage

1. IntelliJ 우측 Tool Window에서 **AI Agents** 패널 열기
2. `@claude 안녕하세요` — Claude에게 질문
3. `@gemini 이 코드 리뷰해줘` — Gemini에게 질문
4. `@all 이 버그 원인이 뭐야?` — 모든 에이전트에 동시 질문
5. `/plan` — Plan 모드 활성화 (실행 계획만 요청)
6. `/doctor` — CLI 상태 확인
7. `#ClassName` — 프로젝트 파일 자동완성

## Slash Commands

| Command | Description |
|---------|-------------|
| `/plan` | Plan 모드 활성화 — 실행 계획만 요청하는 모드로 전환 |
| `/plan on` | Plan 모드 켜기 |
| `/plan off` | Plan 모드 끄기 |
| `/doctor` | CLI 헬스체크 — 설치 및 인증 상태 확인 |
| `/session` | 모든 CLI의 현재 세션 상태 조회 |
| `/clearall` | 모든 CLI의 저장된 세션 초기화 |

## Session Management

각 CLI는 대화 컨텍스트를 유지하기 위해 세션 ID를 자동으로 관리합니다:

- **자동 저장** — CLI 응답이 완료되면 세션 ID가 자동 저장됩니다
- **세션 재개** — 다음 메시지 전송 시 `--resume <sessionId>` 플래그로 이전 대화를 이어갑니다
- **세션 조회** — `/session` 명령으로 각 CLI의 활성 세션 ID를 확인할 수 있습니다
- **세션 초기화** — `/clearall` 명령으로 모든 세션을 초기화하여 새 대화를 시작합니다

## Settings

**Settings > Tools > AI Agents** 에서 다음 설정을 변경할 수 있습니다:

| Setting | Default | Description |
|---------|---------|-------------|
| Project refs config path | `.aiagents/refs-config.json` | 프로젝트 참조 설정 파일 경로 (프로젝트 루트 기준 상대경로) |
| Extra excluded directories | *(empty)* | 자동완성 스캔 제외 디렉토리 (쉼표 또는 줄바꿈 구분) |
| Claude: --dangerously-skip-permissions | `false` | Claude CLI 권한 프롬프트 생략 |
| Codex: --dangerously-bypass-approvals-and-sandbox | `false` | Codex CLI 승인/샌드박스 우회 |
| Gemini: --approval-mode yolo --no-sandbox | `false` | Gemini CLI 자동 승인 모드 |
| Timeout (Claude / Gemini / Codex) | `180` / `60` / `30` 초 | CLI별 응답 타임아웃 |
| Project refs scan depth | `6` | 프로젝트 파일 스캔 최대 깊이 |

## Project Refs (`#` 자동완성)

`#`을 입력하면 프로젝트 내 파일/클래스 이름을 자동완성할 수 있습니다. 프롬프트에 `#ClassName`을 삽입하면 해당 파일의 컨텍스트를 AI에게 전달하는 데 활용됩니다.

### 기본 제외 디렉토리

다음 디렉토리는 기본적으로 스캔에서 제외됩니다:

`.git`, `.idea`, `.gradle`, `.intellijplatform`, `build`, `dist`, `node_modules`, `target`, `out`

### 추가 제외 방법

#### 방법 1: Settings UI

**Settings > Tools > AI Agents > Extra excluded directories** 에 제외할 디렉토리를 쉼표 또는 줄바꿈으로 입력합니다.

```
coverage, tmp, generated
vendor/legacy
```

#### 방법 2: 프로젝트 설정 파일

프로젝트 루트에 `.aiagents/refs-config.json` 파일을 생성합니다 (경로는 Settings에서 변경 가능):

```json
{
  "ignoreDirs": ["coverage", "tmp", "generated"],
  "excludeDirs": ["vendor/legacy", "scripts/deprecated"]
}
```

`ignoreDirs`와 `excludeDirs` 모두 동일하게 동작하며, 두 필드를 함께 사용할 수 있습니다.

### 스캔 대상 확장자

`java`, `kt`, `kts`, `ts`, `tsx`, `js`, `jsx`, `json`, `md`, `xml`, `yml`, `yaml`, `properties`

해시 번들(`*-AbCdEf.js`)과 minified 파일(`*.min.js`)은 자동으로 제외됩니다.

## Project Structure

```
app/
├── src/main/java/io/github/eottabom/aiagents/
│   ├── providers/          # AI CLI 프로세스 관리
│   │   ├── AiProvider.java           # 프로바이더 enum (CLAUDE, GEMINI, CODEX)
│   │   ├── AiProviderArgsBuilder.java # CLI 인자 생성
│   │   ├── AiProviderParsers.java     # JSON 스트림 파싱
│   │   ├── AiProviderProcessRunner.java # 프로세스 실행/타임아웃/취소
│   │   ├── AiProviderJsonUtils.java   # JSON 유틸리티
│   │   ├── StreamChunk.java           # 스트림 청크 record
│   │   └── ChunkType.java            # 청크 타입 enum
│   ├── settings/           # 플러그인 설정
│   │   ├── AiAgentSettings.java       # 영속 설정 (refs config, ignored dirs, CLI flags, timeouts)
│   │   └── AiAgentSettingsConfigurable.java # 설정 UI
│   └── toolwindow/         # Tool Window UI
│       ├── AiAgentToolWindowFactory.java  # Tool Window 팩토리 (CLI 자동 감지)
│       ├── AiAgentPanel.java          # JCEF 브라우저 패널
│       ├── JsBridge.java             # Java ↔ JS 브릿지
│       ├── ChatCommandHandler.java   # 채팅 명령 처리 로직
│       ├── JsBridgeClientNotifier.java # JS 콜백 호출
│       ├── BridgeMessage.java        # 브릿지 메시지 record
│       ├── SessionStore.java         # 세션 ID 저장소
│       └── ProjectRefsCollector.java  # 프로젝트 파일 스캔
├── frontend/               # React 프론트엔드
│   └── src/
│       ├── App.tsx
│       ├── bridge.ts                  # Java 브릿지 래퍼
│       ├── utils/
│       │   └── commandParser.ts       # 명령어 파싱 유틸리티
│       ├── hooks/
│       │   ├── useBridgeCallbacks.ts  # 브릿지 콜백 훅
│       │   └── useAutocomplete.ts     # 자동완성 훅
│       └── components/
│           ├── ChatPanel.tsx          # 채팅 메인 패널
│           ├── MessageList.tsx        # 메시지 목록 + 마크다운 렌더링
│           ├── InputBar.tsx           # 입력바 + 자동완성
│           └── AgentIcon.tsx          # 에이전트 아이콘
└── build.gradle.kts
