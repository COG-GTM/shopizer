# Hibernate 6 readiness on Hibernate 5 (AB-541)

This preparation retains Spring Boot 2, Hibernate 5 and `javax.persistence`.
It does not change REST/JWT payloads, custom Hibernate types or ID generator settings.

- `Description` is a mapped superclass, not an entity inheritance root. Its concrete
  description entities do not have entity subclasses requiring table-per-class inheritance.
- `USERCONNECTION` and its existing uniqueness constraint are declared on the concrete
  `UserConnection` entity, rather than its mapped superclass.
- The customer/group, user/group and product/category associations each own their
  join table. Their columns are writable consistently. The similarly named entity
  primary keys belong to different tables, not duplicate mappings of those columns;
  making the associations read-only would lose relationship inserts.
- Native product SKU lookups accept any JDBC `Number`, not only `BigInteger`.
- `SalesManagerEntityServiceImpl` normalizes a boxed `Long` zero ID to `null` before
  single and bulk saves. Subclass paths that save directly through a repository call
  the same guard. Positive, null, Integer and composite IDs retain their semantics.
  Entity ID accessors use boxed types; no primitive Long ID requires special handling.
- The `OrderFacadeImpl.refreshOrder` customer-ID reset is intentionally retained:
  it clears a potentially previously assigned customer ID when switching to guest
  checkout. Normalizing zero at persistence does not replace that reset operation.
  The API model's default zero ID is retained for compatibility.

Regression coverage: `EntityIdReadinessTest` exercises normalization and native result
types, including empty/error paths. `ZeroIdEntityCreationIntegrationTest` creates
products, customers, orders and categories through services against the existing H2
test context, asserting new rows and generated IDs. These tests run on Hibernate 5;
they are not a claim that a Hibernate 6 upgrade has already been validated.

Run from the repository root:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -B install
```
