terraform {
  backend "s3" {
    # Native S3 lockfiles require Terraform 1.10 or newer.
    use_lockfile = true
  }
}
