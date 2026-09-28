# Wallet service's transactional store (pessimistic locking, ADR-001).
resource "random_password" "db" {
  length  = 24
  special = false
}

locals {
  db_master_password = coalesce(var.db_password, random_password.db.result)
}

resource "aws_secretsmanager_secret" "db" {
  name        = "${local.name_prefix}/rds/wallet"
  description = "Master credentials for the ${local.name_prefix} wallet PostgreSQL instance."
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    username = var.db_username
    password = local.db_master_password
    engine   = "postgres"
    host     = module.db.db_instance_address
    port     = module.db.db_instance_port
    dbname   = module.db.db_instance_name
  })
}

module "db" {
  source  = "terraform-aws-modules/rds/aws"
  version = "~> 6.10"

  identifier = "${local.name_prefix}-wallet-db"

  engine               = "postgres"
  engine_version       = var.db_engine_version
  family               = "postgres16"
  major_engine_version = "16"
  instance_class       = var.db_instance_class

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_encrypted     = true

  db_name  = "nexolor"
  username = var.db_username
  password = local.db_master_password
  port     = 5432

  multi_az = var.db_multi_az

  db_subnet_group_name   = module.vpc.database_subnet_group_name
  vpc_security_group_ids = [aws_security_group.rds.id]

  maintenance_window      = "Mon:00:00-Mon:03:00"
  backup_window           = "03:00-06:00"
  backup_retention_period = var.environment == "prod" ? 14 : 1
  deletion_protection     = var.environment == "prod"
  copy_tags_to_snapshot   = true

  tags = local.common_tags
}

resource "aws_security_group" "rds" {
  name_prefix = "${local.name_prefix}-rds-"
  description = "Postgres access from the EKS nodes"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description     = "PostgreSQL from node/private subnets"
    from_port       = 5432
    to_port         = 5432
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
