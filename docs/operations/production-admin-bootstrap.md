# Production administrator bootstrap

Status: implemented in source; production execution has not been validated against a real deployment.

The production auth service creates its first `system-manager` only when all of these conditions hold:

- The production identity schema is freshly migrated, with the seeded active `system-manager` role.
- There are no user records and no existing role assignment for a system manager.
- Both `GOODSKILL_BOOTSTRAP_ADMIN_USERNAME` and `GOODSKILL_BOOTSTRAP_ADMIN_PASSWORD` are provided through the deployment secret mechanism for the first startup only.
- The username contains 1–120 characters. The password contains at least 15 characters and at most 72 UTF-8 bytes, matching BCrypt's supported input length.

Creation of the user and role assignment runs in one database transaction while locking the system-manager role row. The password is BCrypt-hashed. Startup fails when the role is missing, an identity database already contains non-admin users, a prior manager assignment exists but all assigned accounts are disabled, or bootstrap credentials are supplied after a system manager already exists.

After the first successful startup, remove both bootstrap variables from the deployment configuration before restarting or scaling the service. Never put these values in the repository, a browser bundle, image build arguments, or a checked-in `.env` file. The service does not log either value.

If all system-manager accounts are later unavailable, this bootstrap intentionally does not promote an existing buyer. Use a separately reviewed break-glass database recovery procedure, record the operator and change, then revoke stale sessions. That recovery procedure and its audit controls still need implementation and rehearsal.

This initialization path has unit coverage for credential constraints, but no isolated MySQL startup/restart rehearsal has been completed. Administrators can reset a user's password through the system-manager-only `POST /api/auth/user/{id}/password/reset` endpoint; successful production resets write a target/actor/request audit row in the same transaction and revoke the target's Sa-Token session. This source-level path still needs HTTP/MySQL integration verification. MFA, CSRF, and production release readiness remain incomplete.

Production login failures are counted by a separate account-keyed database throttle. Defaults are five failed attempts within 300 seconds followed by a 900-second lock; deployments can override these with `GOODSKILL_AUTH_LOGIN_MAX_ATTEMPTS`, `GOODSKILL_AUTH_LOGIN_WINDOW_SECONDS`, and `GOODSKILL_AUTH_LOGIN_LOCK_SECONDS`. The account key is stored as a SHA-256 digest and stale rows are periodically removed. This is not an IP/network edge limit; a production reverse proxy or Gateway still needs a tested global and per-source request limit.
