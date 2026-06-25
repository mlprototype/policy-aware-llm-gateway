# ADR-003: Use GitHub Actions OIDC Instead of Static AWS Access Keys

## Status

Accepted

## Date

2026-06-23

## Context

GitHub Actions からテスト、Terraform plan、ECR へのイメージ push、ECS のタスク定義更新、一時起動、smoke test、失敗時のロールバックを実行する必要がある。静的な AWS Access Key と Secret Access Key を GitHub Secrets に保存する方式は、長期認証情報の漏洩、ローテーション、利用範囲の管理というリスクを持つ。

CI 実行中だけ有効な一時認証情報で AWS Role を引き受け、信頼条件で対象リポジトリを制限する方が、個人開発・検証構成でも安全性と運用性の両面で適している。

## Decision

AWS IAM に GitHub Actions 用 OIDC Provider と IAM Role を設定する。GitHub Actions は `aws-actions/configure-aws-credentials` を使って OIDC トークンでこの Role を引き受ける。GitHub Repository Secret に保存する AWS 認証情報は `AWS_ROLE_ARN` のみとし、静的なアクセスキーは使用しない。

Trust Policy は `repo:<owner>/policy-aware-llm-gateway:*` のような subject 条件と `sts.amazonaws.com` の audience 条件で、引受元を対象リポジトリに限定する。Workflow は `id-token: write` を許可し、Terraform の remote state にアクセスできるよう、認証を `terraform init` より前に行う。

## Alternatives Considered

- AWS Access Key を GitHub Secrets に保存
  - 長期キーの漏洩時の影響が大きく、ローテーションと棚卸しも必要になるため採用しない。
- 手動デプロイのみ
  - テストからデプロイ、検証、ロールバックまでの再現性を確保できず、CI/CD の検証にならないため採用しない。
- AWS CodePipeline / CodeBuild
  - AWS 内で完結できるが、現行リポジトリでは GitHub Actions を中心にした方が設定と開発フローを単純に保てるため採用しない。

## Consequences

- 長期 AWS アクセスキーを GitHub に保存せずに済む。
- Role の信頼関係で、認証を利用できるリポジトリを限定できる。
- OIDC、Trust Policy、IAM 権限、`iam:PassRole` の関係を正しく設計・保守する必要がある。
- Terraform plan、ECR push、ECS deploy、ECS Exec に必要な最小権限を定期的に見直す必要がある。

## Security Considerations

- Trust Policy の subject は対象リポジトリに限定する。将来、環境やブランチを分ける場合は branch や GitHub Environment 単位の条件を追加する。
- `iam:PassRole` は ECS Task Execution Role と Task Role など、デプロイに必要な Role のみに限定する。
- CI 用 Role に `secretsmanager:GetSecretValue` を付与しない。CI はシークレットの構成を利用してデプロイするが、実値を読む必要はない。
- S3 remote state には state と lockfile に必要な最小権限を与え、他のバケットへの権限を広げない。

## Cost Considerations

- GitHub Actions と AWS IAM の OIDC 連携自体に追加料金は発生しない。
- CodePipeline や CodeBuild を追加しないため、検証構成のサービス数と管理コストを抑えられる。
- Buildx と Gradle のキャッシュを利用し、CI ランナーの実行時間を抑える。

## Operational Notes

- GitHub Repository Secret に `AWS_ROLE_ARN` を登録する。VPC と subnet などの非機密入力は GitHub Variables で与える。
- Workflow の `permissions` に `id-token: write` を設定し、`aws-actions/configure-aws-credentials` を使用する。
- Terraform job では OIDC 認証を `terraform init` より前に実行する。
- 認可エラーが発生した場合は、広い管理者権限を付ける前に、失敗した AWS API と対象リソースを確認して最小権限を追加する。
