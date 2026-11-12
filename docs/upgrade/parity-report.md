# Runtime parity report (AB-548)

**Final runtime parity sign-off on the fully merged Wave 3 integration commit.**

Generated: 20261112T012535Z. Source commit: `6307ae39f2e10e5487a3d51b08dc87199275a141`.
Working-tree patch SHA-256: `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`; packaged JAR SHA-256: `35a569cfedb931e46bb3aec8a25a39ffc25dfe64b5faee41ce28cafad2f80a8a`.

This report measures the unmodified integration commit containing AB-545 (#383), AB-546 (#382), AB-547 (#380), AB-549 (#381), AB-550 (#384), and the AB-548 harness (#388). The empty working-tree patch hash above confirms no application or harness changes were applied for this run.

Runtime: OpenJDK `21.0.13.0.3` (build `21.0.13.0.3+1-1-22.04.1-Ubuntu`). Evidence: `.runtime-parity/20261112T012535Z-5a80fb/` (local, not committed). Harness regression tests: 6 passed.

Result: **PASS**.

## Full Java 21 reactor

Command: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B clean install`.

```text
BUILD SUCCESS
{"errors": 0, "failures": 0, "skipped": 22, "tests": 213}
```

## Coverage against AB-533

Aggregate JaCoCo counters from the full build, captured before packaged tests. Both exact fractions must meet the baseline; no rounded-percentage comparison or lowered threshold.

| Counter | Baseline covered / total | Current covered / total | Baseline | Current | Gate |
| --- | ---: | ---: | ---: | ---: | --- |
| LINE | 8602 / 30779 | 10618 / 31015 | 27.9476% | 34.2350% | PASS |
| BRANCH | 1286 / 7652 | 1681 / 7778 | 16.8061% | 21.6122% | PASS |

## Packaged runtime and REST/JWT contract

The same eight enabled AB-533 scenarios run over HTTP against the packaged JAR: admin JWT, customer registration/login/profile, admin authorization denials, product CRUD, cart quantity persistence, offline moneyorder checkout, services Basic Auth, and health. KF-1 (deleted-product GET) stays disabled as in the baseline. The ninth enabled check compares OpenAPI and operational endpoints. The embedded suite still mocks SMTP; packaged runs deliver real composed mail to a local SMTP sink. Each checkout must deliver two confirmations with its exact order subject: one to its unique customer and one to the merchant. Registration messages and confirmations for other orders cannot satisfy the gate. Bodies are discarded; only subject/envelope metadata is held in memory.

| Runtime | Executed / skipped | Failed / errors | Operations (baseline / current) | Removed | Added | Health / Swagger / Info |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| h2 | 9 / 1 | 0 / 0 | 308 / 308 | 0 | 0 | UP / 200 / 200 |
| mysql | 9 / 1 | 0 / 0 | 308 / 308 | 0 | 0 | UP / 200 / 200 |

H2 uses a copy of the tracked H2 2.x seed and files with the default/docker H2 profile. MySQL uses the repository MySQL profile in an isolated `mysql:8.0.44` container, bound to loopback. The harness never accepts an external database address and never changes production configuration.

Checkout SMTP (h2): `{"customer": 1, "merchant": 1, "messages": 2}`.

### h2 additive operations

None.

Metadata changed on 308 preserved operations; the complete normalized inventory and metadata-change list are saved in this run's evidence directory. Operation identities include exact path variable names.

Changed metadata fields: `{"parameters": 250, "requestMediaTypes": 202, "responseMediaTypes": 308, "security": 0}`.

The Swagger 2 → OpenAPI 3 normalization exposes media-type declarations and corrected parameter locations (for example, an unspecified `store` parameter is now explicitly `query`). These are documentation differences; the AB-533 HTTP scenarios check the representative REST/JWT behavior separately.

Checkout SMTP (mysql): `{"customer": 1, "merchant": 1, "messages": 2}`.

### mysql additive operations

None.

Metadata changed on 308 preserved operations; the complete normalized inventory and metadata-change list are saved in this run's evidence directory. Operation identities include exact path variable names.

Changed metadata fields: `{"parameters": 250, "requestMediaTypes": 202, "responseMediaTypes": 308, "security": 0}`.

The Swagger 2 → OpenAPI 3 normalization exposes media-type declarations and corrected parameter locations (for example, an unspecified `store` parameter is now explicitly `query`). These are documentation differences; the AB-533 HTTP scenarios check the representative REST/JWT behavior separately.

## Existing MySQL dataset and process restart

The first process creates durable products/customers/orders and completes the smoke suite. After it stops, the harness snapshots all existing entity IDs and SM_SEQUENCER values. A second process opens the same database, creates three more of each entity through the real APIs and checkout, then stops. Read-only SQL checks every prior ID is retained, new IDs are disjoint and sequential starting at the existing source counter × 50, and counters advance. A third process verifies new entities still read back after another restart. This is an existing fixture dataset on the upgraded schema, not a production dump or a Hibernate 5 cutover rehearsal.

| Entity | Existing rows | Counter before → after | First expected | New IDs | Collisions |
| --- | ---: | --- | ---: | --- | ---: |
| product | 2 | 1 → 2 | 50 | 50, 51, 52 | 0 |
| customer | 4 | 1 → 2 | 50 | 50, 51, 52 | 0 |
| order | 2 | 1 → 2 | 50 | 50, 51, 52 | 0 |

## Reproduce and final sign-off

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
python3 tools/runtime-parity/run.py --report docs/upgrade/parity-report.md
```

Requires system Maven, Docker and Python 3; no additional test dependencies. MySQL is pulled if needed. Evidence (build and app logs, Surefire XML, OpenAPI/inventories/diffs, raw coverage, dataset snapshots, result JSON) is preserved under `.runtime-parity/<run>/`, outside Maven's cleaned target directories. Tokens and database passwords are not written to the report. The runner exits nonzero on any failed gate, preserves diagnostics, terminates app processes, and stops only its own uniquely named MySQL container. Stopped containers are retained for diagnosis and can be removed explicitly by their printed name.

Final sign-off completed on the source commit above after all other Wave 3 tickets merged. No removed/renamed operations or additive operations were found on either database. Metadata differences remain the documented Swagger 2 → OpenAPI 3 normalization; no security metadata differences were found. No load/performance or frontend repository verification is claimed.

No gate failures occurred. MySQL emitted transient socket-not-ready/access-denied messages during container initialization; readiness subsequently succeeded and all MySQL smoke, contract, sequence-continuity, and restart-readback gates passed. This is a fresh local MySQL 8 fixture dataset, not a production-data migration rehearsal.
