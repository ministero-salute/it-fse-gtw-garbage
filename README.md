
# Electronic Health Record 2.0 (FSE 2.0)

# _it-fse-gtw-garbage_

This microservice manages data retention in MongoDB by deleting records for
completed or failed workflows, expired validated documents, and obsolete
configuration assets according to the rules described below.

<br/>

## Related repositories
- [*it-fse-support*](https://github.com/ministero-salute/it-fse-support) - FSE 2.0 support page
- [*it-fse-landing*](https://github.com/ministero-salute/it-fse-landing) - FSE 2.0 repository landing page

<br/>

## How garbage collection works

The service performs **physical deletes in MongoDB**. It does not archive data,
calculate transaction expiry dates, or mark configuration assets as deleted.
Those fields must already have been populated by the services that write the data.
There are three independent jobs; enabling one does not enable the others.

- **Transaction retention:** removes expired terminal workflows from
  `transaction_data`, then their associated `ini_eds_invocation` records.
- **Validated-document retention:** removes old `validated_documents`, then their
  associated `ini_eds_invocation` records.
- **Configuration-asset retention:** removes old, soft-deleted assets from
  `schema`, `schematron`, `transform`, `terminology`, and `dictionary`.

The exact selection rules for each job are described below.

All repositories use the same `MongoTemplate`, configured through
`spring.data.mongodb.uri` and `spring.data.mongodb.database`. Despite historical
database names in code comments, the current implementation does not wire separate
databases for these jobs. The `dev` profile selects database `FSE_GTW`.

### 1. Transaction retention

Each invocation selects one batch from `transaction_data` with the predicate:

```text
expiring_date < now
AND (
    (eventType = terminalEventType AND eventStatus = "SUCCESS")
    OR eventStatus = "BLOCKING_ERROR"
    OR eventStatus = "BLOCKING_ERROR_MAX_RETRY"
)
```

The `remove-eds-enabled` configuration determines `terminalEventType`:

| Value  | Successful terminal event    |
| ------ | ---------------------------- |
| `true` | `SEND_TO_INI`                |
| `false`| `EDS_WORKFLOW`               |

Blocking errors qualify regardless of their event type, but must still have an
expired `expiring_date`. Expiry alone is not enough for other statuses.
The service uses the stored `expiring_date` as-is; it does not read
`expiring-date-day` or derive expiry from `eventDate`.

After selecting events, it collects their `workflow_instance_id` values and:

1. Deletes **all** `transaction_data` records for those workflow IDs, including
   events that would not individually satisfy the selection predicate.
2. Deletes all `ini_eds_invocation` records with those `workflow_instance_id` values.

`remove-eds-enabled` changes the successful-event criterion; it is **not** a switch
that disables deletion from `ini_eds_invocation`. This job does not delete from
`validated_documents`.

### 2. Validated-document retention

The cutoff is the current time minus `validated-document-retention-day` calendar
days, calculated in the JVM's default timezone. Each invocation finds and removes
one batch of `validated_documents` whose `insertion_date` is strictly before that
cutoff. It then uses their `w_id` values to delete associated `ini_eds_invocation`
records by `workflow_instance_id`.

There is **no transaction-status or expiry check** in this job. It does not delete
`transaction_data`, and the INI/EDS deletion is not gated by `remove-eds-enabled`.

### 3. Configuration-asset retention

This job physically removes assets that were already soft-deleted by another
service. With a cutoff of the current time minus `cfg-items-retention-day` calendar
days (JVM default timezone), it processes collections in this order:

1. `schema`, `schematron`, `transform`, then `terminology`:
   delete where `deleted = true AND last_update_date < cutoff`.
2. `dictionary`:
   delete where `deleted = true AND creation_date < cutoff`.

Active assets are not selected merely because they are old. For dictionaries the
age is measured from **creation**, not from when they were marked deleted.
All comparisons are strict (`<`, not `<=`). This job has no batch limit and does
not check references from other collections before deleting.

Here “configuration assets” means the rule/terminology collections above, **not**
the runtime properties managed by `it-fse-gtw-config`.

## Configuration and `it-fse-gtw-config`

There are two configuration layers:

* **Local Spring properties** control MongoDB, cron expressions, batch size, the
  config-service URL, and the configuration-cache lifetime.
* **Runtime properties from
  [`it-fse-gtw-config`](https://github.com/ministero-salute/it-fse-gtw-config)**
  control retention days and the successful terminal event. Values are strings
  persisted by that service, grouped by type (`GENERIC` or `GARBAGE`). Setting
  these keys only in this application's `application.properties` does not populate
  its runtime configuration cache.

### Runtime properties

- `validated-document-retention-day`: integer number of days for the
  validated-document job.
- `cfg-items-retention-day`: integer number of days for the configuration-asset job.
- `remove-eds-enabled`: `"true"` or `"false"`; selects the successful terminal event
  for the transaction job.

Outside the test profile, startup reads `GET /v1/config-items?type=GENERIC`, then
`GET /v1/config-items?type=GARBAGE` from `ms.url.gtw-config`. Properties in `GARBAGE`
override properties with the same name in `GENERIC`. The config service must be
available at startup, even if all cron jobs are disabled.

Both retention-day keys are checked for presence at startup. Provision
`remove-eds-enabled` too: its presence is not checked then, but the transaction
job fails when reading it if it was absent from both groups. There are no built-in
retention-day defaults in garbage. Use valid non-negative day counts; zero means
a cutoff of now, **not** “disable cleanup”. Negative counts move the cutoff into
the future and are not rejected by the current implementation.

Values are cached independently and refreshed **on access**, not by a background
poller. Once `ms.config.refresh-rate` milliseconds have elapsed for a key, its next
read checks `GET /status`, then fetches
`GET /v1/config-items/props?type=GARBAGE&props=<key>`. If that response is `null`
(for example, an empty HTTP body), it tries the same key in `GENERIC`.
The checked-in Spring configuration uses `300000` ms (**5 minutes**); the Java
fallback if the setting is absent is `900000` ms (15 minutes).

If the status check encounters a connection/I/O failure, the previous value is
retained and its cache timestamp is renewed. HTTP errors and failures during the
subsequent property fetch are not covered by that fallback. Missing or malformed
values are not a safe way to disable a job: integer parsing can fail, and boolean
parsing treats values other than `"true"` (case-insensitive) as false.

### Provisioning example

The following calls target **gtw-config**, not garbage. The values are examples,
not defaults or a recommended retention policy. `POST` creates the group or merges
the supplied keys into an existing group, overwriting matching keys while leaving
other keys intact:

```sh
CONFIG_URL=http://localhost:8018

curl --fail-with-body -X POST "$CONFIG_URL/v1/config-items?type=GARBAGE" \
  -H 'Content-Type: application/json' \
  -d '{"validated-document-retention-day":"30","cfg-items-retention-day":"30","remove-eds-enabled":"false"}'

curl --fail-with-body "$CONFIG_URL/v1/config-items?type=GARBAGE"
```

The config service also exposes `PUT /v1/config-items` with query parameters
`type`, `itemKey`, and `value` for individual updates. Changes reach a running
garbage instance on its next access after cache expiry, not immediately.
The config service's optional `mock` profile can seed properties from its own
Spring settings; this initializer runs only when that profile is active.

### Local settings and scheduling

**Connections**

- `spring.data.mongodb.uri` and `spring.data.mongodb.database` configure MongoDB
  for all deletes and ShedLock.
- `ms.url.gtw-config` must point to the config service. The `dev` profile uses
  `http://localhost:8018`.

**Batch size and cache**

- `retention.transactions-query.limit` is required. It is blank in the base
  configuration and set to `500` in `dev`. Configure a positive integer;
  startup explicitly rejects null/zero.
- `ms.config.refresh-rate` is set to `300000` milliseconds (5 minutes).

**Job schedules**

All three cron properties default to `-` (disabled):

- `scheduler.data-retention`: transaction retention.
- `scheduler.valdoc-retention`: validated-document retention.
- `scheduler.rules-retention`: configuration-asset retention.

Cron expressions use Spring's six-field format (seconds first) and the scheduler's
default timezone. For example, `0 */5 * * * *` runs every five minutes;
`30 * * * * *` runs at second 30 of every minute, **not** every 30 seconds.
Set each cron explicitly to enable the corresponding job. A value of `-` disables
automatic scheduling only; manual triggers remain available.

The batch limit is applied to transaction-event selection and validated-document
selection. Each invocation processes just one batch, with no loop to drain the
backlog and no explicit sort. It is not a cap on total deleted records: multiple
events can refer to the same workflow, and subsequent workflow-based deletes
remove all matches. The configuration-asset job does not use this limit.

Scheduled methods use MongoDB-backed ShedLock locks, with separate names:
`invokeDataRetentionScheduler`, `invokeFseRetentionScheduler`, and
`invokeRulesRetentionScheduler`. Each has `lockAtMostFor = 60m`. Instances sharing
the same lock database coordinate per job; different jobs have different locks.
The maximum lock duration is not an execution timeout: a run that exceeds it can
overlap a later invocation after the lock expires.

## Manual execution and operational caveats

**These endpoints perform real deletes and have no dry-run mode.** Restrict their
network access and confirm the target database and effective configuration first.
The default port is `9080`, or `8019` with the `dev` profile.

- **Transaction retention:** `POST /run-scheduler`
- **Validated-document retention:**
  `POST /run-scheduler/validatedDocuments`
- **Configuration-asset retention:** `POST /run-scheduler/cfgItems`

The controller calls the same locked scheduler methods used by cron. Calls run
synchronously and return trace/span information, not a deletion report. A lock
held by another invocation can cause the job to be skipped. Scheduler methods
catch and log cleanup exceptions, so **HTTP 200 does not prove cleanup succeeded**.
Inspect error/warning logs and, when enabled, debug-level deletion counts.

Deletes across collections are sequential, **not a MongoDB transaction**. If a
later delete fails, earlier deletes are not rolled back. In particular, both
transaction and validated-document jobs remove their source records before
removing INI/EDS records. A failure at that second step can leave orphaned
`ini_eds_invocation` records that a later run cannot rediscover through the deleted
source records. There is no independent orphan sweep in this service.

### Source references

Implementation classes are under
[`src/main/java/it/finanze/sanita/fse2/ms/gtw/garbage`](src/main/java/it/finanze/sanita/fse2/ms/gtw/garbage):
`scheduler/` contains triggers and locks, `service/impl/` contains deletion order
and configuration caching, `repository/impl/` contains MongoDB predicates, and
`client/impl/ConfigClient.java` contains config refresh/fallback behavior.

## Isolation tests

Run with Java 21, without MongoDB, a running config service, or other infrastructure:

```sh
mvn -PISOLATION clean test
```

Tests in `src/test/java/it/finanze/sanita/fse2/ms/gtw/garbage/isolation/` use
`@Tag("ISOLATION")`: excluded by default, selected only by the Maven profile.
Context tests also use `@ActiveProfiles("ISOLATION")`, which activates Spring's
profile but does not select tests. Narrow `@SpringJUnitConfig` contexts load real
services, repositories, controllers and schedulers; `@MockitoBean` replaces
external dependencies. The HTTP client uses `MockRestServiceServer`, not a network.
Do not extend `AbstractConfig` or start the full application for these tests.

The suite checks retention ordering and failure handling, Mongo query predicates,
cutoffs/projections/batch limits, config cache refresh and fallback, MVC endpoints,
tracing, scheduler failures, profile-dependent collection names and startup validation.
JaCoCo reports are generated during `test`: `target/site/jacoco/index.html`
and `target/site/jacoco/jacoco.xml`.
Use `clean` to avoid stale reports. Full application startup, MongoDB wiring
and global OpenTelemetry initialization remain outside this isolated suite.
Existing tests are unchanged and remain separately selectable with `mvn test`.

## License

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
