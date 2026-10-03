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

## Authentication Provisioning

認証は DB の API Client hash lookup である。`GATEWAY_API_KEY` の Secrets Manager 注入だけでは登録されない。
AWS は local seed を使わず、ECS 起動後に別の non-web process を明示的に一度実行する。
Secrets Manager の `gateway_api_key` には高エントロピーの検証用 key を直接登録し、Terraform / CI に実値を渡さない。

Linux / GNU `timeout` を持つ操作環境で、Repository root から実行する（macOS では `TIMEOUT_COMMAND=gtimeout` を指定）。
AWS CLI と Session Manager plugin、および対象 Task の ECS Exec 権限が必要である。

```bash
export ECS_CLUSTER=multi-llm-gateway-cluster
export ECS_SERVICE=multi-llm-gateway-service
# 初回登録、または同一 key / ACTIVE identity の再確認。通常起動では実行しない。
bash scripts/ecs-verification.sh bootstrap
# HTTP 200 を確認する。実 Provider を呼び、max_tokens=16 で利用費が発生する。
bash scripts/ecs-verification.sh chat
```

`GATEWAY_BOOTSTRAP_OK` / `GATEWAY_CHAT_OK` の出力を成功条件とする。Script は CLI exit code に加え、remote marker を確認する。
Tenant `aws-verification` / Client `verification-client` を作成し、既存 identity なら同じhashと ACTIVE status を確認する。
異なる key、停止 Tenant / Client、他Clientに割り当て済みの key は拒否し、credential や policy を上書きしない。
DBにはSHA-256 hashだけを保存する。Raw key は task の環境変数内にあり、chat HTTP client の引数にも一時的に含まれるため、ECS Exec / process 閲覧権限を制限する。
登録 process は既存 task 内で別 JVM（heap上限96MB）を使うため、実AWSでは memory headroom も検証する。
Key rotation / revoke は別途明示的に管理する。Secret 更新後はtask再作成が必要で、このbootstrapは既存Clientのkeyを自動交換しない。
CI health smoke はこの登録とchatを自動実行しない。

## Run Smoke Test

GitHub Actions は `scripts/ecs-verification.sh health` を ECS Exec で実行し、明示的 shell の `wget --spider` が成功した後の marker を確認する。HTTP 200 を成功条件とし、DOWN / OUT_OF_SERVICE の標準503は失敗となる。AWS profile の Redis indicator は無効で、optional / fail-open dependency を全体healthの必須条件にしない。DB health は維持する。手動でも public IP は使わず、タスク内部から確認する。Session Manager plugin が必要である。

```bash
aws ecs execute-command --cluster <ecs-cluster> --task <running-task-arn> \
  --container gateway --interactive \
  --command "wget -q -O - http://127.0.0.1:8080/actuator/health"
```

`status` が `UP` であることを成功条件とする。

## Check Logs and Metrics

失敗時は CloudWatch Logs、ECS events、Stopped reason、RDS status、GitHub Actions logs を確認する。Dashboard では CPU、メモリ、ログを確認する。Task 数のwidgetは`enable_container_insights=true`時のみ表示し、`ECS/ContainerInsights`を参照する。既定は無効で、opt-inには追加CloudWatch費用が伴う。無効時のZero-Idle確認は`aws ecs describe-services`のdesiredCount / runningCountで行う。実AWSでのmetric出力は別途検証する。

## Rollback Procedure

Workflow はデプロイ後の失敗時に、記録済みの Task Definition と起動数へ自動復元し、service stability を期限付きで待つ。Deploy step 自体が失敗した場合も復元対象で、正常smoke後に元の起動数へ戻す際も安定化を待つ。手動復元では直前の ARN と起動数で `aws ecs update-service --force-new-deployment` を実行する。

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
