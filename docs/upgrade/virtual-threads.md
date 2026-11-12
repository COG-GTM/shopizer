# AB-550: Java 21 virtual threads and mechanical deprecation cleanup

## Decision

Keep virtual threads **OFF by default**. Opt in with
`SHOPIZER_VIRTUAL_THREADS=true` or `--spring.threads.virtual.enabled=true`.
The native Spring Boot 3.5 property configures Tomcat. Shopizer overrides
`AsyncConfigurer`, so relying on Boot's auto-configured task executor alone
would not switch its `@Async` methods. `AsyncConfig` now selects either the
existing five-platform-thread pool or a virtual-thread-per-task executor,
managed as a singleton bean and closed on shutdown. The application event
multicaster also follows the same property; its default remains platform
threads. No new libraries were added.

Local H2 reads do not establish a production benefit: throughput changed
between approximately -6.3% and +3.2%, with mixed tail latency. Framework
pinning was observed during initial concurrent requests (below). Revisit
default ON only after representative external-database, write, email and
integration workloads, longer repeated trials, and memory/CPU measurements.
Virtual threads do not increase JDBC connection-pool capacity or make CPU-bound
work faster.

## Reproduction and measurements

Measured on this Ubuntu development host, OpenJDK 21.0.13, 8 available CPUs,
approximately 32 GiB RAM, Boot 3.5.16 and embedded Tomcat 10.1.55.
Both modes used the same built jar and copied H2 seed database plus CMS files
outside the checkout, on port 8081. The seed on this branch is
`SALESMANAGER.mv.db`, not the older H2 `.h2.db` format.

From the repository root, after a successful build:

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
REPO="$PWD"
R="$HOME/shopizer-runtime"
mkdir -p "$R/config"
# For a fresh runtime directory only:
cp sm-shop/SALESMANAGER.mv.db "$R/"
cp -r sm-shop/files "$R/"
cp sm-shop/src/main/resources/profiles/docker/database.properties "$R/config/"
cd "$R"
"$JAVA_HOME/bin/java" -Djdk.tracePinnedThreads=full \
  -Dloader.path="$R/config" -cp "$REPO/sm-shop/target/shopizer.jar" \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --server.port=8081 --spring.threads.virtual.enabled=false > off-app.log 2>&1
