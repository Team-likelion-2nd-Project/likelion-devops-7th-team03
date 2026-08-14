# infra/terraform/ecr.tf
resource "aws_ecr_repository" "management_service" {
  name                 = "management-service"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository" "redirect_service" {
  name                 = "redirect-service"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
}

output "management_service_ecr_url" {
  value = aws_ecr_repository.management_service.repository_url
}

output "redirect_service_ecr_url" {
  value = aws_ecr_repository.redirect_service.repository_url
}