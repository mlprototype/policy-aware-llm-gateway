# ==============================================================================
# RDS master secret rotation: refresh ECS tasks after RDS changes the password
# ==============================================================================
# ECS injects Secrets Manager values only when a task starts. RDS-managed master
# credentials rotate independently, so a successful rotation must be followed by
# an ECS redeployment to give newly created tasks the current password.

data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

# SSM Automation assumes this role only to request a new ECS service deployment.
resource "aws_iam_role" "rds_secret_rotation_automation" {
  count = var.enable_rds ? 1 : 0
  name  = "${var.project_name}-rds-rotation-automation-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = "sts:AssumeRole"
        Principal = {
          Service = "ssm.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "rds_secret_rotation_automation" {
  count = var.enable_rds ? 1 : 0
  name  = "${var.project_name}-rds-rotation-automation-policy"
  role  = aws_iam_role.rds_secret_rotation_automation[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["ecs:UpdateService"]
        Resource = [aws_ecs_service.app.id]
      }
    ]
  })
}

# The runbook has a fixed, least-privilege assume role. The caller therefore
# cannot choose a more privileged role when it starts the automation.
resource "aws_ssm_document" "restart_ecs_after_rds_secret_rotation" {
  count           = var.enable_rds ? 1 : 0
  name            = "${var.project_name}-restart-ecs-after-rds-rotation"
  document_type   = "Automation"
  document_format = "JSON"

  content = jsonencode({
    schemaVersion = "0.3"
    description   = "Force a new ECS deployment after the RDS-managed master secret rotates."
    assumeRole    = aws_iam_role.rds_secret_rotation_automation[0].arn
    mainSteps = [
      {
        name   = "forceNewEcsDeployment"
        action = "aws:executeAwsApi"
        inputs = {
          Service            = "ecs"
          Api                = "UpdateService"
          cluster            = aws_ecs_cluster.main.name
          service            = aws_ecs_service.app.name
          forceNewDeployment = true
        }
      }
    ]
  })
}

# EventBridge is only allowed to start this one Automation document and to pass
# its fixed execution role to SSM.
resource "aws_iam_role" "eventbridge_rds_secret_rotation" {
  count = var.enable_rds ? 1 : 0
  name  = "${var.project_name}-rds-rotation-eventbridge-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = "sts:AssumeRole"
        Principal = {
          Service = "events.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "eventbridge_rds_secret_rotation" {
  count = var.enable_rds ? 1 : 0
  name  = "${var.project_name}-rds-rotation-eventbridge-policy"
  role  = aws_iam_role.eventbridge_rds_secret_rotation[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["ssm:StartAutomationExecution"]
        Resource = [
          aws_ssm_document.restart_ecs_after_rds_secret_rotation[0].arn,
          "arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:automation-definition/${aws_ssm_document.restart_ecs_after_rds_secret_rotation[0].name}:$DEFAULT",
          "arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:automation-execution/*",
        ]
      },
      {
        Effect   = "Allow"
        Action   = ["iam:PassRole"]
        Resource = [aws_iam_role.rds_secret_rotation_automation[0].arn]
        Condition = {
          StringEquals = {
            "iam:PassedToService" = "ssm.amazonaws.com"
          }
        }
      }
    ]
  })
}

# RotationSucceeded is emitted as an AWS Service Event via CloudTrail. Match the
# RDS-generated secret ARN exactly so provider API-key changes do not restart ECS.
resource "aws_cloudwatch_event_rule" "rds_master_secret_rotated" {
  count       = var.enable_rds ? 1 : 0
  name        = "${var.project_name}-rds-master-secret-rotated"
  description = "Restart ECS tasks after the RDS-managed master secret rotates"

  event_pattern = jsonencode({
    source      = ["aws.secretsmanager"]
    detail-type = ["AWS Service Event via CloudTrail"]
    detail = {
      eventSource = ["secretsmanager.amazonaws.com"]
      eventName   = ["RotationSucceeded"]
      additionalEventData = {
        SecretId = [aws_db_instance.postgres[0].master_user_secret[0].secret_arn]
      }
    }
  })
}

resource "aws_cloudwatch_event_target" "restart_ecs_after_rds_secret_rotation" {
  count     = var.enable_rds ? 1 : 0
  rule      = aws_cloudwatch_event_rule.rds_master_secret_rotated[0].name
  target_id = "RestartEcsService"
  arn       = "arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:automation-definition/${aws_ssm_document.restart_ecs_after_rds_secret_rotation[0].name}:$DEFAULT"
  role_arn  = aws_iam_role.eventbridge_rds_secret_rotation[0].arn
}
