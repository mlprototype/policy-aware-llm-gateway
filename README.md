# Policy-Aware Multi-LLM Gateway

LLM / Agent 呼び出しを本番運用するための **運用統治レイヤー（Gateway）**。
アプリケーションと LLM プロバイダの間に配置し、認証・コスト制御・可観測性・可用性を一元的に統治する。

> **ポートフォリオ第 3 弾** — 第 1 弾 [Agentic Control Plane](https://github.com/mlprototype)（動的制御） / 第 2 弾 [Spec RAG QA](https://github.com/mlprototype)（静的品質保証） に続く運用統治レイヤー。

---

## Architecture

```
Client
  │
  ▼
┌─────────────────────────────────────────────────┐
│                  Filter Chain                    │
│  TraceIdFilter → LatencyFilter → ApiKeyFilter    │
└──────────────────────┬──────────────────────────┘
                       │ authenticated
                       ▼
              ChatCompletionController
              POST /v1/chat/completions
                       │
                       ▼
                 ProviderRouter
                       │
                       ▼
                 OpenAiProvider ──── RestClient ──── OpenAI API
                       │
                       ▼
                  AuditLogger
              (structured JSON log)
```

**Response Headers** — 全レスポンスに Gateway 拡張ヘッダが付与されます:

| Header | Description |
|:---|:---|
| `X-Gateway-Trace-Id` | リクエスト固有の UUID（MDC でログに連携） |
| `X-Gateway-Latency-Ms` | Gateway 内の処理時間 (ms) |
| `X-Gateway-Provider` | 実際に使用された LLM プロバイダ名 |

---

## Tech Stack

| Component | Technology |
|:---|:---|
| Language | Java 21 (Virtual Threads) |
| Framework | Spring Boot 3.5.14 |
| Web | Spring MVC + Virtual Threads |
| HTTP Client | RestClient |
| Database | PostgreSQL 16 + Flyway |
| Logging | Logback + logstash-logback-encoder (structured JSON) |
| Build | Gradle 8.14 |
| Container | Docker Compose |
| CI | GitHub Actions |

---

## Quick Start

### Prerequisites

- Java 21
- Docker & Docker Compose

### 1. Setup

```bash
git clone https://github.com/mlprototype/policy-aware-llm-gateway.git
cd policy-aware-llm-gateway

cp .env.example .env
```

`.env` を編集して API Key を設定:

```dotenv
GATEWAY_API_KEY=your-gateway-api-key-here   # Gateway へのアクセスキー（任意の文字列）
OPENAI_API_KEY=sk-proj-xxxxxxxxxxxxx        # OpenAI API Key
```

### 2. Run with Docker Compose

```bash
docker compose up --build -d
```

- `app` (Gateway 本体) — port 8080
- `postgres` (PostgreSQL 16) — port 5433

### 3. Smoke Test

```bash
# ✅ OpenAI Proxy — 正常リクエスト
curl -s http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-gateway-api-key-here" \
  -d '{
    "messages": [{"role": "user", "content": "Say hello in one word"}],
    "max_tokens": 10
  }' | jq .

# → {"id":"chatcmpl-...","model":"gpt-4o-mini-2024-07-18",
#    "choices":[{"message":{"content":"Hello!"}}]}

# ❌ Auth Failure — API Key なし
curl -s http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"messages": [{"role": "user", "content": "Hello"}]}' | jq .

# → {"status":401,"error":"Unauthorized","message":"Invalid or missing API key",
#    "trace_id":"..."}

# 💚 Health Check — 認証不要
curl -s http://localhost:8080/actuator/health | jq .

# → {"status":"UP"}
```

### Run Locally (without Docker)

```bash
# PostgreSQL を別途起動した上で:
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

---

## API Reference

### `POST /v1/chat/completions`

OpenAI Chat Completions API 互換エンドポイント。

**Headers:**

| Header | Required | Description |
|:---|:---|:---|
| `X-API-Key` | ✅ | Gateway 認証キー |
| `X-Gateway-Provider` | ❌ | 使用プロバイダの指定 (default: `openai`) |
| `X-Request-Id` | ❌ | クライアント指定のトレース ID |

**Request Body:**

```json
{
  "model": "gpt-4o-mini",
  "messages": [
    {"role": "system", "content": "You are helpful."},
    {"role": "user", "content": "Hello!"}
  ],
  "temperature": 0.7,
  "max_tokens": 1024
}
```

**Cost Safety:** `max_tokens` は Gateway 側で上限 4096 にクランプされます。

---

## Project Structure

```
src/main/java/io/github/mlprototype/gateway/
├── api/              # REST Controllers
├── audit/            # Structured audit logging
├── config/           # RestClient, Jackson configuration
├── dto/              # Request / Response DTOs
├── exception/        # Global exception handler
├── filter/           # Servlet filters (TraceId, Latency, ApiKey)
├── provider/         # LLM Provider abstraction
│   └── openai/       #   OpenAI implementation (RestClient)
├── router/           # Provider routing logic
├── security/         # (Sprint 2: tenant auth)
└── policy/           # (Sprint 2+: policy engine)
```

---

## Configuration

主要な設定値 (`application.yml`):

| Property | Default | Description |
|:---|:---|:---|
| `gateway.security.api-key` | env `GATEWAY_API_KEY` | Gateway 認証キー |
| `gateway.provider.openai.api-key` | env `OPENAI_API_KEY` | OpenAI API Key |
| `gateway.provider.openai.default-model` | `gpt-4o-mini` | デフォルトモデル |
| `gateway.provider.openai.max-tokens-limit` | `4096` | max_tokens 上限 |
| `gateway.provider.openai.timeout-seconds` | `30` | HTTP タイムアウト |
| `spring.threads.virtual.enabled` | `true` | Virtual Threads 有効化 |

---

## Testing

```bash
# Unit + Integration tests (mock-based, no API call)
./gradlew test

# E2E smoke test (requires Docker + real API key)
docker compose up -d
source .env
curl -s http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $GATEWAY_API_KEY" \
  -d '{"messages":[{"role":"user","content":"ping"}],"max_tokens":5}'
```

---

## Sprint Roadmap

| Sprint | Focus | Status |
|:---|:---|:---|
| **1** | Gateway 骨格, OpenAI proxy, API Key 認証, trace/audit, Docker | ✅ Done |
| 2 | Anthropic provider, tenant 認証 (DB), rate limiting, Redis | 🔜 |
| 3 | Circuit Breaker (Resilience4j), fallback routing | — |
| 4 | PII masking, prompt injection detection, audit DB 永続化 | — |
| 5 | Prometheus / Grafana dashboard, OpenTelemetry | — |

---

## License

Private — Portfolio Project