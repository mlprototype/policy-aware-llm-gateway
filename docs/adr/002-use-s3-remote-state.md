# ADR-002: Use S3 Remote State with Native Locking for Terraform

## Status

Accepted

## Date

2026-06-23

## Context

初期段階では Terraform state をローカルで扱っていた。ローカル state のままでは、開発端末と GitHub Actions が同じインフラ状態を共有できず、CI の `terraform plan` が AWS の実リソースとの差分確認ではなく静的な検証に近くなる。

state にはリソース構成や識別子が含まれ、定義によっては機密情報が入る可能性もある。そのため Git 管理せず、アクセス制御と履歴管理が可能な場所に保存する必要がある。本プロジェクトは IaC を本番運用を意識して検証するため、CI とローカルで同じ state を参照し、AWS の実リソースを refresh した plan を実行できる構成が必要である。

## Decision

Terraform state は S3 remote backend で管理する。state バケットはこの state とは別に bootstrap し、S3 の versioning、暗号化、public access block を有効化する。Terraform 1.10 以上を前提に、S3 native locking の `use_lockfile = true` を有効化する。

GitHub Actions は AWS OIDC 認証を完了した後、通常の `terraform init` で remote backend を初期化する。`terraform plan` は remote state を参照し、AWS の実リソースを refresh して差分を確認する。`-backend=false`、`-lock=false`、`-refresh=false` は使用しない。

## Alternatives Considered

- ローカル state の継続
  - CI とローカルで state を共有できず、実リソースとの差分や drift を適切に検出できないため採用しない。
- Terraform Cloud
  - 共有 state と実行基盤を提供するが、この個人開発では AWS 内で完結する S3 backend で要件を満たせるため採用しない。
- S3 + DynamoDB lock
  - 従来から有効な方式だが、Terraform の S3 native locking を使うことで追加リソースなしにロックを構成できるため採用しない。

## Consequences

- ローカルと CI が同じ state を基準に `terraform plan` を実行できる。
- AWS 実リソースの refresh を含む差分確認ができ、意図しない変更や drift を検出しやすくなる。
- state バケット自体の権限、暗号化、保持方針を管理する必要がある。
- state に機密値を残さない設計がより重要になる。

## Security Considerations

- state バケットは公開せず、暗号化、versioning、public access block を有効にする。
- GitHub Actions 用 IAM Role には、state とロックファイルに必要な最小限の S3 権限だけを与える。state 本体の削除権限は不要である。
- API Key などの実値を Terraform のリソース、変数、state に書き込まない。
- `.tfstate`、`.tfstate.*`、`terraform.tfvars`、`.terraform/` は Git 管理対象外とする。

## Cost Considerations

- state 保存に必要な S3 のストレージ、リクエスト、versioning の費用は小さい。
- DynamoDB lock table を追加しないため、構成と管理対象を減らせる。
- 変更履歴を保持する versioning はわずかな保存費用を伴うが、誤操作からの復旧可能性を優先する。

## Operational Notes

- 既存のローカル state は `terraform init -migrate-state` で移行し、手作業で S3 にコピーしない。
- 古いローカル state、backup、CI ログに機密値が残っていないかを確認する。
- CI では認証後に `terraform init`、`terraform validate`、`terraform plan` を実行する。
- `.terraform.lock.hcl` はプロバイダーの再現性を保つため Git 管理し、`.terraform/` ディレクトリは管理しない。
