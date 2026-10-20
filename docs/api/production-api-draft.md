# Production API draft

Status: source implementation in progress. Activity lifecycle, order creation, buyer order listing/detail/cancellation, Alipay checkout/notification, expiry reconciliation, full-refund handlers, shipment registration, operations metrics, inventory ledger query, file storage, audit query, and trade-event WebSocket have production-profile code. A production-only buyer/operations Vue interface and static Nginx image configuration are also present. Focused unit tests do not cover the database, object-store, broker, Redis, Nginx container, or HTTPS browser integration. No route in this document is certified for production use.

All routes are same-origin HTTPS in production. Browser sessions use an `HttpOnly; Secure; SameSite=Lax` `access_token` cookie; JavaScript never stores or receives the session token. The Gateway issues a host-only `__Host-XSRF-TOKEN` cookie after login and requires its value in `X-CSRF-TOKEN` on browser mutations. Mutations also require an exact configured HTTPS `Origin`; registration and login are origin-checked, while the signed Alipay callback uses provider signature validation. Buyer identity is derived from the authenticated session. Mutations require an `Idempotency-Key` unless the operation is naturally idempotent by provider/business key. JSON uses CNY amounts as integer fen and timestamps as UTC ISO-8601 values.

## Buyer

| Method and route | Contract | Access |
|---|---|---|
| `GET /api/seckill/goods/list` | Legacy demo catalogue | Disabled in production |
| `GET /api/seckill/goods/{id}` | Legacy demo product detail | Disabled in production |
| `GET /api/seckill/seckill/list` | Legacy demo activity catalogue | Disabled in production |
| `GET /api/seckill/seckill/{id}/detail` | Legacy demo activity detail | Disabled in production |
| `GET /api/seckill/activities` | Published activities that have not ended; bounded list size; exact stock counts are not public | Anonymous |
| `GET /api/seckill/activities/{id}` | Published activity and SKU snapshots; returns in-stock flags without exact inventory | Anonymous |
| `POST /api/seckill/activities/{activityId}/orders` | Requires `Idempotency-Key`; body contains `skuId`, `quantity`, and `shippingAddress` (`recipientName`, `recipientPhone`, `province`, `city`, `district`, `addressLine`). Returns the persisted order number, state, CNY amount in fen, and payment deadline. | Buyer |
| `GET /api/order/me?page=1&size=20` | Paginated orders belonging to the authenticated buyer; page is capped at 1,000,000 and size at 50 | Buyer; server enforces ownership |
| `GET /api/order/me/{orderNo}` | One owned order, with product/SKU snapshot, quantity, shipping snapshot, and fulfillment state | Buyer; server enforces ownership; `orderNo` must match `GS` plus 32 uppercase hex characters |
| `POST /api/order/me/{orderNo}/cancel` | Idempotent cancellation of an owned pending order; atomically closes it and releases held stock/quota. Returns `PAYMENT_IN_PROGRESS` while a channel payment attempt is open. | Buyer; server enforces ownership; requires `Idempotency-Key` |
| `POST /api/order/me/{orderNo}/payment?mode=page\|wap` | Creates or reuses the order's single persisted Alipay attempt and returns a signed checkout form. Amount/title come from the order snapshot. `mode` defaults to the production setting `GOODSKILL_PAYMENT_CHECKOUT_MODE` (`page`); callers may select only `page` or `wap`. | Buyer; server enforces ownership |

The order creation request does not accept buyer ID, product price, stock, payment state, or an exposed simulation token. Payment attempts are created from the persisted order amount and use certificate-mode Alipay SDK calls. Signed notifications verify the app, seller, payment number, provider trade number, and exact CNY fen amount. Successful payment consumes the held inventory/quota and writes ledger, status history, and `OrderPaid` Outbox in one MySQL transaction. On expiry, a task checks payment status and closes the provider trade outside the database transaction; the inventory reservation is released only after the provider confirms closure. Unknown status retains the reservation and retries. A late success after order closure is persisted as a full-refund task. Buyer cancellation is blocked while a payment attempt is open; it does not release stock during a channel race.

