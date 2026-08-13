# ══════════════════════════════════════════════════
# AWS Load Balancer Controller
# ══════════════════════════════════════════════════
# k8s/ingress-with-origin-verify.yaml의 `kubernetes.io/ingress.class: alb`와
# alb.ingress.kubernetes.io/* 어노테이션은 이 컨트롤러가 있어야 ALB를 생성함.
# 컨트롤러 없이는 Ingress를 배포해도 아무 일도 일어나지 않음.
module "irsa_aws_load_balancer_controller" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.39"

  role_name = "${var.cluster_name}-aws-load-balancer-controller"

  attach_load_balancer_controller_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:aws-load-balancer-controller"]
    }
  }
}

resource "helm_release" "aws_load_balancer_controller" {
  name       = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  namespace  = "kube-system"

  set {
    name  = "clusterName"
    value = var.cluster_name
  }

  set {
    name  = "region"
    value = var.aws_region
  }

  set {
    name  = "vpcId"
    value = module.vpc.vpc_id
  }

  set {
    name  = "serviceAccount.name"
    value = "aws-load-balancer-controller"
  }

  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = module.irsa_aws_load_balancer_controller.iam_role_arn
  }

  depends_on = [module.eks, module.irsa_aws_load_balancer_controller]
}
