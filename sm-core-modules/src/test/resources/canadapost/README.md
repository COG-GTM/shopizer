# Canada Post reference contracts

The XML responses are handcrafted Rating API v4 examples replayed by the local
HTTP server. No live carrier account is used. The server returns HTTP 400 for
messages and HTTP 200 for the other fixtures.

The .golden.json and .request.txt files were captured by invoking the **original**
2.17.0 autoconfigure JAR against that server. The initial capture occurred
before removing the starter. The original JAR must precede the installed
sm-core-modules JAR on any subsequent reference-capture classpath because both
contain the retained package names. Reference JAR SHA-256:

`147c13019fece7df44349bc28f6987ae8e3421474688793d99d2bcf0f8e8780c`

Reproduction from the repository root, while the original starter dependency
is available in the reference checkout:

```sh
mvn -B -pl sm-core dependency:build-classpath -Dmdep.outputFile=$HOME/cp-original-classpath.txt
mkdir -p "$HOME/cp-baseline-classes"
ORIGINAL="$HOME/.m2/repository/com/shopizer/shipping-canadapost-spring-boot-autoconfigure/2.17.0/shipping-canadapost-spring-boot-autoconfigure-2.17.0.jar"
CP="$ORIGINAL:$(cat "$HOME/cp-original-classpath.txt")"
javac -proc:none -cp "$CP" -d "$HOME/cp-baseline-classes" sm-core-modules/src/test/java/com/shopizer/modules/shipping/canadapost/CanadaPostContractTest.java
java -Dcanadapost.fixtures=sm-core-modules/src/test/resources/canadapost -cp "$HOME/cp-baseline-classes:$CP" com.shopizer.modules.shipping.canadapost.CanadaPostContractTest
```

Do not run that main method with the port first on the classpath to regenerate
expectations. Normal Maven tests only read goldens; they do not update them.
JSON includes every ShippingOption property, sorted by code. Request trees
include namespace URIs and ordered element values, rather than XML declaration
or namespace prefixes. Empty request files explicitly prove no HTTP exchange.
The international case is rejected by the original before any HTTP request;
its XML example is retained to document the otherwise unreachable response.
