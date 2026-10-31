# Plan-only checks for the private profile photo bucket. Mocked provider, no AWS credentials or resources.

mock_provider "aws" {
  override_resource {
    target = aws_s3_bucket.profile_photos
    values = {
      arn    = "arn:aws:s3:::pritimitra-profile-photos-dev-123456789012"
      bucket = "pritimitra-profile-photos-dev-123456789012"
    }
  }
}

variables {
  environment           = "dev"
  project_name          = "pritimitra"
  root_domain           = "example.com"
  mail_subdomain        = "mail"
  mail_from_subdomain   = "bounce"
  mail_from_address     = "no-reply@mail.example.com"
  route53_zone_id       = "Z0000000000000000"
  manage_dns_records    = false
  cognito_callback_urls = ["https://localhost:3000/api/auth/callback"]
  cognito_logout_urls   = ["https://localhost:3000/"]
  github_repository     = "operationReact/PritiMitra"
}

run "profile_photo_bucket_is_private_and_encrypted" {
  command = plan

  assert {
    condition     = aws_s3_bucket_public_access_block.profile_photos.block_public_acls == true && aws_s3_bucket_public_access_block.profile_photos.block_public_policy == true && aws_s3_bucket_public_access_block.profile_photos.ignore_public_acls == true && aws_s3_bucket_public_access_block.profile_photos.restrict_public_buckets == true
    error_message = "Profile photo bucket must block all public access."
  }

  assert {
    condition     = contains([for r in aws_s3_bucket_server_side_encryption_configuration.profile_photos.rule : r.apply_server_side_encryption_by_default[0].sse_algorithm], "AES256")
    error_message = "Profile photo bucket must enable server-side encryption."
  }

  assert {
    condition     = contains([for r in aws_s3_bucket_ownership_controls.profile_photos.rule : r.object_ownership], "BucketOwnerEnforced")
    error_message = "Profile photo bucket must enforce bucket-owner object ownership (ACLs disabled)."
  }
}

run "profile_photo_bucket_denies_insecure_transport_only" {
  command = plan

  assert {
    condition     = length([for s in jsondecode(aws_s3_bucket_policy.profile_photos.policy).Statement : s if s.Effect == "Deny" && s.Condition.Bool["aws:SecureTransport"] == "false"]) == 1
    error_message = "Profile photo bucket policy must deny requests that are not TLS."
  }

  assert {
    condition     = length([for s in jsondecode(aws_s3_bucket_policy.profile_photos.policy).Statement : s if s.Effect == "Allow"]) == 0
    error_message = "Profile photo bucket policy must not grant any public Allow statement."
  }
}

run "profile_photo_bucket_lifecycle_aborts_stale_uploads" {
  command = plan

  assert {
    condition     = aws_s3_bucket_lifecycle_configuration.profile_photos.rule[0].abort_incomplete_multipart_upload[0].days_after_initiation == 1
    error_message = "Incomplete multipart photo uploads must be aborted after one day."
  }
}
