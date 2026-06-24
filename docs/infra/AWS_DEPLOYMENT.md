# AWS Deployment Guide

本ドキュメントでは、`Policy-Aware Multi-LLM Gateway` をAWS環境へデプロイし、コストを最小限に抑えながら動作検証を行うための詳細な手順について説明します。

---

## 1. 前提条件

デプロイを開始する前に、ローカル環境に以下のツールがインストールされ、設定されていることを確認してください。

1. **AWS CLI**（インストールおよび `aws configure` による認証情報のセットアップ）
2. **Terraform**（>= 1.10.0。S3 ネイティブロックを使用するため）
3. **Docker**（イメージのビルドおよびECRへのプッシュ用）
4. **Java 21 / Gradle**（アプリケーションの事前コンパイル用）
5. 既存の **VPC ID** および **Public Subnet ID**（デフォルトVPCなどを利用し、NAT Gateway等の新規ネットワーク固定費を回避します）

---

## 2. デフォルト低コスト構成

本プロジェクトのTerraformテンプレートは、無駄な常時稼働コストを排除し、検証時の費用を極限まで抑える **「Zero-Idle Architecture」** をデフォルトにしています。

- `desired_count = 0` : ECSタスクの起動数を0に設定し、コンテナの稼働費をアイドル時に発生させません。
- `enable_alb = false` : ALBを作成せず、タスクに付与されるPublic IPへ直接疎通確認を行います。
- `enable_rds = false` : テスト用データベースを無効化します（AWS上での起動検証時は `enable_rds = true` が必須です）。
- `enable_redis = false` : Rate Limitキャッシュ（Redis）を無効化します。Redis接続失敗時は自律的にFail-openで動作します。
- `enable_secrets_manager = true` : OpenAI／Anthropic／Gateway APIキーを**1つのJSON形式のSecretに集約**します。`enable_rds = true` の場合は、これとは別に RDS が DB マスター認証情報を管理するシークレットを作成します。

---

## 3. Terraform初期化

インフラ定義が存在する [infra/aws/](../../infra/aws/) ディレクトリに移動し、Terraformの初期化を行います。

### 初回のみ: S3 Remote State バケットを作成

Terraform state は Git 管理せず、専用 S3 バケットに暗号化・バージョニング付きで保存します。このバケットは state を置くための bootstrap リソースなので、Terraform ではなく AWS CLI で一度だけ作成します。

```bash
aws s3api create-bucket \
  --bucket multi-llm-gateway-tfstate-759655305163-ap-northeast-1-an \
  --region ap-northeast-1 \
  --create-bucket-configuration LocationConstraint=ap-northeast-1

aws s3api put-bucket-versioning \
  --bucket multi-llm-gateway-tfstate-759655305163-ap-northeast-1-an \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption \
  --bucket multi-llm-gateway-tfstate-759655305163-ap-northeast-1-an \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

aws s3api put-public-access-block \
  --bucket multi-llm-gateway-tfstate-759655305163-ap-northeast-1-an \
  --public-access-block-configuration "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
```

既存のローカル state を移行する場合は、`terraform init` の代わりに次を実行します。移行中は GitHub Actions と他の Terraform 実行を停止してください。

```bash
cd infra/aws
umask 077
cp terraform.tfstate ~/terraform.tfstate.backup
terraform init -migrate-state
```

確認プロンプトには `yes` と入力します。ローカルの state を手作業で削除・S3 へコピーしないでください。

### 通常の初期化

```bash
cd infra/aws

# 対象 AWS アカウントと認証情報を確認
aws sts get-caller-identity

# S3 backend と必要なプロバイダープラグインの初期化
terraform init
```

---

## 4. Terraform plan

デプロイ用の変数ファイル `terraform.tfvars` を作成し、インフラ計画を確認します。

### 1. `terraform.tfvars` の準備
`terraform.tfvars.example` をコピーして `terraform.tfvars` を作成し、既存のVPC ID、サブネットID、およびご自身の接続元マシンのグローバルIPアドレスを設定します。

```hcl
# terraform.tfvars
vpc_id               = "vpc-0123456789abcdef0"
public_subnet_ids    = ["subnet-0123456789abcdef0", "subnet-0123456789abcdef1"]
allowed_ingress_cidr = ["203.0.113.1/32"] # セキュリティのため、ご自身のIPアドレスを指定してください
```

