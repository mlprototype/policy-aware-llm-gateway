# Sprint 3 Implementation Plan: Policy-Aware Multi-LLM Gateway

現 repo に合わせると、Sprint 3 は `ChatCompletionController -> ProviderRoutingService -> CircuitBreakerProviderInvoker -> LlmProvider` に整理するのが最も安全です。DB/Flyway 変更は不要で、差分は `router` `exception` `audit` `config` `provider` 周辺に閉じます。

## 1. Sprint 3 の実装方針

- 実装順序は、`failure taxonomy 固定 -> requested/resolved provider の契約固定 -> Circuit Breaker 導入 -> single-step fallback 実装 -> header/log/audit 強化 -> smoke test/README/CI` の順が良いです。
- 先に固定すべきものは 6 点です。`fallback は 1 段だけ`、`4xx は fallback しない`、`X-Gateway-Provider は response では resolved provider`、`request header 競合時ルール`、`fallback_reason enum`、`最終試行 failure で HTTP を決める規則` です。
- `ProviderRouter.resolve(null) -> OPENAI` の現挙動は Sprint 3 ではやめます。default provider 決定は router ではなく routing service 側に移します。
- Sprint 3 でやらないことは、retry 自動化、weighted routing、multi-step fallback、cost/latency aware routing、audit DB 永続化、dashboard、Prometheus/Grafana 連携です。
- README 上の表現は `high availability` ではなく `controlled degradation` に寄せます。

## 2. 推奨アーキテクチャ変更点

- Sprint 2 との差分は、`ProviderRouter` を provider registry に縮小し、実行オーケストレーションを `io.github.mlprototype.gateway.router.ProviderRoutingService` に移すことです。クラス名は Sprint 3 で `ProviderRegistry` または `ProviderResolver` へ rename して責務と名称を一致させることを推奨します。
- 追加クラスは `io.github.mlprototype.gateway.router.ProviderRoutingService`、`ProviderExecutionResult`、`FallbackPolicy`、`io.github.mlprototype.gateway.resilience.CircuitBreakerProviderInvoker`、`io.github.mlprototype.gateway.config.ProviderRoutingProperties`、`io.github.mlprototype.gateway.exception.ProviderFailureType`、`ProviderRoutingException`、`io.github.mlprototype.gateway.provider.ProviderFailureClassifier` を推奨します。
- `build.gradle` には `io.github.resilience4j:resilience4j-spring-boot3` を追加し、テスト用に `com.squareup.okhttp3:mockwebserver` を追加します。
- `ChatCompletionController` の責務は、header 解釈、`ProviderRoutingService` 呼び出し、response header 設定、audit 記録に限定します。provider failure の分類や fallback 判定は持たせません。
- `LlmProvider` 実装の責務は、provider 固有 request/response 変換と upstream 呼び出しまでです。fallback、breaker state 判定、最終 HTTP status 判定は持たせません。
- `ProviderException` は「単一 provider 試行の失敗」を表し、`ProviderRoutingException` は「routing 完了後の client-facing failure」を表す二層に分けます。
- `FallbackReason` は文字列ではなく enum にします。候補は `PRIMARY_TIMEOUT`, `PRIMARY_CONNECTION_ERROR`, `PRIMARY_UPSTREAM_5XX`, `PRIMARY_BREAKER_OPEN` です。
- `AuditEvent` は `requestedProvider` `resolvedProvider` `fallbackUsed` `fallbackReason` を追加し、既存 `provider` は Sprint 3 では `resolved_provider` の互換 alias として残すのが安全です。

## 3. Failure taxonomy 設計

| `ProviderFailureType` | fallback | breaker failure count | client-facing HTTP |
|---|---|---:|---:|
| `TIMEOUT` | yes | yes | 503 |
| `CONNECTION_ERROR` | yes | yes | 503 |
| `UPSTREAM_5XX` | yes | yes | 502 |
| `BREAKER_OPEN` | yes | no | 503 |
| `UPSTREAM_4XX` | no | no | 502 に正規化 |
| `INVALID_RESPONSE` | no | yes | 502 |

- `ProviderException` の保持項目は `providerType`, `failureType`, `upstreamStatusCode`, `message`, `cause` です。`isFallbackEligible()` は enum から導出します。
- `ProviderRoutingException` の保持項目は `requestedProvider`, `resolvedProvider`, `fallbackUsed`, `fallbackReason`, `statusCode`, `message` です。`GlobalExceptionHandler` はこの例外だけを client-facing に扱う形へ寄せます。
- provider 4xx は fallback 対象外です。理由は request 問題や provider 固有制約は provider を変えても解決しないためです。
- `INVALID_RESPONSE` は upstream schema drift と自前 mapper 不整合の両方を含み得るため、Sprint 3 では安全側で fallback 対象外に固定します。README にこの理由を明記します。
- 502/503 は「primary failure だけ」ではなく「最終 routing 結果」で決めます。primary と fallback が両方失敗した場合は最終試行（fallback 側）の failure type を client-facing status に使い、ログには primary/fallback 両方を残します。

