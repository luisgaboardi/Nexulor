variable "project" {
  description = "Project name used as prefix for resources."
  type        = string
  default     = "nexolor"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)."
  type        = string
  default     = "dev"
}

variable "aws_region" {
  description = "AWS region for all resources."
  type        = string
  default     = "us-east-1"
}

# --- Network ---

variable "vpc_cidr" {
  description = "CIDR block of the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "Number of AZs to span (subnets created per AZ)."
  type        = number
  default     = 2
}

# --- EKS ---

variable "kubernetes_version" {
  description = "Kubernetes control plane version."
  type        = string
  default     = "1.33"
}

variable "node_instance_types" {
  description = "EC2 instance types for the managed node group."
  type        = list(string)
  default     = ["t3.medium"]
}

variable "node_desired_size" {
  description = "Desired node count for the managed node group."
  type        = number
  default     = 2
}

variable "node_min_size" {
  description = "Minimum node count for autoscaling."
  type        = number
  default     = 2
}

variable "node_max_size" {
  description = "Maximum node count for autoscaling."
  type        = number
  default     = 4
}

# --- RDS ---

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.medium"
}

variable "db_allocated_storage" {
  description = "Initial allocated storage (GiB)."
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Storage autoscaling ceiling (GiB)."
  type        = number
  default     = 100
}

variable "db_engine_version" {
  description = "PostgreSQL engine version."
  type        = string
  default     = "16.4"
}

variable "db_multi_az" {
  description = "Deploy RDS in Multi-AZ mode."
  type        = bool
  default     = false
}

# --- MSK ---

variable "msk_instance_type" {
  description = "Broker instance type for MSK."
  type        = string
  default     = "kafka.t3.small"
}

variable "msk_broker_count" {
  description = "Number of MSK brokers (AZ spread)."
  type        = number
  default     = 2
}

variable "msk_version" {
  description = "Apache Kafka version on MSK."
  type        = string
  default     = "3.6.0"
}

# --- ElastiCache ---

variable "redis_node_type" {
  description = "ElastiCache node type."
  type        = string
  default     = "cache.t4g.small"
}

# --- Wallet database credentials ---

variable "db_username" {
  description = "Master username for the wallet PostgreSQL database."
  type        = string
  default     = "nexolor_admin"
}

variable "db_password" {
  description = "Master password. Provide via TF_VAR_db_password or a secrets manager; a default is generated for dev."
  type        = string
  default     = null
  sensitive   = true
}
