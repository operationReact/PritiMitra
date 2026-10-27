terraform {
  # S3 native state locking requires Terraform 1.10 or newer.
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.90"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "pritimitra"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
