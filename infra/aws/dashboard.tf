# ==========================================
# CloudWatch Custom Dashboard Configuration
# ==========================================

variable "enable_dashboard" {
  type        = bool
  default     = true
  description = "CloudWatchカスタムダッシュボードを作成するかどうか（無料枠3つのうち1つを消費）"
}

resource "aws_cloudwatch_dashboard" "ecs_gateway" {
  count          = var.enable_dashboard ? 1 : 0
  dashboard_name = "${var.project_name}-${var.environment}-dashboard"

  dashboard_body = jsonencode({
    widgets = [
      # 1. ECS CPU使用率
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ServiceName", aws_ecs_service.app.name, "ClusterName", aws_ecs_cluster.main.name]
          ]
          period = 300
          stat   = "Average"
          region = var.aws_region
          title  = "ECS Fargate - CPU Utilization (%)"
        }
      },
      # 2. ECS メモリ使用率
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/ECS", "MemoryUtilization", "ServiceName", aws_ecs_service.app.name, "ClusterName", aws_ecs_cluster.main.name]
          ]
          period = 300
          stat   = "Average"
          region = var.aws_region
          title  = "ECS Fargate - Memory Utilization (%)"
        }
      },
      # 3. 起動タスク数 (desired_count=0のスケール確認用)
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/ECS", "RunningTaskCount", "ServiceName", aws_ecs_service.app.name, "ClusterName", aws_ecs_cluster.main.name]
          ]
          period = 60
          stat   = "Maximum"
          region = var.aws_region
          title  = "ECS Active Running Tasks (Zero-Idle Check)"
        }
      },
      # 4. CloudWatch Logs Insights (直近ログプレビュー)
      {
        type   = "log"
        x      = 12
        y      = 6
        width  = 12
        height = 6
        properties = {
          query  = "SOURCE '${aws_cloudwatch_log_group.app.name}' | fields @timestamp, @message | sort @timestamp desc | limit 20"
          region = var.aws_region
          title  = "CloudWatch Logs - Latest Gateway Application Logs"
        }
      }
    ]
  })
}