```

Run the server in a separate terminal; wait for `Started ShopApplication`
before running the load client. Stop the process before restarting with
`--spring.threads.virtual.enabled=true` and `on-app.log`. Do not rebuild while
running the jar. Do not copy over a live H2 database.

```sh
python3 docs/upgrade/virtual-threads-benchmark.py off
# After restarting with virtual threads enabled:
python3 docs/upgrade/virtual-threads-benchmark.py on
```

The included dependency-free Python client uses urllib and a thread pool.
Each endpoint has 100 warmup requests followed by 2,000 timed requests at
concurrency 4 and 32, in that order. Each request opens its own HTTP connection;
latency includes local connection overhead, JSON parsing and complete body
reading. p50/p99 use nearest-rank percentiles. An error is a non-200 response,
empty/invalid JSON, transport error or timeout (30 seconds). Results are
single-run local comparisons, not capacity estimates; client and server share
the host, logging and JIT warmup can affect results, and no statistical
significance is claimed.

Both paths below include `?store=DEFAULT`.

| Endpoint | Concurrency | VT | Requests/s | p50 ms | p99 ms | Errors |
| --- | ---: | --- | ---: | ---: | ---: | ---: |
| products | 4 | off | 805.53 | 4.14 | 17.21 | 0 |
| products | 4 | on | 803.44 | 4.27 | 15.33 | 0 |
| products | 32 | off | 1302.69 | 22.08 | 54.7 | 0 |
| products | 32 | on | 1344.57 | 21.43 | 51.17 | 0 |
| category | 4 | off | 1483.25 | 2.38 | 8 | 0 |
| category | 4 | on | 1414.13 | 2.52 | 10.69 | 0 |
| category | 32 | off | 2055.79 | 13.94 | 34.99 | 0 |
| category | 32 | on | 1925.8 | 14.94 | 36.06 | 0 |

Both modes returned nonempty JSON and HTTP 200 for both read endpoints.
`POST /api/v1/private/login` with the seeded
`admin@shopizer.com` / `password` credentials returned HTTP 200 and a nonempty
token in both modes. Tokens were not recorded. All 16,000 timed requests
completed without errors.

## Pinning

Static check:

```sh
rg -n 'synchronized' sm-*/src/main --glob '*.java'
```

No matches: there are no application-source synchronized methods/blocks to
mechanically replace around JDBC or other blocking operations in this tree.
This does not exclude locks inside dependencies.

Both processes were launched with `-Djdk.tracePinnedThreads=full`. The final
VT-on run emitted one distinct trace during the concurrent initial GETs,
immediately after dispatcher initialization:

```text
VirtualThread[#81,tomcat-handler-1]/runnable@ForkJoinPool-1-worker-2 reason:MONITOR
    java.lang.VirtualThread.park
    java.util.concurrent.locks.ReentrantLock.lock
    org.springframework.util.ConcurrentReferenceHashMap$Segment.doTask
    org.springframework.core.annotation.AnnotationsScanner.getDeclaredAnnotations
    org.springframework.web.method.annotation.SessionAttributesHandler.<init>
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.lambda$getSessionAttributesHandler$3
    java.util.concurrent.ConcurrentHashMap.computeIfAbsent <== monitors:1
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.getSessionAttributesHandler
    org.springframework.web.servlet.DispatcherServlet.doDispatch
    org.springframework.web.servlet.FrameworkServlet.doGet
    ...
    org.apache.coyote.http11.Http11Processor.service
    ...
    java.lang.VirtualThread.run
```

This is observed Spring MVC annotation/session-handler cache initialization:
a JDK ConcurrentHashMap monitor is held while waiting for a Spring cache's
ReentrantLock. The stack verifies the Tomcat request actually runs on a virtual
thread and reaches this blocking path. It contains neither JDBC/H2 calls nor an
application-owned synchronized block; replacing application JDBC locking would
not fix this trace. The initial 400-request exploratory run produced no trace,
so the finding is contention-sensitive, not an assertion that every request
pins. No other distinct pinning trace was printed in the final run; the JVM can
deduplicate traces, so one printed trace is not a total event count. No duration
or throughput impact was established.

No lock rewrite was made: the observed monitor is inside Spring/JDK code,
not a small mechanical Shopizer edit. Track as **Sev 3 technical debt** for the
opt-in mode: no HTTP errors or production impact were observed, and the default
platform-thread mode is the workaround. External JDBC drivers, payment,
shipping, SMTP and other unexercised paths remain unverified for pinning.

## Deprecation inventory

Full clean reactor compilation of the epic base and this change, on JDK 21:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
JDK_JAVAC_OPTIONS='-Xmaxwarns 10000' \
mvn -B -DskipTests -Dmaven.compiler.fork=true \
  -Dmaven.compiler.showDeprecation=true clean compile > compile.log 2>&1
rg '\[WARNING\].*deprecat' compile.log | wc -l
```

`showDeprecation` enables javac's deprecation diagnostics (including removal
warnings). Forking lets `JDK_JAVAC_OPTIONS` lift javac's default 100-warning cap,
which otherwise hides many sm-shop diagnostics. Count printed warning lines,
not unique symbols; repeated javac annotation-processing warnings and the two
`dep-ann` warnings are included. Module attribution follows the Maven compiler
goal header, including one location-less sm-shop warning. Both compiles succeeded.
Base: `origin/epic/ab-531-v2` at `0f235b4f`.