Full refunds are limited to unfulfilled orders, require `system-manager` and an idempotency key, and always use the persisted full amount. Replaying the same key succeeds only for the original manager and reason; reusing it with a changed request returns `IDEMPOTENCY_CONFLICT`. A different key after a refund request exists returns `REFUND_ALREADY_REQUESTED`. Provider refund/query calls run outside the local database transaction; status changes and audit history are recorded transactionally. Refunds do not automatically restore sale stock or purchase quota. Payment stays disabled unless `GOODSKILL_PAYMENT_ENABLED=true` and the required merchant private key, seller/app IDs, external certificate paths, HTTPS notify/return URLs, and allowed gateway are configured. `.env.example` documents names only; real keys/certificates must come from an external secret store. A production-only scanner enqueues overdue orders and refund reconciliation in a durable compensation-task table; workers claim tasks with database leases and bounded retries. The transactional Outbox/inbox behavior is described below. MySQL migration/transaction and HTTP integration verification remain required before any of these routes are production-enabled.

Activity creation accepts UTC ISO-8601 `startsAt`/`endsAt` and at most 100 SKU entries. Each SKU contains `skuCode`, `productName`, `skuName`, `unitPriceMinor` (positive integer fen), `purchaseLimitPerUser`, and `allocatedQuantity`. Draft update requires `expectedVersion` and may change only `title`, `startsAt`, and `endsAt`; publishing and pausing require `{ "expectedVersion": n }`. SKU edits after draft creation are not implemented yet.

## Operations

| Method and route | Contract | Access |
|---|---|---|
| `GET /api/seckill/activities/manage/{id}` | Read a draft or published activity with manager-only stock quantities | `seckill-manager` or `system-manager` |
| `GET /api/seckill/activities/manage?limit=100` | Bounded recent activity list, including drafts and manager-only inventory quantities | `seckill-manager` or `system-manager` |
| `POST /api/seckill/activities` | Create a draft activity and its SKU/inventory snapshot | `seckill-manager` or `system-manager` |
| `PUT /api/seckill/activities/{id}` | Update draft title/time using an expected version | `seckill-manager` or `system-manager` |
| `POST /api/seckill/activities/{id}/publish` | Validate inventory rows and publish an immutable sales version using an expected version | `seckill-manager` or `system-manager` |
| `POST /api/seckill/activities/{id}/pause` | Pause future admission while preserving reservations, using an expected version | `seckill-manager` or `system-manager` |
| `POST /api/goods` / `PUT /api/goods/{id}` | Planned replacement product/SKU management API | Not enabled |
| `POST /api/order/manage/{orderNo}/refund` | Request a full refund for an unfulfilled paid order; no partial amount is accepted; provider result is reconciled asynchronously | `system-manager`, idempotency key and audit |
| `GET /api/order/manage/audit` | Bounded audit query; provide at least one actor/action/target filter | `system-manager` |
| `GET /api/order/manage/metrics` | Live counts of in-window published activities, pending-payment/paid/waiting-fulfillment orders, reserved units, and currently sellable units | `system-manager` |
| `GET /api/order/manage/orders?page=1&size=20&state=PAID` | Bounded order operations queue with product snapshot and buyer ID; optional validated state filter | `system-manager` |
| `GET /api/order/manage/orders/{orderNo}` | Operations detail with the shipping snapshot and current carrier/refund state | `system-manager` |
| `POST /api/order/manage/orders/{orderNo}/shipment` | Record carrier code and tracking number; only paid, unfulfilled orders may transition to shipped; exact same retry is idempotent | `system-manager`; audit recorded |
| `GET /api/order/manage/inventory?activityId=42&page=1&size=50` | Bounded inventory ledger query. At least one activity ID, SKU ID, or order number filter is required. | `system-manager` |
| `POST /api/payment/alipay/notify` | Verify and idempotently process a provider notification | Provider certificate signature + app/seller/payment/amount checks; no buyer token |
| `POST /api/seckill/files/product-images` | Upload a product JPEG/PNG; server decodes image content, caps file/pixel size, randomizes object key and enforces per-owner quota | `goods-manager` or `system-manager`; `GOODSKILL_FILES_ENABLED=true` |
| `GET /api/seckill/files/public/{fileId}` | Stream an active product image from private object storage | Public read; only `PRODUCT_PUBLIC` file IDs resolve |
| `DELETE /api/seckill/files/{fileId}` | Mark caller-owned product image deleted and queue object cleanup | `goods-manager` or `system-manager`; owner check in service |

