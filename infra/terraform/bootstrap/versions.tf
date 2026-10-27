terraform {
  required_version = ">= 1.10.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.90"
    }
  }
}

variable "aws_region" {
  description = "AWS region for the Terraform state bucket."
  type        = string
  default     = "ap-south-1"
}

provider "aws" {
  region = var.aws_region
}
