# Operations Runbook

## Overview

AWS 上で Gateway を一時起動し、検証後に高コストリソースを停止・削除する手順である。

## Prerequisites

AWS CLI、Terraform 1.10+、Docker、Java 21、Gradle、VPC/Subnet の入力値を準備する。CI では `AWS_ROLE_ARN` と GitHub Variables を設定する。

## Initial Setup

`terraform.tfvars.example` を参考に、Git 管理しない `terraform.tfvars` を作成する。

```bash
cd infra/aws
terraform init
terraform fmt -check -recursive
terraform validate
```

## Terraform Remote State

state は S3 backend に移行済みである。通常は `terraform init`、旧 state の移行時だけ `terraform init -migrate-state` を使い、無効化オプションは使わない。

## Build and Push Docker Image

テスト後に image を作成し、ECR へ Git SHA タグで push する。

```bash
./gradlew test
./gradlew bootJar
docker build -t multi-llm-gateway .
aws ecr get-login-password --region <aws-region> | \
  docker login --username AWS --password-stdin <ecr-registry>
docker tag multi-llm-gateway:latest <ecr-repository-url>:<git-sha>
docker push <ecr-repository-url>:<git-sha>
```

GitHub Actions では Workflow が同等の build と push を行う。

## Deploy Infrastructure

通常はタスクを起動せずに適用する。

```bash
cd infra/aws
terraform plan
terraform apply
```

## Configure Secrets

Terraform はシークレットコンテナだけを作成する。API Key は ECS 起動前に直接登録し、Terraform、tfvars、CI ログへ書かない。CI Role に `GetSecretValue` は付与しない。

## Start Verification Environment

AWS プロファイルでは PostgreSQL が必須のため、RDS を有効化してタスクを起動する。

```bash
cd infra/aws
terraform apply -var="desired_count=1" -var="enable_rds=true"
```

既存 Service で RDS を後から有効化する場合は、最新 Task Definition へ更新して `--force-new-deployment` を実行する。Redis と ALB は必要時だけ有効化し、task が `RUNNING` になることを確認する。

## Run Smoke Test

GitHub Actions は ECS Exec で自動確認する。手動でも public IP は使わず、タスク内部から確認する。Session Manager plugin が必要である。

```bash
aws ecs execute-command --cluster <ecs-cluster> --task <running-task-arn> \
  --container gateway --interactive \
  --command "wget -q -O - http://127.0.0.1:8080/actuator/health"
```

`status` が `UP` であることを成功条件とする。

## Check Logs and Metrics

失敗時は CloudWatch Logs、ECS events、Stopped reason、RDS status、GitHub Actions logs を確認する。Dashboard では CPU、メモリ、task 数、ログを確認する。

## Rollback Procedure

Workflow はデプロイ後の失敗時に、記録済みの Task Definition と起動数へ自動復元する。手動復元では直前の ARN と起動数で `aws ecs update-service --force-new-deployment` を実行する。

## Cleanup Procedure

検証後はタスクを停止し、不要な有料リソースを無効化する。

```bash
terraform apply -var="desired_count=0" -var="enable_rds=false" \
  -var="enable_redis=false" -var="enable_alb=false"
```

ECR、Secrets Manager、IAM、S3 state は次回検証のため保持する。完全削除が必要な場合だけ `terraform destroy` を使う。

## Troubleshooting

| Symptom | Possible Cause | Action |
| --- | --- | --- |
| ECS task keeps stopping | DB 接続または Task Definition 未反映 | `enable_rds=true`、Task Definition、Logs を確認する。 |
| Terraform plan fails in CI | OIDC Role の S3 権限不足 | 失敗 API と最小権限を確認する。 |
| ECS Exec fails | Exec / Task Role / plugin の設定不足 | Service と SSM 権限を確認する。 |
| health check fails | アプリまたは依存先が未準備 | Logs、ECS events、RDS status を確認する。 |
| RDS cost remains | cleanup 漏れ | `enable_rds=false` で apply する。 |

## Operational Checklist

検証前は state、Secret、RDS、CI Role、Security Group を確認する。検証後は smoke test、`desired_count=0`、RDS・Redis・ALB の無効化、請求対象を確認する。