### 2. インフラ実行計画の確認
```bash
terraform plan
```
エラーが発生せず、デフォルト構成（ECR, ECS Cluster, IAM Role, Secrets Manager, CloudWatch Logs等の最小リソース）の追加が提示されることを確認します。

---

## 5. ECRリポジトリの先行作成

コンテナイメージをプッシュするために、まずECRリポジトリのみを先行して作成します。

```bash
terraform apply \
  -target=aws_ecr_repository.app \
  -target=aws_ecr_lifecycle_policy.app

terraform output -raw ecr_repository_url
```

## 6. Docker image build

アプリケーション（Spring Boot）をJARにコンパイルし、Dockerイメージをビルドします。

```bash
# プロジェクトのルートディレクトリに移動
cd ../..

# Gradleによるビルド
./gradlew bootJar

# Dockerイメージのローカルビルド
docker build -t multi-llm-gateway .
```

---

## 7. ECR push

ビルドしたDockerイメージにタグを付与し、AWSのECRリポジトリへプッシュします。

```bash
# プッシュ先を取得し、ECR にログインする。
cd infra/aws
ECR_REPOSITORY_URL="$(terraform output -raw ecr_repository_url)"
ECR_REGISTRY="${ECR_REPOSITORY_URL%/*}"
aws ecr get-login-password --region ap-northeast-1 \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

# 検証用のイミュータブルなタグを付与してプッシュする。
cd ../..
docker tag multi-llm-gateway:initial "${ECR_REPOSITORY_URL}:initial"

# イメージのプッシュ
docker push "${ECR_REPOSITORY_URL}:initial"
```

---

## 8. インフラ全体のプロビジョニング

イメージを ECR に投入したら、`terraform.tfvars` の `container_image` に実際の ECR URL とタグを設定してから、残りのインフラを適用します。初回は `desired_count = 0` のままにするため、ECS タスクは起動しません。

```hcl
# terraform.tfvars
container_image = "123456789012.dkr.ecr.ap-northeast-1.amazonaws.com/multi-llm-gateway-repo:initial"
```

```bash
cd infra/aws
terraform plan
terraform apply
```

代表的な出力は以下です。

```text
ecr_repository_url       = "123456789012.dkr.ecr.ap-northeast-1.amazonaws.com/multi-llm-gateway-repo"
ecs_cluster_name         = "multi-llm-gateway-cluster"
ecs_service_name         = "multi-llm-gateway-service"
ecs_task_definition_arn  = "arn:aws:ecs:ap-northeast-1:123456789012:task-definition/multi-llm-gateway:1"
```

---

## 9. Secrets Managerへの実値投入

`secrets.tf` は API キー用シークレット本体だけを作成します。値は Terraform で管理しないため、ECS タスクを起動する前に実際の API キーを設定してください。DB のユーザー名・パスワードはここに追加しません。`enable_rds = true` の場合、RDS が専用の管理シークレットを生成し、ECS はその `username` と `password` を直接取得します。

> [!CAUTION]
> **実際の API Key を Git 管理対象ファイル、tfvars、Terraform コードに記述しないこと。**
> DB パスワードも手動で作成・登録せず、RDS 管理シークレットを使用してください。

```bash
aws secretsmanager put-secret-value \
  --secret-id multi-llm-gateway-api-keys \
  --secret-string '{
    "openai_api_key": "sk-proj-YOUR-ACTUAL-OPENAI-KEY",
    "anthropic_api_key": "sk-ant-YOUR-ACTUAL-ANTHROPIC-KEY",
    "gateway_api_key": "dev-gateway-key-001"
  }'
```

RDS を有効にした後は、DB シークレットの ARN のみを確認できます。

```bash
terraform output -raw rds_master_user_secret_arn
```

---

## 10. ECS起動検証

コンテナイメージがプッシュされたら、Fargateタスクを一時的に起動して動作検証を行います。

> [!IMPORTANT]
> **データベース接続に関する制約**
> AWS上でコンテナを起動して結合テストを行う場合、PostgreSQLへの疎通が必須となるため、必ず **`enable_rds = true`** を同時に指定してください（`enable_rds = false` のままでタスクを起動しようとすると、Terraformの `precondition` 検証によって適用がブロックされます）。

