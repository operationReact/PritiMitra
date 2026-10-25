# Regression guard for the Cognito app-client flow contract.
#
# Refresh token rotation is incompatible with ALLOW_REFRESH_TOKEN_AUTH (AWS: "Refresh token rotation isn't compatible
# with the authentication flow REFRESH_TOKEN_AUTH"). Rotated refreshes must use GetTokensFromRefreshToken.
# These runs use a mocked provider and only plan, so no AWS credentials or resources are needed.

mock_provider "aws" {}

variables {
  environment         = "dev"
  project_name        = "pritimitra"
  root_domain         = "example.com"
  mail_subdomain      = "mail"
  mail_from_subdomain = "bounce"
  mail_from_address   = "no-reply@mail.example.com"
  route53_zone_id     = "Z0000000000000000"
  manage_dns_records  = false
  cognito_callback_urls = ["https://localhost:3000/api/auth/callback"]
  cognito_logout_urls   = ["https://localhost:3000/"]
  github_repository     = "operationReact/PritiMitra"
}

run "mobile_client_uses_srp_without_refresh_token_auth" {
  command = plan

  assert {
    condition     = aws_cognito_user_pool_client.app.explicit_auth_flows == toset(["ALLOW_USER_SRP_AUTH"])
    error_message = "Mobile app client must allow only ALLOW_USER_SRP_AUTH. ALLOW_REFRESH_TOKEN_AUTH is incompatible with refresh token rotation."
  }

  assert {
    condition     = aws_cognito_user_pool_client.app.refresh_token_rotation[0].feature == "ENABLED"
    error_message = "Mobile app client must keep refresh token rotation enabled."
  }
}

run "web_client_uses_password_auth_without_refresh_token_auth" {
  command = plan

  assert {
    condition     = aws_cognito_user_pool_client.web.explicit_auth_flows == toset(["ALLOW_USER_PASSWORD_AUTH"])
    error_message = "Web app client must allow only ALLOW_USER_PASSWORD_AUTH. ALLOW_REFRESH_TOKEN_AUTH is incompatible with refresh token rotation."
  }

  assert {
    condition     = aws_cognito_user_pool_client.web.refresh_token_rotation[0].feature == "ENABLED"
    error_message = "Web app client must keep refresh token rotation enabled."
  }

  assert {
    condition     = aws_cognito_user_pool_client.web.generate_secret == false
    error_message = "Web app client must not generate a client secret; it is a public client behind the BFF."
  }
}

run "rotation_grace_period_is_applied_to_both_clients" {
  command = plan

  variables {
    cognito_refresh_retry_grace_seconds = 10
  }

  assert {
    condition     = aws_cognito_user_pool_client.app.refresh_token_rotation[0].retry_grace_period_seconds == 10 && aws_cognito_user_pool_client.web.refresh_token_rotation[0].retry_grace_period_seconds == 10
    error_message = "Both app clients must apply the configured rotation retry grace period."
  }
}
