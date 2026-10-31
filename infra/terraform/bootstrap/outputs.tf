output "state_bucket_name" {
  description = "Bucket name to configure in infra/terraform/backend.hcl."
  value       = aws_s3_bucket.terraform_state.bucket
}

output "aws_account_id" {
  description = "AWS account ID containing the Terraform state bucket."
  value       = data.aws_caller_identity.current.account_id
}