### 1. RDSプロビジョニングとタスク定義の切替

初期構成ではタスク定義の外部 CI/CD 更新を許容しています。すでに作成済みのサービスで RDS を後から有効化する場合、まず RDS と最新タスク定義を作成してから、サービスをそのタスク定義へ切り替えます。

```bash
cd infra/aws

# RDS と RDS 管理シークレットを作成する。まだタスクは起動しない。
terraform apply -var="desired_count=0" -var="enable_rds=true"

# 最新タスク定義（RDS管理シークレットを参照）をサービスへ反映する。
aws ecs update-service \
  --cluster "$(terraform output -raw ecs_cluster_name)" \
  --service "$(terraform output -raw ecs_service_name)" \
  --task-definition "$(terraform output -raw ecs_task_definition_arn)" \
  --force-new-deployment

# タスクを1つだけ起動する。
terraform apply -var="desired_count=1" -var="enable_rds=true"
```

RDS 管理シークレットはローテーション時に EventBridge と SSM Automation を通じて ECS の新規デプロイを起動します。新しいタスクには最新の DB 認証情報が注入されます。

### 2. 起動タスクの接続先IP取得
適用完了後、ECS タスクに割り当てられたパブリック IP は AWS CLI で取得します（`outputs.tf` はタスクの都度変わる Public IP を出力しません）。

```bash
CLUSTER="$(terraform output -raw ecs_cluster_name)"
SERVICE="$(terraform output -raw ecs_service_name)"
TASK_ARN="$(aws ecs list-tasks --cluster "$CLUSTER" --service-name "$SERVICE" --desired-status RUNNING --query 'taskArns[0]' --output text)"
ENI_ID="$(aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$TASK_ARN" --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value | [0]' --output text)"
ECS_PUBLIC_IP="$(aws ec2 describe-network-interfaces --network-interface-ids "$ENI_ID" --query 'NetworkInterfaces[0].Association.PublicIp' --output text)"
echo "$ECS_PUBLIC_IP"
```

### 3. API疎通テスト
接続元IP（`allowed_ingress_cidr`）として指定したマシンから、以下のエンドポイントを呼び出し疎通確認を行います。

```bash
# 1. アクチュエータヘルスチェック
curl -i http://<ECS_PUBLIC_IP>:8080/actuator/health

# 2. LLMプロキシ疎通テスト (マスターAPIキーを使用)
curl -i -X POST http://<ECS_PUBLIC_IP>:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-gateway-key-001" \
  -d '{
    "messages": [{"role": "user", "content": "日本語で一言挨拶してください"}],
    "max_tokens": 10
  }'
```

---

## 11. CloudWatch Logs確認

アプリケーションの起動ログやAPI監査ログ、およびRedisのFail-openログは、CloudWatch Logsの `/ecs/multi-llm-gateway` ロググループに記録されます。

```bash
# CLIによる直近のアプリケーションログ取得
aws logs filter-log-events \
  --log-group-name /ecs/multi-llm-gateway \
  --limit 20 \
  --query events[*].[timestamp,message]
```
※ `dashboard.tf` で定義された CloudWatch Custom Dashboard からグラフィカルにログや使用率を一覧確認することも可能です。

---

## 12. Scale down

疎通確認が終了したら、不要なコンテナ稼働費用をカットするために、即座にECSサービス内のタスク数を `0` にスケールダウンします。

```bash
terraform apply -var="desired_count=0" -var="enable_rds=true"
```
これで ECS Fargate タスクの実行は停止し、コンテナの稼働コストは $0 になります。RDS を残している間は RDS と RDS 管理シークレットの料金は継続します。

---

## 13. Selective cleanup

検証のために作成した一時的な有料インフラ（RDS PostgreSQLやALB、Redis等）だけを削除し、ECRのコンテナイメージやTerraform State、IAMポリシーなどの静的リソースは残したままにして、次回の検証効率を高めます。

```bash
# 有料リソースのトグルをすべて false に戻して適用します
terraform apply \
  -var="desired_count=0" \
  -var="enable_alb=false" \
  -var="enable_rds=false" \
  -var="enable_redis=false"
```
これにより、高コストなRDS/ALB/Redisが安全に削除され、固定コストはSecrets Manager（月額 $0.40）のみに抑えられます。

