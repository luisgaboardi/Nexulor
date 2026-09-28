# Terraform — Nexulor on AWS (Phase 4)

Provisions the showcase cloud topology mirroring `k8s/` + `docker-compose.yml`:

| Piece | Service | Maps to local |
|---|---|---|
| Network | VPC (public/private subnets, NAT) | — |
| Compute | EKS 1.33 + managed node group (IRSA) | kind cluster |
| Wallet store | RDS PostgreSQL 16 | `postgres` container |
| Events | MSK (Kafka 3.6, TLS) | `kafka` container |
| Locks/velocity | ElastiCache Redis 7 | `redis` container |

Mongo (fraud/notification) and Zipkin are intentionally **not** provisioned here — see the roadmap in `docs/PRD.md`; they are the next increment (DocumentDB / self-hosted on EKS).

## Layout

```
terraform/
├── versions.tf        provider pins + common tags
├── backend.tf         S3 remote state (partial config)
├── variables.tf       all knobs, dev-friendly defaults
├── vpc.tf             network
├── eks.tf             cluster + node group
├── rds.tf             wallet PostgreSQL (+ Secrets Manager)
├── msk.tf             Kafka for transaction-completed
├── elasticache.tf     Redis for idempotency/velocity
└── outputs.tf         endpoints for the k8s ConfigMap
```

## Bootstrap (once per AWS account/region)

Remote state needs a bucket and a lock table before the first `init`:

```bash
aws s3api create-bucket --bucket nexolor-tfstate-<account-id> --region us-east-1
aws s3api put-bucket-versioning --bucket nexolor-tfstate-<account-id> \
  --versioning-configuration Status=Enabled
aws dynamodb create-table --table-name nexolor-tflock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

## Usage

```bash
export TF_VAR_db_password='...'        # or let Terraform generate one
terraform init -backend-config="bucket=nexolor-tfstate-<account-id>" \
               -backend-config="key=nexolor/dev/terraform.tfstate" \
               -backend-config="region=us-east-1" \
               -backend-config="dynamodb_table=nexolor-tflock"
terraform plan -var-file=envs/dev.tfvars
terraform apply -var-file=envs/dev.tfvars
```

Then wire the outputs into Kubernetes:

```bash
$(terraform output -raw eks_configure_kubectl)
kubectl create namespace nexolor
```

The `SPRING_DATASOURCE_URL` / `SPRING_KAFKA_BOOTSTRAP_SERVERS` / `SPRING_DATA_REDIS_HOST` values for `k8s/base/namespace.yaml`'s ConfigMap come from `terraform output` — in production, feed them via your CD pipeline or External Secrets instead of committing them.

## Conventions

- Everything tagged `Project=nexolor`, `Environment=<env>`, `ManagedBy=terraform`.
- Dev defaults are small (`t3.medium`, single-NAT, single-AZ RDS); prod flips Multi-AZ, retention and deletion protection via `envs/prod.tfvars`.
- RDS master credentials live in Secrets Manager (`db_secret_arn` output), never in state-only variables.
