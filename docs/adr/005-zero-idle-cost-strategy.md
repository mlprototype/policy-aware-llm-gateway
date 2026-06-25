# ADR-005: Adopt Zero-Idle Architecture for Cost-Aware Personal AWS Development

## Status

Accepted

## Date

2026-06-23

## Context

個人開発で AWS リソースを常時起動すると、ECS タスク、ALB、RDS、Redis、NAT Gateway などの費用が積み上がる。本プロジェクトの目的は、常時商用運用ではなく、AWS 上でのデプロイ、CI/CD、監視、シークレット管理、運用手順を検証することである。

したがって、有料リソースは必要な検証時だけ起動し、終了後に停止または削除する設計が必要になる。一方で、ECR、IAM、remote state まで毎回削除すると、次の検証の準備と再現性が悪化する。本番運用を意識した拡張余地を保ちながら、アイドルコストを抑える方針を採る。

## Decision

通常時の ECS Service は `desired_count = 0` とし、検証時だけ `desired_count = 1` にする。現在のアプリケーションは AWS 上で PostgreSQL 接続を必要とするため、タスクを起動する検証では `enable_rds = true` も指定する。

RDS PostgreSQL、Redis、ALB はそれぞれ `enable_rds`、`enable_redis`、`enable_alb` を `false` とすることを標準状態とし、必要な検証に限り有効化する。NAT Gateway は個人検証構成では採用せず、ECS タスクは Public Subnet で public IP を割り当てて外部 API への通信を行う。検証後は ECS タスクを停止し、RDS、Redis、ALB を選別して削除する。ECR、Secrets Manager、IAM、S3 remote state は次回の検証のために保持する。

## Alternatives Considered

- 常時稼働構成
  - 個人開発の検証頻度に対して固定費が大きく、費用対効果が低いため採用しない。
- すべてのリソースを毎回 `terraform destroy`
  - ECR、IAM、remote state まで消すと、次回検証の準備が増え、再現性も下がるため採用しない。
- NAT Gateway と Private Subnet のみで構成
  - 本番では有力な選択肢だが、NAT Gateway の固定費が個人検証には大きいため採用しない。
- ローカル Docker のみで検証
  - AWS 上の IAM、Secrets Manager、CloudWatch、ECS、CI/CD を確認できないため採用しない。

## Consequences

- 必要な時だけ AWS 上でデプロイと疎通確認を実施でき、個人開発の継続コストを抑えられる。
- 常時稼働の本番構成と比べ、ネットワーク分離、可用性、公開経路は簡略化される。
- cleanup を忘れると課金が継続するため、手順化と利用状況の確認が必要になる。
- RDS、Redis、ALB を破棄する前提のため、保持すべきデータや可用性が必要な用途には別の運用設計が必要になる。

## Security Considerations

- NAT Gateway を省略するため ECS タスクは Public Subnet に配置されるが、Security Group のインバウンドは `allowed_ingress_cidr` に限定する。デフォルトの空リストでは直接 HTTP を許可しない。
- CI Runner の可変 IP に合わせてインバウンドを広げず、CI のヘルスチェックには ECS Exec を使う。
- RDS は検証しやすさのため public accessibility を持つ設定だが、DB 用 Security Group のインバウンドは ECS タスクの Security Group に限定する。
- 本番運用を意識した構成へ移行する際は、Private Subnet、ALB、WAF、より厳密なネットワーク分離を検討する。

## Cost Considerations

- Fargate はタスク稼働中に料金が発生するため、`desired_count = 0` を基本とする。
- ALB、RDS、Redis、NAT Gateway は固定費または稼働費が出やすいため optional にする。
- ECR はライフサイクルポリシーで保持イメージを 3 世代に制限し、CloudWatch Logs は 1 日保持として保存費を抑える。
- Secrets Manager と S3 state は小額の継続費用を伴うが、再現性と安全な管理を優先して保持する。

## Operational Notes

- 検証開始時に、必要なリソースだけ有効化する。AWS 上でタスクを起動する場合は `desired_count = 1` と `enable_rds = true` を合わせる。
- 検証終了後は `desired_count = 0`、`enable_alb = false`、`enable_rds = false`、`enable_redis = false` を適用する Selective Cleanup を実施する。
- ECR、Secrets Manager、IAM、S3 remote state、必要に応じて CloudWatch Logs は保持する。
- AWS Budget Alert は本リポジトリの Terraform では作成していないため、AWS アカウント側で設定し、想定外の課金を検知できるようにする。