インフラ全体を完全に破棄する場合は、以下を実行します。
```bash
terraform destroy
```

---

## 14. GitHub Actions CI/CD

`.github/workflows/deploy.yml` は `main` への対象ファイルの push 時に、以下を順に実行します。前段が失敗した場合、ECR push と ECS 更新には進みません。

1. PostgreSQL / Redis サービスを起動した状態で `./gradlew test`
2. `terraform fmt -check -recursive`、`terraform validate`、`terraform plan`
3. Git SHA タグのコンテナイメージを ECR へ push
4. ECS タスク定義を更新し、タスクを一時的に 1 つ起動
5. ECS Exec でコンテナ内の `http://127.0.0.1:8080/actuator/health` を検証

ECS Exec を使うため、GitHub-hosted runner の変動 IP を Security Group に追加する必要はありません。

### 必須の GitHub 設定

- Secret: `AWS_ROLE_ARN` — GitHub Actions OIDC 用 IAM ロール ARN
- Variable: `AWS_VPC_ID` — 検証環境の VPC ID
- Variable: `AWS_PUBLIC_SUBNET_IDS` — HCL / JSON リスト形式のサブネット ID。例: `["subnet-aaa", "subnet-bbb"]`
- Variable: `AWS_ALLOWED_INGRESS_CIDRS` — 任意。HCL / JSON リスト形式の接続許可 CIDR。未指定時は `[]`

`terraform plan` は S3 上の remote state と AWS の実リソースを照合して実行します。S3 ネイティブロックを使用するため、GitHub Actions 用 IAM ロールには state に対する `s3:GetObject` / `s3:PutObject`、ロックファイルに対する `s3:GetObject` / `s3:PutObject` / `s3:DeleteObject` が必要です。state 本体への `s3:DeleteObject` は付与しません。

### smoke test の前提

このアプリケーションは AWS profile で PostgreSQL を必須とするため、CI が利用する ECS サービスは事前に `enable_rds = true` で構築し、RDS 管理シークレットを参照する最新タスク定義を適用しておく必要があります。ECR、ECS サービス、RDS、Secrets Manager を含むベースインフラを先に Terraform で適用してから、デプロイワークフローを有効化してください。ECS Exec の有効化とタスクロール権限は Terraform で管理されています。

### ロールバック方針

ワークフローはデプロイ前に ECS サービスのタスク定義 ARN と `desired_count` を記録します。smoke test、ECS Exec、または起動数の復元に失敗した場合は、直前のタスク定義に `--force-new-deployment` で戻し、元の起動数を復元します。成功時も元の起動数へ戻るため、通常の `desired_count = 0` 構成では検証後に Fargate タスクは停止します。

---

## 15. Troubleshooting

### Q1: `terraform apply` 時に不整合エラー (precondition failed) が出る
* **エラー例**: `precondition failed: Cannot set desired_count > 0 when enable_rds is false...`
* **対処**: 本システムはAWS上での実行時（`aws` プロファイル）にPostgreSQLデータベースを要求します。`desired_count` を `1` にしてタスクを起動する場合は、必ず `enable_rds = true` を同時に指定して実行してください。

### Q2: ECSタスク起動直後にコンテナが停止する (接続エラー)
* **原因**: RDS がまだ利用可能でない、ECS サービスが RDS 管理シークレットを参照する最新タスク定義へ切り替わっていない、または Flyway マイグレーションが失敗している可能性があります。
* **対処**: CloudWatch Logs の `/ecs/multi-llm-gateway` を確認し、`Database connection failed` などの例外を確認してください。`ecs_task_definition_arn` を指定した `aws ecs update-service` を実行し、ECS タスク実行ロールに RDS 管理シークレットの取得権限が付与されていることを確認してください。DB パスワードを API キー用シークレットへ手動登録する必要はありません。

### Q3: 外からAPIへのHTTPリクエストがタイムアウトする
* **原因**: タスクまたはALBのセキュリティグループにおいて、接続元マシンのグローバルIPが正しく制限（許可）されていない可能性があります。
* **対処**: ご自身の現在のグローバルIPアドレスを確認し、`terraform.tfvars` 内の `allowed_ingress_cidr` を正しい値（例: `["YOUR_NEW_IP/32"]`）に更新して `terraform apply` を再実行してください。
