# Production commerce contract

Status: approved design baseline; implementation is in progress. This document defines the transaction rules the implementation must preserve. It is not evidence that the repository is production-ready.

## Agreed scope

- One merchant selling physical goods in CNY.
- A purchase contains one SKU. Quantity and each buyer's limit are configured on the activity/SKU.
- Buyer identity comes from the authenticated server session, never from a request field or caller-supplied identity header.
- One online payment provider (Alipay), full-order refunds only, free shipping for the first release.
- Production AI is read-only. Local simulation remains isolated from production routes and data.
- Existing demo databases and records remain untouched. New commerce data uses an explicitly separate schema/database.
- Peak traffic, activity size, budget, availability target, RPO, and RTO are still unknown. No capacity or availability claim can be made until those are agreed and measured.

## Source of truth and ownership

`goodskill-service` is the sole writer for activity sales snapshots, SKU inventory, reservations, order/payment/refund state, inventory ledger, idempotency records, and transactional outbox. These records share one MySQL database and local transaction. Buyer order/payment and provider-notification handlers live in the same transaction service so verified payment cannot be separated from inventory consumption. Mongo documents from the historical simulation path are not authoritative commerce orders.

Redis may support sessions, admission control, rate limiting, and display caches. It is not the sole stock ledger. RabbitMQ receives events only after an outbox record commits. Publishers and consumers are at-least-once; handlers must be idempotent by event ID and business key.

## Purchase invariants

1. An order records immutable SKU, title, price in integer minor units, currency, quantity, buyer, activity version, and shipping snapshot.
2. Inventory available, reserved, sold, and returned quantities never become negative. Every change has a unique ledger entry and a reason/actor.
3. One database transaction checks activity state/window, SKU availability, buyer qualification and remaining limit; conditionally reserves inventory; creates the pending-payment order and reservation; records state history and an outbox event. Any failed step rolls back all of them.
4. The update count from conditional stock/quota writes is checked. A database unique constraint is the final guard against duplicate reservations and idempotency keys.
5. Repeating an idempotency key with the same buyer and request returns the original order. Reusing it for a different payload returns a conflict. A timeout is resolved by querying that key; it is not reported as a successful purchase without a persisted order.
6. The initial API accepts SKU and quantity, but never buyer ID, price, stock, payment status, or activity state from the client.
7. Catalogue caches may serve display data only. Purchase eligibility and price are checked against the authoritative version inside the transaction.

## State transitions

Activity: `DRAFT -> PUBLISHED -> PAUSED -> PUBLISHED -> ENDED`; an operator may cancel an unpublished or paused activity. Published sales fields are versioned. No hard delete or retroactive inventory reset is allowed after reservations exist.

Order payment state: `PENDING_PAYMENT -> PAID | CLOSED`; uncertain provider state becomes `PAYMENT_CONFIRMING`. Refund state is tracked separately as `NONE -> REFUND_PENDING -> REFUNDED | REFUND_FAILED`. Fulfillment state is tracked separately as `UNFULFILLED -> PACKING -> SHIPPED -> DELIVERED`, with explicit cancellation/return handling to be specified before enabling fulfillment APIs.

- Only a verified, amount-matching provider response/notification can mark an order paid.
- A timeout or uncertain payment response does not release stock. Query the provider and retry reconciliation.
- Closing an order and confirming payment race through a versioned conditional state change. Stock is released once only after the payment provider confirms that payment cannot complete.
- Refund is an idempotent full-order operation. A completed refund does not automatically replenish flash-sale stock in the first release.
- A payment arriving after the order has closed is persisted as a full-refund reconciliation task; it must not silently mark the closed order paid or consume stock a second time.
- Provider callbacks validate signature, merchant/app identity, payment/order identity, CNY amount, result state, and expected payment identifiers before mutation; unique payment and provider-trade keys make callback replay idempotent.

## Draft API boundary

The intended buyer contract is captured in [production API draft](../api/production-api-draft.md). It is not enabled at the Gateway until ownership checks, persistence, and transaction tests are implemented. Provider callbacks are verified at the payment adapter and do not use buyer login tokens.

## Security and audit

Roles are `buyer`, `goods-manager`, `seckill-manager`, and `system-manager`. Buyers may only read or change resources owned by their authenticated account. Goods and activity management use separate roles; role assignment and privileged inventory adjustments require system-manager authorization and an audit record. Caller-supplied user identity headers are removed at the Gateway; a trusted identity is derived from the authenticated login and forwarded internally. Production service ports remain private to the application network.

All user-visible errors use stable codes without SQL, provider responses, or stack traces. Audit records identify actor, action, target, request/correlation ID, timestamp, and before/after version where appropriate; secrets, passwords, tokens, and payment credentials are excluded.

## Deferred decisions

Capacity, infrastructure budget, SLO, RPO/RTO, retention periods, tax/invoice rules, supported shipping regions, and payment merchant settings require measured or external inputs. These do not block schema and transaction implementation, but they block a production go-live decision.
