# Dev: smallest viable footprint, single-AZ, cheap instances.
environment = "dev"
aws_region  = "us-east-1"

vpc_cidr = "10.0.0.0/16"
az_count = 2

kubernetes_version  = "1.33"
node_instance_types = ["t3.medium"]
node_desired_size   = 2
node_min_size       = 2
node_max_size       = 4

db_instance_class        = "db.t4g.medium"
db_allocated_storage     = 20
db_max_allocated_storage = 100

msk_instance_type = "kafka.t3.small"
msk_broker_count  = 2

redis_node_type = "cache.t4g.small"
