# ==========================================
# 1. プロジェクト基本設定
# ==========================================

variable "project_name" {
  type        = string
  default     = "multi-llm-gateway"
  description = "AWS上のすべてのリソースのプレフィックスとして使用されるプロジェクト名"
}

variable "aws_region" {
  type        = string
  default     = "ap-northeast-1"
  description = "リソースをプロビジョニングするAWSリージョン"
}

variable "environment" {
  type        = string
  default     = "dev"
  description = "適用する環境名 (dev/stg/prod)"

  validation {
    condition     = contains(["dev", "stg", "prod"], var.environment)
    error_message = "環境名は dev, stg, prod のいずれかで指定してください。"
  }
}

# ==========================================
# 2. ネットワーク & セキュリティ設定
# ==========================================

variable "vpc_id" {
  type        = string
  description = "既存のVPC ID (デフォルトVPCなどを指定)"
}

variable "public_subnet_ids" {
  type        = list(string)
  description = "ECSタスクまたはALBを配置する既存のパブリックサブネットIDのリスト"
}

variable "allowed_ingress_cidr" {
  type        = list(string)
  default     = [] # 空リストをデフォルトとし、明示的なIP指定がない場合はアクセスを遮断
  description = "ECSタスクまたはALBへのインバウンドアクセスを許可するCIDRリスト（自身のグローバルIP /32 を推奨）"
}

# ==========================================
# 3. ECSコンテナ設定
# ==========================================

variable "container_port" {
  type        = number
  default     = 8080
  description = "Spring Bootコンテナがリッスンするポート"
}

variable "container_image" {
  type        = string
  default     = ""
  description = "ECSで起動するコンテナイメージのURI"
}

variable "desired_count" {
  type        = number
  default     = 0 # 常時課金を防止するためデフォルトは0
  description = "起動するECS Fargateタスクの数。検証時のみ1以上にスケールアップします"
}

# ==========================================
# 4. コスト最適化 & オプショナルトグル
# ==========================================

variable "enable_alb" {
  type        = bool
  default     = false # デフォルト無効
  description = "ALBおよび関連するTarget Group、Listenerを作成するかどうか"
}

variable "enable_rds" {
  type        = bool
  default     = false # デフォルト無効
  description = "RDS PostgreSQLインスタンスを作成するかどうか"
}

variable "enable_redis" {
  type        = bool
  default     = false # デフォルト無効
  description = "ElastiCache Redisクラスターを作成するかどうか"
}

variable "enable_secrets_manager" {
  type        = bool
  default     = true # 実運用を見据えたセキュリティ基準としてデフォルト有効（月額 $0.40）
  description = "AWS Secrets Managerをプロビジョニングするかどうか"
}

variable "log_retention_in_days" {
  type        = number
  default     = 1 # ログ保管コスト削減のため最短の1日
  description = "CloudWatch Logsにログを保存する日数"

  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90], var.log_retention_in_days)
    error_message = "ログ保持日数は 1, 3, 5, 7, 14, 30, 60, 90 のいずれかで指定してください。"
  }
}

variable "ecr_image_retention_count" {
  type        = number
  default     = 3 # 最新3世代のみ保持してストレージコストを削減
  description = "ECRに保管し続けるコンテナイメージの最大世代数"
}
