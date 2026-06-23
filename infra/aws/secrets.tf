# ==========================================
# AWS Secrets Manager Configuration (Optional)
# ==========================================

# 1. Secrets Manager Secret の定義
resource "aws_secretsmanager_secret" "keys" {
  count                   = var.enable_secrets_manager ? 1 : 0
  name                    = "${var.project_name}-api-keys"
  description             = "Gateway provider and client API keys"
  recovery_window_in_days = 7 # 個人開発の検証用としてリカバリー期間を短縮（デフォルト30日による削除待機コストを抑制）

  tags = {
    Name = "${var.project_name}-secrets"
  }
}
