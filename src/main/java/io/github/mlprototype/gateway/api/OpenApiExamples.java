package io.github.mlprototype.gateway.api;

/**
 * Request and response examples used by the generated OpenAPI document.
 */
final class OpenApiExamples {

    static final String CHAT_REQUEST_OPENAI = """
            {
              "model": "gpt-4o-mini",
              "messages": [
                {
                  "role": "system",
                  "content": "あなたは簡潔に回答するカスタマーサポートアシスタントです。"
                },
                {
                  "role": "user",
                  "content": "ログイン時に認証エラーが表示されます。確認方法を教えてください。"
                }
              ],
              "temperature": 0.2,
              "max_tokens": 512
            }
            """;

    static final String CHAT_REQUEST_ANTHROPIC = """
            {
              "model": "claude-haiku-4-5-20251001",
              "messages": [
                {
                  "role": "system",
                  "content": "あなたは簡潔に回答するカスタマーサポートアシスタントです。"
                },
                {
                  "role": "user",
                  "content": "ログイン時に認証エラーが表示されます。確認方法を教えてください。"
                }
              ],
              "temperature": 0.2,
              "max_tokens": 512
            }
            """;

    static final String CHAT_COMPLETION_SUCCESS = """
            {
              "id": "chatcmpl-9f4c2a7b",
              "object": "chat.completion",
              "created": 1783163647,
              "model": "gpt-4o-mini",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "こんにちは。私は質問への回答や文章作成をお手伝いするAIアシスタントです。"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 18,
                "completion_tokens": 24,
                "total_tokens": 42
              }
            }
            """;

    static final String BAD_REQUEST_VALIDATION = """
            {
              "status": 400,
              "error": "Bad Request",
              "message": "messages: messages must not be empty",
              "timestamp": 1783163647000,
              "trace_id": "4de44b4d-ec10-4f23-96a7-d351d8d93bf8"
            }
            """;

    static final String BAD_REQUEST_PII_BLOCK = """
            {
              "status": 400,
              "error": "Bad Request",
              "message": "Request blocked due to security policy: PII_DETECTED",
              "timestamp": 1783163647000,
              "trace_id": "e9b933cc-0dc8-4925-b971-17b2cf24fbbb"
            }
            """;

    static final String BAD_REQUEST_PROVIDER_MODEL = """
            {
              "status": 400,
              "error": "Bad Request",
              "message": "Model 'gpt-4o-mini' is not compatible with provider 'anthropic'",
              "timestamp": 1783163647000,
              "trace_id": "0adabb34-d7e5-4bdc-a95d-dfc1d3ea06bc"
            }
            """;

    static final String BAD_REQUEST_ANTHROPIC_MESSAGES = """
            {
              "status": 400,
              "error": "Bad Request",
              "message": "Anthropic requests require at least one user or assistant message",
              "timestamp": 1783163647000,
              "trace_id": "860db2af-3f38-40ae-a23f-8a7405504ed1"
            }
            """;

    static final String UNAUTHORIZED = """
            {
              "status": 401,
              "error": "Unauthorized",
              "message": "Invalid or missing API key",
              "timestamp": 1783163647000,
              "trace_id": "021cb519-6cf2-4c65-8e15-c94a1446651a"
            }
            """;

    static final String FORBIDDEN_TENANT_SUSPENDED = """
            {
              "status": 403,
              "error": "Forbidden",
              "message": "Tenant is suspended",
              "timestamp": 1783163647000,
              "trace_id": "238553f0-c628-4ba6-a46a-b97d5e7d33fc"
            }
            """;

    static final String FORBIDDEN_INJECTION_BLOCK = """
            {
              "status": 403,
              "error": "Forbidden",
              "message": "Request blocked due to security policy: INJECTION_DETECTED",
              "timestamp": 1783163647000,
              "trace_id": "03aaae6c-2f5b-478c-9cce-d4f37ecb1285"
            }
            """;

    static final String RATE_LIMIT_EXCEEDED = """
            {
              "status": 429,
              "error": "Too Many Requests",
              "message": "Rate limit exceeded. Limit: 60 requests/min",
              "timestamp": 1783163647000,
              "trace_id": "2935840f-479f-4e85-a776-8baf3f9bd486"
            }
            """;

    static final String BAD_GATEWAY_UPSTREAM = """
            {
              "status": 502,
              "error": "Bad Gateway",
              "message": "openai server error: 500",
              "timestamp": 1783163647000,
              "trace_id": "97122898-c810-48ba-a45f-49f0be5c2e12"
            }
            """;

    static final String BAD_GATEWAY_INVALID_RESPONSE = """
            {
              "status": 502,
              "error": "Bad Gateway",
              "message": "Invalid response from openai",
              "timestamp": 1783163647000,
              "trace_id": "40729ce7-850b-4912-8d4c-5c250265eae5"
            }
            """;

    static final String SERVICE_UNAVAILABLE_TIMEOUT = """
            {
              "status": 503,
              "error": "Service Unavailable",
              "message": "Timed out calling openai",
              "timestamp": 1783163647000,
              "trace_id": "7789a587-d850-41dc-85a8-1bb7830de7a6"
            }
            """;

    static final String SERVICE_UNAVAILABLE_CIRCUIT_BREAKER = """
            {
              "status": 503,
              "error": "Service Unavailable",
              "message": "Circuit breaker open for openai",
              "timestamp": 1783163647000,
              "trace_id": "11627236-72a9-4bc4-aad6-5fd9d985cd80"
            }
            """;

    private OpenApiExamples() {
    }
}
