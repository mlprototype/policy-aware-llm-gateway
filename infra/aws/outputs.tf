# ==========================================
# Terraform Outputs
# ==========================================

output "ecr_repository_url" {
  value       = aws_ecr_repository.app.repository_url
  description = "ECRリポジトリのURL。Dockerイメージのプッシュ先に使用します"
}

output "ecs_cluster_name" {
  value       = aws_ecs_cluster.main.name
  description = "ECSクラスター名"
}

output "ecs_service_name" {
  value       = aws_ecs_service.app.name
  description = "ECSサービス名"
}

output "ecs_task_definition_arn" {
  value       = aws_ecs_task_definition.app.arn
  description = "Terraform が管理する最新 ECS タスク定義 ARN"
}

output "secrets_manager_secret_arn" {
  value       = var.enable_secrets_manager ? aws_secretsmanager_secret.keys[0].arn : "Secrets Manager is disabled"
  description = "AWS Secrets ManagerのARN"
}

# ALB有効時のみエンドポイントを出力
output "alb_dns_name" {
  value       = var.enable_alb ? aws_lb.app[0].dns_name : "ALB is disabled (Direct task access required)"
  description = "Application Load BalancerのDNS名"
}

# RDS有効時のみDB接続情報を出力
output "db_endpoint" {
  value       = var.enable_rds ? aws_db_instance.postgres[0].endpoint : "RDS PostgreSQL is disabled"
  description = "RDS PostgreSQLのエンドポイント"
}

output "rds_master_user_secret_arn" {
  value       = var.enable_rds ? aws_db_instance.postgres[0].master_user_secret[0].secret_arn : "RDS PostgreSQL is disabled"
  description = "RDS が管理する DB マスター認証情報の Secrets Manager ARN（値そのものは出力しない）"
}

# Redis有効時のみ接続先を出力
output "redis_endpoint" {
  value       = var.enable_redis ? aws_elasticache_cluster.redis[0].cache_nodes[0].address : "ElastiCache Redis is disabled"
  description = "ElastiCache Redisの接続アドレス"
}
