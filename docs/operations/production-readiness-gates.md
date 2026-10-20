# Production readiness and release gates

This is the operational checklist for the production upgrade. It is not a deployment runbook for a real environment. The current repository is not certified for production use.

## Decisions required before go-live

- Peak order submission rate, concurrent buyers, SKU/activity count, inventory scale, catalogue read volume, and expected traffic shape.
- Server/cloud budget, regions, network boundaries, data residency, and required availability/SLO.
- RPO, RTO, database backup retention, restore test frequency, and incident escalation ownership.
- Supported shipping regions, privacy/retention policy, tax and invoice obligations, and customer support process.
- Alipay merchant account, approved callback/return domains, sandbox credentials, key custody/rotation owner, and reconciliation source. Real keys stay in an external secret manager.
- First system-manager provisioning uses the [one-time bootstrap procedure](production-admin-bootstrap.md); remove its two bootstrap values immediately after initialization. A break-glass recovery rehearsal remains outstanding.

## Release evidence

### Data and transaction

- Separate production schema and credentials; migration dry-run on a recent sanitized database copy.
- Existing schema migration report has no silent price rounding, fabricated orders, or demo users copied to production.
- Concurrency tests prove no oversell, no duplicate reservation, no quota violation, non-negative inventory, and atomic order/reservation/ledger/outbox writes.
- Duplicate, delayed, reordered, and lost-event tests prove idempotent recovery and bounded retries.
- Payment-vs-close race, uncertain payment status, duplicate callback, mismatched amount/currency, and refund replay tests pass.
- The payment implementation remains disabled by default until the isolated database race tests, Alipay sandbox checkout/notification/query/close/refund, callback-domain check, and merchant key-rotation runbook pass.
- Backup restore evidence meets approved RPO/RTO. Recovery is exercised without deleting or rewriting prior transaction history.

### Security and application

- Anonymous/buyer/manager/system-manager/disabled-account authorization matrix passes at the Gateway and owning service.
- Production browser sessions use secure HTTP-only cookies, an origin allowlist and a host-only CSRF double-submit token. The client does not persist Sa-Token values in browser storage; cookie flags and the effective Nacos override must be verified in an HTTPS integration environment.
- Ownership tests cover order details, cancellation, payment intent, uploaded files, support conversations, and WebSocket subscriptions.
- Production profile denies simulation, AI write tools, unclassified routes, service discovery shortcuts, debug consoles, and non-health actuator endpoints.
- Account-keyed login throttling has controller-level unit coverage; the password reset API has a role assertion and the implementation writes an audit row in the same transaction. Its database behavior still needs MySQL integration verification. Source-IP/Gateway limits and MySQL throttle concurrency remain unverified. Logout/session revocation is implemented at source; MFA, CSRF, cookie flags, CORS, secure headers, CSP, and secret scanning are not yet release-verified.
- Product image upload has bounded size/pixel limits, randomized object keys, private object storage and owner quota; the source implementation remains disabled by default. Its MySQL/MinIO integration, cleanup recovery, ownership, and content-decoder tests remain release gates. Production log WebSocket broadcasting is disabled. A separate per-user trade-event socket now has source code using authenticated Gateway identity, strict origin/destination checks, a durable RabbitMQ consumer queue, and Redis Pub/Sub fanout; Rabbit/Redis delivery, cross-instance ownership and browser reconnect behavior remain release gates. The three-connection and 30-control-frame limits are per web instance.
- Commerce audit writes are transactional for source-covered activity, order, payment, refund and file mutations, with a `system-manager` query route. Retention/archival policy, access review, log redaction and audit integrity tests remain release gates.
- Dependency/image/SBOM and vulnerability review is recorded; container runs as a non-root user with read-only filesystem where feasible.

### Capacity and operations

- Reproducible isolated load tests capture offered/accepted/rejected requests, p50/p95/p99 latency, error rate, CPU, memory, DB connections/locks, Redis, and MQ lag.
- Capacity test data is isolated from the local demo and any production data; request and duration caps are applied.
- Dashboards and alerts cover admission, inventory contention, payment confirmation, outbox age, dead letters, task lag, dependency health, and database capacity.
- Runbooks cover pause/resume activity, uncertain payment, inventory reconciliation, dead-letter replay, partial outage, restore, and credential rotation.
- Rollback plan preserves backward-compatible schema and transaction history; release artifact digest and configuration diff are recorded.

## Current evidence boundary

Local Windows Docker demonstrations, source compilation, unit tests, or a single-node container startup are development evidence only. They do not prove production capacity, availability, disaster recovery, payment settlement, security certification, or safe operation on a real merchant account. Production deployment and any real payment/refund action require a separate approved change after this evidence exists.
