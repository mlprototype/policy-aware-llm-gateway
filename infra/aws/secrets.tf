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

# 2. Secrets Manager Secret Version（Gateway API キー用初期 JSON テンプレート）の定義
# 初回適用時にダミースキーマを作成し、実際のシークレット値は手動または CLI で上書き更新する運用にします。
# DB 認証情報は RDS の管理シークレットで別途管理し、このシークレットには含めません。
resource "aws_secretsmanager_secret_version" "keys_init" {
  count     = var.enable_secrets_manager ? 1 : 0
  secret_id = aws_secretsmanager_secret.keys[0].id

  secret_string = jsonencode({
    openai_api_key    = "dummy-openai-key"
    anthropic_api_key = "dummy-anthropic-key"
    gateway_api_key   = "dummy-gateway-key"
  })

  # 実運用ベストプラクティス:
  # シークレットの中身（値）は運用中にコンソールやCLIで直接書き換えられます。
  # 再度 terraform apply を実行した際に、コード内のダミー値で実際の商用キーが上書き（先祖返り）するのを防ぐために ignore_changes を設定します。
  lifecycle {
    ignore_changes = [
      secret_string
    ]
  }
}