## Realtime trade events

| Method and route | Contract | Access |
|---|---|---|
| `GET /api/web/ws/trades` | HTTPS WebSocket upgrade with STOMP. Subscribe only to `/user/queue/trade-events`; client sends are rejected. Events contain an event ID, event type, order number, and projected order state. | `buyer` role; exact configured HTTPS origin |

The Gateway removes caller-supplied `X-User-Id` and injects the authenticated session ID for the handshake. Keep the web service private so callers cannot forge that internal header by bypassing the Gateway. Commerce events are copied from a durable RabbitMQ queue to Redis Pub/Sub, then each web instance sends them only to the matching authenticated user destination. Duplicate `eventId` values may be delivered after broker retry; clients should deduplicate. Redis Pub/Sub is a live delivery path, not the order record: after reconnect, buyers must refresh `GET /api/order/me` or `GET /api/order/me/{orderNo}`. Production origins must be exact HTTPS origins in `GOODSKILL_WEB_ALLOWED_ORIGINS`; there is no wildcard or SockJS fallback in the production endpoint.

Production file APIs are disabled by default. Uploads require an HTTPS MinIO-compatible endpoint, externally supplied credentials and a pre-created private bucket. File objects have randomized keys, bounded size/dimensions and per-owner storage quota; asset metadata is stored in the commerce MySQL schema. Stale upload/delete objects are removed by a bounded cleanup worker. Legacy common-service upload/download and the legacy original-filename upload utility remain unavailable in the production profile.

## Common errors

| Code | Meaning |
|---|---|
| `UNAUTHENTICATED` | No valid session |
| `FORBIDDEN` | Role or resource ownership check failed |
| `VALIDATION_FAILED` | Request bounds or field validation failed |
| `RESOURCE_CONFLICT` | Activity or SKU business code already exists |
| `ACTIVITY_NOT_OPEN` | Activity is unpublished, paused, outside its window, or ended |
| `ACTIVITY_STATE_CONFLICT` | Activity transition is invalid for its current state |
| `ACTIVITY_INVENTORY_INCOMPLETE` | Activity cannot publish until every SKU has its inventory row |
| `VERSION_CONFLICT` | Activity changed after the caller's expected version |
| `SOLD_OUT` | Conditional inventory reservation found insufficient stock |
| `PURCHASE_LIMIT_REACHED` | Buyer has no remaining activity/SKU quota |
| `IDEMPOTENCY_CONFLICT` | Key was already used with a different request |
| `ORDER_STATE_CONFLICT` | Requested transition is not valid for the current version/state |
| `ORDER_NOT_FOUND` | Order does not exist or does not belong to the authenticated buyer |
| `ORDER_EXPIRED` | Payment deadline has passed |
| `PAYMENT_DISABLED` | Alipay integration is disabled by default |
| `PAYMENT_IN_PROGRESS` | An Alipay attempt is open; cancellation cannot release stock yet |
| `PAYMENT_PROVIDER_UNAVAILABLE` | The provider adapter could not create a checkout form |
| `PAYMENT_AMOUNT_MISMATCH` | Provider amount does not equal the persisted CNY order amount |
| `FILE_SIZE_INVALID` / `FILE_TYPE_INVALID` / `FILE_CONTENT_INVALID` | Upload is empty, too large, has an unsupported image signature, or does not decode as a matching image |
| `FILE_DIMENSIONS_INVALID` / `FILE_QUOTA_EXCEEDED` | Pixel cap or per-owner storage quota was exceeded |
| `FILE_NOT_FOUND` | Asset is unavailable or is not owned by the caller |
| `FILE_STORAGE_UNAVAILABLE` | Object storage operation failed; internal endpoint and credentials are not exposed |
| `ORDER_NOT_REFUNDABLE` | Order is not paid or has started fulfillment |
| `REFUND_ALREADY_REQUESTED` | A full refund request already exists with another idempotency key |
| `IDEMPOTENCY_IN_PROGRESS` | An earlier request with this key has no completed result yet |
| `ADMISSION_PAUSED` | Order admission is closed by the production stop switch; retry after the operator reopens admission |
| `PAYMENT_CONFIRMING` | Provider status is uncertain; reservation remains held pending reconciliation |
| `TEMPORARY_UNAVAILABLE` | Admission or authoritative dependency is unavailable; retry with the same idempotency key |

