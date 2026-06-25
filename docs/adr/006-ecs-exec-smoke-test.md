# ADR-006: Use ECS Exec for Internal Smoke Testing without Opening Public Ingress to CI

## Status

Accepted

## Date

2026-06-23

## Context

GitHub Actions からデプロイ後の `/actuator/health` を確認し、起動できないタスク定義をそのまま残さないようにする必要がある。一般的には ALB やタスクの public IP に HTTP アクセスするが、GitHub-hosted Runner の IP は固定ではない。そのため、CI からアクセスするためだけに Security Group の許可範囲を広げることになる。

この検証構成では ALB を常時有効にせず、コストを抑える方針である。外部公開経路を CI 専用に広げずに、コンテナ内部から HTTP health check を実行する手段が必要になる。

## Decision

ECS Service で ECS Exec を有効化し、GitHub Actions の smoke test は外部公開エンドポイントへアクセスしない。Workflow は一時的に ECS タスクを 1 つ起動し、`aws ecs execute-command` で `gateway` コンテナ内の `http://127.0.0.1:8080/actuator/health` を確認する。現在のコンテナイメージには `wget` があるため、レスポンス内の `"status":"UP"` を確認する。

Workflow はデプロイ前の Task Definition ARN と `desired_count` を記録する。smoke test 成功時は元の起動数へ戻し、デプロイ後に失敗した場合は直前の Task Definition と元の起動数へロールバックする。これにより、CI Runner 向けに Security Group を広く開放しない。

## Alternatives Considered

- タスクの public IP へ `curl`
  - CI Runner の IP 範囲が変動するため、Security Group を広く開放する必要があり採用しない。
- ALB 経由の health check
  - ALB は標準的な選択肢だが、常時の固定費が個人検証の低コスト方針に合わないため採用しない。
- 手動確認のみ
  - デプロイ後の検証と失敗時の復旧が自動化されず、CI/CD の品質ゲートとして弱いため採用しない。
- CloudWatch Logs のみで確認
  - 起動ログは確認できるが、アプリケーションの HTTP health endpoint が成功することを直接検証しにくいため採用しない。

## Consequences

- CI Runner に対して直接 HTTP のインバウンドを許可せずに、デプロイ後の health check を自動化できる。
- ALB が無効でも smoke test を実行できる。
- ECS Exec のために、タスク側の SSM メッセージチャネル権限と、CI Role の ECS Exec 関連権限が必要になる。
- GitHub-hosted Runner には Session Manager plugin の導入が必要であり、Workflow 内で必要時に導入する。
- コンテナイメージには `wget`、`curl` など、health check に使うコマンドを含める必要がある。

## Security Considerations

- ECS Exec の実行権限は CI 用 Role など必要な主体に限定し、ECS の cluster、service、task を対象にした最小権限へ絞る。
- タスク側には ECS Exec の SSM メッセージチャネルに必要な権限のみを付与する。
- CI 用 Role には不要な権限を付与せず、特に `secretsmanager:GetSecretValue` は付与しない。
- 本番運用を意識した拡張では、CloudTrail、Session Manager のログ出力、操作主体の分離を含む監査設計を検討する。

## Cost Considerations

- ALB を smoke test 専用に常時起動しないため、固定費を抑えられる。
- ECS Exec 自体の追加費用は小さいが、smoke test 中は Fargate タスクの実行料金と CI ランナー時間が発生する。
- 成功時も失敗時も元の `desired_count` を復元する。通常の標準値は `0` のため、検証後にタスクは停止する。

## Operational Notes

- CI での smoke test 前に、RDS を有効化した起動可能な ECS Service と、ECS Exec 有効化済みのタスク定義を用意する。
- deploy job は image push、Task Definition 更新後に一時的に `desired_count = 1` にし、サービスが安定するまで待機してから ECS Exec を実行する。
- ECS Exec は準備に時間がかかることがあるため、Workflow ではリトライして health check を実行する。
- 成功・失敗、ロールバック、失敗時の直近アプリケーションログは GitHub Actions のログで確認する。
