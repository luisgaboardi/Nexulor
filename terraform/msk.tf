# transaction-completed events (ADR-004) between wallet and notification.
# Native resources: the community msk module isn't published in the registry.
resource "aws_msk_cluster" "kafka" {
  cluster_name           = "${local.name_prefix}-msk"
  kafka_version          = var.msk_version
  number_of_broker_nodes = var.msk_broker_count # must be a multiple of subnet count

  broker_node_group_info {
    instance_type   = var.msk_instance_type
    client_subnets  = module.vpc.private_subnets
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = 20
      }
    }
  }

  # Showcase keeps TLS to the client but no auth; production should enable
  # SASL/SCRAM or mTLS (client_authentication block) plus unauthenticated
  # access disabled via the same block.
  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  tags = local.common_tags
}

resource "aws_security_group" "msk" {
  name_prefix = "${local.name_prefix}-msk-"
  description = "Kafka access from the EKS nodes"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description     = "Kafka brokers from EKS nodes"
    from_port       = 0
    to_port         = 0
    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.common_tags
}
