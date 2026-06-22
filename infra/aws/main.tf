# ==========================================
# 1. ネットワークデータソース (既存VPC流用)
# ==========================================

data "aws_vpc" "selected" {
  id = var.vpc_id
}

# ==========================================
# 2. ECR Repository & Lifecycle Policy
# ==========================================

resource "aws_ecr_repository" "app" {
  name                 = "${var.project_name}-repo"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last ${var.ecr_image_retention_count} images to optimize storage costs"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.ecr_image_retention_count
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

# ==========================================
# 3. CloudWatch Log Group
# ==========================================

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.project_name}"
  retention_in_days = var.log_retention_in_days
}

# ==========================================
# 4. Security Groups
# ==========================================

# ECS タスク用セキュリティグループ
resource "aws_security_group" "ecs_task" {
  name        = "${var.project_name}-ecs-task-sg"
  description = "Access control for ECS Fargate Task"
  vpc_id      = data.aws_vpc.selected.id

  # インバウンドルール：ALB経由の場合はALBからのみ、ALB無効時は指定CIDRから直接ポートを許可
  ingress {
    description     = "Allow HTTP traffic from ALB or direct allowed CIDR"
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = var.enable_alb ? aws_security_group.alb[*].id : []
    cidr_blocks     = var.enable_alb ? [] : var.allowed_ingress_cidr
  }

  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  tags = { Name = "${var.project_name}-ecs-task-sg" }
}

