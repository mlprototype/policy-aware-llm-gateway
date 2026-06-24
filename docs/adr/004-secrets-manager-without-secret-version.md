# ADR-004: Manage Secret Containers with Terraform but Secret Values Outside Terraform State

## Status

Accepted

## Date

2026-06-23

## Context

LLM Gateway は OpenAI、Anthropic、Gateway API Key、RDS 接続情報などの機密情報を扱う。Terraform の `aws_secretsmanager_secret_version` へ実値を渡すと、その値が Terraform state に記録される。state は S3 remote backend で共有するため、実値を含めないことは特に重要である。

また、CI の目的は構成の検証とデプロイであり、LLM API Key などの実値を読むことではない。CI 用 Role に `secretsmanager:GetSecretValue` を付与しない権限分離を維持する必要がある。

## Decision

Secrets Manager のシークレットコンテナは Terraform で作成するが、`aws_secretsmanager_secret_version` による実値管理は行わない。プロバイダー API Key と Gateway API Key の実値は、初回構築後に AWS Console、AWS CLI、または別の承認された安全な手段で Secrets Manager へ直接登録する。

ECS のタスク起動時にシークレットを環境変数へ注入する権限は Task Execution Role に限定する。CI 用 Role は `GetSecretValue` を持たない。RDS を有効にした場合は、`manage_master_user_password = true` により RDS が別のマスター認証情報シークレットを生成・管理し、ECS はその ARN を参照する。

## Alternatives Considered

- `terraform.tfvars` に API Key を書く
  - 誤コミット、端末への残存、state への混入のリスクが高いため採用しない。
- `aws_secretsmanager_secret_version` で実値を管理
  - シークレット値が Terraform state に保存されるため採用しない。
- 通常の環境変数に実値を直接埋め込む
  - 漏洩、ローテーション、監査の観点で弱く、タスク定義や CI 設定に残りやすいため採用しない。
- AWS Systems Manager Parameter Store
  - 利用可能な選択肢だが、今回の用途では Secrets Manager によるシークレット管理と RDS 管理シークレットの連携の方が適合するため採用しない。

## Consequences

- API Key の実値が Terraform state に残らない。
- CI 用 Role はシークレット実値を取得できず、CI からの漏洩範囲を縮小できる。
- 初回セットアップと値の更新時に、Secrets Manager へ直接登録する運用が必要になる。
- シークレット値の変更は Terraform apply ではなく Secrets Manager 側で管理する。

## Security Considerations

- 旧 state、backup、`terraform.tfvars`、CI ログに実値が残っていないかを確認する。
- 実 API Key を一度でも state や Git 管理ファイルに含めた場合は、その Key を無効化またはローテーションする。
- ECS の Task Execution Role と CI Role を分離し、`GetSecretValue` はタスク起動に必要なシークレット ARN だけに限定する。
- `.env`、`terraform.tfvars`、`terraform.tfstate`、`.terraform/` は Git 管理対象外とし、`.terraform.lock.hcl` は管理する。

## Cost Considerations

- Secrets Manager にはシークレット単位の小額な固定費が発生する。
- 複数のプロバイダー API Key を JSON の一つのシークレットとして扱い、個人検証での固定費を抑える。
- RDS を有効化した期間は、RDS が管理するマスター認証情報の管理費も考慮する。

## Operational Notes

- Terraform apply 後、ECS を起動する前に `openai_api_key`、`anthropic_api_key`、`gateway_api_key` を含む JSON を Secrets Manager へ登録する。
- RDS の認証情報は RDS 管理シークレットに任せ、プロバイダー API Key の JSON へ DB パスワードを追加しない。
- RDS の認証情報がローテーションした場合は、EventBridge と SSM Automation による ECS の新規デプロイで、新しいタスクに最新値を反映する。
- CI はシークレット値を読まず、Terraform plan、イメージ配布、ECS 更新など構成面だけを検証する。
