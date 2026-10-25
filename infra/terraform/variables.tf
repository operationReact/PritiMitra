variable "aws_region" {
  description = "AWS region for all regional resources. Proposed default is Mumbai."
  type        = string
  default     = "ap-south-1"
}

variable "environment" {
  description = "Deployment environment name, for example dev, staging or prod."
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of: dev, staging, prod."
  }
}

variable "project_name" {
  description = "Prefix used for resource names."
  type        = string
  default     = "pritimitra"
}

variable "root_domain" {
  description = "Primary domain that owns the Route 53 hosted zone."
  type        = string
  default     = "pritimitra.com"
}

variable "mail_subdomain" {
  description = "Subdomain used as the SES sending identity."
  type        = string
  default     = "mail.pritimitra.com"
}

variable "mail_from_subdomain" {
  description = "Custom MAIL FROM subdomain for SES bounces. Must be a subdomain of mail_subdomain."
  type        = string
  default     = "bounce.mail.pritimitra.com"
}

variable "mail_from_address" {
  description = "Sender address used by Cognito for verification and recovery emails."
  type        = string
  default     = "no-reply@mail.pritimitra.com"
}

variable "route53_zone_id" {
  description = "Existing Route 53 hosted zone ID for root_domain. Never created by this module."
  type        = string
  default     = ""
}

variable "manage_dns_records" {
  description = "When true, Terraform creates the SES verification, DKIM and MAIL FROM records in route53_zone_id. Keep false until DNS changes are authorized."
  type        = bool
  default     = false
}

variable "cognito_callback_urls" {
  description = "Allowed OAuth callback URLs for the web and mobile app clients. Must be https in prod."
  type        = list(string)
  default     = []
}

variable "cognito_logout_urls" {
  description = "Allowed logout URLs for the web and mobile app clients."
  type        = list(string)
  default     = []
}

variable "cognito_deletion_protection" {
  description = "Cognito user pool deletion protection. Keep ACTIVE for prod."
  type        = string
  default     = "ACTIVE"

  validation {
    condition     = contains(["ACTIVE", "INACTIVE"], var.cognito_deletion_protection)
    error_message = "cognito_deletion_protection must be ACTIVE or INACTIVE."
  }
}

variable "cognito_refresh_retry_grace_seconds" {
  description = "Seconds during which a rotated refresh token may be reused, so concurrent or retried refreshes do not log the user out. Cognito allows 0-60."
  type        = number
  default     = 10

  validation {
    condition     = var.cognito_refresh_retry_grace_seconds >= 0 && var.cognito_refresh_retry_grace_seconds <= 60
    error_message = "cognito_refresh_retry_grace_seconds must be between 0 and 60."
  }
}

variable "github_repository" {
  description = "GitHub repository allowed to assume the deploy role through OIDC, in owner/repo form."
  type        = string
  default     = "operationReact/PritiMitra"
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days."
  type        = number
  default     = 30
}