| Module | Before | After | Reduction |
| --- | ---: | ---: | ---: |
| shopizer parent | 0 | 0 | 0 |
| sm-core-model | 19 | 9 | 10 |
| sm-core-modules | 1 | 0 | 1 |
| sm-core | 62 | 16 | 46 |
| sm-shop-model | 51 | 51 | 0 |
| sm-shop | 185 | 165 | 20 |
| coverage | 0 | 0 | 0 |
| **Total** | **318** | **241** | **77** |

Mechanical replacements: boxed constructors (including preserving the
double-to-float conversion), Locale factories, RoundingMode, independently
built AWS default credentials providers, Hibernate SQLOrder with unchanged SQL
clauses, Spring Data lazy-reference accessors, Spring Security provider
constructors, the nondeprecated ResponseErrorHandler overload, Commons null
validation with the identical default message, case-sensitive bearer-prefix
removal, JJWT builder methods and equivalent amount-format helpers.

The JWT audience intentionally remains a JSON **string**, not an array; the
supported generic `claim("aud", audience)` API preserves it. The JWT test now
asserts the raw JSON shape as well as the existing HS512, validation and refresh
behavior. No REST model or published route was migrated.

This is not a zero-warning migration. Remaining warnings are principally
legacy order/content v0 models and populators, repository/service compatibility
methods, CreditCardUtils, Hibernate REPLICATE cascade metadata, schema constants
and deprecated annotations in internal APIs. They were retained rather than
remove public compatibility paths, change persistence semantics or suppress
warnings. Four URL constructor diagnostics remain: blindly using URI changes
accepted input syntax. Commons Lang StringEscapeUtils remains rather than
introduce another dependency for a sanitizer migration. RestTemplate is not
deprecated in the Spring 6.2 line used here.

An attempted switch of Stripe's amount helper exposed its existing SDK tests'
explicit mock of `getAdminFormatedAmount`; those six call sites were restored
without weakening or rewriting the tests. Five equivalent callers elsewhere
use the existing `getStringAmount` implementation. The final SDK tests pass.

## Verification

`VirtualThreadsTest` covers absent/false/true/invalid properties, actual
`Thread.isVirtual()` execution on the configured @Async executor and event
multicaster, and singleton wiring. The first isolated-context version lacked a
property-placeholder configurer; the test runner now includes Boot's
PropertyPlaceholderAutoConfiguration. The production application uses Boot's
normal configuration. No verification-harness files were changed.

Final full `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B install`:

```text
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 118, Failures: 0, Errors: 0, Skipped: 5
[INFO] Tests run: 80, Failures: 0, Errors: 0, Skipped: 7

[INFO] Reactor Summary for shopizer 3.2.5:
[INFO] shopizer ........................................... SUCCESS [  0.407 s]
[INFO] sm-core-model ...................................... SUCCESS [  0.604 s]
[INFO] sm-core-modules .................................... SUCCESS [  3.707 s]
[INFO] sm-core ............................................ SUCCESS [ 29.018 s]
[INFO] sm-shop-model ...................................... SUCCESS [  0.427 s]
[INFO] sm-shop ............................................ SUCCESS [ 37.055 s]
[INFO] Aggregate test coverage ............................ SUCCESS [  1.112 s]
[INFO] BUILD SUCCESS
[INFO] Total time:  01:12 min
```

Total: **201 tests, 0 failures, 0 errors, 12 skipped** (189 executed).
ApiContractTest: 1/0/0/0; JWTTokenUtilTest: 6/0/0/0; VirtualThreadsTest:
4/0/0/0. BehaviourSmokeTest: 9/0/0/1, including the checkout test; the
reported product-id-11 failure did not occur.

Ignored `sm-core/files` was absent for the first build and moved aside before
the final build after tests recreated it. Tracked CMS test data was restored.
CRLF files retain their line endings. No dependencies, CI, Docker, Drools or
Infinispan configuration were changed. No UI surface changed; verification was
API/runtime and automated tests, not a visual browser check.
