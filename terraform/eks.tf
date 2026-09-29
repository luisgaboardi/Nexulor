# Kubernetes cluster running the k8s/base manifests (see ../k8s). Data stores
# that stay self-hosted on the cluster (mongo, zipkin) rely on the EBS CSI
# addon below for their PersistentVolumeClaims; postgres/msk/redis are
# AWS-managed and talk to the workloads via private endpoints.
module "ebs_csi_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.52"

  role_name = "${local.name_prefix}-ebs-csi"

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:ebs-csi-controller-sa"]
    }
  }

  tags = local.common_tags
}

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.31"

  cluster_name    = "${local.name_prefix}-eks"
  cluster_version = var.kubernetes_version

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  cluster_endpoint_public_access = true

  # IRSA: pod-level roles instead of long-lived keys (EBS CSI controller now,
  # wallet's Kafka/Redis clients as they migrate to IRSA auth).
  enable_irsa = true

  cluster_addons = {
    aws-ebs-csi-driver = {
      # Binds the PVCs from k8s/base StatefulSets (mongo) to EBS volumes.
      service_account_role_arn = module.ebs_csi_irsa.iam_role_arn
    }
  }

  eks_managed_node_groups = {
    default = {
      instance_types = var.node_instance_types
      min_size       = var.node_min_size
      max_size       = var.node_max_size
      desired_size   = var.node_desired_size
    }
  }

  tags = {
    "karpenter.sh/discovery" = local.name_prefix
  }
}
