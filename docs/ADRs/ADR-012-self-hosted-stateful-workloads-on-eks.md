# ADR-012: Self-hosted stateful workloads on EKS instead of managed document stores

- **Status:** Accepted
- **Date:** 2026-09-30
- **Phase:** 4
- **Deciders:** Core engineering

## Context

Phase 4 closes with the full topology on Kubernetes. Postgres, Kafka and
Redis map 1:1 to managed AWS services (RDS, MSK, ElastiCache — see
`terraform/`). MongoDB (fraud rules + notification projections) has no
equally small managed option: DocumentDB is Atlas-flavoured, priced for
production and API-compatible only loosely (no replica-set local mode,
different driver quirks). Zipkin has no managed offering at all. The
showcase needs both somewhere when running on EKS.

## Decision

Run **Mongo and Zipkin as workloads inside the EKS cluster**, reusing the
`k8s/base` manifests already validated on kind:

- Mongo: StatefulSet with `volumeClaimTemplates` (5Gi) bound through the
  **EBS CSI addon** (IRSA role in `terraform/eks.tf`), so pods survive
  rescheduling with data intact.
- Zipkin: plain Deployment, in-memory storage (showcase semantics already
  accepted in ADR-011); the same StorageClass gives a durable upgrade path
  (STORAGE_TYPE + PVC) without redesign.
- Nothing in the service images/config changes: `SPRING_DATA_MONGODB_URI`
  and `ZIPKIN_ENDPOINT` keep pointing at in-cluster DNS names that EKS
  resolves the same way kind does.

## Alternatives considered

- **DocumentDB for Mongo:** zero ops, but ~10x the cost of a t3.medium node
  running the Mongo container, and its API gaps force driver-level testing
  again — exactly what the Testcontainers suite (ADR-008) avoids.
- **Atlas (MongoDB Cloud):** right answer for real production, wrong default
  for a showcase (external dependency, credentials plumbing, egress).
- **Tempo/Grafana stack for tracing:** attractive, but Zipkin is already
  end-to-end validated (ADR-011) and the Brave reporter swap stays available
  as a pure config change.

## Consequences

- 100% of the topology now deploys from `k8s/base` + `terraform/` with no
  unmanaged leftovers: 9 workloads in-cluster, 3 AWS-managed data services.
- We own Mongo ops on EKS: no automated backups, single replica, PVC only as
  durable as its EBS volume — acceptable for a showcase, the first thing to
  replace in production (DocumentDB/Atlas + Vault-managed credentials).
- First-boot dependency on the EBS CSI addon: clusters created without the
  Terraform path need the addon (or a default StorageClass) before Mongo
  schedules — the kind/local path uses its built-in `standard` class.
- Kafka on EKS stays the ADR-011-validated StatefulSet + headless DNS
  pattern; MSK exists in Terraform for teams that prefer managed brokers,
  both target the same `SPRING_KAFKA_BOOTSTRAP_SERVERS` contract.
