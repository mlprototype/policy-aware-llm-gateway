# Policy-Aware Multi-LLM Gateway

LLM / Agent 呼び出しを本番運用するための **運用統治レイヤー（Gateway）**。
アプリケーションと LLM プロバイダの間に配置し、認証、プロバイダ抽象化、レート制御、可観測性、可用性を一元的に扱う。

> **ポートフォリオ第 3 弾** — 第 1 弾 [Agentic Control Plane](https://github.com/mlprototype)（動的制御） / 第 2 弾 [Spec RAG QA](https://github.com/mlprototype)（静的品質保証） に続く運用統治レイヤー。

---
## Why

LLM/Agent を本番で運用する際、コスト暴走・プロバイダ障害・PII 流出・監査要件
への対応が必要になる。本プロジェクトは、これらを横断的に統治する Gateway 層を、
Spring Boot / Flyway / Redis / structured logging を土台に段階的に実装する設計探索である。

---


## Architecture

```mermaid
graph TD
    subgraph Client Layer
        C[Client Application]
    end

    subgraph Gateway Filter Chain
        F1[TraceIdFilter] --> F2[LatencyFilter]
        F2 --> F3[ApiKeyFilter]
        F3 --> F4[RateLimitFilter]
    end

    subgraph Core Logic
        Auth[AuthenticationService<br/>DB / SHA-256]
        RL[RateLimiter<br/>Redis]
        Ctrl[ChatCompletionController]
        Router[ProviderRouter]
        Audit["AuditLogger<br/>(log only / Sprint4でDB永続化)"]
    end

    subgraph Provider Layer
        OpenAI[OpenAiProvider]
        Anthropic[AnthropicProvider]
    end

    C -->|HTTP POST| F1
    F3 -.->|Validate & Fetch Context| Auth
    F4 -.->|Check Window| RL
    F4 -->|Authenticated & Allowed| Ctrl
    Ctrl --> Router
    Router --> OpenAI
    Router --> Anthropic
    OpenAI <-->|HTTPS| ExtO[(OpenAI API)]
    Anthropic <-->|HTTPS| ExtA[(Anthropic API)]

    Ctrl -.->|Audit Event| Audit
    OpenAI -.->|Usage Data| Audit
    Anthropic -.->|Usage Data| Audit
```

**Response Headers** — 全レスポンスに Gateway 拡張ヘッダが付与されます:

| Header | Description |
|:---|:---|
| `X-Gateway-Trace-Id` | リクエスト固有の UUID（MDC でログに連携） |
| `X-Gateway-Latency-Ms` | Gateway 内の処理時間 (ms) |
| `X-Gateway-Provider` | **実解決値** (ルーティング後に実際に使用されたプロバイダ名) |
| `X-RateLimit-Limit` | テナントごとの 1 分間あたりのリクエスト上限 |
| `X-RateLimit-Remaining` | 現在の 1 分間における残りリクエスト可能数 |

---

## Design Decisions

| 判断 | 選定 | Why |
|:---|:---|:---|
| Web 層 | Spring MVC + Virtual Threads | リアクティブの複雑性回避、JPA/Redis 親和性 |
| API I/F | OpenAI 互換 | 既存 SDK 流用、ロックイン回避 |
| Provider 抽象化 | Interface + Mapper | LLM プロバイダ追加を低コスト化 |
| 認証方式 (Sprint 2) | DB (SHA-256) | deterministic hash による lookup simplicity を優先。stronger secret rotation / vault integration は future work。 |
| レートリミット (Sprint 2) | Redis Fixed-Window | Fail-open 設計 (Redis 障害時でもリクエストをブロックしない)。Retry-After は現状 60 秒固定 (将来 window 残り時間へ改善可能)。 |
| ビルドツール | Gradle (Groovy DSL) | Spring Boot 標準、CI キャッシュ親和性 |
---


## Tech Stack

| Component | Technology |
|:---|:---|
| Language | Java 21 (Virtual Threads) |
| Framework | Spring Boot 3.5.14 |
| Web | Spring MVC + Virtual Threads |
| HTTP Client | RestClient |
| Database | PostgreSQL 16 + Flyway |
| Cache | Redis 7 |
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
# === Gateway Authentication ===
# DB投入済みの Dev API Key を使用 (V2_1__seed_dev_tenant.sql 参照)
GATEWAY_API_KEY=dev-gateway-key-001

# === LLM Provider ===
OPENAI_API_KEY=sk-proj-xxxxxxxxxxxxx        # OpenAI API Key
ANTHROPIC_API_KEY=sk-ant-xxxxxxxxxxxxxxxx   # Anthropic API Key
```

### 2. Run with Docker Compose

```bash
docker compose up --build -d
```

- `app` (Gateway 本体) — port 8080
- `postgres` (PostgreSQL 16) — port 5433
- `redis` (Redis 7) — port 6379

### 3. Smoke Test

```bash
# ✅ OpenAI Proxy — 正常リクエスト
source .env
curl -s http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $GATEWAY_API_KEY" \
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

PostgreSQL and Redis must be running locally before starting the application.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

---

## API Reference

### `POST /v1/chat/completions`

OpenAI Chat Completions API 互換エンドポイント。

**Headers:**

| Header | Required | Description |
|:---|:---|:---|
| `X-API-Key` | ✅ | Gateway 認証キー (DBのテナントと紐付け) |
| `X-Gateway-Provider` | ❌ | 使用プロバイダの**要求値** (`openai` または `anthropic` / default: `openai`) |
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

### HTTP Semantics

- `401 Unauthorized`: missing or invalid API key
- `403 Forbidden`: authenticated, but tenant is suspended
- `429 Too Many Requests`: tenant rate limit exceeded

---

## Project Structure

```text
src/main/java/io/github/mlprototype/gateway/
├── api/              # REST Controllers
├── audit/            # Structured audit logging
├── config/           # RestClient, Jackson configuration
├── dto/              # Request / Response DTOs
├── exception/        # Global exception handler
├── filter/           # Servlet filters (TraceId, Latency, ApiKey, RateLimit)
├── provider/         # LLM Provider abstraction
│   ├── openai/       # OpenAI implementation
│   └── anthropic/    # Anthropic implementation
├── ratelimit/        # Redis-based fixed-window rate limiting
├── router/           # Provider routing logic
├── security/         # Tenant / API client authentication
└── policy/           # (Sprint 3+: policy engine)
```

---

## Configuration

主要な設定値 (`application.yml`):

| Property | Default | Description |
|:---|:---|:---|
| `gateway.provider.openai.api-key` | env `OPENAI_API_KEY` | OpenAI API Key |
| `gateway.provider.openai.default-model` | `gpt-4o-mini` | OpenAI デフォルトモデル |
| `gateway.provider.anthropic.api-key` | env `ANTHROPIC_API_KEY` | Anthropic API Key |
| `gateway.provider.anthropic.default-model` | `claude-3-haiku-20240307` | Anthropic デフォルトモデル |
| `spring.data.redis.host` | `localhost` / `redis` | Redis host |
| `spring.data.redis.port` | `6379` | Redis port |
| `spring.threads.virtual.enabled` | `true` | Virtual Threads 有効化 |

---

## Testing

```bash
# Unit + Integration tests (mock-based, no API call)
./gradlew test

# E2E smoke test (requires Docker + real API key)
docker compose up -d
source .env

# 1. OpenAI (Default)
curl -s http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $GATEWAY_API_KEY" \
  -d '{"messages":[{"role":"user","content":"ping"}],"max_tokens":5}'

# 2. Anthropic
curl -s http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $GATEWAY_API_KEY" \
  -H "X-Gateway-Provider: anthropic" \
  -d '{"messages":[{"role":"user","content":"What is 2+2? Keep it short."}],"max_tokens":10}'

# 3. Suspended tenant (403)
curl -s http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: suspended-key-001" \
  -d '{"messages":[{"role":"user","content":"Hello"}]}'

# 4. Rate limit exceeded (429)
for i in {1..5}; do
  curl -i -s http://localhost:8080/v1/chat/completions \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $GATEWAY_API_KEY" \
    -d '{"messages":[{"role":"user","content":"ping"}],"max_tokens":5}'
done
```

---

## Sprint Roadmap

| Sprint | Focus | Status |
|:---|:---|:---|
| **1** | Gateway 骨格, OpenAI proxy, API Key 認証, trace/audit, Docker | ✅ Done |
| **2** | Anthropic provider, tenant 認証 (DB), rate limiting, Redis | ✅ Done |
| 3 | Circuit Breaker (Resilience4j), fallback routing | — |
| 4 | PII masking, prompt injection detection, audit DB 永続化 | — |
| 5 | Prometheus / Grafana dashboard, OpenTelemetry | — |

---

## Current Status

Sprint 2 で実装済み:

- **Multi-provider support**: OpenAI / Anthropic の 2 Provider に対応
- **Tenant-based authentication**: DB (`tenants`, `api_clients`) と SHA-256 hash による API key 認証
- **Redis-based rate limiting**: tenant 単位の fixed-window rate limiting（fail-open 設計）

Not yet implemented in Sprint 2:

- fallback routing (Sprint 3)
- circuit breaker (Sprint 3)
- audit log persistence (Sprint 4)
- PII masking / prompt injection detection (Sprint 4)

---

## License

This repository is published for portfolio purposes only.
Reuse or redistribution is not permitted without prior permission.