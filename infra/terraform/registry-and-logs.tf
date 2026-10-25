resource "aws_ecr_repository" "services" {
  for_each = toset(["api", "web"])

  name                 = "${var.project_name}-${each.key}-${var.environment}"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "services" {
  for_each   = aws_ecr_repository.services
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 30 most recent images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 30
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "services" {
  for_each = toset(["api", "web"])

  name              = "/pritimitra/${var.environment}/${each.key}"
  retention_in_days = var.log_retention_days
}