# ALB用セキュリティグループ (ALB有効時のみ作成)
resource "aws_security_group" "alb" {
  count       = var.enable_alb ? 1 : 0
  name        = "${var.project_name}-alb-sg"
  description = "Access control for Application Load Balancer"
  vpc_id      = data.aws_vpc.selected.id

  ingress {
    description = "Allow HTTP traffic from allowed CIDR"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = var.allowed_ingress_cidr
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-alb-sg" }
}

# RDS用セキュリティグループ (RDS有効時のみ作成)
resource "aws_security_group" "db" {
  count       = var.enable_rds ? 1 : 0
  name        = "${var.project_name}-db-sg"
  description = "Access control for RDS PostgreSQL"
  vpc_id      = data.aws_vpc.selected.id

  ingress {
    description     = "Allow connection from ECS tasks"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_task.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-db-sg" }
}

# Redis用セキュリティグループ (Redis有効時のみ作成)
resource "aws_security_group" "redis" {
  count       = var.enable_redis ? 1 : 0
  name        = "${var.project_name}-redis-sg"
  description = "Access control for ElastiCache Redis"
  vpc_id      = data.aws_vpc.selected.id

  ingress {
    description     = "Allow connection from ECS tasks"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_task.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-redis-sg" }
}

# ==========================================
# 5. IAM Roles for ECS
# ==========================================

# ECS タスク実行ロール (ECRイメージのプル、CloudWatchへのログ保存用)
resource "aws_iam_role" "ecs_execution" {
  name = "${var.project_name}-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution_standard" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# ECS が参照するシークレット（Gateway API キーおよび RDS 管理認証情報）の取得権限。
# RDS を有効にした場合、RDS が生成・ローテーションするマスターシークレットを
# ECS タスク実行ロールから参照する。
resource "aws_iam_policy" "ecs_secrets_access" {
  count       = var.enable_secrets_manager || var.enable_rds ? 1 : 0
  name        = "${var.project_name}-secrets-policy"
  description = "Allow ECS execution role to retrieve required secret values"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = concat(
          var.enable_secrets_manager ? [aws_secretsmanager_secret.keys[0].arn] : [],
          var.enable_rds ? [aws_db_instance.postgres[0].master_user_secret[0].secret_arn] : []
        )
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution_secrets" {
  count      = var.enable_secrets_manager || var.enable_rds ? 1 : 0
  role       = aws_iam_role.ecs_execution.name
  policy_arn = aws_iam_policy.ecs_secrets_access[0].arn
}

# ECS タスクロール (コンテナ内アプリケーションがAWSサービスを呼び出す用 - Bedrock等)
resource "aws_iam_role" "ecs_task" {
  name = "${var.project_name}-ecs-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}

# ECS Exec の SSM エージェントがタスクから制御・データチャネルを開くための最小権限。
# GitHub Actions の smoke test はこの経路で localhost のヘルスチェックを実行する。
resource "aws_iam_policy" "ecs_exec" {
  name        = "${var.project_name}-ecs-exec-policy"
  description = "Allow ECS Exec agent to communicate with SSM Session Manager"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ssmmessages:CreateControlChannel",
          "ssmmessages:CreateDataChannel",
          "ssmmessages:OpenControlChannel",
          "ssmmessages:OpenDataChannel",
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_exec" {
  role       = aws_iam_role.ecs_task.name
  policy_arn = aws_iam_policy.ecs_exec.arn
}

# Bedrockの呼び出し権限を付与 (本番想定AI基盤としての実運用アピール)
resource "aws_iam_policy" "bedrock_access" {
  name        = "${var.project_name}-bedrock-policy"
  description = "Allow AI Gateway to invoke Amazon Bedrock foundation models"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream"]
        Resource = ["*"]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_bedrock" {
  role       = aws_iam_role.ecs_task.name
  policy_arn = aws_iam_policy.bedrock_access.arn
}

# ==========================================
# 6. ECS Cluster & Service & Task Definition
# ==========================================

resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-cluster"
}

# ECS タスク定義
resource "aws_ecs_task_definition" "app" {
  family                   = var.project_name
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  # シークレットを参照するタスクを登録する前に、タスク実行ロールの権限を確実に付与する。
  depends_on = [
    aws_iam_role_policy_attachment.ecs_execution_standard,
    aws_iam_role_policy_attachment.ecs_execution_secrets,
    aws_iam_role_policy_attachment.ecs_task_exec,
  ]

  container_definitions = jsonencode([
    {
      name      = "gateway"
      image     = var.container_image != "" ? var.container_image : "${aws_ecr_repository.app.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = var.container_port
          hostPort      = var.container_port
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.app.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "gateway"
        }
      }

      # コンテナ環境変数設定
      environment = concat(
        [
          # AWS用のプロファイルを指定
          {
            name  = "SPRING_PROFILES_ACTIVE"
            value = "aws"
          },
          {
            name  = "SERVER_PORT"
            value = tostring(var.container_port)
          }
        ],
        # RDS有効時はエンドポイントだけを環境変数にインジェクションする。
        # 認証情報は RDS 管理 Secrets Manager シークレットから secrets で注入する。
        var.enable_rds ? [
          {
            name  = "SPRING_DATASOURCE_URL"
            value = "jdbc:postgresql://${aws_db_instance.postgres[0].endpoint}/${aws_db_instance.postgres[0].db_name}"
          }
        ] : [],
        # Redis有効時は接続先を環境変数にインジェクション
        var.enable_redis ? [
          {
            name  = "SPRING_DATA_REDIS_HOST"
            value = aws_elasticache_cluster.redis[0].cache_nodes[0].address
          },
          {
            name  = "SPRING_DATA_REDIS_PORT"
            value = tostring(aws_elasticache_cluster.redis[0].port)
          }
        ] : []
      )

      # Gateway の API キーと、RDS が管理する DB 認証情報を個別に環境変数へマッピングする。
      # RDS のシークレットは RDS 自身が生成・ローテーションするため、Terraform や tfvars に
      # DB パスワードを保持しない。
      secrets = concat(
        var.enable_secrets_manager ? [
          {
            name      = "OPENAI_API_KEY"
            valueFrom = "${aws_secretsmanager_secret.keys[0].arn}:openai_api_key::"
          },
          {
            name      = "ANTHROPIC_API_KEY"
            valueFrom = "${aws_secretsmanager_secret.keys[0].arn}:anthropic_api_key::"
          },
          {
            name      = "GATEWAY_API_KEY"
            valueFrom = "${aws_secretsmanager_secret.keys[0].arn}:gateway_api_key::"
          }
        ] : [],
        var.enable_rds ? [
          {
            name      = "SPRING_DATASOURCE_USERNAME"
            valueFrom = "${aws_db_instance.postgres[0].master_user_secret[0].secret_arn}:username::"
          },
          {
            name      = "SPRING_DATASOURCE_PASSWORD"
            valueFrom = "${aws_db_instance.postgres[0].master_user_secret[0].secret_arn}:password::"
          }
        ] : []
      )
    }
  ])
}

# ECS サービス
resource "aws_ecs_service" "app" {
  name            = "${var.project_name}-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  # GitHub Actions の smoke test は ECS Exec でタスク内の localhost を検証する。
  # 外部公開用の Security Group に CI ランナーの IP を追加する必要はない。
  enable_execute_command = true

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [aws_security_group.ecs_task.id]
    assign_public_ip = true # NAT Gatewayを省略するためPublic IPを直接アサイン
  }

  dynamic "load_balancer" {
    for_each = var.enable_alb ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.app[0].arn
      container_name   = "gateway"
      container_port   = var.container_port
    }
  }

  # タスク定義の外部更新（CI/CD経由など）を許容する。
  # desired_count は Terraform のスケール操作で必ず反映させるため無視しない。
  lifecycle {
    ignore_changes = [task_definition]

    # 誤設定によるクラッシュループの事前検知 (desired_count > 0 の場合は enable_rds = true を必須とする)
    precondition {
      condition     = !(var.desired_count > 0 && !var.enable_rds)
      error_message = "Cannot set desired_count > 0 when enable_rds is false. The ECS task requires a database connection to boot."
    }
  }
}

