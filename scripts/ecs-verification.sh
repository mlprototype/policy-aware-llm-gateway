#!/usr/bin/env bash
# Verify remote success explicitly: ECS Exec CLI status alone is not the command status.
set -euo pipefail
mode=${1:?Usage: ecs-verification.sh health|bootstrap|chat}
: "${ECS_CLUSTER:?ECS_CLUSTER is required}"
: "${ECS_SERVICE:?ECS_SERVICE is required}"
export AWS_PAGER=""
case "$mode" in
  health)
    marker=GATEWAY_HEALTH_OK
    remote_command="/bin/sh -c 'wget -q --spider http://127.0.0.1:8080/actuator/health && printf \"${marker}\\n\"'"
    attempts=${SMOKE_ATTEMPTS:-12}
    ;;
  bootstrap)
    marker=GATEWAY_BOOTSTRAP_OK
    # The key is read from the task environment, never from CLI arguments or CI.
    remote_command='java -Xmx96m -jar /app/app.jar --spring.profiles.active=aws,aws-bootstrap --gateway.bootstrap.tenant-name=aws-verification --gateway.bootstrap.client-name=verification-client'
    attempts=1
    ;;
  chat)
    marker=GATEWAY_CHAT_OK
    # No shell tracing or response-body logging; the provider may return sensitive text.
    remote_command='/bin/sh -c '\''test -n "$GATEWAY_API_KEY" && wget -q -O /dev/null --header="Content-Type: application/json" --header="X-API-Key: $GATEWAY_API_KEY" --post-data="{\"messages\":[{\"role\":\"user\",\"content\":\"Say hello\"}],\"max_tokens\":16}" http://127.0.0.1:8080/v1/chat/completions && printf "GATEWAY_CHAT_OK\n"'\'''
    attempts=1
    ;;
  *) echo 'Unknown verification mode' >&2; exit 2 ;;
esac
[[ "$attempts" =~ ^[0-9]+$ ]] && (( attempts >= 1 && attempts <= 12 )) || exit 2
umask 077
output=$(mktemp)
trap 'rm -f "$output"' EXIT
for ((attempt=1; attempt<=attempts; attempt++)); do
  task=${ECS_TASK_ARN:-$(aws ecs list-tasks --cluster "$ECS_CLUSTER" --service-name "$ECS_SERVICE" \
    --desired-status RUNNING --query 'taskArns[0]' --output text)}
  if [[ -z "$task" || "$task" == None ]]; then
    echo 'No running ECS task found' >&2
    exit 1
  fi
  if [[ -n "${EXPECTED_TASK_DEFINITION:-}" ]]; then
    actual=$(aws ecs describe-tasks --cluster "$ECS_CLUSTER" --tasks "$task" --query 'tasks[0].taskDefinitionArn' --output text)
    [[ "$actual" == "$EXPECTED_TASK_DEFINITION" ]] || { echo 'Task revision does not match deployment' >&2; exit 1; }
  fi
  : > "$output"
  # GNU timeout is available on the GitHub Ubuntu runner (macOS: use gtimeout).
  timeout_cmd=${TIMEOUT_COMMAND:-timeout}
  if "$timeout_cmd" --foreground 90s aws ecs execute-command --cluster "$ECS_CLUSTER" --task "$task" \
    --container gateway --interactive --command "$remote_command" > "$output" 2>&1; then
    if tr -d '\r' < "$output" | grep -Fxq "$marker"; then
      printf '%s\n' "$marker"
      exit 0
    fi
  fi
  # Do not print the raw session output, particularly for provisioning failures.
  echo "Remote $mode verification failed (attempt $attempt/$attempts)" >&2
  if (( attempt < attempts )); then sleep 10; fi
done
exit 1
