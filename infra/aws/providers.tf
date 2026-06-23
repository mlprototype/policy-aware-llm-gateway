terraform {
  # S3 native state locking (use_lockfile) requires Terraform 1.10 or later.
  required_version = ">= 1.10.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # This bucket is bootstrapped outside this state. Do not store credentials here;
  # Terraform uses the local AWS profile or GitHub Actions OIDC credentials.
  backend "s3" {
    bucket       = "multi-llm-gateway-tfstate-759655305163-ap-northeast-1-an"
    key          = "dev/terraform.tfstate"
    region       = "ap-northeast-1"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}
