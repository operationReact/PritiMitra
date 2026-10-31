# PritiMitra AWS provisioning — development foundation

This is a manual, development-only setup. The current Terraform root configuration provisions the existing foundation only: Cognito, SES identity/configuration, ECR repositories, CloudWatch log groups, the private profile-photo S3 bucket, and the GitHub OIDC deploy role. It does not yet define the application runtime stack (VPC, ECS/Fargate, ALB, or RDS PostgreSQL), so this step alone does not deploy a usable web/mobile API.

## Safety rules

- Use a dedicated AWS development account or a tightly scoped development role. Confirm the account ID before applying.
- Do not paste access keys, session tokens, or credentials into GitHub issues or ChatGPT.
- DNS changes remain disabled by default. Keep manage_dns_records=false until you confirm the Route 53 hosted-zone ID and explicitly decide to create SES records.
- Replace localhost Cognito callback/logout URLs with the intended development URLs before testing sign-in.
- An SES identity declared in Terraform is not proof of domain verification or working email delivery. DNS verification and SES production access are separate steps.
- Review the plan for replacements and deletions before applying. Never apply a production plan from this development guide.

## 1. Authenticate to the intended AWS account

Install AWS CLI v2 and Terraform 1.10 or later. If the account uses IAM Identity Center / SSO, run:

    aws configure sso --profile pritimitra-dev
    aws sso login --profile pritimitra-dev
    AWS_PROFILE=pritimitra-dev aws sts get-caller-identity

Confirm the returned account and role are the intended development account. Do not share credentials; only account/role identity information is needed for troubleshooting.

## 2. Create the remote Terraform state bucket

The bootstrap uses local state for the initial bucket creation only. Keep its state file in a secure local location and never commit it.

    cd infra/terraform/bootstrap
    AWS_PROFILE=pritimitra-dev terraform init
    AWS_PROFILE=pritimitra-dev terraform plan -out=bootstrap.tfplan
    AWS_PROFILE=pritimitra-dev terraform show bootstrap.tfplan
    AWS_PROFILE=pritimitra-dev terraform apply bootstrap.tfplan
    terraform output -raw state_bucket_name

The bucket has versioning, server-side encryption, S3 public access blocks, a TLS-only bucket policy, and a Terraform prevent-destroy lifecycle guard. Native S3 lockfiles are enabled in the main state backend.

## 3. Configure the main Terraform backend

From the repository root:

    cd infra/terraform
    cp backend.hcl.example backend.hcl

Replace ACCOUNT_ID in backend.hcl with the account ID returned by step 1. Then prepare local variables:

    cp dev.tfvars.example dev.tfvars

Review dev.tfvars, especially callback/logout URLs and manage_dns_records. Keep DNS management disabled unless you explicitly intend to change the hosted zone. Do not commit either local file.

Initialize Terraform with the remote backend:

    AWS_PROFILE=pritimitra-dev terraform init -backend-config=backend.hcl
    terraform fmt -check -recursive
    terraform validate
    terraform test
    AWS_PROFILE=pritimitra-dev terraform plan -var-file=dev.tfvars -out=dev.tfplan
    AWS_PROFILE=pritimitra-dev terraform show dev.tfplan

Inspect the plan for the correct AWS account/region and expected resources. If it shows unexpected replacements or deletions, stop and investigate.

## 4. Apply only after reviewing the plan

    AWS_PROFILE=pritimitra-dev terraform apply dev.tfplan

Save the non-secret output values needed by the application configuration, including Cognito pool/client IDs, issuer URI, ECR repository URLs, profile-photo bucket, and GitHub deploy role ARN. Store them in the correct GitHub Environment or local secret manager; do not commit real values.

## Remaining before a real app deployment

- Provision and review the runtime layer: VPC/subnets/security groups, ECS/Fargate services, ALB with a validated HTTPS certificate, and private RDS PostgreSQL.
- Add Dockerfiles and a repeatable image build/deploy pipeline.
- Give the backend runtime role narrowly scoped S3 photo and Cognito administrative permissions.
- Configure the dev GitHub Environment and verify the OIDC trust relationship after the role is provisioned.
- Verify SES DNS and delivery with authorized test users. Do not enable real sign-up until HTTPS, approved legal policy versions/URLs, and onboarding acceptance checks are ready.

No AWS resource has been created by this guide.