## 4. Circuit Breaker 設計

- breaker は provider 単位で 2 つ持ちます。instance 名は `openai` と `anthropic` 固定で十分です。
- `application.yml` には `resilience4j.circuitbreaker.configs.llm-provider-default.*` と `resilience4j.circuitbreaker.instances.openai.baseConfig`, `instances.anthropic.baseConfig` を追加します。
- 初期値は `slidingWindowType=COUNT_BASED`, `slidingWindowSize=20`, `minimumNumberOfCalls=10`, `failureRateThreshold=50`, `waitDurationInOpenState=30s`, `permittedNumberOfCallsInHalfOpenState=3`, `automaticTransitionFromOpenToHalfOpenEnabled=true`, `registerHealthIndicator=true`, `writableStackTraceEnabled=false` を推奨します。
- `CircuitBreakerProviderInvoker` は `CircuitBreakerRegistry` を使って `provider.complete()` を wrap し、`CallNotPermittedException` を `ProviderException(BREAKER_OPEN)` に変換します。
- `recordException` は `TIMEOUT/CONNECTION_ERROR/UPSTREAM_5XX/INVALID_RESPONSE` のみ、`ignoreException` は `UPSTREAM_4XX` に限定します。slow-call breaker は Sprint 3 では入れません。
- timeout の発生源は RestClient の connect/read timeout に限定し、provider 実装側で HTTP client 例外を `ProviderException(TIMEOUT / CONNECTION_ERROR / ...)` に変換します。breaker 層は例外分類済みの `ProviderException` だけを見る構成にします。
- `CLOSED` は通常実行、`OPEN` は即 fallback 判定、`HALF_OPEN` は少数 probe を通し、失敗時は reopen して当該リクエストだけ fallback へ流します。
- テストは、state machine の unit test と、repeated 5xx で breaker open を確認する integration test を分けるのがよいです。時間依存を避けるため unit では `transitionToOpenState()` を使います。

## 5. Fallback routing 設計

- requested provider は `X-Gateway-Requested-Provider` を正規入力にし、`X-Gateway-Provider` は 1 sprint だけ legacy alias として受けます。どちらも無い場合は `gateway.provider.default` を使います。
- 両ヘッダが同時に来た場合は `X-Gateway-Requested-Provider` を優先します。不一致なら 400 を返します。`X-Gateway-Provider` を request で使った場合は deprecated warning を構造化ログへ出します。
- response header は `X-Gateway-Provider=resolved provider`、`X-Gateway-Requested-Provider=requested provider`、`X-Gateway-Fallback-Used=true|false` を固定します。
- fallback map は `OPENAI -> ANTHROPIC`、`ANTHROPIC -> OPENAI` の 1 段だけです。`FallbackPolicy` に閉じ込め、chain fallback は禁止します。
- fallback 条件は `TIMEOUT`, `CONNECTION_ERROR`, `UPSTREAM_5XX`, `BREAKER_OPEN` のみです。`UPSTREAM_4XX` と `INVALID_RESPONSE` は fallback しません。
- fallback 不可能は、alternate provider 未登録、alternate breaker open、alternate call failure、header 不正の 4 パターンです。header 不正は 400、それ以外は最終 failure type から 502/503 を決定します。
- `ProviderExecutionResult` は `requestedProvider`, `resolvedProvider`, `fallbackUsed`, `fallbackReason`, `ChatResponse response` を持たせます。成功時は controller がこれをそのまま header/audit に流します。
- fallback 発生時は `ProviderRoutingService` で `WARN` の構造化ログを 1 本出し、`requested_provider`, `resolved_provider`, `fallback_used`, `fallback_reason`, `primary_failure_type` を載せます。`fallback_reason` は enum 値を出力します。
- error 時も `ProviderRoutingException` に routing 情報を載せ、`GlobalExceptionHandler` から同じ header を返せる形にしておくと運用が楽です。

## 6. 実装タスク分解

