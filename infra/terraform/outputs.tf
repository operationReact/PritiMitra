output "cognito_user_pool_id" {
  description = "Cognito user pool ID. Set as COGNITO_USER_POOL_ID for the API and web server."
  value       = aws_cognito_user_pool.main.id
}

output "cognito_app_client_id" {
  description = "Public app client ID. Set as COGNITO_CLIENT_ID for web and mobile."
  value       = aws_cognito_user_pool_client.app.id
}

output "cognito_issuer_uri" {
  description = "Issuer URI for Spring Boot JWT validation."
  value       = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
}

output "ses_identity_arn" {
  description = "SES identity ARN used by the Cognito email configuration."
  value       = aws_ses_domain_identity.mail.arn
}

output "ses_dns_records_required" {
  description = "DNS records that must exist for SES verification, DKIM and custom MAIL FROM. Create them manually or set manage_dns_records = true after authorization."
  value = {
    verification_txt = {
      name  = "_amazonses.${var.mail_subdomain}"
      type  = "TXT"
      value = aws_ses_domain_identity.mail.verification_token
    }
    dkim_cname = [
      for token in aws_ses_domain_dkim.mail.dkim_tokens : {
        name  = "${token}._domainkey.${var.mail_subdomain}"
        type  = "CNAME"
        value = "${token}.dkim.amazonses.com"
      }
    ]
    mail_from_mx = {
      name  = var.mail_from_subdomain
      type  = "MX"
      value = "10 feedback-smtp.${var.aws_region}.amazonses.com"
    }
    mail_from_spf = {
      name  = var.mail_from_subdomain
      type  = "TXT"
      value = "v=spf1 include:amazonses.com -all"
    }
  }
}

output "ecr_repository_urls" {
  description = "ECR repository URLs for API and web images."
  value       = { for key, repo in aws_ecr_repository.services : key => repo.repository_url }
}

output "github_deploy_role_arn" {
  description = "IAM role assumed by GitHub Actions through OIDC."
  value       = aws_iam_role.github_deploy.arn
}

output "aws_account_id" {
  description = "AWS account ID that owns these resources."
  value       = data.aws_caller_identity.current.account_id
}
