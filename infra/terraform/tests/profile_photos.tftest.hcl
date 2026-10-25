# Plan-only checks for the private profile photo bucket. Mocked provider, no AWS credentials or resources.

mock_provider "aws" {}

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
