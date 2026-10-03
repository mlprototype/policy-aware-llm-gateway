# Plans use a mock provider: no AWS credentials, remote state, or AWS API calls.
mock_provider "aws" {}

variables {
  vpc_id            = "vpc-test"
  public_subnet_ids = ["subnet-test-a", "subnet-test-b"]
}

run "container_insights_disabled_by_default" {
  command = plan

  assert {
    condition     = one(aws_ecs_cluster.main.setting).value == "disabled"
    error_message = "Container Insights must stay opt-in."
  }

  assert {
    condition     = length(jsondecode(aws_cloudwatch_dashboard.ecs_gateway[0].dashboard_body).widgets) == 3 && !strcontains(aws_cloudwatch_dashboard.ecs_gateway[0].dashboard_body, "RunningTaskCount")
    error_message = "The disabled dashboard must omit the unavailable task-count widget."
  }
}

run "container_insights_enabled_explicitly" {
  command = plan

  variables {
    enable_container_insights = true
  }

  assert {
    condition     = one(aws_ecs_cluster.main.setting).value == "enabled"
    error_message = "The explicit opt-in must enable cluster collection."
  }

  assert {
    condition     = length(jsondecode(aws_cloudwatch_dashboard.ecs_gateway[0].dashboard_body).widgets) == 4 && contains(jsondecode(aws_cloudwatch_dashboard.ecs_gateway[0].dashboard_body).widgets[3].properties.metrics[0], "ECS/ContainerInsights") && contains(jsondecode(aws_cloudwatch_dashboard.ecs_gateway[0].dashboard_body).widgets[3].properties.metrics[0], "RunningTaskCount") && contains(jsondecode(aws_cloudwatch_dashboard.ecs_gateway[0].dashboard_body).widgets[3].properties.metrics[0], "ClusterName") && contains(jsondecode(aws_cloudwatch_dashboard.ecs_gateway[0].dashboard_body).widgets[3].properties.metrics[0], "ServiceName")
    error_message = "Task counts must use the real service-level Container Insights metric."
  }
}