Error details must not reveal another buyer's order existence, database internals, provider credentials/responses, or stack traces.

## Versioning and activation

Payment variables are `GOODSKILL_PAYMENT_ENABLED`, `GOODSKILL_PAYMENT_APP_ID`, `GOODSKILL_PAYMENT_SELLER_ID`, `GOODSKILL_PAYMENT_PRIVATE_KEY`, `GOODSKILL_PAYMENT_APP_CERTIFICATE`, `GOODSKILL_PAYMENT_ALIPAY_CERTIFICATE`, `GOODSKILL_PAYMENT_ROOT_CERTIFICATE`, `GOODSKILL_PAYMENT_GATEWAY`, `GOODSKILL_PAYMENT_NOTIFY_URL`, `GOODSKILL_PAYMENT_RETURN_URL`, and `GOODSKILL_PAYMENT_CHECKOUT_MODE`. The SDK uses certificate mode; certificate files are referenced by external paths and are not stored in this repository. Enabled mode rejects missing merchant values, missing/unreadable certificate files, unsupported gateways, and non-HTTPS callback/return URLs at startup. The switch defaults to false.

The routes above remain unavailable for production use until their owning service validates session identity, resource ownership, request bounds, idempotency, state transitions, and persistence behavior. Payment notification activation additionally requires official provider-contract review, sandbox verification, key rotation procedure, and signature/replay tests. No production payment credential is stored in this repository.

The production Gateway disables legacy goods/activity simulations and every legacy order route except exact buyer `GET /api/order/me`, buyer detail/cancel/payment paths, exact manager full-refund path, and exact `POST /api/payment/alipay/notify`. Buyer mutations require `buyer`; refunds require `system-manager`; only the signed provider callback is anonymous. The exact `POST /api/seckill/activities/{positiveId}/orders` path also requires the `buyer` role and uses a Redis token bucket, configured by `GOODSKILL_GATEWAY_ORDER_RATE_PER_SECOND` (default `2`) and `GOODSKILL_GATEWAY_ORDER_RATE_BURST` (default `10`). Its key uses the authenticated gateway user ID, or the direct peer IP when no authenticated identity exists; the identifier is SHA-256 hashed. Redis limiter script failures are converted to a denial. `GOODSKILL_REDIS_HOST` and `GOODSKILL_REDIS_PASSWORD` must be configured for production. Forwarded IP headers are not trusted by this resolver; deployments behind a proxy need a separately reviewed trusted-proxy configuration. `GOODSKILL_TRADE_ADMISSION_ENABLED` defaults to `false`; the Gateway and transaction service both reject order creation with `ADMISSION_PAUSED` until an operator enables it. New activity catalogue/lifecycle routes are separately classified for public or manager roles. The handlers are not ready for production use until migration, transaction/concurrency, sandbox, and HTTP routing checks above pass. Other existing controllers still target demonstration tables, Mongo documents, or unreviewed file handlers; role checks alone do not make those implementations production-safe. Re-enable other routes only when replacement production handlers and their persistence/authorization tests are in place.
