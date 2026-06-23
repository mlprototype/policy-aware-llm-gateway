# Multi-LLM Gateway AWS Infrastructure (Terraform)

本ディレクトリには、`Policy-Aware Multi-LLM Gateway` をAWS環境へ展開するためのTerraformテンプレートが格納されています。
本構成は、セキュリティ基準（Secrets ManagerやIAM最小権限）を満たしつつ、**常時稼働コストを極小化する設計（Zero-Idle Architecture）**を採用しています。

---

## 1. ディレクトリ概要

- `providers.tf` : AWSプロバイダーとプロジェクトタグ定義
- `variables.tf` : 各トグルスイッチと低コスト検証用のデフォルト値定義
- `main.tf`      : ECS, ECR, IAM, Security Group およびオプショナルリソース（ALB, RDS, Redis）
- `secrets.tf`   : AWS Secrets Manager（JSONによるAPIキー等の集約管理）
- `dashboard.tf` : CloudWatchカスタムダッシュボード（無料枠対応）
- `outputs.tf`   : デプロイ結果の接続エンドポイント情報

---

## 2. デプロイ手順

### ステップ 1: 事前準備
1. AWS CLI および Terraform（>= 1.10.0）をローカルにインストールします。
2. 適切なIAM権限（管理者権限またはECS/RDS/SecretsManager等の操作権限）を設定し、`aws configure` 等でログインします。
3. 本ディレクトリに `terraform.tfvars` を作成し、[terraform.tfvars.example](file:///Users/apple/develop/policy-aware-llm-gateway/infra/aws/terraform.tfvars.example) をコピーして既存のVPC ID、サブネットID、アクセス元のグローバルIPアドレスを設定します。

> [!NOTE]
> Terraform state は S3 backend（`multi-llm-gateway-tfstate-759655305163-ap-northeast-1-an`）で管理します。初回の `terraform init` 前に S3 バケットを作成し、既存のローカル state がある場合は `terraform init -migrate-state` で移行してください。詳細は [AWS Deployment Guide](file:///Users/apple/develop/policy-aware-llm-gateway/docs/AWS_DEPLOYMENT.md) を参照してください。

### ステップ 2: 初期化とリポジトリの作成
インフラの土台となるECRリポジトリだけを先行して作成します。

```bash
# Terraformの初期化
terraform init

# ECRリポジトリの作成
terraform apply -target=aws_ecr_repository.app
```

出力された `ecr_repository_url` を控えます。

### ステップ 3: コンテナイメージのプッシュ
ローカルでGatewayのDockerイメージをビルドし、ECRにプッシュします。

```bash
# ECRへのログイン認証
aws ecr get-login-password --region ap-northeast-1 | docker login --username AWS --password-stdin <YOUR_AWS_ACCOUNT_ID>.dkr.ecr.ap-northeast-1.amazonaws.com

# コンテナのビルド（Java jarの作成後）
./gradlew bootJar
docker build -t multi-llm-gateway .

# ECRタグ付けとプッシュ
docker tag multi-llm-gateway:latest <YOUR_ECR_REPOSITORY_URL>:latest
docker push <YOUR_ECR_REPOSITORY_URL>:latest
```

### ステップ 4: インフラ全体を展開 (Fargate起動数は 0)
コンテナイメージの準備ができたら、インフラ定義全体を適用します。

```bash
terraform apply
```

この段階では、`desired_count = 0` のため、Fargateコンテナは起動せず、課金はほぼ発生しません。

---

## 3. シークレット（API Key等）の初期設定

`secrets.tf` で作成された AWS Secrets Manager のシークレットには、安全性の観点から**ダミーの API キー**が登録されています。ECS タスクを起動する前に、実際の API キーに更新してください。

> [!NOTE]
> `enable_rds = true` の場合、RDS のマスター認証情報はこのシークレットとは別に RDS が作成・管理します。DB のユーザー名・パスワードをこの JSON に追加・更新しないでください。ECS は RDS 管理シークレットを直接参照するため、DB パスワードは Terraform、`terraform.tfvars`、Git に保存されません。

> [!CAUTION]
> **セキュリティ上の最重要注意点**
> **実際のAPI KeyをGit管理対象ファイル、tfvars、Terraformコードに記述しないこと。**
> 漏洩やTerraform Stateへのプレーンテキスト記録を回避するため、必ずインフラ構築（`terraform apply`）後にSecrets Managerへ直接値を設定してください。

### 更新手順 (AWS CLI の場合)
以下のコマンドを実行し、実際のAPIキーをJSONオブジェクトとしてSecrets Managerに直接書き込みます。

```bash
aws secretsmanager put-secret-value \
  --secret-id multi-llm-gateway-api-keys \
  --secret-string '{
    "openai_api_key": "sk-proj-YOUR-ACTUAL-OPENAI-KEY",
    "anthropic_api_key": "sk-ant-YOUR-ACTUAL-ANTHROPIC-KEY",
    "gateway_api_key": "dev-gateway-key-001"
  }'
```

※ AWSマネジメントコンソールの「Secrets Manager」画面（`multi-llm-gateway-api-keys`）を開き、GUI上の「シークレット値を設定する」ボタンからJSONオブジェクトを直接編集して更新することも可能です。

RDS を有効にした場合は、以下で RDS 管理シークレットの ARN のみを確認できます。シークレット値をアプリ以外に配布する必要はありません。

```bash
terraform output -raw rds_master_user_secret_arn
```

---

## 4. オンデマンド検証とクリーンアップ

### 4.1 ECSタスクを一時的に起動して検証する
結合テストを実行するときのみ、ECSの起動数を `1` にスケールアウトします。

```bash
# terraform.tfvars 内で desired_count = 1 に書き換えるか、コマンドライン引数で渡します
terraform apply -var="desired_count=1"
```

デプロイ完了後、`outputs.tf` に定義されたECSのPublic IP（またはALBを有効化した場合はALBのDNS名）がターミナルに出力されます。

```bash
# 疎通確認 (allowed_ingress_cidrで許可した接続元からのみアクセス可能)
curl -i http://<ECS_PUBLIC_IP>:8080/actuator/health
```

> [!IMPORTANT]
> **データベース接続に関する注意点 (`enable_rds = false` 時)**
> 本システムは起動時にPostgreSQLへの接続を必須とします。そのため、`enable_rds = false`（データベースを作成しない状態）で `desired_count = 1` にスケールアウトすると、コンテナはデータベース接続エラーとなり起動に失敗（クラッシュループ）します。AWS上での疎通および動作検証を行う際は、必ず一時的に `enable_rds = true` を設定してRDSインスタンスを同時にプロビジョニングしてください。

RDS を有効にすると、RDS が Secrets Manager に DB マスター認証情報を生成・管理します。ECS はタスク起動時にこのシークレットから DB ユーザー名とパスワードを取得するため、DB パスワードを手動で作成・設定する手順は不要です。RDS のローテーション成功イベントは EventBridge と SSM Automation を経由して ECS の新規デプロイを開始するため、新しいタスクはローテーション後の認証情報を取得します。

既存の ECS サービスで RDS を後から有効化する場合は、Terraform 適用後にサービスを最新タスク定義へ切り替えてからスケールアウトしてください。通常は GitHub Actions のデプロイワークフローで実施しますが、CLI では次のとおりです。

```bash
aws ecs update-service \
  --cluster "$(terraform output -raw ecs_cluster_name)" \
  --service "$(terraform output -raw ecs_service_name)" \
  --task-definition "$(terraform output -raw ecs_task_definition_arn)" \
  --force-new-deployment
```


### 4.2 検証後のスケールダウン (常時課金防止)
検証が終わったら、無駄なコンテナ稼働コストの発生を防ぐために、即座にタスク数を `0` に戻します。

```bash
terraform apply -var="desired_count=0"
```

### 4.3 有料リソースの選別破棄 (Selective Cleanup)
ALB、RDS、Redisなどの高コストなリソースを一時的に構築してテストしていた場合、ECRのコンテナイメージやTerraform State、IAM設定は再構築の手間を省くために**維持**し、有料リソースだけを**ピンポイントで削除**します。

```bash
# terraform.tfvars 内で各トグルを false に戻して適用します
terraform apply \
  -var="desired_count=0" \
  -var="enable_alb=false" \
  -var="enable_rds=false" \
  -var="enable_redis=false"
```
これにより、ECR内に保存されたイメージやSecrets Managerは残したまま、ALBやRDSインスタンス、RedisキャッシュのみがAWS上から削除され、固定費を月額約 $0.40 (Secrets Manager分) のみに戻すことができます。

すべてのインフラを完全にクリーンアップして初期状態に戻す場合は、以下を実行します。
```bash
terraform destroy
```

---

## 5. GitHub Actions OIDC 認証用 IAM Role 設定

GitHub Actionsから安全にAWS ECRへプッシュおよびECSへデプロイを行うため、静的なアクセスキーではなく **OIDC（OpenID Connect）信頼関係** を用いたIAMロールを作成します。

### 1. AWS IAM OIDC IDプロバイダーの作成 (初回のみ)
AWS IAMコンソールで「IDプロバイダ」を追加します。
- **プロバイダのタイプ**: OpenID Connect
- **プロバイダのURL**: `https://token.actions.githubusercontent.com`
- **対象者 (Audience)**: `sts.amazonaws.com`

### 2. IAM ロールの作成と信頼関係（Trust Policy）の設定
GitHub Actionsが一時的な認証トークンを取得できるように、以下の信頼関係ポリシーを持つIAMロール（例: `github-actions-ecs-deploy-role`）を作成します。

**信頼関係ポリシー (Trust Relationship Policy):**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<YOUR_AWS_ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:<YOUR_GITHUB_ORGANIZATION_OR_USER>/policy-aware-llm-gateway:*"
        }
      }
    }
  ]
}
```

### 3. IAM ロールへのアクセス権限ポリシーの付与
作成したロールに、ECRへのログイン・イメージプッシュ、およびECSタスク定義の登録・更新を行うための最小限のポリシーを付与します。

**ロールに付与するカスタムポリシー例:**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:PutImage"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ecs:RegisterTaskDefinition",
        "ecs:DescribeTaskDefinition",
        "ecs:DescribeServices",
        "ecs:DescribeTasks",
        "ecs:ListTasks",
        "ecs:ExecuteCommand",
        "ecs:UpdateService",
        "ec2:DescribeVpcs"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "iam:PassRole"
      ],
      "Resource": [
        "arn:aws:iam::<YOUR_AWS_ACCOUNT_ID>:role/multi-llm-gateway-ecs-execution-role",
        "arn:aws:iam::<YOUR_AWS_ACCOUNT_ID>:role/multi-llm-gateway-ecs-task-role"
      ]
    }
  ]
}
```
*(※ `<YOUR_AWS_ACCOUNT_ID>` や `<YOUR_GITHUB_ORGANIZATION_OR_USER>` は実際の環境に置き換えてください)*