# ==========================================
# 7. Application Load Balancer (Optional)
# ==========================================

resource "aws_lb" "app" {
  count              = var.enable_alb ? 1 : 0
  name               = "${var.project_name}-${var.environment}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb[0].id]
  subnets            = var.public_subnet_ids

  tags = { Name = "${var.project_name}-alb" }
}

resource "aws_lb_target_group" "app" {
  count       = var.enable_alb ? 1 : 0
  name        = "${var.project_name}-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = data.aws_vpc.selected.id
  target_type = "ip"

  health_check {
    path                = "/actuator/health" # Spring Boot Actuatorを利用したヘルスチェック
    healthy_threshold   = 3
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }
}

resource "aws_lb_listener" "http" {
  count             = var.enable_alb ? 1 : 0
  load_balancer_arn = aws_lb.app[0].arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app[0].arn
  }
}

# ==========================================
# 8. RDS PostgreSQL Instance (Optional)
# ==========================================

resource "aws_db_subnet_group" "db" {
  count      = var.enable_rds ? 1 : 0
  name       = "${var.project_name}-db-subnet-group"
  subnet_ids = var.public_subnet_ids
  tags       = { Name = "${var.project_name}-db-subnet-group" }
}

resource "aws_db_instance" "postgres" {
  count                 = var.enable_rds ? 1 : 0
  identifier            = "${var.project_name}-${var.environment}-db"
  allocated_storage     = 20
  max_allocated_storage = 100
  engine                = "postgres"
  engine_version        = "16.14"
  instance_class        = "db.t4g.micro" # 最小・最新のバーストパフォーマンスクラス
  db_name               = "gatewaydb"
  username              = "dbuser"
  # RDS が生成・保管・ローテーションする Secrets Manager の認証情報を利用する。
  # password を Terraform に渡さないため、state に DB パスワードが残らない。
  manage_master_user_password = true
  skip_final_snapshot         = true
  vpc_security_group_ids      = [aws_security_group.db[0].id]
  db_subnet_group_name        = aws_db_subnet_group.db[0].name
  publicly_accessible         = true # 検証のしやすさを優先

  tags = { Name = "${var.project_name}-rds" }
}

# ==========================================
# 9. ElastiCache Redis Cluster (Optional)
# ==========================================

resource "aws_elasticache_subnet_group" "redis" {
  count      = var.enable_redis ? 1 : 0
  name       = "${var.project_name}-redis-subnet-group"
  subnet_ids = var.public_subnet_ids
}

resource "aws_elasticache_cluster" "redis" {
  count                = var.enable_redis ? 1 : 0
  cluster_id           = "${var.project_name}-redis"
  engine               = "redis"
  node_type            = "cache.t4g.micro" # 最小・最新世代のキャッシュインスタンス
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  port                 = 6379
  security_group_ids   = [aws_security_group.redis[0].id]
  subnet_group_name    = aws_elasticache_subnet_group.redis[0].name

  tags = { Name = "${var.project_name}-redis" }
}
