# Security Model

## Overview

本書は AWS 構成の認証、シークレット、ネットワーク、Terraform state、CI/CD の権限分離を説明する。本構成は本番運用を意識した個人開発・検証構成であり、完全な商用セキュリティ基準を満たすことを主張するものではない。

## Security Goals

- 長期 AWS キーを使わない
- API Key を Git、state、CI ログ、image から分離する
- CI、Task Execution、Task Role の責務を分ける
- ネットワークと state のアクセスを必要な範囲に制限する

## Identity and Access Management

GitHub Actions 用 Role は ECR push、ECS 更新・Exec、Terraform plan 用の S3 操作を担う。`iam:PassRole` は ECS の Task Execution Role と Task Role などに限定する。Task Execution Role は image 取得、CloudWatch Logs、タスク起動時のシークレット注入を担う。Task Role は Bedrock 呼び出しと ECS Exec の SSM メッセージチャネルを担う。現行の広いリソース指定は、本番適用時にモデル ARN や対象リソースへさらに絞る。

## GitHub Actions OIDC

GitHub Actions は OIDC の一時認証情報で Role を引き受け、静的 Access Key / Secret Access Key を使用しない。Trust Policy は audience と対象リポジトリの subject で制限する。`AWS_ROLE_ARN` は GitHub Secrets に保持し、`terraform init` より前に認証する。

## Secret Management

Terraform は Secrets Manager のシークレットコンテナだけを作成する。`aws_secretsmanager_secret_version` で値を管理せず、API Key は安全な運用手段で直接登録する。RDS の認証情報は RDS 管理シークレットを使い、`terraform.tfvars` に DB パスワードを渡さない。CI Role には `secretsmanager:GetSecretValue` を付与しない。

## Terraform State Security

state は S3 remote backend で管理し、versioning、暗号化、public access block、`use_lockfile = true` を使う。実シークレットを state に含めないことが前提である。旧 state、backup、CI ログに残存がないかを確認し、含まれた Key はローテーションする。`.env`、tfvars、state、`.terraform/` は Git 管理せず、`.terraform.lock.hcl` は管理する。

## Network Security

ECS は低コスト検証のため Public Subnet を使うが、Security Group の `allowed_ingress_cidr` で接続元を制限する。CI Runner の IP は変動するため、CI 用に公開ポートを開放しない。RDS のインバウンドは ECS タスクの Security Group に限定する。本番適用時は Private Subnet、ALB、WAF、VPC Endpoint または NAT Gateway を検討する。

## ECS Runtime Security

シークレットを image や通常の環境変数定義へ焼き込まず、起動時に注入する。ECS Exec は必要な主体だけに許可する。CloudWatch Logs にも機密値を出力しない実装と運用が必要である。

## CI/CD Security

CI は test、plan、image push、ECS 更新、ECS Exec を実行する。Secret 値は読まず、state、ECR、ECS を操作するため、Role の権限を定期的に確認する。state 本体への削除権限は不要である。

## Data Exposure Considerations

Gateway は API Key と利用者の入力テキストを扱う。アプリケーションは PII 検知・マスキング、プロンプトインジェクション検知、監査ログを実装する。扱うデータに応じ、ログ保持、アクセス制御、暗号化、出力データのリダクションを本番適用時に強化する。

## Known Limitations

WAF、常設 Private Subnet、厳密な環境分離、SIEM 連携は未導入である。RDS 管理シークレット以外の自動ローテーションは今後の検討事項である。

## Related ADRs

- [ADR-002: S3 Remote State](../adr/002-use-s3-remote-state.md)
- [ADR-003: GitHub Actions OIDC](../adr/003-use-github-actions-oidc.md)
- [ADR-004: Secrets Manager](../adr/004-secrets-manager-without-secret-version.md)
- [ADR-006: ECS Exec Smoke Test](../adr/006-ecs-exec-smoke-test.md)
