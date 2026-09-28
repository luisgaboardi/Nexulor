# Kubernetes cluster running the k8s/base manifests (see ../k8s). Data stores
# are AWS-managed, so the workloads talk to RDS/MSK/ElastiCache endpoints via
# a ConfigMap generated in k8s-generated.tf.
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.31"

  cluster_name    = "${local.name_prefix}-eks"
  cluster_version = var.kubernetes_version

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  cluster_endpoint_public_access = true

  #IRSA: wallet's Kafka producer and the Redis/Postgres clients authenticate
  # via pod-level roles instead of long-lived keys.
  enable_irsa = true

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