| タスク名 | 目的 | 実装内容 | 完了条件 | 優先度 | 見積もり |
|---|---|---|---|---|---|
| Failure taxonomy refactor | 失敗判定の一元化 | `ProviderFailureType`、`ProviderException` 拡張、`ProviderFailureClassifier` 追加 | provider 失敗が enum で分類される | P0 | 1.0d |
| Routing contract fix | requested/resolved 分離 | `ProviderRoutingService`、`ProviderExecutionResult`、header 契約整理 | success/error 両方で routing 情報が取れる | P0 | 1.0d |
| Breaker integration | provider 単位 CB 導入 | Resilience4j 依存追加、`CircuitBreakerProviderInvoker`、`application.yml` 設定 | openai/anthropic 各 breaker が作動する | P0 | 1.0d |
| Single-step fallback | 可用性向上 | `FallbackPolicy` 実装、primary->fallback 1 回だけ実行 | timeout/5xx/open で fallback 成功 | P0 | 1.5d |
| Exception/HTTP mapping cleanup | 502/503 明確化 | `ProviderRoutingException`、`GlobalExceptionHandler` 改修 | 4xx/502/503 の振る舞いが固定される | P0 | 0.5d |
| Audit/log enhancement | degraded mode 可視化 | `AuditEvent`/`AuditLogger` 拡張、fallback warn log | audit に 4 項目が出る | P1 | 0.5d |
| Test expansion | 回帰防止 | unit/integration 追加、既存 `ProviderRouterTest` 更新 | 主要分岐が自動テスト化 | P0 | 1.5d |
| Smoke/README/CI | 運用確認 | README 更新、軽量 smoke（app 起動確認）追加、fallback 検証は integration に集約 | CI で重すぎず fallback 検証可能 | P1 | 0.5d |

## 7. テスト戦略

- unit test は `ProviderRoutingServiceTest`, `CircuitBreakerProviderInvokerTest`, `ProviderFailureClassifierTest`, `GlobalExceptionHandlerTest` を中心にします。観点は requested/resolved, fallback 条件, 4xx no fallback, breaker-open, final 502/503, header 競合 400, `fallback_reason` enum です。
- integration test は `@SpringBootTest(webEnvironment = RANDOM_PORT)` と `MockWebServer` 2 台で行い、OpenAI 側 503 -> Anthropic 200、OpenAI timeout -> Anthropic 200、OpenAI 400 -> no fallback、breaker open -> immediate fallback を HTTP レベルで確認します。
- smoke test は `docker compose` で app の起動確認と health 確認に限定します。`provider failure -> fallback success` の検証は integration test に集約します。
- 実 API 利用は CI から外します。通常 test/smoke は stub provider のみ、live provider 確認は手動の任意 run に残すのが妥当です。

## 8. リスクと対策

- Circuit Breaker 過検知: `minimumNumberOfCalls=10` と `COUNT_BASED` で初期過敏反応を抑え、4xx を `ignoreException` にします。slow-call 判定は Sprint 3 では入れません。
- fallback 条件の誤判定: `ProviderFailureClassifier` を 1 箇所に集約し、routing service 側で `failureType` のみを見る設計にします。
- provider 4xx の誤フォールバック: `UPSTREAM_4XX` を enum レベルで `fallback=false` に固定し、client-facing は 502 正規化で単純化します。gateway 自身の validation/auth/rate-limit 由来 4xx は gateway で返します。
- retry / timeout と breaker の相互作用: retry は Sprint 3 では入れません。1 provider 1 回、fallback 先 1 回までに固定し、timeout 分類は provider 層で確定させてから breaker に渡します。
- Sprint 3 のスコープ超過: DB 変更なし、metrics/dashboard なし、policy engine 連携なしを明示し、PR を `taxonomy`, `breaker`, `fallback`, `observability`, `tests/docs` の 5 つ程度に分けます。

## 9. Definition of Done

- `openai` と `anthropic` に対して独立した Circuit Breaker が存在し、設定値が `application.yml` に明示されている。
- success/error の両ケースで `requested provider` と `resolved provider` をコード上で区別できる。
- primary provider が `timeout / connection error / 5xx / breaker-open` のときのみ、1 回だけ fallback する。
- provider 4xx では fallback しない。少なくとも 400 と 429 の自動テストがある。
- response header に `X-Gateway-Provider`, `X-Gateway-Requested-Provider`, `X-Gateway-Fallback-Used` が反映される。
- structured audit/log に `requested_provider`, `resolved_provider`, `fallback_used`, `fallback_reason` が出る。
- fallback 不可能時に 502/503 が設計どおり返り、primary/fallback 両失敗時は最終試行 failure で status が決まることがテストで固定されている。
- integration test で `provider failure -> fallback success` が再現でき、README に `controlled degradation`、`INVALID_RESPONSE` の扱い理由、request header migration note が記載されている。
- CI で unit/integration は常時 green、smoke は app health の軽量確認として安定運用できる。
