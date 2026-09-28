output "vpc_id" {
  description = "VPC hosting the whole stack."
  value       = module.vpc.vpc_id
}

output "eks_cluster_name" {
  description = "EKS cluster hosting the four services."
  value       = module.eks.cluster_name
}

output "eks_configure_kubectl" {
  description = "Command to point kubectl at the cluster."
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${module.eks.cluster_name}"
}

output "rds_endpoint" {
  description = "PostgreSQL endpoint for SPRING_DATASOURCE_URL."
  value       = module.db.db_instance_address
}

output "msk_bootstrap_brokers_tls" {
  description = "Kafka bootstrap string (TLS) for SPRING_KAFKA_BOOTSTRAP_SERVERS."
  value       = aws_msk_cluster.kafka.bootstrap_brokers_tls
  sensitive   = true
}

output "redis_endpoint" {
  description = "Redis endpoint for SPRING_DATA_REDIS_HOST."
  value       = aws_elasticache_cluster.redis.cache_nodes[0].address
}

output "db_secret_arn" {
  description = "Secrets Manager ARN holding the wallet database credentials."
  value       = aws_secretsmanager_secret.db.arn
}
