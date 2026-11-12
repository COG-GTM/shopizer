# Infinispan 15 CMS stores (AB-546)

Shopizer uses `infinispan-core` **15.0.22.Final** on Java 21. These regular 15.x
coordinates use Jakarta Transaction 2.x; no `-jakarta` classifier is needed.
The managed (but unused) `infinispan-cachestore-jdbc` version follows the same
property. There is no JDBC CMS store configuration or runtime JDBC-store
dependency to port. The tree dependency/property was already removed by AB-540.
The temporary Caffeine 2 override is gone; Spring Boot manages Caffeine 3.

## Configuration and compatibility

Keep the AB-540 flat String path keys and byte-array values, cache names (including
the historic `DownlaodRepository` spelling), non-segmented SingleFileStore,
write-behind, no passivation, no preload and no shared store. Infinispan 15's
default ProtoStream persistence marshaller handles these built-in types; no Java
serialization allow-list, custom schema or remote server is introduced. Spring's
CMS XML bean wiring and the CMS content/image managers are unchanged.

SingleFileStore is deprecated upstream, but remains supported in 15. Moving to
SoftIndexFileStore or another CMS backend is deliberately out of scope.

**Do not point 15 at a 9.4 store.** Files with header `FCS1` require an offline
migration even if they already contain AB-540 flat entries. Startup rejects them
before starting Infinispan, without modifying the source or creating its temporary
migration file. This is distinct from migrating pre-AB-540 tree keys: the procedure
below is only for flat String-key / byte-array CMS stores. Tree stores must first
be exported through the old CMS API and re-imported through the AB-540 CMS API;
do not add the removed TreeCache classes back to the application.

## Offline 9.4 flat-store migration

1. Stop **all** writers and back up the entire CMS `files/` directory, together
   with the database. Keep that immutable backup for rollback. Work on a copy.
2. Use the **15.0.22.Final Infinispan CLI** (`bin/cli.sh` from the official
   Infinispan distribution) and JDK 21. No migration libraries belong in the
   application's runtime classpath.
3. For each cache, create a properties file like this, with absolute paths:

   ```properties
   source.type=SINGLE_FILE_STORE
   source.cache_name=DownlaodRepository
   source.version=9
   source.location=/migration/source/files/store
   source.marshaller.externalizers=21:org.infinispan.commons.marshall.WrappedByteArray$Externalizer
   target.type=SINGLE_FILE_STORE
   target.cache_name=DownlaodRepository
   target.location=/migration/target/files/store
   ```

   The `source.location`/`target.location` values are **directories**, not `.dat`
   files. Leave segment counts unset (non-segmented). The source externalizer is
   important: 9.4 wraps stored byte arrays with internal externalizer ID 21,
   which the migration tool does not register by default. It only applies to the
   offline reader; the application retains the default 15.x marshaller.

4. Run `bin/cli.sh migrate store -p /migration/DownlaodRepository.properties`.
   The equivalent Java entry point is
   `org.infinispan.tools.store.migrator.StoreMigrator <properties-file>` with
   `infinispan-tools` and its JDBC/RocksDB store dependencies on the tool's
   classpath. This Java entry point was used to migrate and verify the bundled
   seeds. Repeat for all three caches:

   | Cache | Default relative directory |
   | --- | --- |
   | `StoreRepository` | `files/store` |
   | `DownlaodRepository` | `files/store` |
   | `FilesRepository` | `files/repos` |

   **Never reuse a source directory as the target.** The upstream migrator
   purges its target cache. Use a new empty target per migration attempt, and
   discard failed output rather than installing it.

5. Check the command exits successfully, then start Shopizer on a copy of the
   target stores. List and download all expected content; compare file counts
   and SHA-256 hashes with the source CMS API/export. Restart the app and repeat.
   Do not cut over on warnings about skipped/unreadable entries. Preserve file
   ownership/permissions when installing the validated target directory.
6. Roll back by stopping the app, restoring **the backed-up 9.4 files**, and
   running the prior application build. Never open 15.x files with 9.4. Reconcile
   any uploads after cut-over before rollback; no reverse migration is provided.

The committed seed files have been converted, not emptied. The only seeded entry
is `product-fileDEFAULT/LOGO/image.jpg` in `DownlaodRepository`, 29,968 bytes,
SHA-256 `64473d10f4fb4ea1afee81cb40afafd385361091556d8ac224a9ed26d809f36a`.
Both 9.4 and 15 read the same bytes. The two other seeded stores remain empty.

## Verification

- AB-540 `FlatCacheContentTest` and `CacheManagerPersistenceTest` are unchanged.
- `CmsStoreRestartTest` starts three separate JVMs: write 128 KiB content through
  CMS managers in all three persistent stores, exit via the production shutdown
  hook (flushing write-behind), read/list/delete after restart, then verify the
  deletes after a second restart. It also verifies legacy-format rejection does
  not modify the source file.
- Full suite: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B install`.
- CI runs `./mvnw -B verify` on JDK 21. The system Maven command above is used
  locally, as instructed for this upgrade wave.

Upstream references:
- [15.x persistent storage and store migrator](https://infinispan.org/docs/15.0.x/titles/configuring/configuring.html#cache_store_migrator)
- [15.0.22 SingleFileStore format compatibility](https://github.com/infinispan/infinispan/blob/15.0.22.Final/core/src/main/java/org/infinispan/persistence/file/SingleFileStore.java)
