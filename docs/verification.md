# Verification record

This file records the actual checks performed on the `portfolio-upgrade` branch. The committed transcript is a captured run, not invented sample output; random IDs and timestamps are expected to differ on each run. Credentials, bearer tokens, and presigned storage URLs are redacted by the smoke script.

## Shared-host procedure

The verification workstation also runs production services. A shared exclusive lock covers the complete build/test/demo pipeline; a user systemd scope caps host processes at 6 GiB with no swap. Maven finishes before the stack starts, and the stack is removed before releasing the lock. Before the Maven stage **and** the Compose stage, the wrapper waits until `/proc/meminfo` reports at least 8 GiB available:

```bash
awk '/MemAvailable/{print $2/1048576}' /proc/meminfo
flock /home/taiwei/portfolio-upgrade/.heavy.lock \
  systemd-run --user --scope -q -p MemoryMax=6G -p MemorySwapMax=0 \
  bash pu-erp-work/verify.sh
```

`pu-erp-work/verify.sh` is an ignored local wrapper with these substantive stages:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export MAVEN_OPTS=-Xmx512m
mvn -B -ntp clean verify
# Recheck available memory here.
VERBOSE=1 ./scripts/stack-smoke.sh
```

The local wrapper also redirects JVM temporary files into its own `pu-erp-work/pu-erp-tmp` directory and removes any remaining `pu-erp-test-*` containers on exit. No production containers, networks, images, or volumes are altered. The smoke script's temporary directories use `pu-erp-smoke-*` names.

The Compose service limits total 5,760 MiB. Each Java service has a 512 MiB heap and a 768 MiB container limit; infrastructure containers are individually limited. Testcontainers uses one module at a time, with a 512 MiB limit per container. `ContainerLimits` also binds test ports only to loopback in 41000–49999. JVM test forks are limited to one.

## Original upgrade results

Verified on **2026-10-19**, with OpenJDK **17.0.17**, Maven **3.9.9**, and Docker **29.1.3**:

| Check | Result |
| --- | --- |
| `mvn -B -ntp clean verify` | **BUILD SUCCESS**, 155 tests: 120 unit + 35 integration; zero failures, errors or skips; 1 min 18 sec |
| `VERBOSE=1 ./scripts/stack-smoke.sh` | **83 checks passed**, all five applications and four infrastructure services running |
| OpenAPI and Swagger | HTTP 200 from `/v3/api-docs` and `/swagger-ui/index.html` on all five services |
| `docker compose config --quiet`, Bash syntax, complete branch diff whitespace checks | Passed |
| Cleanup | No `pu-erp-*` containers, demo volumes, or networks remained after the wrapper exited successfully |

Artifacts:

- [Test results by module](evidence/test-results.txt), extracted from Surefire/Failsafe XML reports.
- [Captured HTTP transcript](evidence/smoke-transcript.txt), including errors, RabbitMQ-driven results, and Swagger checks.
- [Resource limits and teardown](evidence/runtime-resources.txt), captured from Docker inspection/stats and the verification log.

[CI](https://github.com/Ctwqk/ERP/actions/workflows/ci.yml) repeats the reactor build and disposable full-stack smoke from a fresh GitHub checkout and uploads reports. The workflow badge in the README tracks the branch's latest run.

The first local attempt selected the workstation's Java 21 runtime, which does not include `javac`; verification was rerun successfully using the installed Java 17 JDK. No global toolchain configuration was changed.

## Independent review (2026-10-19)

The review started from `4797abd`. Its existing 155 tests passed independently, and the original [CI run](https://github.com/Ctwqk/ERP/actions/runs/39344351651) was green.

| Review verification | Result |
| --- | --- |
| `./mvnw -B -ntp clean verify` | **BUILD SUCCESS**, 196 tests: 156 unit + 40 integration; zero failures, errors or skips; 1 min 13 sec |
| `./mvnw -B -ntp test` | **BUILD SUCCESS**, 156 unit tests; 10.774 sec |
| `./mvnw -B -ntp -pl inventory-service -am verify` | **BUILD SUCCESS**, 19 unit + 5 integration tests; 10.711 sec |
| `VERBOSE=1 ./scripts/stack-smoke.sh` | **83 checks passed**, followed by successful container, volume and network teardown |

The [review evidence](evidence/review-results.txt) records before-fix failures, final test counts, resource limits and fix commits. Final figures come from a clean rebuild that explicitly checked the new notification test was included. Additional regression tests exposed these gaps:

- **P1 — upload authorization:** another authenticated user could initialize an upload against an existing private order. The service now checks the order owner before storage or database changes, and rejects a missing user ID. A fresh batch UUID still supports imports before an order exists.
- **P2 — spreadsheet data loss:** numeric casts truncated fractions or saturated overflow, incomplete rows were skipped, and invalid IDs were silently ignored. Both spreadsheet readers now reject these inputs; unsupported cell types and lossy numeric SKU conversions are also rejected.
- **P2 — import provenance and concurrency:** an ordinary attachment could be mistaken for a completed import, returning an unrelated order or blocking the uploader. Four simultaneous imports also created four orders. Import provenance now has an explicit, unique source-document column, and a document row lock serializes lookup and creation in one transaction.
- **P2 — message test coverage:** the duplicate-notification assertion could finish before the duplicate was consumed. A later marker event now proves both earlier deliveries were processed. Another integration test verifies that malformed JSON does not block subsequent valid events.

Migration V3 deliberately does not infer source-document IDs from existing attachments. Before re-importing older spreadsheets, reconcile their existing orders or recreate disposable demo data. Event publication still has no transactional outbox, and exhausted messages have no dead-letter recovery workflow; the review does not claim production delivery guarantees.

The review used a separate `pu-review-erp-*` checkout and resource names. Every Maven/Testcontainers/Compose stage held the global lock inside a 6 GiB systemd scope, checked for at least 8 GiB available memory, and used the existing per-container limits, 512 MiB JVM heaps and single test fork.

## What the HTTP smoke test proves

- Real login/JWKS and signed tokens across all five services; missing/forged tokens rejected.
- Role restrictions on user administration and owner restrictions on orders/notifications.
- Item/UOM creation, duplicate SKU rejection, BOM activation and scrap-adjusted quantities.
- First receipt, decimal stock movements, ledger retrieval and insufficient-stock rejection.
- Order confirmation calls inventory-service and checks aggregated demand.
- Presigned upload/download transfers actual bytes to/from MinIO; completion verifies presence/size and uploader ownership.
- A real `document.file.upload` RabbitMQ message causes an uploaded workbook to become an order and a notification.
- Task reference lookups call user/item services; prerequisite stock is checked before starting.
- A real `task.completed` RabbitMQ message causes a notification, which can be marked read.
- Logout revocation in user-service; `/v3/api-docs` and `/swagger-ui/index.html` served by every application.
- Automatic teardown, including all demo volumes.

Concurrency of inventory withdrawals is covered separately by PostgreSQL integration tests. Broker crash recovery, production deployment, legacy schema migration, and full multi-tenant authorization are not claimed by these checks.
