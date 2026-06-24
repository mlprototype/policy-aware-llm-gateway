# ADR-001: Use ECS Fargate for Containerized LLM Gateway Deployment

## Status

Accepted

## Date

2026-06-23

## Context

`Policy-Aware Multi-LLM Gateway` は Spring Boot アプリケーションであり、Docker コンテナとして実行できる。個人開発であっても、ローカル実行に留めず、AWS 上でのデプロイ、ログ確認、IAM 設計、CI/CD の一連の流れを検証する必要がある。

EC2 に直接デプロイする方式では、OS の更新、パッチ適用、SSH の管理などが必要になる。これらは重要な運用課題だが、本プロジェクトで主に検証したいコンテナ実行基盤とアプリケーション運用の範囲を超える。EKS は高機能である一方、個人開発・小規模検証には学習と運用の負荷が大きい。Lambda は短時間・イベント駆動の処理には適するが、常駐する HTTP Gateway、コンテナイメージ、ECS Exec を組み合わせた検証には適合しにくい。

## Decision

コンテナ実行基盤として Amazon ECS on Fargate を採用する。Docker イメージは Amazon ECR に格納し、ECS Service で Spring Boot Gateway を実行する。アプリケーションログは CloudWatch Logs に出力する。

ECS Service の `desired_count` は Terraform 変数で制御し、通常は `0`、結合検証時のみ `1` にする。ただし、現在の AWS プロファイルでは PostgreSQL 接続が起動時に必須であるため、`desired_count > 0` にする場合は `enable_rds = true` を同時に指定する。Terraform の precondition により、この組み合わせを満たさない適用は拒否する。

ALB、RDS PostgreSQL、ElastiCache Redis は Terraform のトグルで必要なときだけ作成する。本判断は、本番運用を意識した個人開発・検証構成における選定であり、常時稼働する商用構成を示すものではない。

## Alternatives Considered

- EC2
  - サーバー、SSH、OS の保守が主目的ではなく、検証対象に対して運用負荷が大きいため採用しない。
- EKS
  - Kubernetes の機能は有用だが、個人検証ではクラスタ運用と学習のコストが過大なため採用しない。
- Lambda
  - 常駐 HTTP API、Docker ベースのデプロイ、ECS Exec を用いたコンテナ内部の確認には Fargate の方が自然なため採用しない。
- AWS App Runner
  - 手軽に公開できる一方、タスクロール、ネットワーク、ECS Exec を含む ECS の設計・検証を行う目的には適合しないため採用しない。

## Consequences

- サーバーを管理せずに、コンテナ化したアプリケーションを AWS 上で実行できる。
- ECR、ECS、IAM Role、Security Group、CloudWatch Logs を組み合わせた実務に近い検証ができる。
- Task Definition、Service、IAM、Security Group など複数の AWS リソースを理解し、整合させる必要がある。
- 本番相当へ拡張する場合は、Private Subnet、ALB、Auto Scaling、WAF、可用性設計を別途検討する必要がある。

## Security Considerations

- ECS Task Execution Role と Task Role を分離する。前者はイメージ取得、ログ出力、タスク起動時のシークレット注入を担い、後者はアプリケーションからの AWS API 利用と ECS Exec のメッセージチャネルに必要な権限を担う。
- Security Group の許可元は `allowed_ingress_cidr` に限定し、未指定時は直接 HTTP のインバウンドを許可しない。
- API Key などの実値をタスク定義の通常の環境変数や Terraform 変数へ書かず、Secrets Manager から注入する。
- 本番運用を意識した拡張では、Private Subnet、ALB、WAF などにより公開経路を分離する。

## Cost Considerations

- Fargate の実行料金はタスク稼働中に発生するため、通常は `desired_count = 0` とする。
- 検証終了後は `desired_count = 0` に戻す。
- ALB、RDS、Redis は固定費または稼働費が発生しやすいため、デフォルトでは無効にする。

## Operational Notes

- AWS 上で起動検証する際は、`desired_count = 1` と `enable_rds = true` をセットで適用する。
- コンテナイメージは ECR へ push し、ECS Service には CI/CD または明示的なサービス更新で最新タスク定義を反映する。
- 検証後はタスクを `0` に戻し、不要であれば RDS、ALB、Redis も無効化して適用する。
- CloudWatch Logs とダッシュボードを使って、起動失敗、CPU・メモリ利用率、実行タスク数を確認する。
