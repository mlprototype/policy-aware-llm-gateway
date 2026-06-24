# Cost Optimization Strategy

## Overview

個人開発で AWS リソースを常時稼働すると、ECS、RDS、ALB、Redis、NAT Gateway の費用が継続する。本プロジェクトの目的は商用サービスの常時提供ではなく、AWS デプロイ、CI/CD、セキュリティ、運用設計を実環境で検証することである。そのため、Zero-Idle Architecture を採用する。

## Cost Optimization Goals

- 有料リソースは検証に必要な時間だけ起動する
- ECS、RDS、Redis、ALB の停止・削除漏れを防ぐ
- ECR、Secrets Manager、S3 state、IAM など再利用性の高い構成は保持する
- 本番適用時の可用性・セキュリティ拡張を妨げない

## Zero-Idle Architecture

ECS Service の既定値は `desired_count = 0` であり、非検証時に Fargate タスクを実行しない。検証時だけ `desired_count = 1` にする。現在の AWS プロファイルでは PostgreSQL が起動に必要なため、タスク起動時は `enable_rds = true` も必要である。GitHub Actions の smoke test はタスクを一時起動し、成功後に元の起動数へ戻す。

## Optional Resource Toggles

| Resource | Terraform Variable | Default | Reason |
| --- | --- | --- | --- |
| ECS Task | `desired_count` | `0` | アイドル時の Fargate 実行費を削減する。 |
| RDS | `enable_rds` | `false` | DB の稼働費を検証時だけにする。 |
| Redis | `enable_redis` | `false` | キャッシュの固定費を抑える。 |
| ALB | `enable_alb` | `false` | ALB の固定費を必要時だけにする。 |

## Resources Kept Between Verifications

ECR repository と必要なイメージ、Secrets Manager のシークレットコンテナ、IAM Role と Policy、S3 remote state bucket、`.terraform.lock.hcl`、CloudWatch の Log Group と Dashboard は保持対象である。ECR はライフサイクルポリシーでイメージ保持数を制限し、CloudWatch Logs は短い保持期間に設定するため、再作成の手間に対して費用を抑えやすい。

## Resources Disabled After Verification

検証後は ECS の実行タスクを `desired_count = 0` にする。RDS、Redis、ALB は Terraform のトグルを `false` にして削除する。特に RDS と ALB は停止や削除を忘れると継続費用が発生するため、検証完了条件に cleanup を含める。

## Why NAT Gateway Is Not Used in the Default Verification Setup

NAT Gateway は Private Subnet からの外向き通信を安全に設計する有力な手段だが、個人検証では固定費が大きい。既定構成は Public Subnet と public IP を利用し、Security Group の接続元制限でリスクを抑える。この選択は低コスト検証のためであり、本番の推奨ネットワーク構成ではない。

本番適用時は Private Subnet、NAT Gateway または VPC Endpoint、ALB、WAF を組み合わせ、公開経路と外向き通信を分離する。その結果、可用性とセキュリティのための固定費は増加する。

## Cleanup Strategy

Selective Cleanup により、再利用する基盤を残して高コストリソースだけを無効化する。検証後は次の状態を基準とする。

```text
desired_count = 0
enable_rds   = false
enable_redis = false
enable_alb   = false
```

必要なら `terraform apply` でこの状態を反映する。すべてのリソースを廃棄するのは、検証環境を完全に作り直す場合に限り、`terraform destroy` を使う。

## Budget and Billing Monitoring

AWS Budget Alert は Terraform では作成していないため、AWS アカウント側で予算と通知を設定する。定期的に Billing、Cost Explorer、稼働中の RDS・ALB・Redis・ECS タスクを確認し、無料枠やクレジットの残量も確認する。CI の実行時間もコストと利用枠に影響するため、Buildx と Gradle のキャッシュを利用する。

## Production Cost Considerations

本番では ALB、Multi-AZ RDS、NAT Gateway、WAF、CloudWatch Alarm、バックアップ、長期ログ保持などにより固定費が増える。個人検証構成の低コスト化と本番構成の可用性・安全性は別の最適化問題であり、要件と SLA に応じて設計する。

## Related ADRs

- [ADR-001: ECS Fargate](../adr/001-use-ecs-fargate.md)
- [ADR-005: Zero-Idle Strategy](../adr/005-zero-idle-cost-strategy.md)
- [ADR-006: ECS Exec Smoke Test](../adr/006-ecs-exec-smoke-test.md)
