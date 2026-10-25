resource "aws_cognito_user_pool" "main" {
  name                = "${var.project_name}-${var.environment}"
  username_attributes = ["email"]

  auto_verified_attributes = ["email"]
  mfa_configuration        = "OFF"
  deletion_protection      = var.cognito_deletion_protection

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }

  password_policy {
    minimum_length                   = 12
    require_lowercase                = true
    require_uppercase                = true
    require_numbers                  = true
    require_symbols                  = true
    temporary_password_validity_days = 7
  }

  email_configuration {
    email_sending_account  = "DEVELOPER"
    source_arn             = aws_ses_domain_identity.mail.arn
    from_email_address     = var.mail_from_address
    reply_to_email_address = var.mail_from_address
  }

  verification_message_template {
    default_email_option = "CONFIRM_WITH_CODE"
    email_subject        = "Verify your PritiMitra account"
    email_message        = "Your PritiMitra verification code is {####}."
  }

  admin_create_user_config {
    allow_admin_create_user_only = false
  }

  depends_on = [aws_ses_domain_identity.mail]
}

resource "aws_cognito_user_pool_client" "app" {
  name         = "${var.project_name}-${var.environment}-app"
  user_pool_id = aws_cognito_user_pool.main.id

  # Public clients: Flutter and the Next.js server use PKCE/SRP without a client secret stored in the app.
  generate_secret = false

  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]

  prevent_user_existence_errors = "ENABLED"
  enable_token_revocation       = true

  access_token_validity  = 60
  id_token_validity      = 60
  refresh_token_validity = 30

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }

  refresh_token_rotation {
    feature                    = "ENABLED"
    retry_grace_period_seconds = 0
  }

  allowed_oauth_flows_user_pool_client = false
  callback_urls                        = var.cognito_callback_urls
  logout_urls                          = var.cognito_logout_urls
}
