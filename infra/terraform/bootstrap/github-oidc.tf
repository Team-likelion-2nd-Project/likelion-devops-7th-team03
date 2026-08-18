# ══════════════════════════════════════════════════
# GitHub Actions OIDC — CI가 정적 IAM 키 대신 단기 세션으로 AWS 접근
# MFA 필수 계정에서도 자동화가 막히지 않도록: IAM User 대신 GitHub의
# OIDC 토큰으로 Role을 assume하는 방식 (신뢰정책의 sub claim으로 통제)
# ══════════════════════════════════════════════════

data "tls_certificate" "github_actions" {
  url = "https://token.actions.githubusercontent.com/.well-known/openid-configuration"
}

resource "aws_iam_openid_connect_provider" "github_actions" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github_actions.certificates[0].sha1_fingerprint]
}

resource "aws_iam_role" "github_actions_ci" {
  name = "snipy-github-actions-ci"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = aws_iam_openid_connect_provider.github_actions.arn
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        }
        # 이미지 push가 실제로 일어나는 develop/main push 이벤트에서만 assume 허용
        # (PR 이벤트나 다른 브랜치에선 이 role을 못 씀)
        StringLike = {
          "token.actions.githubusercontent.com:sub" = [
            "repo:Team-likelion-2nd-Project/likelion-devops-7th-team03:ref:refs/heads/develop",
            "repo:Team-likelion-2nd-Project/likelion-devops-7th-team03:ref:refs/heads/main",
          ]
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "github_actions_ecr_push" {
  name = "snipy-github-actions-ecr-push"
  role = aws_iam_role.github_actions_ci.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "EcrAuth"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Sid    = "EcrPush"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:BatchGetImage",
        ]
        Resource = [
          module.registry.management_service_ecr_arn,
          module.registry.redirect_service_ecr_arn,
        ]
      }
    ]
  })
}

output "github_actions_role_arn" {
  value = aws_iam_role.github_actions_ci.arn
}