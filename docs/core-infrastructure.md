# Core infrastructure configuration (sm-core)

Configuration reference for the storage, email, search and cache integrations in `sm-core`.
Properties live in `sm-core/src/main/resources/shopizer-core.properties` (and the per-profile
copies under `sm-core/src/main/resources/profiles/<profile>/`), except SMTP settings which are
in `sm-core/src/main/resources/email.properties`.

## Content storage (CMS)

| Property | Values / meaning |
|---|---|
| `config.cms.method` | `default` (embedded Infinispan file store), `httpd`, `aws` (S3), `gcp` (Google Cloud Storage) |
| `config.cms.contentUrl` | Public base URL images/files are served from (e.g. `https://s3.ca-central-1.amazonaws.com/<bucket>`) |
| `config.cms.static.path` | Path segment for static content, default `/static` |
| `config.cms.store.location` / `config.cms.files.location` | Infinispan single-file-store folders (`default` method) |
| `config.cms.http.path.location` | htdocs root (`httpd` method) |
| `config.cms.aws.bucket` | S3 bucket (`aws` method). Defaults to `shopizer-content` when blank |
| `config.cms.aws.region` | S3 region. Defaults to `us-east-1` when blank. Accepts `ca-central-1` or `CA_CENTRAL_1` |
| `config.cms.gcp.bucket` | GCS bucket (`gcp` method) |

### S3 (AWS SDK for Java v2)

`S3StaticContentAssetsManagerImpl` and `S3ProductContentFileManager` use the AWS SDK v2
`S3Client` (`software.amazon.awssdk:s3`, version from the `aws-sdk-v2.version` property / AWS BOM).
Objects are written with `public-read` ACL and the uploaded content type, as before. Listing uses
the `ListObjectsV2` paginator, so prefixes with more than 1000 objects are now fully returned.

Note: buckets created after April 2023 have ACLs disabled by default ("Bucket owner enforced").
Because uploads set `public-read`, either enable ACLs on the bucket or switch it to a bucket policy
granting public read and leave Object Ownership on "Bucket owner preferred".

Credentials come from the SDK v2
[default credentials provider chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html):
`AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` (/ `AWS_SESSION_TOKEN`) environment variables,
`aws.accessKeyId` / `aws.secretAccessKey` JVM system properties, `~/.aws/credentials` profiles
(`AWS_PROFILE`), web identity tokens, ECS container credentials and EC2 instance profiles.
Migration note: SDK v1 also accepted the `aws.secretKey` system property; SDK v2 only reads
`aws.secretAccessKey`.

## Email

| Property | Values / meaning |
|---|---|
| `config.emailSender` | `default` (SMTP via Spring `JavaMailSender`) or `ses` (Amazon SES) |
| `config.emailSender.region` | SES region, e.g. `US_EAST_1` or `us-east-1` (`ses` only) |
| `mailSender.protocol` / `mailSender.host` / `mailSender.port` | SMTP server (`default` only) |
| `mailSender.username` / `mailSender.password` | SMTP credentials |
| `mailSender.mail.smtp.auth` / `mail.smtp.starttls.enable` | JavaMail properties |

SMTP settings saved for a store in the admin console (`EmailConfig`) override the properties above
at send time.

- SMTP: `spring-boot-starter-mail` (Jakarta Mail 2.x, Eclipse Angus implementation). The
  `mailSender` bean is still defined in `spring/shopizer-core-modules.xml`, so the Spring Boot
  `spring.mail.*` properties are **not** used.
- SES: `SESEmailSenderImpl` uses the AWS SDK v2 `SesClient` (`software.amazon.awssdk:ses`) with the
  same default credentials chain as S3. The sender (`Email.fromEmail`) must be a verified SES identity.

## Search

Product search goes through the `com.shopizer:shopizer-search-opensearch-spring-boot-starter`
abstraction (`SearchModule` / `search.*` properties), which uses the OpenSearch 2.x client. The
application has no direct dependency on the Elasticsearch client; the unused
`elasticsearch.version` (7.5.2) build property has been removed. The `elasticsearch.*` keys still
present in the properties files are not read by any code.

| Property | Values / meaning |
|---|---|
| `search.host[n].scheme` / `.host` / `.port` | OpenSearch nodes |
| `search.credentials.username` / `.password` | Basic-auth credentials |
| `search.clusterName` | Cluster name |
| `search.jksAbsolutePath` | Optional trust store for TLS |
| `search.searchLanguages` | Comma separated languages to index (e.g. `en,fr`) |
| `search.noindex` | `true` disables indexing (default profile) |

## Caching

- Spring Cache (`@Cacheable`) and the Hibernate second-level cache use Caffeine
  (`spring/shopizer-core-cache.xml`, `DataConfiguration`).
- Infinispan 9.4 (`infinispan-core`, `infinispan-tree`) is kept **only** as the embedded file store
  for the `default` CMS method (`config.cms.method=default`): product images, store logos and
  content files are persisted as a `TreeCache` backed by a single-file store under
  `config.cms.store.location` / `config.cms.files.location`.

### Infinispan evaluation

Infinispan is not used as a cache in this application, so "replace with Spring Cache + Caffeine"
does not apply to it: Caffeine is an in-memory cache and cannot replace a persistent file store.
Options for the CMS store:

1. **Keep Infinispan 9.4 (current).** Works on Java 21 / Spring Boot 3 (embedded, no
   `javax`/`jakarta` API surface). Needs Caffeine 2.9.x for its internal data container, which is
   why `caffeine.version` is pinned to 2.9.3.
2. **Upgrade to Infinispan 15.x.** The `infinispan-tree` module was removed upstream, so
   `CacheManagerImpl` and the `infinispan` CMS managers would have to be rewritten against a flat
   `Cache<String, byte[]>`, and the single-file-store format changed, so existing
   `files/store/*.dat` repositories (including the demo data shipped in `sm-shop/files`) would need
   a one-off export/import.
3. **Replace the `default` CMS method with plain filesystem storage** (the `local`/`httpd`
   managers already do this) and retire Infinispan.

Recommendation: keep option 1 for this upgrade; plan option 3 as a follow-up (it removes the
dependency and the Caffeine 2.x pin) together with a migration that copies existing
Infinispan-stored files to disk.
